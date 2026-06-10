package me.codeleep.victor.web.websocket.protocol.client.interview;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * 面试流结束消息（客户端→服务端）。
 * <p>协议：{"type":"interview.stream_end"}</p>
 */
public class InterviewClientStreamEndMessage extends BaseClientMessage {

    public InterviewClientStreamEndMessage() {
        super("interview.stream_end");
    }
}
