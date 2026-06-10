package me.codeleep.victor.web.websocket.protocol.client.tts;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * TTS 流数据消息（客户端→服务端）。
 * <p>协议：{"type":"tts.stream_chunk","text":"..."}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TtsClientStreamChunkMessage extends BaseClientMessage {

    private String text;

    public TtsClientStreamChunkMessage() {
        super("tts.stream_chunk");
    }
}
