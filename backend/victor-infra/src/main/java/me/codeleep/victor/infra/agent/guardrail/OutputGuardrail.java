package me.codeleep.victor.infra.agent.guardrail;

import me.codeleep.victor.infra.agent.core.AgentContext;

/**
 * 输出 Guardrail
 * 在 Agent 响应后校验输出
 */
public interface OutputGuardrail extends Guardrail {

    /**
     * 校验 Agent 输出
     *
     * @param context 执行上下文
     * @param output Agent 输出
     * @return 校验结果
     */
    @Override
    GuardrailResult validate(AgentContext context, String output);
}
