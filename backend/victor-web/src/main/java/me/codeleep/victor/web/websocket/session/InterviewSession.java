package me.codeleep.victor.web.websocket.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.codeleep.victor.core.interviewer.InterviewContextRestorer;
import me.codeleep.victor.core.interviewer.InterviewContextResult;
import me.codeleep.victor.core.interviewer.Interviewer;
import me.codeleep.victor.web.websocket.processor.ProcessingContext;
import me.codeleep.victor.web.websocket.processor.TextProcessor;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.protocol.ServerMessage;
import me.codeleep.victor.web.websocket.protocol.UnknownMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientReconnectMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStartMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStopMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamEndMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerErrorMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerReconnectedMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStatusMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStreamEndMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 面试会话，管理单个 WebSocket 连接的面试流程。
 *
 * <p>负责处理客户端命令、流式文本缓冲、LLM 调用及中断响应。</p>
 *
 * <h3>客户端命令：</h3>
 * <ul>
 *   <li>interview.start - 开始面试</li>
 *   <li>interview.reconnect - 重连恢复上下文</li>
 *   <li>interview.stream_begin/stream_chunk/stream_end - 流式文本输入</li>
 *   <li>interview.interrupt - 中断当前处理</li>
 *   <li>interview.stop - 停止面试</li>
 * </ul>
 *
 * <h3>服务端消息：</h3>
 * <ul>
 *   <li>interview.status - 状态通知</li>
 *   <li>interview.stream_begin/stream_chunk/stream_end - 流式 LLM 输出</li>
 *   <li>interview.reconnected - 重连成功</li>
 *   <li>interview.error - 错误通知</li>
 * </ul>
 */
@Slf4j
public class InterviewSession implements Session {

    @Getter
    private final String sessionId;
    private final WebSocketSession wsSession;
    private final TextProcessor textProcessor;
    private final InterviewContextRestorer contextRestorer;

    /** 面试上下文（AgentDefinition + AgentContext） */
    private final ProcessingContext processingContext;

    /**
     * 会话状态枚举。
     */
    public enum Status {
        /** 已创建，等待 start 命令 */
        CREATED,
        /** 运行中，可接收流式输入 */
        RUNNING,
        /** 正在处理 LLM 请求 */
        STREAMING,
        /** 已断开连接 */
        DISCONNECTED
    }

    private volatile Status status = Status.CREATED;
    private final AtomicLong eventSequence = new AtomicLong(0);
    private final InterruptResponder interruptResponder = new InterruptResponder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 流式文本输入缓冲区 */
    private final StringBuilder inputBuffer = new StringBuilder();
    /** 本次输入附件。附件不参与流式拼接，随 stream_chunk 一次性完整发送。 */
    private final List<Object> attachmentBuffer = new ArrayList<>();

    /**
     * 构造面试会话。
     *
     * @param wsSession         WebSocket 会话
     * @param textProcessor     文本处理器（LLM 调用）
     * @param contextRestorer   面试上下文恢复器
     */
    public InterviewSession(WebSocketSession wsSession, TextProcessor textProcessor,
                            InterviewContextRestorer contextRestorer) {
        this.sessionId = wsSession.getId();
        this.wsSession = wsSession;
        this.textProcessor = textProcessor;
        this.contextRestorer = contextRestorer;
        this.processingContext = new ProcessingContext(sessionId);
    }

    /**
     * 处理客户端命令，根据命令类型分发到对应的处理方法。
     *
     * @param command 客户端命令
     */
    @Override
    public void handleCommand(ClientMessage command) {
        fillServerEventMetadata(command);

        switch (command) {
            case InterviewClientStartMessage start -> handleStart(start);
            case InterviewClientReconnectMessage reconnect -> handleReconnect(reconnect);
            case InterviewClientInterruptMessage interrupt -> handleInterrupt(interrupt);
            case InterviewClientStreamBeginMessage streamBegin -> handleStreamBegin();
            case InterviewClientStreamChunkMessage streamChunk -> handleStreamChunk(streamChunk);
            case InterviewClientStreamEndMessage streamEnd -> handleStreamEnd();
            case InterviewClientStopMessage stopCommand -> cleanup();
            case UnknownMessage unknownCommand -> log.warn("[Session:{}] Unknown command: {}", sessionId, command.getType());
            default -> log.warn("[Session:{}] Unsupported command: {}", sessionId, command.getType());
        }
    }

    /**
     * 处理面试开始命令。
     * <p>初始化面试上下文，切换状态为 RUNNING。</p>
     *
     * @param start 开始命令
     */
    private void handleStart(InterviewClientStartMessage start) {
        Long interviewSessionId = start.getInterviewSessionId();
        long result = ensureInterviewContext(interviewSessionId);
        if (result < 0) {
            status = Status.CREATED;
            return;
        }
        status = Status.RUNNING;
        sendMessage(new InterviewServerStatusMessage("listening"));
    }

    /**
     * 处理流开始命令。
     * <p>清空输入缓冲区，准备接收流式文本。</p>
     */
    private void handleStreamBegin() {
        if (status != Status.RUNNING) {
            log.warn("[Session:{}] Stream begin ignored, status={}", sessionId, status);
            return;
        }
        inputBuffer.setLength(0);
        attachmentBuffer.clear();
        log.info("[Session:{}] Stream begin", sessionId);
    }

    /**
     * 处理流数据命令。
     * <p>将文本追加到输入缓冲区。</p>
     *
     * @param command 流数据命令
     */
    private void handleStreamChunk(InterviewClientStreamChunkMessage command) {
        if (status != Status.RUNNING) {
            log.warn("[Session:{}] Stream chunk ignored, status={}", sessionId, status);
            return;
        }
        String text = command.getText();
        if (text != null && !text.isEmpty()) {
            inputBuffer.append(text);
        }
        if (command.getAttachments() != null && !command.getAttachments().isEmpty()) {
            attachmentBuffer.addAll(command.getAttachments());
        }
    }

    /**
     * 处理流结束命令。
     * <p>从缓冲区提取文本，提交处理任务。</p>
     */
    private void handleStreamEnd() {
        if (status != Status.RUNNING) {
            log.warn("[Session:{}] Stream end ignored, status={}", sessionId, status);
            return;
        }

        String text = inputBuffer.toString().trim();
        List<Object> attachments = new ArrayList<>(attachmentBuffer);
        inputBuffer.setLength(0);
        attachmentBuffer.clear();

        if (text.isEmpty() && attachments.isEmpty()) {
            log.warn("[Session:{}] Empty stream, ignoring", sessionId);
            return;
        }

        processingContext.setAttribute(ProcessingContext.ATTR_INPUT_TEXT, text);
        processingContext.setAttribute(ProcessingContext.ATTR_ATTACHMENTS, attachments);
        String input = buildAiInput(text, attachments);
        log.info("[Session:{}] Stream end, processing text: {}", sessionId, input);
        submitProcessing(input);
    }

    private String buildAiInput(String text, List<Object> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        if (text != null && !text.isEmpty()) {
            builder.append(text).append("\n\n");
        }
        builder.append("候选人附加了附件数据，以下是附件 JSON，不是图片。请根据附件 type、format、language、data 理解候选人的补充内容：\n")
                .append("```json\n");
        try {
            builder.append(objectMapper.writeValueAsString(attachments));
        } catch (Exception e) {
            builder.append(attachments);
        }
        builder.append("\n```");
        return builder.toString();
    }

    /**
     * 提交文本处理任务到线程池。
     * <p>生成处理 ID，记录事件序列，异步执行 LLM 处理。</p>
     *
     * @param text 待处理的文本
     */
    private void submitProcessing(String text) {
        String processingId = UUID.randomUUID().toString();
        long operationSequence = nextEventSequence();
        interruptResponder.startProcessing(processingId);

        status = Status.STREAMING;
        executor.submit(() -> {
            try {
                processText(text, processingId, operationSequence);
            } catch (Exception e) {
                log.error("[Session:{}] Error processing text", sessionId, e);
                sendMessage(new InterviewServerErrorMessage("Processing error: " + e.getMessage()));
            } finally {
                processingContext.removeAttribute(ProcessingContext.ATTR_INPUT_TEXT);
                processingContext.removeAttribute(ProcessingContext.ATTR_ATTACHMENTS);
                if (status == Status.STREAMING) {
                    status = Status.RUNNING;
                }
            }
        });
    }

    /**
     * 处理中断命令。
     * <p>记录中断事件，发送 listening 状态。</p>
     *
     * @param command 中断命令
     */
    private void handleInterrupt(InterviewClientInterruptMessage command) {
        long ts = System.currentTimeMillis();
        long seq = nextEventSequence();
        interruptResponder.record(ts, seq, command.getInterruptType());
        sendMessage(new InterviewServerStatusMessage("listening"));
        log.info("[Session:{}] Interrupt completed: type={}, serverTimestamp={}, eventSequence={}",
                sessionId, command.getInterruptType(), ts, seq);
    }

    /**
     * 处理重连命令。
     * <p>从 DB 恢复面试上下文，返回历史对话轮数。</p>
     *
     * @param command 重连命令
     */
    private void handleReconnect(InterviewClientReconnectMessage command) {
        Long interviewSessionId = command.getInterviewSessionId();
        if (interviewSessionId == null) {
            sendMessage(new InterviewServerErrorMessage("reconnect 缺少 interviewSessionId"));
            return;
        }

        log.info("[Session:{}] Reconnecting to interview session: {}", sessionId, interviewSessionId);

        status = Status.RUNNING;

        long result = ensureInterviewContext(interviewSessionId);
        if (result < 0) {
            return;
        }

        sendMessage(new InterviewServerReconnectedMessage(interviewSessionId, (int) result));
        sendMessage(new InterviewServerStatusMessage("listening"));
        log.info("[Session:{}] Reconnected: interviewSessionId={}, historyTurns={}",
                sessionId, interviewSessionId, result);
    }

    /**
     * 确保面试上下文就绪（AgentDefinition + AgentContext）。
     *
     * @param interviewSessionId 面试会话 ID
     * @return 对话轮数；负数表示错误（已发送 error 消息）
     */
    private long ensureInterviewContext(Long interviewSessionId) {
        InterviewContextResult result = contextRestorer.ensureContext(interviewSessionId);
        if (result.getCode() < 0) {
            switch (result.getCode()) {
                case -1 -> sendMessage(new InterviewServerErrorMessage("面试会话不存在: " + interviewSessionId));
                case -2 -> sendMessage(new InterviewServerErrorMessage("面试已结束，无法恢复: " + interviewSessionId));
                case -3 -> sendMessage(new InterviewServerErrorMessage("Agent 不存在或配置异常"));
                case -4 -> sendMessage(new InterviewServerErrorMessage("Interview questions are not ready. Please start after config status is READY."));
            }
            return result.getCode();
        }
        // 将结果填充到 ProcessingContext
        processingContext.setAttribute(ProcessingContext.ATTR_INTERVIEWER, result.getInterviewer());
        processingContext.setAttribute(ProcessingContext.ATTR_AGENT_KEY, result.getAgentKey());
        processingContext.setAttribute(ProcessingContext.ATTR_USER_ID, result.getUserId());
        processingContext.setAttribute(ProcessingContext.ATTR_INTERVIEW_SESSION_ID, result.getInterviewSessionId());
        processingContext.setAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID, result.getCurrentQuestionId());
        return result.getTurnCount();
    }

    /**
     * 调用 LLM 处理文本并流式返回结果。
     * <p>发送 stream_begin，逐句发送 stream_chunk，最后发送 stream_end。</p>
     *
     * @param text              待处理文本
     * @param processingId      处理 ID，用于中断检测
     * @param operationSequence 操作序列号，用于中断检测
     */
    private void processText(String text, String processingId, long operationSequence) {
        try {
            log.info("[Session:{}] Starting text processing: {}", sessionId, text);

            sendMessage(new InterviewServerStreamBeginMessage());

            Flux<String> sentenceFlux = textProcessor.process(processingContext, text);

            sentenceFlux.doOnNext(sentence -> {
                        if (interruptResponder.isInterrupted(processingId, operationSequence, "LLM_RESPONSE")) {
                            return;
                        }
                        sendMessage(new InterviewServerStreamChunkMessage(sentence));
                    })
                    .doOnComplete(() -> {
                        if (!interruptResponder.isInterrupted(processingId, operationSequence, "LLM_RESPONSE")) {
                            sendMessage(new InterviewServerStreamEndMessage(null));
                            sendMessage(new InterviewServerStatusMessage("listening"));
                            log.info("[Session:{}] Text processing completed", sessionId);
                        }
                    })
                    .doOnError(e -> {
                        log.error("[Session:{}] Text processing error", sessionId, e);
                        sendMessage(new InterviewServerErrorMessage("Processing error: " + e.getMessage()));
                    })
                    .blockLast();

        } catch (Exception e) {
            log.error("[Session:{}] Error in text processing", sessionId, e);
            sendMessage(new InterviewServerErrorMessage("Processing error: " + e.getMessage()));
        }
    }

    /**
     * 填充服务端事件元数据（serverTimestamp）。
     *
     * @param command 客户端命令
     */
    private void fillServerEventMetadata(ClientMessage command) {
        if (command instanceof InterviewClientInterruptMessage ic) {
            ic.setServerTimestamp(System.currentTimeMillis());
        }
    }

    /**
     * 获取下一个事件序列号。
     *
     * @return 递增的序列号
     */
    private long nextEventSequence() {
        return eventSequence.incrementAndGet();
    }

    /**
     * 发送消息给客户端。
     *
     * @param message 服务端消息
     */
    @Override
    public void sendMessage(ServerMessage message) {
        try {
            String json = message.toJson();
            wsSession.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("[Session:{}] Error sending message", sessionId, e);
        }
    }

    /**
     * 清理会话资源，关闭线程池。
     */
    @Override
    public void cleanup() {
        log.info("[Session:{}] Cleaning up...", sessionId);
        status = Status.DISCONNECTED;
        executor.shutdownNow();
        Interviewer interviewer = processingContext.getAttribute(ProcessingContext.ATTR_INTERVIEWER);
        if (interviewer != null) {
            interviewer.close();
            processingContext.removeAttribute(ProcessingContext.ATTR_INTERVIEWER);
        }
    }
}
