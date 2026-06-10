package me.codeleep.victor.infra.agent.handoff;

import me.codeleep.victor.infra.agent.tool.AgentTool;

import java.util.Map;

/**
 * Handoff 工具
 * 将 Handoff 包装为 AgentTool，LLM 通过 tool_call 触发 Agent 切换
 */
public class HandoffTool implements AgentTool {

    private final Handoff handoff;

    public HandoffTool(Handoff handoff) {
        this.handoff = handoff;
    }

    @Override
    public String getName() {
        return handoff.getToolName();
    }

    @Override
    public String getDescription() {
        return handoff.getDescription() != null
                ? handoff.getDescription()
                : "将对话转移给 " + handoff.getTargetAgent().getName();
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "reason", Map.of(
                                "type", "string",
                                "description", "转移原因"
                        )
                )
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // Handoff 的执行结果是返回目标 Agent 名称
        // Runner 会检测到这个结果并切换 Agent
        String reason = arguments.get("reason") != null
                ? arguments.get("reason").toString()
                : "Agent 请求转移";
        return Map.of(
                "status", "handoff",
                "target", handoff.getTargetAgent().getName(),
                "reason", reason
        );
    }

    /**
     * 获取关联的 Handoff
     */
    public Handoff getHandoff() {
        return handoff;
    }
}
