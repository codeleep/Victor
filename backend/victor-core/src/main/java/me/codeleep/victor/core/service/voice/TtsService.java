package me.codeleep.victor.core.service.voice;

import me.codeleep.victor.core.entity.VoiceTtsConfig;

/**
 * TTS（语音合成）服务接口
 */
public interface TtsService {

    /**
     * 将文本转换为语音
     *
     * @param text 文本内容
     * @param config 语音合成配置
     * @return 音频数据
     */
    byte[] synthesize(String text, VoiceTtsConfig config);

    /**
     * 流式语音合成
     *
     * @param text 文本内容
     * @param config 语音合成配置
     * @return 音频数据流
     */
    default byte[] synthesizeStream(String text, VoiceTtsConfig config) {
        return synthesize(text, config);
    }
}
