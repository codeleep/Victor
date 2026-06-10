package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.service.VoiceAsrConfigService;
import me.codeleep.victor.core.service.VoiceTtsConfigService;
import me.codeleep.victor.core.service.voice.AsrService;
import me.codeleep.victor.core.service.voice.TtsService;
import me.codeleep.victor.infra.voice.volcengine.utils.AudioUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 语音服务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceAsrConfigService voiceAsrConfigService;
    private final VoiceTtsConfigService voiceTtsConfigService;
    private final AsrService asrService;
    private final TtsService ttsService;

    // ==================== ASR配置相关 ====================

    /**
     * 创建ASR配置
     */
    @PostMapping("/asr-configs")
    public Result<Long> createAsrConfig(@Valid @RequestBody VoiceAsrConfig config) {
        Long id = voiceAsrConfigService.create(config);
        return Result.success(id);
    }

    /**
     * 获取ASR配置列表
     */
    @GetMapping("/asr-configs")
    public Result<List<VoiceAsrConfig>> listAsrConfigs() {
        List<VoiceAsrConfig> list = voiceAsrConfigService.listByCurrentUser();
        return Result.success(list);
    }

    /**
     * 获取ASR配置详情
     */
    @GetMapping("/asr-configs/{id}")
    public Result<VoiceAsrConfig> getAsrConfig(@PathVariable Long id) {
        VoiceAsrConfig config = voiceAsrConfigService.getById(id);
        return Result.success(config);
    }

    /**
     * 更新ASR配置
     */
    @PutMapping("/asr-configs/{id}")
    public Result<Void> updateAsrConfig(@PathVariable Long id, @Valid @RequestBody VoiceAsrConfig config) {
        voiceAsrConfigService.update(id, config);
        return Result.success();
    }

    /**
     * 删除ASR配置
     */
    @DeleteMapping("/asr-configs/{id}")
    public Result<Void> deleteAsrConfig(@PathVariable Long id) {
        voiceAsrConfigService.delete(id);
        return Result.success();
    }

    /**
     * 设为默认ASR配置
     */
    @PostMapping("/asr-configs/{id}/set-default")
    public Result<Void> setDefaultAsr(@PathVariable Long id) {
        voiceAsrConfigService.setDefault(id);
        return Result.success();
    }

    /**
     * 测试ASR配置
     * 读取 classpath:voice/test_audio.wav 进行语音识别，
     * 预期识别结果为 "你好这是一段测试语音"
     */
    @PostMapping("/asr-configs/{id}/test")
    public Result<String> testAsrConfig(@PathVariable Long id) {
        VoiceAsrConfig config = voiceAsrConfigService.getById(id);
        if (config == null) {
            return Result.error(ResultCode.BAD_REQUEST, "ASR配置不存在");
        }
        try {
            ClassPathResource resource = new ClassPathResource("voice/test_audio.wav");
            byte[] audioData = StreamUtils.copyToByteArray(resource.getInputStream());
            String text = asrService.recognize(audioData, config);
            return Result.success(text);
        } catch (Exception e) {
            log.error("ASR配置测试失败: id={}, error={}", id, e.getMessage());
            return Result.error(ResultCode.BAD_REQUEST, "测试失败: " + e.getMessage());
        }
    }

    // ==================== TTS配置相关 ====================

    /**
     * 创建TTS配置
     */
    @PostMapping("/tts-configs")
    public Result<Long> createTtsConfig(@Valid @RequestBody VoiceTtsConfig config) {
        Long id = voiceTtsConfigService.create(config);
        return Result.success(id);
    }

    /**
     * 获取TTS配置列表
     */
    @GetMapping("/tts-configs")
    public Result<List<VoiceTtsConfig>> listTtsConfigs() {
        List<VoiceTtsConfig> list = voiceTtsConfigService.listByCurrentUser();
        return Result.success(list);
    }

    /**
     * 获取TTS配置详情
     */
    @GetMapping("/tts-configs/{id}")
    public Result<VoiceTtsConfig> getTtsConfig(@PathVariable Long id) {
        VoiceTtsConfig config = voiceTtsConfigService.getById(id);
        return Result.success(config);
    }

    /**
     * 更新TTS配置
     */
    @PutMapping("/tts-configs/{id}")
    public Result<Void> updateTtsConfig(@PathVariable Long id, @Valid @RequestBody VoiceTtsConfig config) {
        voiceTtsConfigService.update(id, config);
        return Result.success();
    }

    /**
     * 删除TTS配置
     */
    @DeleteMapping("/tts-configs/{id}")
    public Result<Void> deleteTtsConfig(@PathVariable Long id) {
        voiceTtsConfigService.delete(id);
        return Result.success();
    }

    /**
     * 设为默认TTS配置
     */
    @PostMapping("/tts-configs/{id}/set-default")
    public Result<Void> setDefaultTts(@PathVariable Long id) {
        voiceTtsConfigService.setDefault(id);
        return Result.success();
    }

    /**
     * 测试TTS配置
     * 合成文本 "你好这是一段测试语音"，返回音频数据供前端直接播放。
     * 前端可通过 Audio 对象播放返回的音频流:
     *   const audio = new Audio('/api/v1/voice/tts-configs/1/test');
     *   audio.play();
     */
    @PostMapping("/tts-configs/{id}/test")
    public ResponseEntity<byte[]> testTtsConfig(@PathVariable Long id) {
        VoiceTtsConfig config = voiceTtsConfigService.getById(id);
        if (config == null) {
            return ResponseEntity.badRequest().build();
        }
        byte[] audioData = ttsService.synthesize("你好这是一段测试语音", config);

        String encoding = getStringParam(config.getExtraParams(), "encoding", "wav");
        byte[] responseBody = "wav".equalsIgnoreCase(encoding)
                ? AudioUtils.wrapPcmWithWavHeader(audioData, 24000, 16, 1)
                : audioData;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, AudioUtils.contentTypeForEncoding(encoding))
                .body(responseBody);
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

}
