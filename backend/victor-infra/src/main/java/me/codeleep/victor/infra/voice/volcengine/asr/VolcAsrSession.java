package me.codeleep.victor.infra.voice.volcengine.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import me.codeleep.victor.infra.voice.asr.AsrSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 火山引擎 ASR 会话，代表一次独立的语音识别任务。
 *
 * <p>每个 VolcAsrSession 绑定唯一的 sessionId，使用Main2中的二进制协议进行通信，
 * 支持流式输入音频、流式输出识别结果。</p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>{@link #start()} — 发送初始配置请求（FULL_CLIENT_REQUEST），等待服务端确认</li>
 *   <li>{@link #sendAudio(byte[])} — 流式发送音频数据（AUDIO_ONLY_REQUEST，可多次调用）</li>
 *   <li>{@link #pollResult(long)} / {@link #takeResult()} — 流式获取识别结果</li>
 *   <li>{@link #finishAudio()} — 发送最后一段音频，通知服务端音频已全部发送</li>
 *   <li>{@link #close()} — 关闭会话（支持 try-with-resources）</li>
 * </ol>
 */
@Slf4j
public class VolcAsrSession implements AsrSession {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 音频处理常量 */
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int DEFAULT_BITS = 16;
    private static final int DEFAULT_CHANNELS = 1;
    private static final int DEFAULT_SEGMENT_DURATION_MS = 200;

    /** 所属的 VolcAsrClient */
    private final VolcAsrClient client;
    /** 会话ID */
    private final String sessionId;
    /** 识别结果队列 */
    private final BlockingQueue<AsrResult> resultQueue = new LinkedBlockingQueue<>();
    /** 会话是否已激活 */
    private final AtomicBoolean active = new AtomicBoolean(false);
    /** 会话是否已结束 */
    private final AtomicBoolean finished = new AtomicBoolean(false);
    /** 音频是否已发送完毕 */
    private final AtomicBoolean audioFinished = new AtomicBoolean(false);
    /** 音频发送序列号 */
    private int audioSequence = 0;
    /** 最近一次错误 */
    private volatile Throwable lastError;

    VolcAsrSession(VolcAsrClient client, String sessionId) {
        this.client = client;
        this.sessionId = sessionId;
    }

    /**
     * 启动会话：发送初始配置请求，对应Main2.sendFullClientRequest。
     *
     * @throws Exception 发送失败或等待超时（30秒）
     */
    void start() throws Exception {
        Map<String, Object> payload = Map.of(
                "user", Map.of("uid", "demo_uid"),
                "audio", Map.of(
                        "format", "pcm",
                        "codec", "raw",
                        "rate", DEFAULT_SAMPLE_RATE,
                        "bits", DEFAULT_BITS,
                        "channel", DEFAULT_CHANNELS),
                "request", Map.of(
                        "model_name", "bigmodel",
                        "enable_itn", true,
                        "enable_punc", true,
                        "enable_ddc", true,
                        "show_utterances", true,
                        "enable_nonstream", true,
                        "result_timeout", 1200)); // 10分钟超时

        String payloadStr = objectMapper.writeValueAsString(payload);
        log.info("Sending full client request: {}", payloadStr);

        // 序列号在 VolcAsrClient 级别维护，服务器按API key追踪，跨连接连续递增
        audioSequence = client.getNextSequence();
        client.getWebSocketClient().sendFullClientRequest(payloadStr.getBytes(), audioSequence);

        active.set(true);
        log.info("ASR Session started: {}", sessionId);
    }

    private void waitForSessionStarted() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 30000) {
            AsrResult result = resultQueue.poll(50, TimeUnit.MILLISECONDS);
            if (result != null && result.isFinal() && result.getText() == null) {
                return;
            }
        }
        throw new RuntimeException("Timeout waiting for ASR session started");
    }

    @Override
    public void sendAudio(byte[] audioData) throws Exception {
        if (!active.get()) {
            throw new IllegalStateException("Session not active");
        }
        if (audioFinished.get()) {
            throw new IllegalStateException("Audio already finished");
        }

        // format=pcm，直接发送原始PCM数据
        int segmentSize = calculateSegmentSize();
        log.debug("Sending audio: total size={}, segmentSize={}, segments={}",
                audioData.length, segmentSize, (audioData.length + segmentSize - 1) / segmentSize);

        int offset = 0;
        while (offset < audioData.length) {
            int len = Math.min(segmentSize, audioData.length - offset);
            byte[] segment = new byte[len];
            System.arraycopy(audioData, offset, segment, 0, len);
            offset += len;

            audioSequence++;
            client.getWebSocketClient().sendAudioSegment(segment, false, audioSequence);
        }
    }

    /**
     * 通知服务端音频已全部发送完毕。
     * 发送最后一段标记为isLast的空音频。
     */
    @Override
    public void finishAudio() throws Exception {
        if (!active.get() || audioFinished.get()) {
            return;
        }

        audioFinished.set(true);
        audioSequence++;
        client.getWebSocketClient().sendAudioSegment(new byte[0], true, -audioSequence);
    }

    @Override
    public AsrResult takeResult() throws InterruptedException {
        AsrResult result = resultQueue.take();
        if (result.isFinal() && result.getText() == null) {
            return null;
        }
        return result;
    }

    @Override
    public AsrResult pollResult(long timeoutMs) throws InterruptedException {
        AsrResult result = resultQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (result == null) {
            return null;
        }
        if (result.isFinal() && result.getText() == null) {
            return null;
        }
        return result;
    }

    @Override
    public boolean hasResult() {
        return !resultQueue.isEmpty();
    }

    @Override
    public void audioFinished() {
        audioFinished.set(true);
    }

    @Override
    public boolean isAudioFinished() {
        return audioFinished.get();
    }

    @Override
    public boolean isFinished() {
        return finished.get();
    }

    @Override
    public void close() throws Exception {
        if (!active.getAndSet(false)) {
            return;
        }
        if (!finished.get()) {
            try {
                finishAudio();
            } catch (Exception e) {
                log.warn("Failed to send finish audio during close", e);
            }
        }
        client.removeSession();
        log.info("ASR Session closed: {}", sessionId);
    }

    /**
     * 处理收到的ASR消息，对应Main2.processResponses的解析逻辑
     */
    void handleMessage(AsrMessage msg) {
        log.debug("Received ASR message: type={}, code={}, isLast={}, seq={}, payloadMsg='{}'",
                msg.getType(), msg.getCode(), msg.isLastPackage(), msg.getSequence(),
                msg.getPayloadMsg() != null && msg.getPayloadMsg().length() > 200
                        ? msg.getPayloadMsg().substring(0, 200) + "..." : msg.getPayloadMsg());

        // 检查错误
        if (msg.getCode() != 0) {
            log.error("ASR error: code={}, msg={}", msg.getCode(), msg.getPayloadMsg());
            lastError = new RuntimeException("ASR error: " + msg.getPayloadMsg());
            finished.set(true);
            resultQueue.offer(new AsrResult(null, true, -1));
            return;
        }

        // 解析payloadMsg中的识别结果
        if (msg.getPayloadMsg() != null && !msg.getPayloadMsg().isEmpty()) {
            try {
                JsonNode root = objectMapper.readTree(msg.getPayloadMsg());
                String text = "";
                if (root.has("result")) {
                    JsonNode result = root.get("result");
                    if (result.has("text")) {
                        text = result.get("text").asText();
                    }
                }
                boolean isFinal = msg.isLastPackage()
                        || (root.has("is_final") && root.get("is_final").asBoolean());
                int seq = msg.getSequence();
                log.debug("ASR result: text='{}', isFinal={}, seq={}", text, isFinal, seq);
                resultQueue.offer(new AsrResult(text, isFinal, seq));
            } catch (Exception e) {
                log.error("Failed to parse ASR payload", e);
            }
        }

        // 最后一个包
        if (msg.isLastPackage()) {
            finished.set(true);
            resultQueue.offer(new AsrResult(null, true, -1));
        }
    }

    private int calculateSegmentSize() {
        int sampWidth = DEFAULT_BITS / 8;
        int bytesPerSec = DEFAULT_CHANNELS * sampWidth * DEFAULT_SAMPLE_RATE;
        return bytesPerSec * DEFAULT_SEGMENT_DURATION_MS / 1000;
    }
}
