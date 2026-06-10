package me.codeleep.victor.web.websocket.protocol.client.interview;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * 面试开始消息（客户端→服务端）。
 * <p>协议：{"type":"interview.start","interviewSessionId":123}</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterviewClientStartMessage extends BaseClientMessage {

    private Long interviewSessionId;

    public InterviewClientStartMessage() {
        super("interview.start");
    }
}
