package me.codeleep.victor.web.websocket.protocol.server.asr;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * ASR 流数据消息（服务端→客户端）。
 * <p>协议：{"type":"asr.stream_chunk","text":"..."}</p>
 */
public class AsrServerStreamChunkMessage extends BaseServerMessage {

    private final String text;

    public AsrServerStreamChunkMessage(String text) {
        super("asr.stream_chunk");
        this.text = text;
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType(), "text", text);
    }
}
