package me.codeleep.victor.web.websocket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API Key 认证提供者。
 *
 * <p>使用火山引擎 API Key 进行认证。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * speech:
 *   auth:
 *     type: apikey
 *     api-key: your-api-key
 *     asr-resource-id: volc.bigasr.sauc.duration
 *     tts-resource-id: seed-tts-2.0
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "speech.auth")
public class ApiKeyAuthProvider implements AuthProvider {

    private String type = "apikey";

    /** 火山引擎 API Key */
    private String apiKey;

    /** ASR 资源ID */
    private String asrResourceId = "volc.bigasr.sauc.duration";

    /** TTS 资源ID */
    private String ttsResourceId = "seed-tts-2.0";

    @Override
    public String getType() {
        return "apikey";
    }

    @Override
    public Map<String, String> getAsrAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", apiKey);
        headers.put("X-Api-Resource-Id", asrResourceId);
        headers.put("X-Api-Connect-Id", UUID.randomUUID().toString());
        return headers;
    }

    @Override
    public Map<String, String> getTtsAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", apiKey);
        headers.put("X-Api-Resource-Id", ttsResourceId);
        headers.put("X-Api-Connect-Id", UUID.randomUUID().toString());
        return headers;
    }

    @Override
    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
