package me.codeleep.victor.web.websocket.protocol.server.tts;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * TTS 流结束消息（服务端→客户端）。
 * <p>协议：{"type":"tts.stream_end"}</p>
 */
public class TtsServerStreamEndMessage extends BaseServerMessage {

    public TtsServerStreamEndMessage() {
        super("tts.stream_end");
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType());
    }
}
