package me.codeleep.victor.web.websocket.protocol.client.asr;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * ASR 流结束消息（客户端→服务端）。
 * <p>协议：{"type":"asr.stream_end"}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AsrClientStreamEndMessage extends BaseClientMessage {

    public AsrClientStreamEndMessage() {
        super("asr.stream_end");
    }
}
