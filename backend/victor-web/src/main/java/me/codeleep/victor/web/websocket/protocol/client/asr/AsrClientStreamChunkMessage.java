package me.codeleep.victor.web.websocket.protocol.client.asr;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * ASR 流数据消息（客户端→服务端）。
 * <p>协议：{"type":"asr.stream_chunk","text":"..."}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AsrClientStreamChunkMessage extends BaseClientMessage {

    private String text;

    public AsrClientStreamChunkMessage() {
        super("asr.stream_chunk");
    }
}
