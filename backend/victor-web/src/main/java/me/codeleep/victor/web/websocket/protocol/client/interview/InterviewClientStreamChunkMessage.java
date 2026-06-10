package me.codeleep.victor.web.websocket.protocol.client.interview;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

import java.util.List;

/**
 * 面试流数据消息（客户端→服务端）。
 * <p>协议：{"type":"interview.stream_chunk","text":"...","attachments":[...]}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterviewClientStreamChunkMessage extends BaseClientMessage {

    private String text;

    /**
     * 输入附件列表。附件随本消息一次性完整发送，不做流式拼接。
     */
    private List<InterviewClientAttachment> attachments;

    public InterviewClientStreamChunkMessage() {
        super("interview.stream_chunk");
    }
}
