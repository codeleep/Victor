package me.codeleep.victor.web.websocket;

import me.codeleep.victor.web.websocket.handler.AsrProxyHandler;
import me.codeleep.victor.web.websocket.handler.InterviewHandler;
import me.codeleep.victor.web.websocket.handler.TtsProxyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 原生 WebSocket 配置。
 *
 * <p>注册三个 WebSocket 端点：</p>
 * <ul>
 *   <li>{@code /ws/interview} - 面试会话端点</li>
 *   <li>{@code /ws/asr} - ASR 语音识别代理端点</li>
 *   <li>{@code /ws/tts} - TTS 语音合成代理端点</li>
 * </ul>
 *
 * <p>同时配置 WebSocket 容器参数：消息缓冲区 50MB、空闲超时 30 分钟，
 * 以支持长时间的音频流传输场景。</p>
 */
@Configuration
@EnableWebSocket
@Profile("!test")
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final InterviewHandler interviewHandler;
    private final AsrProxyHandler asrProxyHandler;
    private final TtsProxyHandler ttsProxyHandler;

    public RawWebSocketConfig(InterviewHandler interviewHandler,
                           AsrProxyHandler asrProxyHandler,
                           TtsProxyHandler ttsProxyHandler) {
        this.interviewHandler = interviewHandler;
        this.asrProxyHandler = asrProxyHandler;
        this.ttsProxyHandler = ttsProxyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewHandler, "/ws/interview")
                .setAllowedOrigins("*");
        registry.addHandler(asrProxyHandler, "/ws/asr")
                .setAllowedOrigins("*");
        registry.addHandler(ttsProxyHandler, "/ws/tts")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 20分钟音频: 16kHz * 2字节 * 1声道 * 1200秒 = 38.4MB
        // 设置为50MB以留有余量
        int bufferSize = 50 * 1024 * 1024;
        container.setMaxTextMessageBufferSize(bufferSize);
        container.setMaxBinaryMessageBufferSize(bufferSize);
        // 增加空闲超时到30分钟
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }
}
