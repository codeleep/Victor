package me.codeleep.victor.web.websocket.protocol.client.tts;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * TTS 取消消息（客户端→服务端）。
 * <p>协议：{"type":"tts.cancel"}</p>
 */
public class TtsClientCancelMessage extends BaseClientMessage {

    public TtsClientCancelMessage() {
        super("tts.cancel");
    }
}
