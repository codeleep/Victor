package me.codeleep.victor.infra.voice.volcengine.tts;


import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.tts.TtsClient;
import me.codeleep.victor.infra.voice.tts.TtsSession;
import me.codeleep.victor.infra.voice.volcengine.protocol.EventType;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 火山引擎双向TTS WebSocket客户端。
 *
 * <p>一个 VolcTtsClient 对应一个 WebSocket 连接，持有唯一一个 {@link VolcTtsSession}。</p>
 *
 * <h3>典型用法 - 流式合成：</h3>
 * <pre>{@code
 * VolcTtsClient client = new VolcTtsClient(apiKey, resourceId, voice, encoding);
 * client.connect();
 *
 * try (TtsSession session = client.createSession()) {
 *     session.speakText("你好，世界");
 *     session.finish();
 *
 *     while (!session.isFinished()) {
 *         byte[] audio = session.pollAudio(5000);
 *         if (audio != null) {
 *             // 播放音频
 *         }
 *     }
 * } finally {
 *     client.disconnect();
 * }
 * }</pre>
 */
@Slf4j
public class VolcTtsClient implements TtsClient {
    /** 火山引擎双向TTS WebSocket 服务端点 */
    private static final String ENDPOINT = "wss://openspeech.bytedance.com/api/v3/tts/bidirection";
    /** 固定会话ID */
    private static final String DEFAULT_SESSION_ID = "default";

    // Getter methods
    /** 火山引擎 API 密钥，用于鉴权 */
    @Getter
    private final String apiKey;
    /** 资源ID，决定使用哪种TTS模型 */
    @Getter
    private final String resourceId;
    /** 发音人名称，如 "zh_female_xiaohe_uranus_bigtts" */
    @Getter
    private final String voice;
    /** 音频编码格式，如 "wav"、"mp3"、"pcm" */
    @Getter
    private final String encoding;
    /** 语速倍率，1.0 为默认，2.0 为两倍速 */
    @Getter
    private final double speedRatio;

    /** 底层 WebSocket 客户端 */
    private TtsWebSocketClient client;
    /** 连接状态标记 */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    /** 唯一会话 */
    private VolcTtsSession session;
    /** 后台消息接收线程 */
    private Thread receiverThread;

    /**
     * 创建 VolcTtsClient 实例。
     *
     * @param apiKey     火山引擎 API Key
     * @param resourceId 资源ID，可为空（将根据 voice 自动推断）
     * @param voice      发音人名称
     * @param encoding   音频编码格式
     */
    public VolcTtsClient(String apiKey, String resourceId, String voice, String encoding) {
        this(apiKey, resourceId, voice, encoding, 1.0);
    }

    public VolcTtsClient(String apiKey, String resourceId, String voice, String encoding, double speedRatio) {
        this.apiKey = apiKey;
        this.resourceId = resourceId;
        this.voice = voice;
        this.encoding = encoding;
        this.speedRatio = speedRatio;
    }

    /**
     * 根据发音人名称推断默认的 resourceId。
     *
     * <p>以 "S_" 开头的发音人使用 megatts 模型，其余使用 bigtts 模型。</p>
     *
     * @param voice 发音人名称
     * @return 对应的 resourceId
     */
    public static String voiceToResourceId(String voice) {
        if (voice.startsWith("S_")) {
            return "volc.megatts.default";
        }
        return "volc.service_type.10029";
    }

    @Override
    public void connect() throws Exception {
        if (connected.get()) {
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TTS apiKey must not be null or blank. Check speech.auth.api-key configuration.");
        }
        if (voice == null || voice.isBlank()) {
            throw new IllegalStateException("TTS voice must not be null or blank.");
        }

        String resolvedResourceId = (resourceId == null || resourceId.isEmpty()) ? voiceToResourceId(voice) : resourceId;

        Map<String, String> headers = Map.of(
                "X-Api-Key", apiKey,
                "X-Api-Resource-Id", resolvedResourceId,
                "X-Api-Connect-Id", UUID.randomUUID().toString());

        client = new TtsWebSocketClient(new URI(ENDPOINT), headers);
        client.connectBlocking();

        client.sendStartConnection();
        waitForConnectionStarted();

        connected.set(true);
        startReceiver();
        log.info("VolcTtsClient connected");
    }

    /**
     * 重新连接：关闭旧连接后重新建立
     */
    public void reconnect() throws Exception {
        if (connected.get()) {
            disconnect();
        }
        connect();
    }

    private void waitForConnectionStarted() throws InterruptedException {
        while (true) {
            TtsMessage msg = client.receiveMessage();
            if (msg.getType() == TtsMsgType.FULL_SERVER_RESPONSE && msg.getEvent() == EventType.CONNECTION_STARTED) {
                return;
            }
        }
    }

    private void startReceiver() {
        receiverThread = new Thread(() -> {
            try {
                while (connected.get()) {
                    TtsMessage msg = client.receiveMessage();
                    log.info("[TtsClient-Receiver] Received message: type={}, event={}, sessionId={}, payloadSize={}",
                            msg.getType(), msg.getEvent(), msg.getSessionId(),
                            msg.getPayload() != null ? msg.getPayload().length : 0);

                    // 直接发给当前 session，无需路由
                    if (session != null) {
                        session.handleMessage(msg);
                    } else {
                        log.warn("[TtsClient-Receiver] No active session to handle message");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Receiver error", e);
            }
        }, "VolcTtsClient-Receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @Override
    public TtsSession createSession() throws Exception {
        checkConnected();

        // 如果之前的session未正常关闭，先清理
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Failed to close previous session", e);
            }
            removeSession();
        }

        // 检查连接是否仍然有效，如果断开则重连
        if (!isConnected()) {
            log.info("TTS connection lost, reconnecting...");
            reconnect();
        }

        session = new VolcTtsSession(this, DEFAULT_SESSION_ID);
        session.start();
        return session;
    }

    @Override
    public byte[] synthesize(String text) throws Exception {
        try (TtsSession session = createSession()) {
            session.speakText(text);
            session.finish();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (!session.isFinished()) {
                byte[] audio = session.pollAudio(5000);
                if (audio != null) {
                    baos.write(audio);
                }
            }
            // drain remaining
            while (session.hasAudio()) {
                byte[] audio = session.pollAudio(100);
                if (audio != null) {
                    baos.write(audio);
                }
            }
            return baos.toByteArray();
        }
    }

    void removeSession() {
        session = null;
    }

    void sendMessage(TtsMessage message) throws Exception {
        client.sendMessage(message);
    }

    private void checkConnected() {
        if (!connected.get()) {
            throw new IllegalStateException("VolcTtsClient not connected");
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (!connected.getAndSet(false)) {
            return;
        }

        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.error("Error closing session", e);
            }
        }

        try {
            client.sendFinishConnection();
        } catch (Exception e) {
            log.error("Error sending finish connection", e);
        }

        client.closeBlocking();
        log.info("VolcTtsClient disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected.get() && client != null;
    }

}