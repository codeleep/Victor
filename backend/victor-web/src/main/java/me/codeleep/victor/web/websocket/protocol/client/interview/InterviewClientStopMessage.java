package me.codeleep.victor.web.websocket.protocol.client.interview;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * 面试停止消息（客户端→服务端）。
 * <p>协议：{"type":"interview.stop"}</p>
 */
public class InterviewClientStopMessage extends BaseClientMessage {

    public InterviewClientStopMessage() {
        super("interview.stop");
    }
}
