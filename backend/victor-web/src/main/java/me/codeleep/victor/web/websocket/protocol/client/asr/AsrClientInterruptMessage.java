package me.codeleep.victor.web.websocket.protocol.client.asr;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * ASR 中断消息（客户端→服务端）。
 * <p>协议：{"type":"asr.interrupt"}</p>
 */
public class AsrClientInterruptMessage extends BaseClientMessage {

    public AsrClientInterruptMessage() {
        super("asr.interrupt");
    }
}
