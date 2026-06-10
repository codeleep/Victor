package me.codeleep.victor.infra.agent.tool;

import me.codeleep.victor.infra.agent.core.AgentContext;

import java.util.Map;

/**
 * Agent 工具接口
 * 参考 OpenAI Agents SDK 的 Tool 抽象
 */
public interface AgentTool {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 获取参数 Schema（JSON Schema 格式）
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具
     *
     * @param arguments 工具参数
     * @return 执行结果
     */
    Object execute(Map<String, Object> arguments);

    /**
     * 执行工具（带上下文）
     * 子类可覆写此方法以访问执行上下文（如 sessionId）
     *
     * @param arguments 工具参数
     * @param context   执行上下文
     * @return 执行结果
     */
    default Object execute(Map<String, Object> arguments, AgentContext context) {
        return execute(arguments);
    }

    /**
     * 转换为 OpenAI Function Calling 格式
     */
    default Map<String, Object> toFunctionDefinition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", getName(),
                        "description", getDescription(),
                        "parameters", getParametersSchema()
                )
        );
    }
}
