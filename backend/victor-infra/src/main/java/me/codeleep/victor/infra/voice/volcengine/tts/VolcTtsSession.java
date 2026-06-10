package me.codeleep.victor.infra.voice.volcengine.tts;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.tts.TtsSession;
import me.codeleep.victor.infra.voice.volcengine.protocol.EventType;
import me.codeleep.victor.infra.voice.volcengine.protocol.MsgTypeFlagBits;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 火山引擎双向TTS会话，代表一次独立的文本转语音任务。
 *
 * <p>每个 VolcTtsSession 绑定唯一的 sessionId，与 {@link VolcTtsClient} 一对一关联。</p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>{@link #start()} — 发送 START_SESSION，等待服务端确认</li>
 *   <li>{@link #speakText(String)} — 流式发送待合成的文本（可多次调用）</li>
 *   <li>{@link #pollAudio(long)} / {@link #takeAudio()} — 流式获取合成的音频数据</li>
 *   <li>{@link #finish()} — 发送 FINISH_SESSION，通知服务端文本已全部发送</li>
 *   <li>{@link #close()} — 关闭会话（支持 try-with-resources）</li>
 * </ol>
 */
@Slf4j
public class VolcTtsSession implements TtsSession {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 所属的 VolcTtsClient，用于发送消息和访问配置 */
    private final VolcTtsClient client;
    /** 服务端会话ID */
    private final String sessionId;
    /** 音频数据队列，同时承载音频数据和状态信号（空 byte[0]） */
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    /** 会话是否已激活（START_SESSION 确认后为 true） */
    private final AtomicBoolean active = new AtomicBoolean(false);
    /** 会话是否已结束（收到 SESSION_FINISHED 或 ERROR 后为 true） */
    private final AtomicBoolean finished = new AtomicBoolean(false);
    /** 文本是否已发送完毕（调用 finish() 后为 true） */
    private final AtomicBoolean textFinished = new AtomicBoolean(false);
    /** 最近一次错误，用于诊断 */
    private volatile Throwable lastError;

    VolcTtsSession(VolcTtsClient client, String sessionId) {
        this.client = client;
        this.sessionId = sessionId;
    }

    /**
     * 启动会话：向服务端发送 START_SESSION 请求，并等待确认。
     *
     * @throws Exception 发送失败或等待超时（30秒）
     */
    void start() throws Exception {
        Map<String, Object> reqParams = new java.util.LinkedHashMap<>();
        reqParams.put("speaker", client.getVoice());
        reqParams.put("audio_params", Map.of(
                "format", client.getEncoding(),
                "sample_rate", 24000,
                "enable_timestamp", true));
        reqParams.put("additions", objectMapper.writeValueAsString(Map.of(
                "disable_markdown_filter", false)));
        if (client.getSpeedRatio() != 1.0) {
            reqParams.put("speed_ratio", client.getSpeedRatio());
        }

        Map<String, Object> baseRequest = Map.of(
                "user", Map.of("uid", UUID.randomUUID().toString()),
                "namespace", "BidirectionalTTS",
                "req_params", reqParams,
                "event", EventType.START_SESSION.getValue());

        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.START_SESSION);
        message.setSessionId(sessionId);
        message.setPayload(objectMapper.writeValueAsBytes(baseRequest));
        client.sendMessage(message);

        waitForSessionStarted();
        active.set(true);
        log.info("Session started: {}", sessionId);
    }

    private void waitForSessionStarted() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 30000) {
            byte[] audio = audioQueue.poll(50, TimeUnit.MILLISECONDS);
            if (audio != null && audio.length == 0) {
                return;
            }
        }
        throw new RuntimeException("Timeout waiting for session started");
    }

    @Override
    public void speakText(String text) throws Exception {
        if (!active.get()) {
            throw new IllegalStateException("Session not active");
        }
        if (textFinished.get()) {
            throw new IllegalStateException("Text already finished");
        }

        Map<String, Object> reqParams = new java.util.LinkedHashMap<>();
        reqParams.put("speaker", client.getVoice());
        reqParams.put("audio_params", Map.of(
                "format", client.getEncoding(),
                "sample_rate", 24000,
                "enable_timestamp", true));
        reqParams.put("text", text);
        reqParams.put("additions", objectMapper.writeValueAsString(Map.of(
                "disable_markdown_filter", false)));
        if (client.getSpeedRatio() != 1.0) {
            reqParams.put("speed_ratio", client.getSpeedRatio());
        }

        Map<String, Object> baseRequest = Map.of(
                "user", Map.of("uid", UUID.randomUUID().toString()),
                "namespace", "BidirectionalTTS",
                "req_params", reqParams,
                "event", EventType.TASK_REQUEST.getValue());

        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.TASK_REQUEST);
        message.setSessionId(sessionId);
        message.setPayload(objectMapper.writeValueAsBytes(baseRequest));
        client.sendMessage(message);
    }

    @Override
    public byte[] takeAudio() throws InterruptedException {
        byte[] audio = audioQueue.take();
        if (audio.length == 0) {
            return null;
        }
        return audio;
    }

    @Override
    public byte[] pollAudio(long timeoutMs) throws InterruptedException {
        byte[] audio = audioQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (audio == null || audio.length == 0) {
            return null;
        }
        return audio;
    }

    @Override
    public boolean hasAudio() {
        return !audioQueue.isEmpty();
    }

    @Override
    public void textFinished() {
        textFinished.set(true);
    }

    @Override
    public boolean isTextFinished() {
        return textFinished.get();
    }

    @Override
    public boolean isFinished() {
        return finished.get();
    }

    @Override
    public void finish() throws Exception {
        if (!active.get() || finished.get()) {
            return;
        }

        textFinished.set(true);

        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.FINISH_SESSION);
        message.setSessionId(sessionId);
        message.setPayload("{}".getBytes());
        client.sendMessage(message);
    }

    @Override
    public void close() throws Exception {
        if (!active.get()) {
            return;
        }
        if (!finished.get()) {
            finish();
        }
        active.set(false);
        client.removeSession();
        log.info("Session closed: {}", sessionId);
    }

    void handleMessage(TtsMessage msg) {
        log.info("[TtsSession] Received message: type={}, event={}, sessionId={}, payloadSize={}",
                msg.getType(), msg.getEvent(), msg.getSessionId(),
                msg.getPayload() != null ? msg.getPayload().length : 0);

        switch (msg.getType()) {
            case FULL_SERVER_RESPONSE:
                if (msg.getEvent() == EventType.SESSION_STARTED) {
                    audioQueue.offer(new byte[0]);
                    log.info("[TtsSession] SESSION_STARTED received");
                } else if (msg.getEvent() == EventType.SESSION_FINISHED) {
                    finished.set(true);
                    audioQueue.offer(new byte[0]);
                    log.info("[TtsSession] SESSION_FINISHED received");
                }
                break;
            case AUDIO_ONLY_SERVER:
                if (msg.getPayload() != null && msg.getPayload().length > 0) {
                    log.info("[TtsSession] AUDIO_ONLY_SERVER received, payload size: {}", msg.getPayload().length);
                    byte[] pcmData = stripWavHeader(msg.getPayload());
                    if (pcmData.length > 0) {
                        log.info("[TtsSession] Offering PCM data to queue, size: {}", pcmData.length);
                        audioQueue.offer(pcmData);
                    }
                }
                break;
            case ERROR:
                log.error("[TtsSession] Session error: {}", msg);
                lastError = new RuntimeException("Session error: " + msg);
                finished.set(true);
                audioQueue.offer(new byte[0]);
                break;
            default:
                log.warn("[TtsSession] Unexpected message type: {}", msg.getType());
        }
    }

    private byte[] stripWavHeader(byte[] data) {
        if (data.length < 44) {
            return data;
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            int pos = 12;
            while (pos + 8 <= data.length) {
                String chunkId = new String(data, pos, 4);
                int chunkSize = ((data[pos + 4] & 0xFF))
                        | ((data[pos + 5] & 0xFF) << 8)
                        | ((data[pos + 6] & 0xFF) << 16)
                        | ((data[pos + 7] & 0xFF) << 24);
                if ("data".equals(chunkId)) {
                    int dataPos = pos + 8;
                    int dataLen = Math.min(chunkSize, data.length - dataPos);
                    if (dataLen > 0) {
                        byte[] pcmData = new byte[dataLen];
                        System.arraycopy(data, dataPos, pcmData, 0, dataLen);
                        return pcmData;
                    }
                }
                pos += 8 + chunkSize;
                if (pos > data.length) {
                    break;
                }
            }
            byte[] pcmData = new byte[data.length - 44];
            System.arraycopy(data, 44, pcmData, 0, pcmData.length);
            return pcmData;
        }
        return data;
    }
}
