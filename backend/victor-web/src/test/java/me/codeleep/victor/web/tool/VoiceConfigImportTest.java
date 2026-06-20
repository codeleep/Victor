package me.codeleep.victor.web.tool;

import me.codeleep.victor.common.enums.VoiceServiceProvider;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.mapper.VoiceAsrConfigMapper;
import me.codeleep.victor.core.mapper.VoiceTtsConfigMapper;
import me.codeleep.victor.web.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ASR/TTS 语音配置导入脚本
 * 环境变量：
 *   VOLC_API_KEY      - 火山引擎 API Key（必填）
 *   VOLC_ASR_ENDPOINT - ASR WebSocket 地址（可选，有默认值）
 *   VOLC_TTS_ENDPOINT - TTS WebSocket 地址（可选，有默认值）
 *   VOLC_ASR_VOICE    - ASR 发音人/语言（可选，默认 zh-CN）
 *   VOLC_TTS_VOICE    - TTS 音色（可选，默认 zh_female_xiaohe_uranus_bigtts）
 */
class VoiceConfigImportTest extends BaseApiTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private VoiceAsrConfigMapper voiceAsrConfigMapper;
    @Autowired
    private VoiceTtsConfigMapper voiceTtsConfigMapper;

    @Test
    @DisplayName("导入ASR和TTS语音配置")
    @Rollback(false)
    @Transactional
    void importVoiceConfig() {
        String apiKey = env("VOLC_API_KEY", "");
        Assumptions.assumeTrue(!apiKey.isEmpty(), "环境变量 VOLC_API_KEY 未设置，跳过语音配置导入");

        // ==================== 1. ASR 配置 ====================
        VoiceAsrConfig asr = new VoiceAsrConfig();
        asr.setUserId(USER_ID);
        asr.setName("火山引擎语音识别");
        asr.setDescription("火山引擎 BigASR 语音识别服务");
        asr.setProvider(VoiceServiceProvider.VOLCENGINE);
        asr.setApiEndpoint(env("VOLC_ASR_ENDPOINT", "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"));
        asr.setAuthParams(Map.of("apiKey", apiKey));
        asr.setExtraParams(Map.of(
                "resourceId", env("VOLC_ASR_RESOURCE_ID", "volc.bigasr.sauc.duration")
        ));
        asr.setLanguage(env("VOLC_ASR_VOICE", "zh-CN"));
        asr.setIsEnabled(true);
        asr.setIsDefault(true);
        voiceAsrConfigMapper.insert(asr);
        System.out.println("[成功] ASR配置 id=" + asr.getId() + " " + asr.getName());

        // ==================== 2. TTS 配置 ====================
        VoiceTtsConfig tts = new VoiceTtsConfig();
        tts.setUserId(USER_ID);
        tts.setName("火山引擎语音合成");
        tts.setDescription("火山引擎双向TTS语音合成服务");
        tts.setProvider(VoiceServiceProvider.VOLCENGINE);
        tts.setApiEndpoint(env("VOLC_TTS_ENDPOINT", "wss://openspeech.bytedance.com/api/v3/tts/bidirection"));
        tts.setAuthParams(Map.of("apiKey", apiKey));
        tts.setExtraParams(Map.of(
                "resourceId", env("VOLC_TTS_RESOURCE_ID", "volc.service_type.10029"),
                "encoding", env("VOLC_TTS_ENCODING", "wav"),
                "speedRatio", Double.parseDouble(env("VOLC_TTS_SPEED_RATIO", "1.5"))
        ));
        tts.setVoiceName(env("VOLC_TTS_VOICE", "zh_female_xiaohe_uranus_bigtts"));
        tts.setIsEnabled(true);
        tts.setIsDefault(true);
        voiceTtsConfigMapper.insert(tts);
        System.out.println("[成功] TTS配置 id=" + tts.getId() + " " + tts.getName());

        System.out.println("========================================");
        System.out.println("语音配置导入完成! ASR id=" + asr.getId() + ", TTS id=" + tts.getId());
    }

    // ==================== 工具方法 ====================

    private String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }
}
