package me.codeleep.victor.infra.agent.guardrail;

import me.codeleep.victor.infra.agent.core.AgentContext;

/**
 * Guardrail 接口
 * 用于校验 Agent 的输入和输出
 * 参考 OpenAI Agents SDK 的 Guardrail 抽象
 */
public interface Guardrail {

    /**
     * 获取 Guardrail 名称
     */
    String getName();

    /**
     * 校验内容
     *
     * @param context 执行上下文
     * @param content 待校验的内容（用户输入或 Agent 输出）
     * @return 校验结果
     */
    GuardrailResult validate(AgentContext context, String content);
}
