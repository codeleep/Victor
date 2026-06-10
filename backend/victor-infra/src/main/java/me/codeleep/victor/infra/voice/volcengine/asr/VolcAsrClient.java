package me.codeleep.victor.infra.voice.volcengine.asr;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.asr.AsrClient;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import me.codeleep.victor.infra.voice.asr.AsrSession;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 火山引擎 ASR WebSocket客户端，使用Main2的二进制协议。
 *
 * <p>一个 VolcAsrClient 对应一个 WebSocket 连接，持有唯一一个 {@link VolcAsrSession}。</p>
 *
 * <h3>典型用法 - 流式识别：</h3>
 * <pre>{@code
 * VolcAsrClient client = new VolcAsrClient(apiKey, "volc.bigasr.sauc.duration");
 * client.connect();
 *
 * try (AsrSession session = client.createSession()) {
 *     session.sendAudio(chunk1);
 *     session.sendAudio(chunk2);
 *     session.finishAudio();
 *
 *     while (!session.isFinished()) {
 *         AsrResult result = session.pollResult(5000);
 *         if (result != null) {
 *             System.out.println(result.getText());
 *         }
 *     }
 * } finally {
 *     client.disconnect();
 * }
 * }</pre>
 *
 * <h3>典型用法 - 一次性识别：</h3>
 * <pre>{@code
 * VolcAsrClient client = new VolcAsrClient(apiKey, "volc.bigasr.sauc.duration");
 * client.connect();
 *
 * AsrResult result = client.recognize(audioData);
 * System.out.println(result.getText());
 *
 * client.disconnect();
 * }</pre>
 */
@Slf4j
public class VolcAsrClient implements AsrClient {
    /** 火山引擎 ASR WebSocket 服务端点 */
    private static final String ENDPOINT = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async";
    /** 固定会话ID */
    private static final String DEFAULT_SESSION_ID = "default";

    /** 火山引擎 API 密钥，用于鉴权 */
    private final String apiKey;
    /** 资源ID，如 "volc.bigasr.sauc.duration" */
    private final String resourceId;

    /** 底层 WebSocket 客户端 */
    private AsrWebSocketClient client;
    /** 连接状态标记 */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    /** 唯一会话 */
    private VolcAsrSession session;
    /** 音频序列号，服务器按API key追踪，跨连接连续递增 */
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    /** 后台消息接收线程 */
    private Thread receiverThread;

    /**
     * 创建 VolcAsrClient 实例。
     *
     * @param apiKey     火山引擎 API Key
     * @param resourceId 资源ID，如 "volc.bigasr.sauc.duration"
     */
    public VolcAsrClient(String apiKey, String resourceId) {
        this.apiKey = apiKey;
        this.resourceId = resourceId;
    }

    @Override
    public void connect() throws Exception {
        if (connected.get()) {
            return;
        }

        Map<String, String> headers = Map.of(
                "X-Api-Key", apiKey,
                "X-Api-Resource-Id", resourceId,
                "X-Api-Connect-Id", UUID.randomUUID().toString());

        client = new AsrWebSocketClient(new URI(ENDPOINT), headers);
        client.connectBlocking();

        connected.set(true);
        startReceiver();
        log.info("VolcAsrClient connected");
    }

    /**
     * 重新连接：关闭旧连接后重新建立，序列号保持连续
     */
    public void reconnect() throws Exception {
        if (connected.get()) {
            disconnect();
        }
        connect();
    }

    /**
     * 获取下一个音频序列号，服务器按API key追踪，跨连接连续递增。
     */
    int getNextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    private void startReceiver() {
        receiverThread = new Thread(() -> {
            try {
                while (connected.get()) {
                    AsrMessage msg = client.receiveMessage();

                    if (session != null) {
                        session.handleMessage(msg);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Receiver error", e);
            }
        }, "VolcAsrClient-Receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @Override
    public AsrSession createSession() throws Exception {
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
            log.info("ASR connection lost, reconnecting...");
            reconnect();
        }

        session = new VolcAsrSession(this, DEFAULT_SESSION_ID);
        session.start();
        return session;
    }

    @Override
    public AsrResult recognize(byte[] audioData) throws Exception {
        try (AsrSession session = createSession()) {
            session.sendAudio(audioData);
            session.finishAudio();

            AsrResult finalResult = null;
            while (!session.isFinished()) {
                AsrResult result = session.pollResult(5000);
                if (result != null && result.isFinal()) {
                    finalResult = result;
                }
            }
            // drain remaining
            while (session.hasResult()) {
                AsrResult result = session.pollResult(100);
                if (result != null && result.isFinal()) {
                    finalResult = result;
                }
            }
            return finalResult;
        }
    }

    AsrWebSocketClient getWebSocketClient() {
        return client;
    }

    void removeSession() {
        session = null;
    }

    private void checkConnected() {
        if (!connected.get()) {
            throw new IllegalStateException("VolcAsrClient not connected");
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

        client.close();
        log.info("VolcAsrClient disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected.get() && client != null && !client.isConnectionLost();
    }
}
