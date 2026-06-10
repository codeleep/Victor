package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * 面试错误消息（服务端→客户端）。
 * <p>协议：{"type":"interview.error","message":"..."}</p>
 */
public class InterviewServerErrorMessage extends BaseServerMessage {

    private final String message;

    public InterviewServerErrorMessage(String message) {
        super("interview.error");
        this.message = message;
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType(), "message", message);
    }
}
