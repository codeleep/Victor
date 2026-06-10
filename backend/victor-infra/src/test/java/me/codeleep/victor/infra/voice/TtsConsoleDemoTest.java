package me.codeleep.victor.infra.voice;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.tts.TtsSession;
import me.codeleep.victor.infra.voice.volcengine.TextRectifier;
import me.codeleep.victor.infra.voice.volcengine.tts.VolcTtsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TTS 控制台示例测试：连接 TTS 服务，合成文本并验证音频输出。
 *
 * <p>需要环境变量：</p>
 * <ul>
 *   <li>{@code VOLC_API_KEY} — 火山引擎 API Key（必需）</li>
 *   <li>{@code VOLC_TTS_RESOURCE_ID} — TTS 资源 ID（可选，默认 {@code seed-tts-2.0}）</li>
 *   <li>{@code VOLC_TTS_VOICE} — 发音人（可选，默认 {@code zh_female_xiaohe_uranus_bigtts}）</li>
 *   <li>{@code VOLC_TTS_ENCODING} — 输出音频编码格式（可选，默认 {@code wav}）</li>
 * </ul>
 *
 * <p>如果 {@code VOLC_API_KEY} 未设置，测试将被跳过。</p>
 */
@Slf4j
class TtsConsoleDemoTest {

    @Test
    @DisplayName("TTS合成: 单句文本合成")
    void synthesizeSingleSentence() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String resourceId = getEnvOrDefault("VOLC_TTS_RESOURCE_ID", "seed-tts-2.0");
        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, resourceId, voice, encoding);
        try {
            ttsClient.connect();
            log.info("TTS 已连接");

            byte[] audioData = ttsClient.synthesize("你好，这是一段测试语音。");
            assertNotNull(audioData, "TTS 合成音频不应为 null");
            assertTrue(audioData.length > 0, "TTS 合成音频不应为空");
            log.info("TTS 合成完成，音频大小: {} 字节", audioData.length);

            Path outDir = Path.of("target", "dist");
            Files.createDirectories(outDir);
            Path wavFile = outDir.resolve("tts-output.wav");
            Files.write(wavFile, audioData);
            log.info("音频已保存到: {}", wavFile.toAbsolutePath());

            // 播放音频（忽略异常）
            try {
                int headerSize = 32; // INFO+ISFT+size+data+size
                byte[] pcmData = java.util.Arrays.copyOfRange(audioData, headerSize, audioData.length);
                AudioFormat format = new AudioFormat(24000, 16, 1, true, false);
                AudioInputStream ais = new AudioInputStream(
                        new ByteArrayInputStream(pcmData), format, pcmData.length / format.getFrameSize());
                try (Clip clip = AudioSystem.getClip()) {
                    clip.open(ais);
                    clip.start();
                    Thread.sleep(clip.getMicrosecondLength() / 1000);
                }
                log.info("音频播放完成");
            } catch (Exception e) {
                log.warn("音频播放失败（已忽略）: {}", e.getMessage());
            }
        } finally {
            ttsClient.disconnect();
            log.info("TTS 已断开");
        }
    }

    @Test
    @DisplayName("TTS合成: 流式合成多句文本")
    void synthesizeWithSession() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String resourceId = getEnvOrDefault("VOLC_TTS_RESOURCE_ID", "seed-tts-2.0");
        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, resourceId, voice, encoding);
        try {
            ttsClient.connect();
            log.info("TTS 已连接");

            try (TtsSession session = ttsClient.createSession()) {
                session.speakText("第一句话。");
                session.finish();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (!session.isFinished()) {
                    byte[] audio = session.pollAudio(5000);
                    if (audio != null) {
                        baos.write(audio);
                    }
                }
                while (session.hasAudio()) {
                    byte[] audio = session.pollAudio(100);
                    if (audio != null) {
                        baos.write(audio);
                    }
                }

                byte[] totalAudio = baos.toByteArray();
                assertTrue(totalAudio.length > 0, "流式合成音频不应为空");
                log.info("流式合成完成，音频大小: {} 字节", totalAudio.length);
            }
        } finally {
            ttsClient.disconnect();
            log.info("TTS 已断开");
        }
    }

    @Test
    @DisplayName("TTS合成: TextRectifier分句合成")
    void synthesizeWithTextRectifier() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        String resourceId = getEnvOrDefault("VOLC_TTS_RESOURCE_ID", "seed-tts-2.0");
        String voice = getEnvOrDefault("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts");
        String encoding = getEnvOrDefault("VOLC_TTS_ENCODING", "wav");

        VolcTtsClient ttsClient = new VolcTtsClient(apiKey, resourceId, voice, encoding);
        try {
            ttsClient.connect();
            log.info("TTS 已连接");

            CountDownLatch latch = new CountDownLatch(3);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            TextRectifier rectifier = new TextRectifier(200, text -> {
                try (TtsSession session = ttsClient.createSession()) {
                    session.speakText(text);
                    session.finish();

                    while (!session.isFinished()) {
                        byte[] audio = session.pollAudio(5000);
                        if (audio != null) {
                            synchronized (baos) {
                                baos.write(audio);
                            }
                        }
                    }
                    while (session.hasAudio()) {
                        byte[] audio = session.pollAudio(100);
                        if (audio != null) {
                            synchronized (baos) {
                                baos.write(audio);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("TTS 合成失败: {}", text, e);
                }
                latch.countDown();
            });

            rectifier.start();
            rectifier.addTextWithSplit("你好！这是第一句。这是第二句！这是第三句？");

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            rectifier.stop();

            assertTrue(completed, "TextRectifier 分句合成应在30秒内完成");
            assertTrue(baos.size() > 0, "分句合成音频不应为空");
            log.info("TextRectifier 分句合成完成，音频大小: {} 字节", baos.size());
        } finally {
            ttsClient.disconnect();
            log.info("TTS 已断开");
        }
    }

    private String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
