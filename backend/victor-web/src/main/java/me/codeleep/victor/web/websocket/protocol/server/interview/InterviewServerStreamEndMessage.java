package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * 面试流结束消息（服务端→客户端）。
 * <p>协议：{"type":"interview.stream_end","text":"..."}</p>
 */
public class InterviewServerStreamEndMessage extends BaseServerMessage {

    private final String text;

    public InterviewServerStreamEndMessage(String text) {
        super("interview.stream_end");
        this.text = text;
    }

    @Override
    protected Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", getType());
        if (text != null) {
            map.put("text", text);
        }
        return map;
    }
}
