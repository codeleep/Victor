package me.codeleep.victor.infra.agent.guardrail;

import me.codeleep.victor.infra.agent.core.AgentContext;

/**
 * 输入 Guardrail
 * 在 Agent 处理前校验用户输入
 */
public interface InputGuardrail extends Guardrail {

    /**
     * 校验用户输入
     *
     * @param context 执行上下文
     * @param userInput 用户输入
     * @return 校验结果
     */
    @Override
    GuardrailResult validate(AgentContext context, String userInput);
}
