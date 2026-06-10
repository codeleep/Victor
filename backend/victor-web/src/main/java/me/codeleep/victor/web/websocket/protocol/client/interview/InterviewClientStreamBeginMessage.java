package me.codeleep.victor.web.websocket.protocol.client.interview;

import me.codeleep.victor.web.websocket.protocol.BaseClientMessage;

/**
 * 面试流开始消息（客户端→服务端）。
 * <p>协议：{"type":"interview.stream_begin"}</p>
 */
public class InterviewClientStreamBeginMessage extends BaseClientMessage {

    public InterviewClientStreamBeginMessage() {
        super("interview.stream_begin");
    }
}
