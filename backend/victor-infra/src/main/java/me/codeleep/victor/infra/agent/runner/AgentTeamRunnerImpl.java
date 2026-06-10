package me.codeleep.victor.infra.agent.runner;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.infra.agent.tool.AgentToolAdapter;
import me.codeleep.victor.infra.agent.tool.AgentToolAdapterConfig;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 团队默认运行器
 * <p>
 * 执行逻辑：
 * 1. 使用 AgentTeamDefinition.mainAgent 作为主 Agent
 * 2. 将 subAgents 包装为 AgentToolAdapter（Agent-as-Tool 模式）
 * 3. 主 Agent 携带子 Agent 工具运行，由主 Agent 自主决定何时调用子 Agent
 */
@Slf4j
@Component
public class AgentTeamRunnerImpl implements AgentTeamRunner {

    private final AgentRunner agentRunner;

    public AgentTeamRunnerImpl(AgentRunner agentRunner) {
        this.agentRunner = agentRunner;
    }

    @Override
    public AgentResult run(AgentTeamDefinition team, AgentContext context) {
        log.info("AgentTeamRunner 开始执行: teamName={}", team.getName());

        AgentDefinition mainAgentDef = buildMainAgentWithTools(team);
        log.info("AgentTeamRunner 主 Agent: {}, 子 Agent 工具数: {}", mainAgentDef.getName(), mainAgentDef.getTools().size());

        return agentRunner.run(mainAgentDef, context);
    }

    @Override
    public Flux<AgentResult> streamRun(AgentTeamDefinition team, AgentContext context) {
        log.info("AgentTeamRunner 流式执行: teamName={}", team.getName());

        AgentDefinition mainAgentDef = buildMainAgentWithTools(team);
        return agentRunner.streamRun(mainAgentDef, context);
    }

    /**
     * 构建主 Agent 定义，并将子 Agent 包装为工具附加到主 Agent
     */
    private AgentDefinition buildMainAgentWithTools(AgentTeamDefinition team) {
        AgentDefinition mainDef = team.getMainAgent();

        List<AgentTool> subAgentTools = buildSubAgentTools(team);
        if (!subAgentTools.isEmpty()) {
            List<AgentTool> allTools = new ArrayList<>(mainDef.getTools());
            allTools.addAll(subAgentTools);
            return mainDef.toBuilder().tools(allTools).build();
        }

        return mainDef;
    }

    /**
     * 将子 Agent 包装为 AgentToolAdapter
     */
    private List<AgentTool> buildSubAgentTools(AgentTeamDefinition team) {
        List<AgentTool> tools = new ArrayList<>();
        if (team.getSubAgents() == null || team.getSubAgents().isEmpty()) {
            return tools;
        }

        for (AgentTeamDefinition.SubAgentEntry entry : team.getSubAgents()) {
            String toolName = "call_" + (entry.getAgentKey() != null ? entry.getAgentKey() : entry.getAgentDefinition().getName());
            String toolDescription = buildToolDescription(entry);

            AgentToolAdapterConfig config = AgentToolAdapterConfig.builder()
                    .toolName(toolName)
                    .toolDescription(toolDescription)
                    .build();

            tools.add(new AgentToolAdapter(entry.getAgentDefinition(), agentRunner, config));
            log.info("注册子 Agent 工具: toolName={}, agentName={}", toolName, entry.getAgentDefinition().getName());
        }

        return tools;
    }

    private String buildToolDescription(AgentTeamDefinition.SubAgentEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("调用 ").append(entry.getAgentName());
        if (entry.getRole() != null && !entry.getRole().isEmpty()) {
            sb.append("（").append(entry.getRole()).append("）");
        }
        sb.append(" 处理任务");
        return sb.toString();
    }
}
