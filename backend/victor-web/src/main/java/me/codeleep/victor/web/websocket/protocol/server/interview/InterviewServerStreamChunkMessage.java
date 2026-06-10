package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * 面试流数据消息（服务端→客户端）。
 * <p>协议：{"type":"interview.stream_chunk","text":"..."}</p>
 */
public class InterviewServerStreamChunkMessage extends BaseServerMessage {

    private final String text;

    public InterviewServerStreamChunkMessage(String text) {
        super("interview.stream_chunk");
        this.text = text;
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType(), "text", text);
    }
}
