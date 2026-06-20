package me.codeleep.victor.core.engine.tools;

/**
 * Agent 工具标记接口
 * <p>
 * 实现该接口并标注 {@link io.agentscope.core.tool.Tool @Tool} 注解的 Spring Bean，
 * 会被 {@code AgentDefinitionFactory} 收集并按工具名注册，供 Agent 调用。
 * <p>
 * 仅注入实现本接口的 Bean，避免注入 {@code List<Object>} 时拉入全部 Bean 造成循环依赖。
 */
public interface AgentTool {
}