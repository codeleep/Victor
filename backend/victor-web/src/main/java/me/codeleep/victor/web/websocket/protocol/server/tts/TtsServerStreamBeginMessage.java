package me.codeleep.victor.web.websocket.protocol.server.tts;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * TTS 流开始消息（服务端→客户端）。
 * <p>协议：{"type":"tts.stream_begin"}</p>
 */
public class TtsServerStreamBeginMessage extends BaseServerMessage {

    public TtsServerStreamBeginMessage() {
        super("tts.stream_begin");
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType());
    }
}
