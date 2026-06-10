package me.codeleep.victor.infra.voice;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import me.codeleep.victor.infra.voice.asr.AsrSession;
import me.codeleep.victor.infra.voice.tts.TtsSession;
import me.codeleep.victor.infra.voice.volcengine.asr.VolcAsrClient;
import me.codeleep.victor.infra.voice.volcengine.tts.VolcTtsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ASR + TTS 集成测试：生成一段静音PCM → ASR识别 → 将文本送入TTS合成 → 验证音频输出。
 *
 * <p>需要环境变量：</p>
 * <ul>
 *   <li>{@code VOLC_API_KEY} — 火山引擎 API Key（必需）</li>
 * </ul>
 *
 * <p>如果 {@code VOLC_API_KEY} 未设置，测试将被跳过。</p>
 */
@Slf4j
class AsrTtsEchoTest {

    private static final int ASR_SAMPLE_RATE = 16000;
    private static final int ASR_BITS = 16;
    private static final int ASR_CHANNELS = 1;

    @Test
    @DisplayName("ASR+TTS集成: ASR连接测试")
    void asrConnectAndDisconnect() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        VolcAsrClient asrClient = new VolcAsrClient(apiKey, "volc.bigasr.sauc.duration");
        asrClient.connect();
        log.info("ASR 已连接");
        assertTrue(asrClient.isConnected(), "ASR 应处于连接状态");

        asrClient.disconnect();
        log.info("ASR 已断开");
        assertFalse(asrClient.isConnected(), "ASR 应处于断开状态");
    }

    @Test
    @DisplayName("ASR+TTS集成: TTS连接测试")
    void ttsConnectAndDisconnect() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, "seed-tts-2.0", voice, encoding);
        ttsClient.connect();
        log.info("TTS 已连接");
        assertTrue(ttsClient.isConnected(), "TTS 应处于连接状态");

        ttsClient.disconnect();
        log.info("TTS 已断开");
        assertFalse(ttsClient.isConnected(), "TTS 应处于断开状态");
    }

    @Test
    @DisplayName("ASR+TTS集成: TTS合成文本并返回音频")
    void ttsSynthesizeText() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, "seed-tts-2.0", voice, encoding);
        try {
            ttsClient.connect();
            log.info("TTS 已连接");

            byte[] audioData = ttsClient.synthesize("你好，这是一段测试语音。");
            assertNotNull(audioData, "TTS 合成音频不应为 null");
            assertTrue(audioData.length > 0, "TTS 合成音频不应为空");
            log.info("TTS 合成完成，音频大小: {} 字节", audioData.length);
        } finally {
            ttsClient.disconnect();
            log.info("TTS 已断开");
        }
    }

    @Test
    @DisplayName("ASR+TTS集成: 完整复读流程（生成WAV→ASR识别→TTS合成）")
    void fullEchoPipeline() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcAsrClient asrClient = new VolcAsrClient(apiKey, "volc.bigasr.sauc.duration");
        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, "seed-tts-2.0", voice, encoding);

        try {
            // 连接 ASR 和 TTS
            asrClient.connect();
            log.info("ASR 已连接");
            ttsClient.connect();
            log.info("TTS 已连接");

            // 生成一段静音 WAV 用于 ASR 测试（1秒 16kHz 16bit 单声道）
            byte[] silenceWav = generateSilenceWav(ASR_SAMPLE_RATE, ASR_BITS, ASR_CHANNELS, 1000);
            log.info("生成静音WAV: {} 字节", silenceWav.length);

            // ASR 识别
            try (AsrSession asrSession = asrClient.createSession()) {
                asrSession.sendAudio(silenceWav);
                asrSession.finishAudio();

                String lastText = null;
                while (!asrSession.isFinished()) {
                    AsrResult result = asrSession.pollResult(5000);
                    if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                        lastText = result.getText();
                        if (result.isFinal()) break;
                    }
                }
                log.info("ASR 识别结果: {}", lastText);

                // TTS 合成（即使 ASR 识别为空也测试 TTS 合成能力）
                String textToSpeak = (lastText != null && !lastText.isEmpty()) ? lastText : "你好，世界";
                byte[] ttsAudio = ttsClient.synthesize(textToSpeak);
                assertNotNull(ttsAudio, "TTS 合成音频不应为 null");
                assertTrue(ttsAudio.length > 0, "TTS 合成音频不应为空");
                log.info("TTS 合成完成，文本: \"{}\"，音频大小: {} 字节", textToSpeak, ttsAudio.length);
            }
        } finally {
            if (asrClient.isConnected()) {
                asrClient.disconnect();
                log.info("ASR 已断开");
            }
            if (ttsClient.isConnected()) {
                ttsClient.disconnect();
                log.info("TTS 已断开");
            }
        }
    }

    /**
     * 生成指定时长的静音 WAV 数据。
     */
    private byte[] generateSilenceWav(int sampleRate, int bitsPerSample, int channels, int durationMs) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = byteRate * durationMs / 1000;

        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataSize);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});

        // fmt sub-chunk
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1); // PCM
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);

        // data sub-chunk（静音 = 全零）
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataSize);
        buffer.put(new byte[dataSize]); // 全零 = 静音

        return buffer.array();
    }

    private String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
