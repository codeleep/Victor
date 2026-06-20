package me.codeleep.victor.core.engine;

import io.agentscope.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.LlmDefinition;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentDefinition 工厂
 * 从数据库 Agent 实体构建 AgentDefinition（配置对象，由 AgentFactory 转为 ReActAgent 实例）
 */
@Slf4j
@Component
public class AgentDefinitionFactory {

    private final AgentMapper agentMapper;
    private final AgentLlmConfigMapper agentLlmConfigMapper;
    /** 工具名(@Tool name) -> 工具实例 */
    private final Map<String, Object> registeredTools = new ConcurrentHashMap<>();

    public AgentDefinitionFactory(AgentMapper agentMapper, AgentLlmConfigMapper agentLlmConfigMapper,
                                   List<Object> tools) {
        this.agentMapper = agentMapper;
        this.agentLlmConfigMapper = agentLlmConfigMapper;
        if (tools != null) {
            tools.forEach(this::registerTool);
        }
    }

    /**
     * 注册工具（读取 @io.agentscope.core.tool.Tool 注解的 name 作为键）
     */
    public void registerTool(Object tool) {
        for (java.lang.reflect.Method method : tool.getClass().getMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation != null) {
                registeredTools.put(annotation.name(), tool);
                log.info("注册工具: {}", annotation.name());
                return;
            }
        }
        log.warn("工具未找到 @Tool 注解，跳过: {}", tool.getClass().getSimpleName());
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

        LlmDefinition llm = buildLlmDefinition(llmConfig);
        List<Object> tools = resolveTools(agent.getAvailableTools());

        return AgentDefinition.builder()
                .key(agent.getKey())
                .name(agent.getName())
                .instructions(agent.getSystemPrompt())
                .llm(llm)
                .tools(tools)
                .build();
    }

    public AgentDefinition buildWithPrompt(Agent agent, String systemPrompt) {
        AgentDefinition base = build(agent);
        if (base == null) {
            return null;
        }
        return base.toBuilder()
                .instructions(systemPrompt)
                .build();
    }

    private LlmDefinition buildLlmDefinition(AgentLlmConfig llmConfig) {
        String apiKey = "";
        if (llmConfig.getAuthParams() != null) {
            apiKey = (String) llmConfig.getAuthParams().getOrDefault("apiKey", "");
        }
        LlmProtocol protocol = llmConfig.getProtocol() != null ? llmConfig.getProtocol() : LlmProtocol.DOUBAO;

        return LlmDefinition.builder()
                .protocol(protocol)
                .baseUrl(llmConfig.getApiEndpoint())
                .apiKey(apiKey)
                .modelName(llmConfig.getModelName())
                .temperature(llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.7)
                .maxTokens(llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 4096)
                .build();
    }

    private List<Object> resolveTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (String name : toolNames) {
            Object tool = registeredTools.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }
}
