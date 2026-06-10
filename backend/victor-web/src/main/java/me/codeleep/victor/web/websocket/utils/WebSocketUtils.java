package me.codeleep.victor.web.websocket.utils;

import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 工具类。
 */
public final class WebSocketUtils {

    private WebSocketUtils() {
    }

    /**
     * 从 WebSocket URL 中提取指定参数。
     *
     * @param session   WebSocket 会话
     * @param paramName 参数名
     * @return 参数值，不存在则返回 null
     */
    public static String extractQueryParam(WebSocketSession session, String paramName) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && paramName.equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /**
     * 从 WebSocket URL 中提取 token 参数。
     *
     * @param session WebSocket 会话
     * @return token 值，不存在则返回 null
     */
    public static String extractToken(WebSocketSession session) {
        return extractQueryParam(session, "token");
    }
}
