package me.codeleep.victor.core.engine;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentDefinition 工厂
 * 从数据库 Agent 实体构建 AgentDefinition
 */
@Slf4j
@Component
public class AgentDefinitionFactory {

    private final AgentMapper agentMapper;
    private final AgentLlmConfigMapper agentLlmConfigMapper;
    private final Map<String, AgentTool> registeredTools = new ConcurrentHashMap<>();

    public AgentDefinitionFactory(AgentMapper agentMapper, AgentLlmConfigMapper agentLlmConfigMapper,
                                   List<AgentTool> tools) {
        this.agentMapper = agentMapper;
        this.agentLlmConfigMapper = agentLlmConfigMapper;
        if (tools != null) {
            tools.forEach(this::registerTool);
        }
    }

    public void registerTool(AgentTool tool) {
        registeredTools.put(tool.getName(), tool);
        log.info("注册工具: {}", tool.getName());
    }

    public AgentDefinition build(Long agentId) {
        Agent agent = agentMapper.selectById(agentId);
        return agent != null ? build(agent) : null;
    }

    public AgentDefinition build(Agent agent) {
        AgentLlmConfig llmConfig = agentLlmConfigMapper.selectById(agent.getLlmConfigId());
        if (llmConfig == null) {
            log.warn("Agent LLM 配置不存在: agentId={}, llmConfigId={}", agent.getId(), agent.getLlmConfigId());
            return null;
        }

        String apiKey = "";
        if (llmConfig.getAuthParams() != null) {
            apiKey = (String) llmConfig.getAuthParams().getOrDefault("apiKey", "");
        }

        List<AgentTool> tools = resolveTools(agent.getAvailableTools());

        return AgentDefinition.builder()
                .name(agent.getName())
                .instructions(agent.getSystemPrompt())
                .llmProtocol(llmConfig.getProtocol())
                .llmBaseUrl(llmConfig.getApiEndpoint())
                .llmApiKey(apiKey)
                .modelName(llmConfig.getModelName())
                .temperature(llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.7)
                .maxTokens(llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 4096)
                .tools(tools)
                .build();
    }

    public AgentDefinition buildWithPrompt(Agent agent, String systemPrompt) {
        AgentDefinition base = build(agent);
        if (base == null) {
            return null;
        }
        return AgentDefinition.builder()
                .name(base.getName())
                .instructions(systemPrompt)
                .llmProtocol(base.getLlmProtocol())
                .llmBaseUrl(base.getLlmBaseUrl())
                .llmApiKey(base.getLlmApiKey())
                .modelName(base.getModelName())
                .temperature(base.getTemperature())
                .maxTokens(base.getMaxTokens())
                .tools(base.getTools())
                .build();
    }

    private List<AgentTool> resolveTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
                .map(registeredTools::get)
                .filter(tool -> tool != null)
                .toList();
    }
}
