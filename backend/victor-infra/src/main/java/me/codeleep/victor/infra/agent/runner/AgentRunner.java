package me.codeleep.victor.infra.agent.runner;

import me.codeleep.victor.infra.agent.core.AgentDefinition;

/**
 * 单 Agent 运行器接口
 * 负责驱动单个 Agent 的 agentic loop，管理 tool calling、handoff、guardrail
 */
public interface AgentRunner extends Runner<AgentDefinition> {
}
