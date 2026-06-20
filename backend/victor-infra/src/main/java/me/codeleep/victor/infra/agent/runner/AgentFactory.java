package me.codeleep.victor.infra.agent.runner;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.core.LlmDefinition;
import me.codeleep.victor.infra.agent.llm.ModelWrapperFactory;
import me.codeleep.victor.infra.agent.memory.AgentMemoryRepository;
import me.codeleep.victor.infra.agent.memory.RepositoryAgentStateStore;
import org.springframework.stereotype.Component;

/**
 * Agent 工厂 - 将 Definition 配置一次性构建为 AgentScope ReActAgent 实例
 * 由上层（如面试官对象）在初始化时调用，构建后长期持有 agent 实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final ModelWrapperFactory modelWrapperFactory;

    /**
     * 构建单个 Agent
     *
     * @param definition Agent 配置
     * @param sessionId  Agent 会话 ID（用于记忆持久化，可为 null 表示不持久化）
     * @param userId     用户 ID
     * @param repository 记忆持久化仓库（可为 null 表示不持久化）
     * @return ReActAgent 实例
     */
    public ReActAgent buildAgent(AgentDefinition definition, String sessionId, String userId,
                                  AgentMemoryRepository repository) {
        Model model = modelWrapperFactory.create(definition.getLlm());

        Toolkit toolkit = new Toolkit();
        if (definition.isToolEnabled() && definition.getTools() != null) {
            for (Object tool : definition.getTools()) {
                toolkit.registerTool(tool);
            }
        }

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(definition.getName())
                .sysPrompt(definition.getInstructions())
                .model(model)
                .toolkit(toolkit)
                .maxIters(definition.getMaxIters());

        applyStateStore(builder, definition, sessionId, userId, repository);

        return builder.build();
    }

    /**
     * 构建团队 Agent - 主 Agent 内部注册子 Agent 为可调用工具
     *
     * @param team      团队配置
     * @param sessionId Agent 会话 ID
     * @param userId    用户 ID
     * @param repository 记忆持久化仓库（可为 null）
     * @return 主 Agent 的 ReActAgent 实例（子 Agent 已作为工具注册）
     */
    public ReActAgent buildTeam(AgentTeamDefinition team, String sessionId, String userId,
                                 AgentMemoryRepository repository) {
        AgentDefinition mainDef = team.getMainAgent();
        Model mainModel = modelWrapperFactory.create(mainDef.getLlm());

        Toolkit toolkit = new Toolkit();
        if (mainDef.isToolEnabled() && mainDef.getTools() != null) {
            for (Object tool : mainDef.getTools()) {
                toolkit.registerTool(tool);
            }
        }

        RepositoryAgentStateStore stateStore = repository != null
                ? new RepositoryAgentStateStore(repository) : null;

        // 把每个子 Agent 注册为可调用工具
        for (AgentTeamDefinition.SubAgentEntry entry : team.getSubAgents()) {
            ReActAgent subAgent = buildAgent(entry.getAgentDefinition(), sessionId, userId, repository);
            SubAgentConfig config = SubAgentConfig.builder()
                    .toolName(entry.getAgentKey())
                    .description(entry.getAgentName() != null ? entry.getAgentName() : entry.getRole())
                    .forwardEvents(true)
                    .build();
            toolkit.registerAgentTool(new SubAgentTool(providerOf(subAgent), config));
        }

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(mainDef.getName())
                .sysPrompt(mainDef.getInstructions())
                .model(mainModel)
                .toolkit(toolkit)
                .maxIters(mainDef.getMaxIters());

        if (stateStore != null) {
            builder.stateStore(stateStore);
            builder.defaultSessionId(agentSessionId(sessionId, mainDef.getKey()));
        }

        return builder.build();
    }

    private void applyStateStore(ReActAgent.Builder builder, AgentDefinition definition,
                                  String sessionId, String userId, AgentMemoryRepository repository) {
        if (repository == null || sessionId == null) {
            return;
        }
        builder.stateStore(new RepositoryAgentStateStore(repository));
        builder.defaultSessionId(agentSessionId(sessionId, definition.getKey()));
    }

    private String agentSessionId(String sessionId, String agentKey) {
        return sessionId + ":" + agentKey;
    }

    /**
     * SubAgentProvider 每次返回同一个 agent 实例（团队生命周期内复用）
     */
    private SubAgentProvider<ReActAgent> providerOf(ReActAgent agent) {
        return () -> agent;
    }
}
