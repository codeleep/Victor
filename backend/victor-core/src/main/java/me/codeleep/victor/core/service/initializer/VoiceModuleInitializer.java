package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.VoiceServiceProvider;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.mapper.VoiceAsrConfigMapper;
import me.codeleep.victor.core.mapper.VoiceTtsConfigMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音模块初始化器
 * 负责创建默认 ASR 和 TTS 配置
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class VoiceModuleInitializer implements ModuleInitializer {

    private final VoiceAsrConfigMapper voiceAsrConfigMapper;
    private final VoiceTtsConfigMapper voiceTtsConfigMapper;

    @Override
    public Map<String, Object> init(Long userId) {
        int asrCreated = 0;
        int ttsCreated = 0;

        if (ensureDefaultAsrConfig(userId)) asrCreated = 1;
        if (ensureDefaultTtsConfig(userId)) ttsCreated = 1;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asrCreated", asrCreated);
        result.put("ttsCreated", ttsCreated);
        return result;
    }

    private boolean ensureDefaultAsrConfig(Long userId) {
        VoiceAsrConfig existing = voiceAsrConfigMapper.selectOne(
                new LambdaQueryWrapper<VoiceAsrConfig>()
                        .eq(VoiceAsrConfig::getUserId, userId)
                        .eq(VoiceAsrConfig::getIsDefault, true)
                        .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        VoiceAsrConfig config = new VoiceAsrConfig();
        config.setUserId(userId);
        config.setName("默认ASR配置");
        config.setDescription("系统初始化创建的默认语音识别配置，请修改API端点和密钥后启用");
        config.setProvider(VoiceServiceProvider.ALIYUN);
        config.setApiEndpoint("");
        config.setAuthParams(Map.of("apiKey", ""));
        config.setLanguage("zh-CN");
        config.setExtraParams(Map.of());
        config.setIsEnabled(true);
        config.setIsDefault(true);
        voiceAsrConfigMapper.insert(config);
        log.info("[VoiceInit] 创建默认ASR配置: id={}", config.getId());
        return true;
    }

    private boolean ensureDefaultTtsConfig(Long userId) {
        VoiceTtsConfig existing = voiceTtsConfigMapper.selectOne(
                new LambdaQueryWrapper<VoiceTtsConfig>()
                        .eq(VoiceTtsConfig::getUserId, userId)
                        .eq(VoiceTtsConfig::getIsDefault, true)
                        .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        VoiceTtsConfig config = new VoiceTtsConfig();
        config.setUserId(userId);
        config.setName("默认TTS配置");
        config.setDescription("系统初始化创建的默认语音合成配置，请修改API端点和密钥后启用");
        config.setProvider(VoiceServiceProvider.ALIYUN);
        config.setApiEndpoint("");
        config.setAuthParams(Map.of("apiKey", ""));
        config.setVoiceName("");
        config.setExtraParams(Map.of());
        config.setIsEnabled(true);
        config.setIsDefault(true);
        voiceTtsConfigMapper.insert(config);
        log.info("[VoiceInit] 创建默认TTS配置: id={}", config.getId());
        return true;
    }
}
