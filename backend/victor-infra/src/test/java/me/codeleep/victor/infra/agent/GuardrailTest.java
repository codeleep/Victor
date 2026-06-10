package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.guardrail.GuardrailResult;
import me.codeleep.victor.infra.agent.guardrail.InputGuardrail;
import me.codeleep.victor.infra.agent.guardrail.OutputGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guardrail 单元测试
 */
class GuardrailTest {

    @Test
    @DisplayName("输入 Guardrail - 通过校验")
    void inputGuardrailPass() {
        InputGuardrail guardrail = new InputGuardrail() {
            @Override
            public String getName() {
                return "length-check";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String input) {
                if (input != null && input.length() > 10) {
                    return GuardrailResult.fail("输入过长");
                }
                return GuardrailResult.pass();
            }
        };

        AgentContext context = new AgentContext("s1", 1L);
        GuardrailResult result = guardrail.validate(context, "短输入");
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("输入 Guardrail - 拦截")
    void inputGuardrailFail() {
        InputGuardrail guardrail = new InputGuardrail() {
            @Override
            public String getName() {
                return "length-check";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String input) {
                if (input != null && input.length() > 5) {
                    return GuardrailResult.fail("输入过长");
                }
                return GuardrailResult.pass();
            }
        };

        AgentContext context = new AgentContext("s1", 1L);
        GuardrailResult result = guardrail.validate(context, "这是一个很长的输入内容");
        assertFalse(result.isPassed());
        assertEquals("输入过长", result.getReason());
    }

    @Test
    @DisplayName("输出 Guardrail - 内容过滤")
    void outputGuardrailFilter() {
        OutputGuardrail guardrail = new OutputGuardrail() {
            @Override
            public String getName() {
                return "content-filter";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String output) {
                if (output != null && output.contains("敏感词")) {
                    return GuardrailResult.fail("输出包含敏感内容", GuardrailResult.Severity.ERROR);
                }
                return GuardrailResult.pass();
            }
        };

        AgentContext context = new AgentContext("s1", 1L);

        // 正常输出
        assertTrue(guardrail.validate(context, "正常回复").isPassed());

        // 包含敏感词
        GuardrailResult failed = guardrail.validate(context, "包含敏感词的回复");
        assertFalse(failed.isPassed());
        assertEquals(GuardrailResult.Severity.ERROR, failed.getSeverity());
    }

    @Test
    @DisplayName("GuardrailResult 工厂方法")
    void guardrailResultFactories() {
        GuardrailResult pass = GuardrailResult.pass();
        assertTrue(pass.isPassed());

        GuardrailResult fail = GuardrailResult.fail("reason");
        assertFalse(fail.isPassed());
        assertEquals("reason", fail.getReason());
        assertEquals(GuardrailResult.Severity.ERROR, fail.getSeverity());

        GuardrailResult warn = GuardrailResult.fail("warning", GuardrailResult.Severity.WARNING);
        assertFalse(warn.isPassed());
        assertEquals(GuardrailResult.Severity.WARNING, warn.getSeverity());
    }
}
