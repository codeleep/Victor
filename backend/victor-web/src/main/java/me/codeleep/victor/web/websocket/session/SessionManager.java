package me.codeleep.victor.web.websocket.session;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用会话管理器，管理所有类型的 WebSocket 会话。
 */
@Slf4j
@Component
public class SessionManager {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话。
     *
     * @param session 会话实例
     */
    public void register(Session session) {
        sessions.put(session.getSessionId(), session);
        log.info("Session registered: {}", session.getSessionId());
    }

    /**
     * 获取会话。
     *
     * @param sessionId 会话 ID
     * @return 会话实例，不存在则返回 null
     */
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 处理客户端命令。
     *
     * @param sessionId 会话 ID
     * @param command   客户端命令
     */
    public void handleCommand(String sessionId, ClientMessage command) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.handleCommand(command);
        } else {
            log.warn("Session not found: {}", sessionId);
        }
    }

    /**
     * 移除并清理会话。
     *
     * @param sessionId 会话 ID
     * @return 被移除的会话实例，不存在则返回 null
     */
    public Session removeSession(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session != null) {
            session.cleanup();
            log.info("Session removed: {}", sessionId);
        }
        return session;
    }

    /**
     * 获取当前会话数量。
     *
     * @return 会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }
}
