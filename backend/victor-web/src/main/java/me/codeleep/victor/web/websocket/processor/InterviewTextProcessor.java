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
import me.codeleep.victor.core.service.interview.InterviewSessionService;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStreamChunkMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 面试Agent文本处理器。
 *
 * <p>使用常驻的 AgentScope ReActAgent（封装在 {@link Interviewer} 中）驱动面试流程。
 * 面试官 Agent 自主评估候选人回答,决定追问或调用 advance_to_next_question 工具切换到下一道预备题。
 * 处理器在每轮回答后做单题追问次数兜底:达到上限则强制推进,防止面试官死磕一题。</p>
 *
 * <p>题目推进严格基于 interview_question 预备题表,不即时生成新题。</p>
 *
 * <p>流式输出策略（降低候选人感知等待延迟）:
 * <ul>
 *   <li>THINKING/TOOL_RESULT 事件以细粒度增量实时下发(kind=thinking/tool),让候选人在 Agent 推理阶段即看到反馈;</li>
 *   <li>ANSWER 事件按完整句子切分下发(kind=answer),供前端展示与 TTS 合成;</li>
 *   <li>仅 answer 内容落库与喂 TTS,thinking/tool 不入库不合成。</li>
 * </ul>
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
    private final InterviewSessionService interviewSessionService;
    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewContextBuilder interviewContextBuilder;

    public InterviewTextProcessor(InterviewTurnMapper turnMapper,
                                   InterviewSessionService interviewSessionService,
                                   InterviewConfigMapper interviewConfigMapper,
                                   InterviewQuestionMapper interviewQuestionMapper,
                                   InterviewContextBuilder interviewContextBuilder) {
        this.turnMapper = turnMapper;
        this.interviewSessionService = interviewSessionService;
        this.interviewConfigMapper = interviewConfigMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.interviewContextBuilder = interviewContextBuilder;
        log.info("InterviewTextProcessor initialized");
    }

    @Override
    public Flux<StreamChunk> process(ProcessingContext context, String text) {
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
                //    thinking/tool 增量实时下发;answer 按句切分下发,并累积用于落库。
                StringBuilder sentenceBuffer = new StringBuilder();
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder thinkingBuffer = new StringBuilder();
                // 按 toolCallId 去重合并的工具事件: 流式 THINKING 增量会重复携带同一 ToolUseBlock,
                // 落库前合并为一个条目, 避免数据库存储上千条重复记录
                LinkedHashMap<String, AgentResult.ToolEvent> toolEventMap = new LinkedHashMap<>();
                AtomicInteger nullIdSeq = new AtomicInteger();
                StringBuilder toolTextFallback = new StringBuilder();

                interviewer.chat(text)
                        .doOnNext(result -> {
                            AgentResult.EventType type = result.getType();
                            String content = result.getContent();
                            // [DEBUG] 诊断事件分流:打印每个事件的类型/来源/工具事件
                            log.info("[Interview][DEBUG] event: type={}, sourceAgent={}, depth={}, contentLen={}, toolEvents={}",
                                    type, result.getSourceAgentKey(), result.getAgentDepth(),
                                    content == null ? 0 : content.length(),
                                    result.getToolEvents() == null ? 0 : result.getToolEvents().size());

                            // 推理思考:细粒度增量直发思考文本,让候选人立即看到 Agent 在思考;
                            // 同时该事件可能携带工具调用(call)块,一并结构化下发。
                            if (type == AgentResult.EventType.THINKING) {
                                // 先发思考文本(符合“先思考再行动”的自然顺序),
                                // 再发该事件携带的工具调用(call)块。
                                if (content != null && !content.isEmpty()) {
                                    thinkingBuffer.append(content);
                                    emitter.next(new StreamChunk(content,
                                            InterviewServerStreamChunkMessage.Kind.THINKING));
                                }
                                List<AgentResult.ToolEvent> toolCallEvents = result.getToolEvents();
                                if (toolCallEvents != null && !toolCallEvents.isEmpty()) {
                                    for (AgentResult.ToolEvent te : toolCallEvents) {
                                        // 流式 REASONING 增量会重复携带同一 ToolUseBlock,按 ID 去重
                                        String callId = te.getToolCallId();
                                        InterviewServerStreamChunkMessage.ToolData td =
                                                new InterviewServerStreamChunkMessage.ToolData(
                                                        callId, te.getName(), te.getArgs(), te.getResult());
                                        emitter.next(new StreamChunk(null,
                                                InterviewServerStreamChunkMessage.Kind.TOOL_CALL, td));
                                        // 落库前按 toolCallId 去重合并(流式增量重复携带同一 ToolUseBlock)
                                        mergeToolEvent(toolEventMap, nullIdSeq, te);
                                    }
                                }
                                return;
                            }

                            // 工具调用/结果:结构化下发,前端渲染为可展开卡片(name+args/result)
                            if (type == AgentResult.EventType.TOOL_RESULT
                                    || type == AgentResult.EventType.TOOL_CALL) {
                                List<AgentResult.ToolEvent> toolEvents = result.getToolEvents();
                                if (toolEvents != null && !toolEvents.isEmpty()) {
                                    for (AgentResult.ToolEvent te : toolEvents) {
                                        InterviewServerStreamChunkMessage.Kind tk = te.isResultEvent()
                                                ? InterviewServerStreamChunkMessage.Kind.TOOL_RESULT
                                                : InterviewServerStreamChunkMessage.Kind.TOOL_CALL;
                                        InterviewServerStreamChunkMessage.ToolData td =
                                                new InterviewServerStreamChunkMessage.ToolData(
                                                        te.getToolCallId(), te.getName(), te.getArgs(), te.getResult());
                                        emitter.next(new StreamChunk(null, tk, td));
                                        // 落库前按 toolCallId 去重合并
                                        mergeToolEvent(toolEventMap, nullIdSeq, te);
                                    }
                                } else if (content != null && !content.isEmpty()) {
                                    // 兜底:无结构化数据时退化为文本
                                    if (toolTextFallback.length() > 0) {
                                        toolTextFallback.append('\n');
                                    }
                                    toolTextFallback.append(content);
                                    emitter.next(new StreamChunk(content,
                                            type == AgentResult.EventType.TOOL_RESULT
                                                    ? InterviewServerStreamChunkMessage.Kind.TOOL_RESULT
                                                    : InterviewServerStreamChunkMessage.Kind.TOOL_CALL));
                                }
                                return;
                            }
// 最终回答:按完整句子切分下发,同时累积全文用于落库
                            if (type == AgentResult.EventType.ANSWER
                                    && content != null && !content.isEmpty()) {
                                sentenceBuffer.append(content);
                                fullResponse.append(content);

                                String buffered = sentenceBuffer.toString();
                                int lastSplit = StrUtil.findLastSentenceEnd(buffered);
                                if (lastSplit >= 0) {
                                    String sentences = buffered.substring(0, lastSplit + 1);
                                    sentenceBuffer.setLength(0);
                                    sentenceBuffer.append(buffered.substring(lastSplit + 1));

                                    for (String sentence : StrUtil.splitSentences(sentences)) {
                                        if (!sentence.trim().isEmpty()) {
                                            emitter.next(new StreamChunk(sentence.trim(),
                                                    InterviewServerStreamChunkMessage.Kind.ANSWER));
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
                                    emitter.next(new StreamChunk(remaining,
                                            InterviewServerStreamChunkMessage.Kind.ANSWER));
                                }
                            }

                            // 持久化面试官回复(题目可能已被 Agent 工具切换,按 DB 当前题 ID 落库)
                            Long spokenQuestionId = resolveCurrentQuestionId(interviewSessionId, currentQuestionId);
                            if (interviewSessionId != null && !fullResponse.isEmpty()) {
                                // 落库前从去重后的工具事件构建结构化列表与 reasoning 摘要(每个工具仅一条)
                                StringBuilder toolBuffer = new StringBuilder();
                                List<Map<String, Object>> toolEventsForPersist = new ArrayList<>();
                                for (AgentResult.ToolEvent te : toolEventMap.values()) {
                                    Map<String, Object> eventMap = new HashMap<>();
                                    eventMap.put("name", te.getName());
                                    if (te.getArgs() != null && !te.getArgs().isEmpty()) {
                                        eventMap.put("args", te.getArgs());
                                    }
                                    if (te.getResult() != null) {
                                        eventMap.put("result", te.getResult());
                                    }
                                    eventMap.put("type", te.isResultEvent() ? "result" : "call");
                                    toolEventsForPersist.add(eventMap);
                                    appendToolSummary(toolBuffer, te);
                                }
                                if (toolTextFallback.length() > 0) {
                                    if (toolBuffer.length() > 0) {
                                        toolBuffer.append('\n');
                                    }
                                    toolBuffer.append(toolTextFallback);
                                }
                                String reasoning = buildReasoning(thinkingBuffer, toolBuffer);
                                saveTurn(interviewSessionId, spokenQuestionId, Speaker.AI, fullResponse.toString(), null, reasoning, toolEventsForPersist);
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
            InterviewSessionService.ForceAdvanceResult result =
                    interviewSessionService.forceAdvanceIfLimitReached(interviewSessionId, maxFollowUps);
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
        saveTurn(interviewSessionId, questionId, speaker, content, attachments, null, null);
    }

    private void saveTurn(Long interviewSessionId, Long questionId, Speaker speaker, String content,
                          List<Object> attachments, String reasoning) {
        saveTurn(interviewSessionId, questionId, speaker, content, attachments, reasoning, null);
    }

    private void saveTurn(Long interviewSessionId, Long questionId, Speaker speaker, String content,
                          List<Object> attachments, String reasoning, List<Map<String, Object>> toolEvents) {
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
            turn.setReasoning(reasoning);
            if (toolEvents != null) {
                List<Object> boxed = new ArrayList<>(toolEvents.size());
                boxed.addAll(toolEvents);
                turn.setToolEvents(boxed);
            }
            turn.setIsFollowup(false);
            turn.setIsHint(false);
            turnMapper.insert(turn);
        } catch (Exception e) {
            log.error("[Interview] Failed to save turn: sessionId={}, speaker={}", interviewSessionId, speaker, e);
        }
    }

    /**
     * 将结构化工具事件转为摘要文本追加到 toolBuffer，供落库 reasoning。
     * 调用记为"工具名(参数)"，结果记为"工具名 -> 结果摘要"。
     */
    /**
     * 按 toolCallId 合并工具事件到去重 Map: call 提供 name+args, result 补充 result。
     * 流式 THINKING 增量会重复下发同一 ToolUseBlock, 合并后落库仅保留一条完整记录。
     * callId 为 null 时退化为独立条目(用合成键), 保留原始行为。
     */
    private void mergeToolEvent(LinkedHashMap<String, AgentResult.ToolEvent> map,
                                AtomicInteger nullIdSeq, AgentResult.ToolEvent te) {
        if (te == null) {
            return;
        }
        String callId = te.getToolCallId();
        String key = (callId != null && !callId.isEmpty())
                ? callId
                : "__null_" + nullIdSeq.incrementAndGet();
        AgentResult.ToolEvent merged = map.get(key);
        if (merged == null) {
            merged = new AgentResult.ToolEvent();
            merged.setToolCallId(te.getToolCallId());
            map.put(key, merged);
        }
        if (te.getName() != null) {
            merged.setName(te.getName());
        }
        if (te.getArgs() != null && !te.getArgs().isEmpty()) {
            merged.setArgs(te.getArgs());
        }
        if (te.isResultEvent()) {
            merged.setResultEvent(true);
            if (te.getResult() != null) {
                merged.setResult(te.getResult());
            }
        }
    }

    private void appendToolSummary(StringBuilder toolBuffer, AgentResult.ToolEvent te) {
        if (te == null) {
            return;
        }
        if (toolBuffer.length() > 0) {
            toolBuffer.append('\n');
        }
        if (te.isResultEvent()) {
            toolBuffer.append(te.getName()).append(" -> ");
            String result = te.getResult();
            if (result != null && result.length() > 200) {
                result = result.substring(0, 200) + "...";
            }
            toolBuffer.append(result == null ? "" : result);
        } else {
            toolBuffer.append(te.getName());
            if (te.getArgs() != null && !te.getArgs().isEmpty()) {
                toolBuffer.append("(").append(te.getArgs()).append(")");
            }
        }
    }
    /**
     * 合并本轮推理内容（thinking + tool）为落库文本。
     * thinking 作为正文；tool 作为"工具调用"小节追加。
     */
    private String buildReasoning(StringBuilder thinkingBuffer, StringBuilder toolBuffer) {
        String thinking = thinkingBuffer.toString().trim();
        String tool = toolBuffer.toString().trim();
        if (thinking.isEmpty() && tool.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!thinking.isEmpty()) {
            sb.append(thinking);
        }
        if (!tool.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("### 工具调用\n\n").append(tool);
        }
        return sb.toString();
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