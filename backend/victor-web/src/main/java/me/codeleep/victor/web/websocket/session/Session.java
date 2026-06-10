package me.codeleep.victor.web.websocket.session;

import me.codeleep.victor.web.websocket.protocol.ClientMessage;
import me.codeleep.victor.web.websocket.protocol.ServerMessage;

/**
 * WebSocket 会话接口，定义所有会话类型的通用行为。
 */
public interface Session {

    /**
     * 获取会话 ID。
     *
     * @return 会话 ID
     */
    String getSessionId();

    /**
     * 处理客户端命令。
     *
     * @param command 客户端命令
     */
    void handleCommand(ClientMessage command);

    /**
     * 发送服务端消息给客户端。
     *
     * @param message 服务端消息
     */
    void sendMessage(ServerMessage message);

    /**
     * 清理会话资源。
     */
    void cleanup();
}
