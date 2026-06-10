package me.codeleep.victor.infra.voice;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import me.codeleep.victor.infra.voice.volcengine.asr.VolcAsrClient;
import me.codeleep.victor.infra.voice.volcengine.protocol.AudioInfo;
import me.codeleep.victor.infra.voice.volcengine.utils.AudioUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ASR 文件识别测试：读取本地 WAV 文件，调用火山引擎 ASR 进行语音识别。
 *
 * <p>需要环境变量：</p>
 * <ul>
 *   <li>{@code VOLC_API_KEY} — 火山引擎 API Key（必需）</li>
 * </ul>
 * <p>音频文件从 classpath 读取：{@code src/test/resources/test_audio.wav}</p>
 *
 * <p>如果 {@code VOLC_API_KEY} 未设置，测试将被跳过。</p>
 */
@Slf4j
class AsrFileDemoTest {

    @Test
    @DisplayName("ASR文件识别: 读取WAV文件并识别")
    void recognizeWavFile() throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOLC_API_KEY 环境变量未设置，跳过测试");

        VolcAsrClient client = new VolcAsrClient(apiKey, "volc.bigasr.sauc.duration");
        try {
            client.connect();
            log.info("ASR 已连接");

            byte[] fullData;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("test_audio.wav")) {
                assumeTrue(is != null, "测试音频文件 test_audio.wav 未找到");
                fullData = is.readAllBytes();
            }
            log.info("读取音频文件完成，总大小: {} 字节", fullData.length);

            // 播放音频（忽略异常）
            try (Clip clip = AudioSystem.getClip()) {
                clip.open(AudioSystem.getAudioInputStream(new ByteArrayInputStream(fullData)));
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000);
            } catch (Exception e) {
                log.warn("音频播放失败（已忽略）: {}", e.getMessage());
            }

            if (AudioUtils.isWavFormat(fullData)) {
                AudioInfo audioInfo = AudioUtils.parseWavInfo(fullData);
                log.info("音频信息: 采样率={}, 声道数={}, 位深={}",
                        audioInfo.sampleRate, audioInfo.channels, audioInfo.bitsPerSample);
            } else {
                log.info("音频文件非标准WAV格式，跳过头部解析，直接识别");
            }

            AsrResult result = client.recognize(fullData);

            assertNotNull(result, "ASR 识别结果不应为 null");
            assertNotNull(result.getText(), "识别文本不应为 null");
            assertFalse(result.getText().isEmpty(), "识别文本不应为空");
            log.info("识别结果: {}", result.getText());
        } finally {
            client.disconnect();
            log.info("ASR 已断开");
        }
    }
}
