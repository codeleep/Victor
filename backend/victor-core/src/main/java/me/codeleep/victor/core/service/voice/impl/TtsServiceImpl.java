package me.codeleep.victor.core.service.voice.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.VoiceServiceProvider;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.service.voice.TtsService;
import me.codeleep.victor.core.voice.VoiceClientFactory;
import me.codeleep.victor.infra.voice.tts.TtsClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsServiceImpl implements TtsService {

    private final ObjectMapper objectMapper;
    private final VoiceClientFactory voiceClientFactory;

    @Override
    public byte[] synthesize(String text, VoiceTtsConfig config) {
        if (config == null || config.getProvider() == null) {
            return synthesizeWithOpenAi(text, config);
        }

        VoiceServiceProvider provider = config.getProvider();
        switch (provider) {
            case DOUBAO:
                return synthesizeWithVolc(text, config);
            case OPENAI:
            default:
                return synthesizeWithOpenAi(text, config);
        }
    }

    private byte[] synthesizeWithOpenAi(String text, VoiceTtsConfig config) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(config.getApiEndpoint())
                    .defaultHeader("Authorization", "Bearer " + getApiKey(config))
                    .build();

            Map<String, Object> body = new HashMap<>();
            body.put("model", "tts-1");
            body.put("input", text);
            body.put("voice", config.getVoiceName() != null ? config.getVoiceName() : "alloy");

            if (config.getExtraParams() != null) {
                Object model = config.getExtraParams().get("model");
                if (model != null) {
                    body.put("model", model.toString());
                }
            }

            byte[] audioData = webClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            return audioData != null ? audioData : new byte[0];
        } catch (Exception e) {
            log.error("OpenAI TTS合成失败: {}", e.getMessage(), e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }

    private byte[] synthesizeWithVolc(String text, VoiceTtsConfig config) {
        TtsClient client = voiceClientFactory.createTtsClient(config);
        try {
            client.connect();
            byte[] audioData = client.synthesize(text);
            return audioData != null ? audioData : new byte[0];
        } catch (Exception e) {
            log.error("火山引擎TTS合成失败: {}", e.getMessage(), e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        } finally {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("断开TTS连接失败: {}", e.getMessage());
            }
        }
    }

    private String getApiKey(VoiceTtsConfig config) {
        if (config.getAuthParams() != null) {
            Object apiKey = config.getAuthParams().get("apiKey");
            return apiKey != null ? apiKey.toString() : "";
        }
        return "";
    }
}
