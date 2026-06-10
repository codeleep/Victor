package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * 面试流开始消息（服务端→客户端）。
 * <p>协议：{"type":"interview.stream_begin"}</p>
 */
public class InterviewServerStreamBeginMessage extends BaseServerMessage {

    public InterviewServerStreamBeginMessage() {
        super("interview.stream_begin");
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType());
    }
}
