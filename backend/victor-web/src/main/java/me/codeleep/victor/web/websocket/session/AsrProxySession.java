package me.codeleep.victor.web.websocket.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import me.codeleep.victor.infra.voice.asr.AsrSession;
import me.codeleep.victor.infra.voice.asr.AsrClient;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.protocol.UnknownMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientStreamEndMessage;
import me.codeleep.victor.web.websocket.protocol.server.asr.AsrServerStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.server.asr.AsrServerStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.server.asr.AsrServerStreamEndMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ASR 代理会话，管理单个 WebSocket 连接的语音识别流程。
 *
 * <p>负责处理客户端命令、音频数据转发、ASR 结果收集。</p>
 *
 * <h3>客户端命令：</h3>
 * <ul>
 *   <li>asr.stream_begin - 开始语音流</li>
 *   <li>binary - 音频数据</li>
 *   <li>asr.stream_end - 结束语音流</li>
 *   <li>asr.interrupt - 中断识别</li>
 * </ul>
 *
 * <h3>服务端消息：</h3>
 * <ul>
 *   <li>asr.stream_begin - 识别开始</li>
 *   <li>asr.stream_chunk - 识别中间结果</li>
 *   <li>asr.stream_end - 识别最终结果</li>
 * </ul>
 */
@Slf4j
public class AsrProxySession implements Session {

    @Getter
    private final String sessionId;
    private final WebSocketSession wsSession;
    private final AsrClient asrClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicLong eventSequence = new AtomicLong(0);
    private final InterruptResponder interruptResponder = new InterruptResponder();

    /** 当前 ASR 会话 */
    private volatile AsrSession asrSession;

    /**
     * 构造 ASR 代理会话。
     *
     * @param wsSession WebSocket 会话
     * @param asrClient ASR 客户端
     */
    public AsrProxySession(WebSocketSession wsSession, AsrClient asrClient) {
        this.sessionId = wsSession.getId();
        this.wsSession = wsSession;
        this.asrClient = asrClient;
    }

    /**
     * 处理客户端文本命令，根据命令类型分发到对应的处理方法。
     *
     * @param command 客户端命令
     */
    @Override
    public void handleCommand(ClientMessage command) {
        switch (command) {
            case AsrClientStreamBeginMessage streamBegin -> handleStreamBegin();
            case AsrClientStreamEndMessage streamEnd -> handleStreamEnd();
            case AsrClientInterruptMessage interrupt -> handleInterrupt();
            case UnknownMessage unknown -> log.warn("[ASR Session:{}] Unknown command: {}", sessionId, command.getType());
            default -> log.warn("[ASR Session:{}] Unsupported command: {}", sessionId, command.getType());
        }
    }

    /**
     * 处理二进制音频数据。
     *
     * @param data 音频数据
     */
    public void handleBinaryData(byte[] data) {
        try {
            AsrSession session = getOrCreateSession();
            session.sendAudio(data);
        } catch (Exception e) {
            log.error("[ASR Session:{}] Error sending audio", sessionId, e);
        }
    }

    /**
     * 处理流开始命令。
     * <p>创建 ASR 会话，发送 stream_begin 消息。</p>
     */
    private void handleStreamBegin() {
        try {
            asrSession = asrClient.createSession();
            sendMessage(new AsrServerStreamBeginMessage());
            log.info("[ASR Session:{}] Stream begin", sessionId);
        } catch (Exception e) {
            log.error("[ASR Session:{}] Error creating ASR session", sessionId, e);
        }
    }

    /**
     * 处理流结束命令。
     * <p>通知 ASR 会话完成音频输入，异步收集识别结果。</p>
     */
    private void handleStreamEnd() {
        if (asrSession == null) {
            log.warn("[ASR Session:{}] No active session to end", sessionId);
            return;
        }

        AsrSession session = asrSession;
        asrSession = null;

        try {
            session.finishAudio();
        } catch (Exception e) {
            log.error("[ASR Session:{}] Error finishing audio", sessionId, e);
            try {
                session.close();
            } catch (Exception closeEx) {
                log.warn("[ASR Session:{}] Error closing session after finish failure", sessionId, closeEx);
            }
            return;
        }

        submitCollectResults(session);
    }

    /**
     * 提交结果收集任务到线程池。
     *
     * @param session ASR 会话
     */
    private void submitCollectResults(AsrSession session) {
        String processingId = UUID.randomUUID().toString();
        long operationSequence = nextEventSequence();
        interruptResponder.startProcessing(processingId);

        executor.submit(() -> {
            try {
                collectAndSendResults(session, processingId, operationSequence);
            } catch (Exception e) {
                log.error("[ASR Session:{}] Error collecting results", sessionId, e);
            } finally {
                try {
                    session.close();
                } catch (Exception e) {
                    log.warn("[ASR Session:{}] Error closing session", sessionId, e);
                }
            }
        });
    }

    /**
     * 处理中断命令。
     * <p>记录中断事件，关闭当前 ASR 会话。</p>
     */
    private void handleInterrupt() {
        long ts = System.currentTimeMillis();
        long seq = nextEventSequence();
        interruptResponder.record(ts, seq, "ASR_RESPONSE");

        if (asrSession != null) {
            try {
                asrSession.close();
            } catch (Exception e) {
                log.warn("[ASR Session:{}] Error closing session on interrupt", sessionId, e);
            }
            asrSession = null;
        }
        log.info("[ASR Session:{}] Interrupted: serverTimestamp={}, eventSequence={}", sessionId, ts, seq);
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
     * 收集 ASR 识别结果并发送给客户端。
     *
     * @param session          ASR 会话
     * @param processingId     处理 ID，用于中断检测
     * @param operationSequence 操作序列号，用于中断检测
     */
    private void collectAndSendResults(AsrSession session, String processingId, long operationSequence) {
        try {
            String lastText = null;

            // 等待识别完成，收集中间结果
            while (!session.isFinished()) {
                if (interruptResponder.isInterrupted(processingId, operationSequence, "ASR_RESPONSE")) {
                    log.info("[ASR Session:{}] Interrupted during result collection", sessionId);
                    return;
                }
                AsrResult result = session.pollResult(5000);
                if (result != null && result.getText() != null) {
                    lastText = result.getText();
                    sendMessage(new AsrServerStreamChunkMessage(lastText));
                }
            }

            // 收集剩余结果
            while (session.hasResult()) {
                if (interruptResponder.isInterrupted(processingId, operationSequence, "ASR_RESPONSE")) {
                    log.info("[ASR Session:{}] Interrupted during result collection", sessionId);
                    return;
                }
                AsrResult result = session.pollResult(100);
                if (result != null && result.getText() != null) {
                    lastText = result.getText();
                    sendMessage(new AsrServerStreamChunkMessage(lastText));
                }
            }

            // 发送最终结果
            if (!interruptResponder.isInterrupted(processingId, operationSequence, "ASR_RESPONSE")) {
                sendMessage(new AsrServerStreamEndMessage(lastText));
            }
        } catch (Exception e) {
            log.error("[ASR Session:{}] Error collecting results", sessionId, e);
        }
    }

    /**
     * 获取或创建 ASR 会话。
     *
     * @return ASR 会话
     */
    private AsrSession getOrCreateSession() throws Exception {
        if (asrSession != null && !asrSession.isFinished()) {
            return asrSession;
        }
        asrSession = asrClient.createSession();
        return asrSession;
    }

    /**
     * 发送文本消息给客户端。
     *
     * @param message 服务端消息
     */
    @Override
    public void sendMessage(me.codeleep.victor.web.websocket.protocol.ServerMessage message) {
        try {
            String json = message.toJson();
            synchronized (wsSession) {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.error("[ASR Session:{}] Error sending message", sessionId, e);
        }
    }

    /**
     * 清理会话资源，关闭 ASR 客户端和线程池。
     */
    @Override
    public void cleanup() {
        log.info("[ASR Session:{}] Cleaning up...", sessionId);

        if (asrSession != null) {
            try {
                asrSession.close();
            } catch (Exception e) {
                log.warn("[ASR Session:{}] Error closing ASR session", sessionId, e);
            }
            asrSession = null;
        }

        try {
            asrClient.disconnect();
        } catch (Exception e) {
            log.warn("[ASR Session:{}] Error disconnecting ASR client", sessionId, e);
        }
        executor.shutdownNow();
    }
}
