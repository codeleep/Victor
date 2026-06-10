package me.codeleep.victor.core.llm;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.runner.AgentRunnerImpl;

import java.util.List;

/**
 * 测试用 Agent 引擎
 * 不依赖数据库，直接传入 Agent 和 LLM 配置，构建 AgentDefinition 后委托 AgentRunnerImpl 执行
 */
@Slf4j
public class TestAgentEngine {

    private final AgentRunnerImpl runner;

    public TestAgentEngine(me.codeleep.victor.infra.agent.llm.ChatClientFactory chatClientFactory) {
        this.runner = new AgentRunnerImpl(chatClientFactory);
    }

    /**
     * 执行 Agent（直接传入配置）
     */
    public AgentResult execute(Agent agent, AgentLlmConfig llmConfig, AgentContext context) {
        if (llmConfig == null) {
            return AgentResult.error("LLM配置不存在");
        }

        AgentDefinition definition = buildAgentDefinition(agent, llmConfig);
        return runner.run(definition, context);
    }

    /**
     * 从业务实体构建 AgentDefinition
     */
    private AgentDefinition buildAgentDefinition(Agent agent, AgentLlmConfig llmConfig) {
        String apiKey = "";
        if (llmConfig.getAuthParams() != null) {
            apiKey = (String) llmConfig.getAuthParams().getOrDefault("apiKey", "");
        }

        return AgentDefinition.builder()
                .name(agent.getName())
                .instructions(agent.getSystemPrompt())
                .llmProtocol(llmConfig.getProtocol())
                .llmBaseUrl(llmConfig.getApiEndpoint())
                .llmApiKey(apiKey)
                .modelName(llmConfig.getModelName())
                .temperature(llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.7)
                .maxTokens(llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 4096)
                .tools(List.of())
                .build();
    }
}
