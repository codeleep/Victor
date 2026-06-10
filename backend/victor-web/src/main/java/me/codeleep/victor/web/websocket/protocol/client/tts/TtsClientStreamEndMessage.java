package me.codeleep.victor.web.websocket.protocol.client.tts;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * TTS 流结束消息（客户端→服务端）。
 * <p>协议：{"type":"tts.stream_end"}</p>
 */
public class TtsClientStreamEndMessage extends BaseClientMessage {

    public TtsClientStreamEndMessage() {
        super("tts.stream_end");
    }
}
