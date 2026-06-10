package me.codeleep.victor.infra.agent.handoff;

import lombok.Builder;
import lombok.Data;
import me.codeleep.victor.infra.agent.core.AgentDefinition;

/**
 * Handoff 定义
 * 描述从当前 Agent 到目标 Agent 的转移条件
 * 参考 OpenAI Agents SDK 的 Handoff 抽象
 */
@Data
@Builder
public class Handoff {

    /**
     * 目标 Agent 定义
     */
    private AgentDefinition targetAgent;

    /**
     * Handoff 描述（LLM 用于判断何时触发）
     */
    private String description;

    /**
     * 输入过滤器（控制下一个 Agent 能看到什么对话历史）
     */
    private InputFilter inputFilter;

    /**
     * 获取 Handoff 工具名称
     */
    public String getToolName() {
        return "handoff_to_" + targetAgent.getName().toLowerCase().replace(" ", "_");
    }
}
