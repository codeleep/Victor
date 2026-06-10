package me.codeleep.victor.web.websocket.protocol.server.asr;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * ASR 流结束消息（服务端→客户端）。
 * <p>协议：{"type":"asr.stream_end","text":"最终结果"}</p>
 */
public class AsrServerStreamEndMessage extends BaseServerMessage {

    private final String text;

    public AsrServerStreamEndMessage(String text) {
        super("asr.stream_end");
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
