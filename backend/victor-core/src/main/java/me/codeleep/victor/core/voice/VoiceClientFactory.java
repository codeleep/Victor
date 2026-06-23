package me.codeleep.victor.core.voice;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.VoiceServiceProvider;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.infra.voice.asr.AsrClient;
import me.codeleep.victor.infra.voice.tts.TtsClient;
import me.codeleep.victor.infra.voice.volcengine.asr.VolcAsrClient;
import me.codeleep.victor.infra.voice.volcengine.tts.VolcTtsClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 语音客户端工厂，根据数据库配置（VoiceAsrConfig/VoiceTtsConfig）创建对应的客户端实例。
 *
 * <p>根据配置中的 {@link VoiceServiceProvider} 字段决定实例化哪个具体实现，
 * 新增服务商只需在本类中增加对应的 case 即可。</p>
 */
@Slf4j
@Component
public class VoiceClientFactory {

    /**
     * 根据数据库配置创建 ASR 客户端实例。
     *
     * @param config ASR 配置
     * @return ASR 客户端
     */
    public AsrClient createAsrClient(VoiceAsrConfig config) {
        VoiceServiceProvider provider = config.getProvider();
        log.info("Creating ASR client for provider: {}", provider);

        switch (provider) {
            case VOLCENGINE:
                return createVolcAsrClient(config);
            default:
                throw new UnsupportedOperationException("Unsupported ASR provider: " + provider);
        }
    }

    /**
     * 根据数据库配置创建 TTS 客户端实例。
     *
     * @param config TTS 配置
     * @return TTS 客户端
     */
    public TtsClient createTtsClient(VoiceTtsConfig config) {
        VoiceServiceProvider provider = config.getProvider();
        log.info("Creating TTS client for provider: {}", provider);

        switch (provider) {
            case VOLCENGINE:
                return createVolcTtsClient(config);
            default:
                throw new UnsupportedOperationException("Unsupported TTS provider: " + provider);
        }
    }

    private VolcAsrClient createVolcAsrClient(VoiceAsrConfig config) {
        String apiKey = getApiKey(config.getAuthParams());
        String resourceId = getStringParam(config.getExtraParams(), "resourceId", "volc.bigasr.sauc.duration");

        return new VolcAsrClient(apiKey, resourceId);
    }

    private VolcTtsClient createVolcTtsClient(VoiceTtsConfig config) {
        String apiKey = getApiKey(config.getAuthParams());
        String voice = config.getVoiceName() != null ? config.getVoiceName() : "zh_female_xiaohe_uranus_bigtts";
        String resourceId = getStringParam(config.getExtraParams(), "resourceId", "seed-tts-2.0");
        String encoding = getStringParam(config.getExtraParams(), "encoding", "wav");
        double speedRatio = getDoubleParam(config.getExtraParams(), "speedRatio", 1.5);

        return new VolcTtsClient(apiKey, resourceId, voice, encoding, speedRatio);
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString();
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value.toString());
    }

    private String getApiKey(Map<String, Object> authParams) {
        if (authParams != null) {
            Object apiKey = authParams.get("apiKey");
            if (apiKey != null) {
                return apiKey.toString();
            }
        }
        return "";
    }
}
