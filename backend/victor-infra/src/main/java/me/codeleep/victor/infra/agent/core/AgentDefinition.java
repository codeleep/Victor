package me.codeleep.victor.infra.agent.core;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * Agent 定义 - 不可变配置
 * 基于 AgentScope ReActAgent 的单 Agent 配置
 * 工具直接持有 AgentScope 原生工具对象（带 @io.agentscope.core.tool.Tool 注解的实例），
 * 由上层自行构造实现，Runner 原样注册到 Toolkit
 */
@Data
@Builder(toBuilder = true)
public class AgentDefinition {

    /**
     * Agent 标识（唯一）
     */
    private String key;

    /**
     * Agent 名称
     */
    private String name;

    /**
     * 系统提示词（instructions）
     */
    private String instructions;

    /**
     * LLM 配置，Runner 根据 protocol 创建对应 AgentScope ModelWrapper
     */
    private LlmDefinition llm;

    /**
     * 可用工具列表 - AgentScope 原生工具对象
     * 元素为带 @io.agentscope.core.tool.Tool 注解的实例，上层自行实现
     */
    @Singular
    private List<Object> tools;

    /**
     * 最大 ReAct 迭代轮数
     */
    @Builder.Default
    private int maxIters = 20;

    /**
     * 是否启用工具调用
     */
    @Builder.Default
    private boolean toolEnabled = true;
}
