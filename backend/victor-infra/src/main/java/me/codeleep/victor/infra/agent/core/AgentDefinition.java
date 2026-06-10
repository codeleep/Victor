package me.codeleep.victor.infra.agent.core;

import lombok.Builder;
import lombok.Data;
import me.codeleep.victor.infra.agent.guardrail.Guardrail;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.lifecycle.AgentLifecycleListener;
import me.codeleep.victor.infra.agent.tool.AgentTool;

import java.util.List;

/**
 * Agent 定义 - 不可变配置
 * 参考 OpenAI Agents SDK 的 Agent 抽象
 */
@Data
@Builder(toBuilder = true)
public class AgentDefinition {

    /**
     * Agent 名称
     */
    private String name;

    /**
     * 系统提示词（instructions）
     */
    private String instructions;

    /**
     * LLM 协议类型
     */
    private LlmProtocol llmProtocol;

    /**
     * LLM API 地址
     */
    private String llmBaseUrl;

    /**
     * LLM API Key
     */
    private String llmApiKey;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 温度参数
     */
    @Builder.Default
    private double temperature = 0.7;

    /**
     * 最大 Token
     */
    @Builder.Default
    private int maxTokens = 4096;

    /**
     * 可用工具列表
     */
    @Builder.Default
    private List<AgentTool> tools = List.of();

    /**
     * Handoff 列表（可转移到的其他 Agent）
     */
    @Builder.Default
    private List<Handoff> handoffs = List.of();

    /**
     * 输入 Guardrail 列表
     */
    @Builder.Default
    private List<Guardrail> inputGuardrails = List.of();

    /**
     * 输出 Guardrail 列表
     */
    @Builder.Default
    private List<Guardrail> outputGuardrails = List.of();

    /**
     * 生命周期监听器（Agent 级）
     */
    private AgentLifecycleListener lifecycleListener;

    /**
     * 是否启用工具调用
     */
    @Builder.Default
    private boolean toolEnabled = true;
}
