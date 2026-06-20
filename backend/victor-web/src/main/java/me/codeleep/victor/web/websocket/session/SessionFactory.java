package me.codeleep.victor.web.websocket.session;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.service.VoiceAsrConfigService;
import me.codeleep.victor.core.service.VoiceTtsConfigService;
import me.codeleep.victor.infra.voice.asr.AsrClient;
import me.codeleep.victor.infra.voice.tts.TtsClient;
import me.codeleep.victor.core.voice.VoiceClientFactory;
import me.codeleep.victor.core.interviewer.InterviewContextRestorer;
import me.codeleep.victor.web.websocket.processor.TextProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 会话工厂，负责创建各种类型的会话实例。
 */
@Slf4j
@Component
public class SessionFactory {

    private final TextProcessor textProcessor;
    private final InterviewContextRestorer contextRestorer;
    private final VoiceClientFactory voiceClientFactory;
    private final VoiceAsrConfigService asrConfigService;
    private final VoiceTtsConfigService ttsConfigService;

    public SessionFactory(TextProcessor textProcessor,
                          InterviewContextRestorer contextRestorer,
                          VoiceClientFactory voiceClientFactory,
                          VoiceAsrConfigService asrConfigService,
                          VoiceTtsConfigService ttsConfigService) {
        this.textProcessor = textProcessor;
        this.contextRestorer = contextRestorer;
        this.voiceClientFactory = voiceClientFactory;
        this.asrConfigService = asrConfigService;
        this.ttsConfigService = ttsConfigService;
    }

    /**
     * 创建面试会话。
     *
     * @param wsSession WebSocket 会话
     * @return 面试会话实例
     */
    public InterviewSession createInterviewSession(WebSocketSession wsSession) {
        return new InterviewSession(wsSession, textProcessor, contextRestorer);
    }

    /**
     * 创建 ASR 代理会话。
     *
     * <p>根据用户默认配置创建对应的 ASR 客户端。</p>
     *
     * @param wsSession WebSocket 会话
     * @param userId    用户ID，用于查询语音配置
     * @return ASR 代理会话实例
     * @throws Exception 连接 ASR 服务失败时抛出
     */
    public AsrProxySession createAsrSession(WebSocketSession wsSession, Long userId) throws Exception {
        VoiceAsrConfig config = asrConfigService.getDefaultByUserId(userId);
        if (config == null) {
            throw new IllegalStateException("用户未配置默认ASR服务，请先在语音设置中配置");
        }

        AsrClient client = voiceClientFactory.createAsrClient(config);
        client.connect();
        return new AsrProxySession(wsSession, client);
    }

    /**
     * 创建 TTS 代理会话。
     *
     * <p>根据用户默认配置创建对应的 TTS 客户端。</p>
     *
     * @param wsSession WebSocket 会话
     * @param userId    用户ID，用于查询语音配置
     * @return TTS 代理会话实例
     * @throws Exception 连接 TTS 服务失败时抛出
     */
    public TtsProxySession createTtsSession(WebSocketSession wsSession, Long userId) throws Exception {
        VoiceTtsConfig config = ttsConfigService.getDefaultByUserId(userId);
        if (config == null) {
            throw new IllegalStateException("用户未配置默认TTS服务，请先在语音设置中配置");
        }

        TtsClient client = voiceClientFactory.createTtsClient(config);
        client.connect();
        return new TtsProxySession(wsSession, client);
    }
}
