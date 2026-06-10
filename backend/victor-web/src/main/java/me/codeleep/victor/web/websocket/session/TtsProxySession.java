package me.codeleep.victor.web.websocket.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.tts.TtsSession;
import me.codeleep.victor.infra.voice.tts.TtsClient;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.protocol.UnknownMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamEndMessage;
import me.codeleep.victor.web.websocket.protocol.server.tts.TtsServerStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.server.tts.TtsServerStreamEndMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TTS 代理会话，管理单个 WebSocket 连接的语音合成流程。
 *
 * <p>负责处理客户端命令、文本转发、音频数据收集。</p>
 *
 * <h3>客户端命令：</h3>
 * <ul>
 *   <li>tts.stream_begin - 开始合成流</li>
 *   <li>tts.stream_chunk - 待合成文本</li>
 *   <li>tts.stream_end - 结束合成流</li>
 *   <li>tts.interrupt - 中断合成</li>
 * </ul>
 *
 * <h3>服务端消息：</h3>
 * <ul>
 *   <li>tts.stream_begin - 合成开始</li>
 *   <li>binary - 音频数据</li>
 *   <li>tts.stream_end - 合成结束</li>
 * </ul>
 */
@Slf4j
public class TtsProxySession implements Session {

    @Getter
    private final String sessionId;
    private final WebSocketSession wsSession;
    private final TtsClient ttsClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicLong eventSequence = new AtomicLong(0);
    private final InterruptResponder interruptResponder = new InterruptResponder();

    /** 当前 TTS 会话 */
    private volatile TtsSession ttsSession;

    /**
     * 构造 TTS 代理会话。
     *
     * @param wsSession WebSocket 会话
     * @param ttsClient TTS 客户端
     */
    public TtsProxySession(WebSocketSession wsSession, TtsClient ttsClient) {
        this.sessionId = wsSession.getId();
        this.wsSession = wsSession;
        this.ttsClient = ttsClient;
    }

    /**
     * 处理客户端文本命令，根据命令类型分发到对应的处理方法。
     *
     * @param command 客户端命令
     */
    @Override
    public void handleCommand(ClientMessage command) {
        switch (command) {
            case TtsClientStreamBeginMessage streamBegin -> handleStreamBegin();
            case TtsClientStreamChunkMessage streamChunk -> handleStreamChunk(streamChunk);
            case TtsClientStreamEndMessage streamEnd -> handleStreamEnd();
            case TtsClientInterruptMessage interrupt -> handleInterrupt();
            case UnknownMessage unknown -> log.warn("[TTS Session:{}] Unknown command: {}", sessionId, command.getType());
            default -> log.warn("[TTS Session:{}] Unsupported command: {}", sessionId, command.getType());
        }
    }

    /**
     * 处理流开始命令。
     * <p>创建 TTS 会话。</p>
     */
    private void handleStreamBegin() {
        try {
            ttsSession = ttsClient.createSession();
            log.info("[TTS Session:{}] Stream begin", sessionId);
        } catch (Exception e) {
            log.error("[TTS Session:{}] Error creating TTS session", sessionId, e);
        }
    }

    /**
     * 处理流数据命令。
     * <p>将文本发送到 TTS 会话进行合成。</p>
     *
     * @param command 流数据命令
     */
    private void handleStreamChunk(TtsClientStreamChunkMessage command) {
        String text = command.getText();
        if (text == null || text.isEmpty()) {
            log.warn("[TTS Session:{}] Empty text in stream chunk", sessionId);
            return;
        }

        try {
            TtsSession session = getOrCreateSession();
            session.speakText(text);
        } catch (Exception e) {
            log.error("[TTS Session:{}] Error sending text to TTS", sessionId, e);
        }
    }

    /**
     * 处理流结束命令。
     * <p>通知 TTS 会话完成文本输入，异步收集音频数据。</p>
     */
    private void handleStreamEnd() {
        if (ttsSession == null) {
            log.warn("[TTS Session:{}] No active session to end", sessionId);
            return;
        }

        TtsSession session = ttsSession;
        ttsSession = null;

        try {
            session.finish();
        } catch (Exception e) {
            log.error("[TTS Session:{}] Error finishing TTS session", sessionId, e);
            return;
        }

        submitCollectAudio(session);
    }

    /**
     * 提交音频收集任务到线程池。
     *
     * @param session TTS 会话
     */
    private void submitCollectAudio(TtsSession session) {
        String processingId = UUID.randomUUID().toString();
        long operationSequence = nextEventSequence();
        interruptResponder.startProcessing(processingId);

        executor.submit(() -> {
            try {
                collectAndSendAudio(session, processingId, operationSequence);
            } catch (Exception e) {
                log.error("[TTS Session:{}] Error collecting audio", sessionId, e);
            } finally {
                try {
                    session.close();
                } catch (Exception e) {
                    log.warn("[TTS Session:{}] Error closing session", sessionId, e);
                }
            }
        });
    }

    /**
     * 处理中断命令。
     * <p>记录中断事件，关闭当前 TTS 会话。</p>
     */
    private void handleInterrupt() {
        long ts = System.currentTimeMillis();
        long seq = nextEventSequence();
        interruptResponder.record(ts, seq, "TTS_RESPONSE");

        if (ttsSession != null) {
            try {
                ttsSession.close();
            } catch (Exception e) {
                log.warn("[TTS Session:{}] Error closing session on interrupt", sessionId, e);
            }
            ttsSession = null;
        }
        log.info("[TTS Session:{}] Interrupted: serverTimestamp={}, eventSequence={}", sessionId, ts, seq);
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
     * 收集 TTS 合成的音频数据并发送给客户端。
     *
     * @param session          TTS 会话
     * @param processingId     处理 ID，用于中断检测
     * @param operationSequence 操作序列号，用于中断检测
     */
    private void collectAndSendAudio(TtsSession session, String processingId, long operationSequence) {
        try {
            // 发送 stream_begin
            sendMessage(new TtsServerStreamBeginMessage());

            // 收集并发送音频数据
            while (!session.isFinished()) {
                if (interruptResponder.isInterrupted(processingId, operationSequence, "TTS_RESPONSE")) {
                    log.info("[TTS Session:{}] Interrupted during audio collection", sessionId);
                    return;
                }
                byte[] audio = session.pollAudio(5000);
                sendBinaryData(audio);
            }

            // 收集剩余音频数据
            while (session.hasAudio()) {
                if (interruptResponder.isInterrupted(processingId, operationSequence, "TTS_RESPONSE")) {
                    log.info("[TTS Session:{}] Interrupted during audio collection", sessionId);
                    return;
                }
                byte[] audio = session.pollAudio(100);
                sendBinaryData(audio);
            }

            // 发送 stream_end
            if (!interruptResponder.isInterrupted(processingId, operationSequence, "TTS_RESPONSE")) {
                sendMessage(new TtsServerStreamEndMessage());
            }
        } catch (Exception e) {
            log.error("[TTS Session:{}] Error collecting audio", sessionId, e);
        }
    }

    /**
     * 获取或创建 TTS 会话。
     *
     * @return TTS 会话
     */
    private TtsSession getOrCreateSession() throws Exception {
        if (ttsSession != null && !ttsSession.isFinished()) {
            return ttsSession;
        }
        ttsSession = ttsClient.createSession();
        return ttsSession;
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
            log.error("[TTS Session:{}] Error sending message", sessionId, e);
        }
    }

    /**
     * 发送二进制音频数据给客户端。
     *
     * @param data 音频数据
     */
    private void sendBinaryData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            synchronized (wsSession) {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(new BinaryMessage(data));
                }
            }
        } catch (Exception e) {
            log.error("[TTS Session:{}] Error sending binary data", sessionId, e);
        }
    }

    /**
     * 清理会话资源，关闭 TTS 客户端和线程池。
     */
    @Override
    public void cleanup() {
        log.info("[TTS Session:{}] Cleaning up...", sessionId);

        if (ttsSession != null) {
            try {
                ttsSession.close();
            } catch (Exception e) {
                log.warn("[TTS Session:{}] Error closing TTS session", sessionId, e);
            }
            ttsSession = null;
        }

        try {
            ttsClient.disconnect();
        } catch (Exception e) {
            log.warn("[TTS Session:{}] Error disconnecting TTS client", sessionId, e);
        }
        executor.shutdownNow();
    }
}
