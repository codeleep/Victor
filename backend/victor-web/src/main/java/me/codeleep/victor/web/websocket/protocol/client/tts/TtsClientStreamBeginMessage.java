package me.codeleep.victor.web.websocket.protocol.client.tts;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * TTS 流开始消息（客户端→服务端）。
 * <p>协议：{"type":"tts.stream_begin"}</p>
 */
public class TtsClientStreamBeginMessage extends BaseClientMessage {

    public TtsClientStreamBeginMessage() {
        super("tts.stream_begin");
    }
}
