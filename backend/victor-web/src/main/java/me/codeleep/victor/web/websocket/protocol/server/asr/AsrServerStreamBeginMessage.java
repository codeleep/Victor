package me.codeleep.victor.web.websocket.protocol.server.asr;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * ASR 流开始消息（服务端→客户端）。
 * <p>协议：{"type":"asr.stream_begin"}</p>
 */
public class AsrServerStreamBeginMessage extends BaseServerMessage {

    public AsrServerStreamBeginMessage() {
        super("asr.stream_begin");
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of("type", getType());
    }
}
