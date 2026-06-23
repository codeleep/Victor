package me.codeleep.victor.infra.agent.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * 事件来源 Agent 名称（子 Agent 的展示名，主 Agent 时为 null）
     */
    private String sourceAgentName;

    /**
     * 事件来源 Agent 深度（主 Agent depth=0，子 Agent depth>=1），用于前端嵌套渲染
     */
    private Integer agentDepth;

    /**
     * 是否为流的最后一项
     */
    private boolean last;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 结构化工具事件（type=TOOL_CALL/TOOL_RESULT 时填充），供前端卡片化展示
     */
    private List<ToolEvent> toolEvents;

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

    /**
     * 结构化工具事件，供前端以卡片形式展示工具调用（名称+参数）与结果。
     * 一个 AgentResult 可携带多个 ToolEvent（一条消息含多个工具块时）。
     */
    @Data
    public static class ToolEvent {
        /** 工具名，如 advance_to_next_question / resource_query */
        private String name;
        /** 工具入参（已解析为 Map） */
        private Map<String, Object> args;
        /** 工具结果文本（TOOL_RESULT 时填充） */
        private String result;
        /** 是否为调用结果（true=结果，false=调用） */
        private boolean resultEvent;
        /** 工具调用唯一 ID（来自 ToolUseBlock/ToolResultBlock.getId()），用于流式去重 */
        private String toolCallId;
    }
}
