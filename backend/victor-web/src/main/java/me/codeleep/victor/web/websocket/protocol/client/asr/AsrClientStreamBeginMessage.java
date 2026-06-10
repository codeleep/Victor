package me.codeleep.victor.web.websocket.protocol.client.asr;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * ASR 流开始消息（客户端→服务端）。
 * <p>协议：{"type":"asr.stream_begin"}</p>
 */
public class AsrClientStreamBeginMessage extends BaseClientMessage {

    public AsrClientStreamBeginMessage() {
        super("asr.stream_begin");
    }
}
