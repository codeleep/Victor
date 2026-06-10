package me.codeleep.victor.web.websocket.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 客户端发送的消息接口。
 *
 * <p>所有客户端消息均通过 {@code type} 字段区分类型，由 Jackson 多态反序列化
 * 自动映射到对应的子类。解析失败时返回 {@link UnknownMessage}。</p>
 */
public interface ClientMessage {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取消息类型标识。
     *
     * @return 消息类型，如 "interview.start"、"asr.stream_begin"
     */
    String getType();

    /**
     * 将 JSON 字符串解析为具体的客户端消息对象。
     *
     * @param json JSON 字符串
     * @return 解析后的消息对象；解析失败时返回 {@link UnknownMessage}
     */
    static ClientMessage parse(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, BaseClientMessage.class);
        } catch (Exception e) {
            return new UnknownMessage();
        }
    }
}
