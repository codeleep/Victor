package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.Map;

/**
 * 面试重连成功消息（服务端→客户端）。
 * <p>协议：{"type":"interview.reconnected","interviewSessionId":123,"historyTurns":5}</p>
 */
public class InterviewServerReconnectedMessage extends BaseServerMessage {

    private final Long interviewSessionId;
    private final int historyTurns;

    public InterviewServerReconnectedMessage(Long interviewSessionId, int historyTurns) {
        super("interview.reconnected");
        this.interviewSessionId = interviewSessionId;
        this.historyTurns = historyTurns;
    }

    @Override
    protected Map<String, Object> toMap() {
        return Map.of(
                "type", getType(),
                "interviewSessionId", interviewSessionId,
                "historyTurns", historyTurns
        );
    }
}
