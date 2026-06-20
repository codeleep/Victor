package me.codeleep.victor.infra.agent.runner;

/**
 * 单 Agent 运行器接口
 * 负责驱动已构建 Agent 的 agentic loop，管理工具调用、流式事件
 * Agent 实例由 {@link AgentFactory} 构建，上层持有后传入
 */
public interface AgentRunner extends Runner {
}
