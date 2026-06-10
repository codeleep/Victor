package me.codeleep.victor.core.service.voice;

import me.codeleep.victor.core.entity.VoiceAsrConfig;

/**
 * ASR（语音识别）服务接口
 */
public interface AsrService {

    /**
     * 将语音转换为文本
     *
     * @param audioData 音频数据
     * @param config 语音识别配置
     * @return 识别的文本
     */
    String recognize(byte[] audioData, VoiceAsrConfig config);

    /**
     * 流式语音识别
     *
     * @param audioData 音频数据
     * @param config 语音识别配置
     * @return 识别的文本（流式）
     */
    default String recognizeStream(byte[] audioData, VoiceAsrConfig config) {
        return recognize(audioData, config);
    }
}
