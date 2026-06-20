package me.codeleep.victor.infra.agent.core;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 执行结果
 * 同步执行返回最终结果；流式执行每一项也是一个 AgentResult，通过 type 区分事件类型。
 * 作为上层与 AgentScope 之间的中性契约，上层无需感知 AgentScope 类型。
 */
@Data
public class AgentResult {

    /**
     * 事件类型（流式时前端据此区分输出内容）
     */
    private EventType type;

    /**
     * 响应内容（思考文本/工具调用描述/工具结果文本/最终回答增量）
     */
    private String content;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 事件来源 Agent key（团队编排时区分主/子 Agent）
     */
    private String sourceAgentKey;

    /**
     * 是否为流的最后一项
     */
    private boolean last;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    public AgentResult() {
        this.success = true;
        this.last = false;
        this.metadata = new HashMap<>();
    }

    public static AgentResult answer(String content) {
        AgentResult r = new AgentResult();
        r.setType(EventType.ANSWER);
        r.setContent(content);
        return r;
    }

    public static AgentResult error(String errorMessage) {
        AgentResult r = new AgentResult();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        r.setType(EventType.ERROR);
        return r;
    }

    /**
     * 流式事件类型
     * 对应 AgentScope EventType 的映射，前端据此渲染不同内容
     */
    public enum EventType {
        /** 思考/推理过程 */
        THINKING,
        /** 发起工具调用 */
        TOOL_CALL,
        /** 工具返回结果 */
        TOOL_RESULT,
        /** 最终回答（文本增量或完整） */
        ANSWER,
        /** 切换到子 Agent */
        HANDOFF,
        /** 错误 */
        ERROR,
        /** 流结束 */
        DONE
    }
}
