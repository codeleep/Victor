package me.codeleep.victor.web.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.web.config.JwtUtils;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.session.SessionFactory;
import me.codeleep.victor.web.websocket.session.SessionManager;
import me.codeleep.victor.web.websocket.utils.WebSocketUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * TTS WebSocket 代理处理器。
 *
 * <p>负责连接认证、会话创建，将消息委托给 SessionManager 处理。</p>
 *
 * <h3>协议：</h3>
 * <ul>
 *   <li>客户端 → 服务端：{"type":"tts.stream_begin"} / {"type":"tts.stream_chunk","text":"..."} / {"type":"tts.stream_end"} / {"type":"tts.interrupt"}</li>
 *   <li>服务端 → 客户端：{"type":"tts.stream_begin"} / binary / {"type":"tts.stream_end"}</li>
 * </ul>
 */
@Slf4j
@Component
public class TtsProxyHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final SessionFactory sessionFactory;
    private final JwtUtils jwtUtils;

    public TtsProxyHandler(SessionManager sessionManager, SessionFactory sessionFactory, JwtUtils jwtUtils) {
        this.sessionManager = sessionManager;
        this.sessionFactory = sessionFactory;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 连接建立时，验证 token 并创建 TTS 会话。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = WebSocketUtils.extractToken(session);
        if (token == null || !validateToken(token)) {
            session.close(new CloseStatus(4001, "Unauthorized"));
            return;
        }

        Long userId = jwtUtils.extractUserId(token);
        var proxySession = sessionFactory.createTtsSession(session, userId);
        sessionManager.register(proxySession);
        log.info("[TTS Proxy] Connected: {}", session.getId());
    }

    /**
     * 处理文本消息，解析命令并委托给管理器处理。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientMessage command = ClientMessage.parse(message.getPayload());
        sessionManager.handleCommand(session.getId(), command);
    }

    /**
     * 连接关闭时，从管理器移除会话。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) {
        sessionManager.removeSession(session.getId());
        log.info("[TTS Proxy] Disconnected: {}", session.getId());
    }

    /**
     * 传输错误处理。
     */
    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        log.error("[TTS Proxy] Transport error: {}", session.getId(), exception);
    }

    /**
     * 验证 JWT token 有效性。
     *
     * @param token JWT token
     * @return 是否有效
     */
    private boolean validateToken(String token) {
        try {
            String username = jwtUtils.extractUsername(token);
            return jwtUtils.validateToken(token, username);
        } catch (Exception e) {
            log.warn("[TTS Proxy] Invalid token: {}", e.getMessage());
            return false;
        }
    }
}
