package me.codeleep.victor.web.websocket.protocol;

/**
 * 未知类型的消息，作为 JSON 反序列化失败时的兜底实现。
 */
public class UnknownMessage extends BaseClientMessage {

    public UnknownMessage() {
        super("unknown");
    }
}
