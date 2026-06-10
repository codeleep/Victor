package me.codeleep.victor.web.websocket.protocol.client.tts;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * TTS 中断消息（客户端→服务端）。
 * <p>协议：{"type":"tts.interrupt"}</p>
 */
public class TtsClientInterruptMessage extends BaseClientMessage {

    public TtsClientInterruptMessage() {
        super("tts.interrupt");
    }
}
