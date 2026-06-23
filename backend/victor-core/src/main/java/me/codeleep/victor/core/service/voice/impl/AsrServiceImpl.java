package me.codeleep.victor.core.service.voice.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.VoiceServiceProvider;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.service.voice.AsrService;
import me.codeleep.victor.core.voice.VoiceClientFactory;
import me.codeleep.victor.infra.voice.asr.AsrClient;
import me.codeleep.victor.infra.voice.asr.AsrResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrServiceImpl implements AsrService {

    private final ObjectMapper objectMapper;
    private final VoiceClientFactory voiceClientFactory;

    @Override
    public String recognize(byte[] audioData, VoiceAsrConfig config) {
        if (config == null || config.getProvider() == null) {
            return recognizeWithOpenAi(audioData, config);
        }

        VoiceServiceProvider provider = config.getProvider();
        switch (provider) {
            case VOLCENGINE:
                return recognizeWithVolc(audioData, config);
            case OPENAI:
            default:
                return recognizeWithOpenAi(audioData, config);
        }
    }

    private String recognizeWithOpenAi(byte[] audioData, VoiceAsrConfig config) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(config.getApiEndpoint())
                    .defaultHeader("Authorization", "Bearer " + getApiKey(config))
                    .build();

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audioData) {
                @Override
                public String getFilename() {
                    return "audio.mp3";
                }
            });
            body.add("model", "whisper-1");

            if (config.getLanguage() != null) {
                body.add("language", config.getLanguage());
            }

            if (config.getExtraParams() != null) {
                Object model = config.getExtraParams().get("model");
                if (model != null) {
                    body.set("model", model.toString());
                }
            }

            JsonNode response = webClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("text")) {
                return response.get("text").asText();
            }

            return "";
        } catch (Exception e) {
            log.error("OpenAI ASR识别失败: {}", e.getMessage(), e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        }
    }

    private String recognizeWithVolc(byte[] audioData, VoiceAsrConfig config) {
        AsrClient client = voiceClientFactory.createAsrClient(config);
        try {
            client.connect();
            AsrResult result = client.recognize(audioData);
            return result != null ? result.getText() : "";
        } catch (Exception e) {
            log.error("火山引擎ASR识别失败: {}", e.getMessage(), e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        } finally {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("断开ASR连接失败: {}", e.getMessage());
            }
        }
    }

    private String getApiKey(VoiceAsrConfig config) {
        if (config.getAuthParams() != null) {
            Object apiKey = config.getAuthParams().get("apiKey");
            return apiKey != null ? apiKey.toString() : "";
        }
        return "";
    }
}
