package me.codeleep.victor.infra.agent.runner;

import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;

/**
 * Agent 团队运行器接口
 * 负责编排主 Agent + 子 Agent 的协作执行
 */
public interface AgentTeamRunner extends Runner<AgentTeamDefinition> {
}
