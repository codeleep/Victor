package me.codeleep.victor.web.websocket.protocol;

/**
 * 服务端发送的消息接口。
 *
 * <p>所有服务端消息均通过 {@code type} 字段区分类型，序列化为 JSON 后通过
 * WebSocket 发送给客户端。</p>
 */
public interface ServerMessage {

    /**
     * 获取消息类型标识。
     *
     * @return 消息类型，如 "interview.status"、"asr.stream_chunk"
     */
    String getType();

    /**
     * 将消息序列化为 JSON 字符串。
     *
     * @return JSON 字符串
     */
    String toJson();
}
