package me.codeleep.victor.web.websocket.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.utils.StrUtil;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * 面试Agent文本处理器。
 *
 * <p>使用 Victor Agent 框架驱动面试流程。将 ASR 识别的文本送入面试 Agent，
 * Agent 返回的文本按句子分割后流式返回给 TTS 合成。</p>
 *
 * <p>{@code AgentDefinition} 和 {@code AgentContext} 在会话初始化阶段由
 * {@code InterviewContextRestorer.ensureContext()} 构建并写入 {@code ProcessingContext}，
 * 本处理器只负责读取和使用。</p>
 *
 * <p>配置方式：{@code speech.processor=interview}</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "speech.processor", havingValue = "interview")
public class InterviewTextProcessor implements TextProcessor {

    private final AgentRunner runner;
    private final InterviewTurnMapper turnMapper;

    public InterviewTextProcessor(AgentRunner runner, InterviewTurnMapper turnMapper) {
        this.runner = runner;
        this.turnMapper = turnMapper;
        log.info("InterviewTextProcessor initialized");
    }

    @Override
    public Flux<String> process(ProcessingContext context, String text) {
        log.info("[Interview] Processing: sessionId={}, text={}", context.getSessionId(), text);

        final Long interviewSessionId = context.getAttribute(ProcessingContext.ATTR_INTERVIEW_SESSION_ID);
        final Long currentQuestionId = context.getAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID);

        return Flux.create(emitter -> {
            try {
                // 1. 从 ProcessingContext 获取 AgentContext（会话初始化时已创建）
                final AgentContext agentContext = context.getAttribute(ProcessingContext.ATTR_AGENT_CONTEXT);
                if (agentContext == null) {
                    log.error("[Interview] AgentContext 未初始化，请先发送 start 或 reconnect 命令");
                    emitter.error(new IllegalStateException("AgentContext 未初始化，请先发送 start 或 reconnect 命令"));
                    return;
                }

                // 2. 从 ProcessingContext 获取 AgentDefinition（会话初始化时已构建）
                AgentDefinition agentDef = context.getAttribute(ProcessingContext.ATTR_AGENT_DEFINITION);
                if (agentDef == null) {
                    log.error("[Interview] AgentDefinition 未初始化，请先发送 start 或 reconnect 命令");
                    emitter.error(new IllegalStateException("AgentDefinition 未初始化，请先发送 start 或 reconnect 命令"));
                    return;
                }

                // 3. 添加用户消息到对话历史
                agentContext.addUserMessage(text);
                if (interviewSessionId != null) {
                    String displayText = context.getAttribute(ProcessingContext.ATTR_INPUT_TEXT);
                    List<Object> attachments = context.getAttribute(ProcessingContext.ATTR_ATTACHMENTS);
                    saveTurn(interviewSessionId, currentQuestionId, Speaker.USER,
                            displayText != null ? displayText : text,
                            attachments);
                }

                // 4. 流式执行 Agent
                StringBuilder sentenceBuffer = new StringBuilder();
                StringBuilder fullResponse = new StringBuilder();

                runner.streamRun(agentDef, agentContext)
                        .doOnNext(result -> {
                            String content = result.getContent();
                            if (content != null && !content.isEmpty()) {
                                sentenceBuffer.append(content);
                                fullResponse.append(content);

                                // 按标点分句，逐句发送给 TTS
                                String buffered = sentenceBuffer.toString();
                                int lastSplit = StrUtil.findLastSentenceEnd(buffered);
                                if (lastSplit >= 0) {
                                    String sentences = buffered.substring(0, lastSplit + 1);
                                    sentenceBuffer.setLength(0);
                                    sentenceBuffer.append(buffered.substring(lastSplit + 1));

                                    // 按句分割逐个发送
                                    for (String sentence : StrUtil.splitSentences(sentences)) {
                                        if (!sentence.trim().isEmpty()) {
                                            log.info("[Interview] Sentence: {}", sentence.trim());
                                            emitter.next(sentence.trim());
                                        }
                                    }
                                }
                            }
                        })
                        .doOnComplete(() -> {
                            // 处理剩余不完整句子
                            if (!sentenceBuffer.isEmpty()) {
                                String remaining = sentenceBuffer.toString().trim();
                                if (!remaining.isEmpty()) {
                                    log.info("[Interview] Final: {}", remaining);
                                    fullResponse.append(remaining);
                                    emitter.next(remaining);
                                }
                            }

                            // 持久化 Agent 回复
                            if (interviewSessionId != null && !fullResponse.isEmpty()) {
                                agentContext.addAssistantMessage(fullResponse.toString());
                                saveTurn(interviewSessionId, currentQuestionId, Speaker.AI, fullResponse.toString(), null);
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
