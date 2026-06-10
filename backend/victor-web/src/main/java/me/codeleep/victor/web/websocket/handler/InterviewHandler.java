package me.codeleep.victor.web.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStatusMessage;
import me.codeleep.victor.web.websocket.session.InterviewSession;
import me.codeleep.victor.web.websocket.session.SessionFactory;
import me.codeleep.victor.web.websocket.session.SessionManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 面试 WebSocket 处理器。
 *
 * <p>负责连接生命周期管理，将消息委托给 SessionManager 处理。</p>
 */
@Slf4j
@Component
public class InterviewHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final SessionFactory sessionFactory;

    public InterviewHandler(SessionManager sessionManager, SessionFactory sessionFactory) {
        this.sessionManager = sessionManager;
        this.sessionFactory = sessionFactory;
    }

    /**
     * 连接建立时，创建面试会话并注册到管理器。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        InterviewSession interviewSession = sessionFactory.createInterviewSession(session);
        sessionManager.register(interviewSession);
        interviewSession.sendMessage(new InterviewServerStatusMessage("connected"));
    }

    /**
     * 处理文本消息，解析命令并委托给管理器处理。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("Received text message: {}", payload);

        ClientMessage cmd = ClientMessage.parse(payload);
        sessionManager.handleCommand(session.getId(), cmd);
    }

    /**
     * 连接关闭时，从管理器移除会话。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) {
        log.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        sessionManager.removeSession(session.getId());
    }

    /**
     * 传输错误处理。
     */
    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        log.error("WebSocket transport error: {}", session.getId(), exception);
    }
}
