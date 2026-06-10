package me.codeleep.victor.web.websocket.protocol.client.interview;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * 面试中断消息（客户端→服务端）。
 * <p>协议：{"type":"interview.interrupt","interruptType":"LLM_RESPONSE"}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterviewClientInterruptMessage extends BaseClientMessage {

    private String interruptType;

    public InterviewClientInterruptMessage() {
        super("interview.interrupt");
    }
}
