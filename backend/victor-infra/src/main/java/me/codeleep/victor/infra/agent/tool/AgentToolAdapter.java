package me.codeleep.victor.infra.agent.tool;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.runner.AgentRunner;

import java.util.Map;

/**
 * Agent-as-Tool 适配器
 * 将一个 Agent 包装为 AgentTool，调用方 Agent 保持控制权
 * 参考 OpenAI Agents SDK 的 Agent.as_tool()
 *
 * <p>与 Handoff 的区别：
 * <ul>
 *   <li>Handoff: 控制权转移，新 Agent 接管对话</li>
 *   <li>Agent-as-Tool: 调用方保持控制权，子 Agent 执行后返回结果</li>
 * </ul>
 */
@Slf4j
public class AgentToolAdapter implements AgentTool {

    private final AgentDefinition targetAgent;
    private final AgentRunner runner;
    private final AgentToolAdapterConfig config;

    public AgentToolAdapter(AgentDefinition targetAgent, AgentRunner runner, AgentToolAdapterConfig config) {
        this.targetAgent = targetAgent;
        this.runner = runner;
        this.config = config;
    }

    /**
     * 简化构造函数，使用默认配置
     */
    public AgentToolAdapter(AgentDefinition targetAgent, AgentRunner runner, String toolName, String toolDescription) {
        this(targetAgent, runner, AgentToolAdapterConfig.builder()
                .toolName(toolName)
                .toolDescription(toolDescription)
                .build());
    }

    @Override
    public String getName() {
        return config.getToolName();
    }

    @Override
    public String getDescription() {
        return config.getToolDescription();
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return config.getInputSchema();
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return execute(arguments, null);
    }

    @Override
    public Object execute(Map<String, Object> arguments, AgentContext parentContext) {
        log.info("AgentToolAdapter 执行: tool={}, targetAgent={}", getName(), targetAgent.getName());

        // 1. 提取输入消息
        String inputMessage = extractInput(arguments);

        // 2. 创建子上下文，继承父上下文的 sessionId
        AgentContext subContext = new AgentContext();
        if (parentContext != null && parentContext.getSessionId() != null) {
            subContext.setSessionId(parentContext.getSessionId() + ":" + getName());
        }
        subContext.addUserMessage(inputMessage);

        // 3. 执行子 Agent
        AgentResult result;
        try {
            result = runner.run(targetAgent, subContext);
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("AgentToolAdapter 执行失败: targetAgent={}, error={}", targetAgent.getName(), detail, e);
            return "执行失败: " + detail;
        }

        // 4. 提取输出
        if (!result.isSuccess()) {
            String errorMsg = result.getErrorMessage();
            return "执行错误: " + (errorMsg != null ? errorMsg : targetAgent.getName() + " 执行失败");
        }

        String output = config.getOutputExtractor().apply(result);
        log.info("AgentToolAdapter 完成: tool={}, outputLength={}", getName(),
                output != null ? output.length() : 0);
        return output != null ? output : "";
    }

    private String extractInput(Map<String, Object> arguments) {
        // 尝试从 "input" 字段提取
        Object input = arguments.get("input");
        if (input != null) {
            return input.toString();
        }
        // 如果没有 "input" 字段，将所有参数转为字符串
        return arguments.toString();
    }
}
