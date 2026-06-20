package me.codeleep.victor.web.websocket.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.utils.StrUtil;
import me.codeleep.victor.core.engine.InterviewContextBuilder;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.interviewer.Interviewer;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.core.service.interview.InterviewService;
import me.codeleep.victor.infra.agent.core.AgentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * 面试Agent文本处理器。
 *
 * <p>使用常驻的 AgentScope ReActAgent（封装在 {@link Interviewer} 中）驱动面试流程。
 * 面试官 Agent 自主评估候选人回答,决定追问或调用 advance_to_next_question 工具切换到下一道预备题。
 * 处理器在每轮回答后做单题追问次数兜底:达到上限则强制推进,防止面试官死磕一题。</p>
 *
 * <p>题目推进严格基于 interview_question 预备题表,不即时生成新题。</p>
 *
 * <p>配置方式：{@code speech.processor=interview}</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "speech.processor", havingValue = "interview")
public class InterviewTextProcessor implements TextProcessor {

    /** 单题最大追问次数(候选人回答次数),达到即强制推进下一题 */
    @Value("${interview.max-follow-ups:5}")
    private int maxFollowUps;

    private final InterviewTurnMapper turnMapper;
    private final InterviewService interviewService;
    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewContextBuilder interviewContextBuilder;

    public InterviewTextProcessor(InterviewTurnMapper turnMapper,
                                   InterviewService interviewService,
                                   InterviewConfigMapper interviewConfigMapper,
                                   InterviewQuestionMapper interviewQuestionMapper,
                                   InterviewContextBuilder interviewContextBuilder) {
        this.turnMapper = turnMapper;
        this.interviewService = interviewService;
        this.interviewConfigMapper = interviewConfigMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.interviewContextBuilder = interviewContextBuilder;
        log.info("InterviewTextProcessor initialized");
    }

    @Override
    public Flux<String> process(ProcessingContext context, String text) {
        log.info("[Interview] Processing: sessionId={}, text={}", context.getSessionId(), text);

        final Long interviewSessionId = context.getAttribute(ProcessingContext.ATTR_INTERVIEW_SESSION_ID);
        final Long currentQuestionId = context.getAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID);

        return Flux.create(emitter -> {
            try {
                Interviewer interviewer = context.getAttribute(ProcessingContext.ATTR_INTERVIEWER);
                if (interviewer == null) {
                    log.error("[Interview] Interviewer 未初始化，请先发送 start 或 reconnect 命令");
                    emitter.error(new IllegalStateException("Interviewer 未初始化，请先发送 start 或 reconnect 命令"));
                    return;
                }

                // 1. 持久化用户输入
                if (interviewSessionId != null) {
                    String displayText = context.getAttribute(ProcessingContext.ATTR_INPUT_TEXT);
                    List<Object> attachments = context.getAttribute(ProcessingContext.ATTR_ATTACHMENTS);
                    saveTurn(interviewSessionId, currentQuestionId, Speaker.USER,
                            displayText != null ? displayText : text,
                            attachments);
                }

                // 2.Agent 自主评估追问价值:有则追问,无则调用 advance_to_next_question 工具切下一题
                StringBuilder sentenceBuffer = new StringBuilder();
                StringBuilder fullResponse = new StringBuilder();

                interviewer.chat(text)
                        .doOnNext(result -> {
                            if (result.getType() == AgentResult.EventType.ANSWER
                                    && result.getContent() != null && !result.getContent().isEmpty()) {
                                sentenceBuffer.append(result.getContent());
                                fullResponse.append(result.getContent());

                                String buffered = sentenceBuffer.toString();
                                int lastSplit = StrUtil.findLastSentenceEnd(buffered);
                                if (lastSplit >= 0) {
                                    String sentences = buffered.substring(0, lastSplit + 1);
                                    sentenceBuffer.setLength(0);
                                    sentenceBuffer.append(buffered.substring(lastSplit + 1));

                                    for (String sentence : StrUtil.splitSentences(sentences)) {
                                        if (!sentence.trim().isEmpty()) {
                                            emitter.next(sentence.trim());
                                        }
                                    }
                                }
                            }
                        })
                        .doOnComplete(() -> {
                            if (!sentenceBuffer.isEmpty()) {
                                String remaining = sentenceBuffer.toString().trim();
                                if (!remaining.isEmpty()) {
                                    fullResponse.append(remaining);
                                    emitter.next(remaining);
                                }
                            }

                            // 持久化面试官回复(题目可能已被 Agent 工具切换,按 DB 当前题 ID 落库)
                            Long spokenQuestionId = resolveCurrentQuestionId(interviewSessionId, currentQuestionId);
                            if (interviewSessionId != null && !fullResponse.isEmpty()) {
                                saveTurn(interviewSessionId, spokenQuestionId, Speaker.AI, fullResponse.toString(), null);
                            }
                            // 同步 ProcessingContext 的当前题目 ID
                            if (spokenQuestionId != null && !spokenQuestionId.equals(currentQuestionId)) {
                                context.setAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID, spokenQuestionId);
                            }

                            // 3. 单题追问次数兜底:达到上限则强制推进下一题
                            if (interviewSessionId != null) {
                                applyFollowUpLimit(context, interviewSessionId, interviewer);
                            }

                            log.info("[Interview] Stream completed: sessionId={}", context.getSessionId());
                            emitter.complete();
                        })
                        .doOnError(e -> {
                            log.error("[Interview] Stream error: sessionId={}", context.getSessionId(), e);
                            emitter.error(e);
                        })
                        .blockLast();

            } catch (Exception e) {
                log.error("[Interview] Error: sessionId={}", context.getSessionId(), e);
                emitter.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 单题追问次数兜底:当前题候选人回答次数达到上限则强制推进。
     * 推进后同步面试官 Agent 的"当前题目"记忆。
     */
    private void applyFollowUpLimit(ProcessingContext context, Long interviewSessionId, Interviewer interviewer) {
        try {
            InterviewService.ForceAdvanceResult result =
                    interviewService.forceAdvanceIfLimitReached(interviewSessionId, maxFollowUps);
            if (!result.advanced()) {
                return;
            }
            context.setAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID, result.currentQuestionId());
            if (result.currentQuestionId() != null) {
                syncInterviewerQuestion(interviewSessionId, result.currentQuestionId(), interviewer);
                log.info("[Interview] 兜底强制推进完成: sessionId={}, questionId={}, finished={}",
                        interviewSessionId, result.currentQuestionId(), result.finished());
            } else {
                log.info("[Interview] 兜底强制推进:面试已结束, sessionId={}", interviewSessionId);
            }
        } catch (Exception e) {
            log.warn("[Interview] 兜底推进失败: sessionId={}", interviewSessionId, e);
        }
    }

    private void syncInterviewerQuestion(Long interviewSessionId, Long questionId, Interviewer interviewer) {
        try {
            InterviewConfig cfg = interviewConfigMapper.selectById(interviewSessionId);
            InterviewQuestion q = interviewQuestionMapper.selectById(questionId);
            if (cfg != null && q != null) {
                interviewer.updateCurrentQuestion(interviewContextBuilder.buildCurrentQuestionContext(cfg, q));
            }
        } catch (Exception e) {
            log.warn("[Interview] 同步面试官题目记忆失败: sessionId={}, questionId={}", interviewSessionId, questionId, e);
        }
    }

    private Long resolveCurrentQuestionId(Long interviewSessionId, Long fallback) {
        if (interviewSessionId == null) {
            return fallback;
        }
        try {
            InterviewConfig cfg = interviewConfigMapper.selectById(interviewSessionId);
            return cfg != null && cfg.getCurrentQuestionId() != null ? cfg.getCurrentQuestionId() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public String getName() {
        return "interview";
    }

    private void saveTurn(Long interviewSessionId, Long questionId, Speaker speaker, String content, List<Object> attachments) {
        try {
            if (questionId == null) {
                log.warn("[Interview] Skip saving turn because current question is missing: sessionId={}, speaker={}",
                        interviewSessionId, speaker);
                return;
            }
            InterviewTurn turn = new InterviewTurn();
            turn.setSessionId(interviewSessionId);
            turn.setQuestionId(questionId);
            turn.setTurnIndex(nextTurnIndex(interviewSessionId));
            turn.setSpeaker(speaker);
            turn.setContent(content);
            turn.setAttachments(attachments);
            turn.setIsFollowup(false);
            turn.setIsHint(false);
            turnMapper.insert(turn);
        } catch (Exception e) {
            log.error("[Interview] Failed to save turn: sessionId={}, speaker={}", interviewSessionId, speaker, e);
        }
    }

    private int nextTurnIndex(Long interviewSessionId) {
        InterviewTurn last = turnMapper.selectOne(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, interviewSessionId)
                        .orderByDesc(InterviewTurn::getTurnIndex)
                        .last("LIMIT 1")
        );
        return last != null && last.getTurnIndex() != null ? last.getTurnIndex() + 1 : 1;
    }
}