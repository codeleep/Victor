package me.codeleep.victor.infra.agent.lifecycle;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 生命周期事件
 * 记录 Agent 执行过程中的生命周期事件
 */
@Data
@Builder
public class LifecycleEvent {

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 事件时间戳
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 事件数据
     */
    private Map<String, Object> data;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        AGENT_START,
        AGENT_END,
        HANDOFF,
        TURN_START,
        TOOL_START,
        TOOL_END,
        LLM_START,
        LLM_END,
        LLM_ERROR,
        GUARDRAIL_CHECK,
        HUMAN_INPUT_REQUIRED
    }
}
