package me.codeleep.victor.core.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentTeamDefinition 工厂
 * 从数据库 AgentTeam 实体构建 AgentTeamDefinition 运行时定义
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTeamDefinitionFactory {

    private final AgentDefinitionFactory agentDefinitionFactory;

    /**
     * 从 AgentTeam 构建 AgentTeamDefinition
     *
     * @param team 数据库团队实体
     * @return 运行时定义，构建失败返回 null
     */
    public AgentTeamDefinition build(AgentTeam team) {
        if (team.getMainAgentId() == null) {
            log.error("团队未配置主Agent: teamId={}", team.getId());
            return null;
        }

        // 1. 构建主 Agent 定义
        AgentDefinition mainDef = agentDefinitionFactory.build(team.getMainAgentId());
        if (mainDef == null) {
            log.error("主 Agent 定义构建失败: mainAgentId={}", team.getMainAgentId());
            return null;
        }

        // 2. 构建子 Agent 定义
        List<AgentTeamDefinition.SubAgentEntry> subAgents = new ArrayList<>();
        if (team.getMembers() != null) {
            for (TeamMemberInfo member : team.getMembers()) {
                // 跳过主 Agent
                if (member.getAgentId() != null && member.getAgentId().equals(team.getMainAgentId())) {
                    continue;
                }

                AgentDefinition subDef = agentDefinitionFactory.build(member.getAgentId());
                if (subDef == null) {
                    log.warn("子 Agent 定义构建失败，跳过: agentId={}", member.getAgentId());
                    continue;
                }

                subAgents.add(AgentTeamDefinition.SubAgentEntry.builder()
                        .agentDefinition(subDef)
                        .role(member.getRole())
                        .agentKey(member.getAgentKey())
                        .agentName(member.getAgentName())
                        .build());
            }
        }

        return AgentTeamDefinition.builder()
                .key(team.getKey())
                .name(team.getName())
                .executionMode(team.getExecutionMode() != null ? team.getExecutionMode().name() : "SEQUENTIAL")
                .mainAgent(mainDef)
                .subAgents(subAgents)
                .build();
    }
}
