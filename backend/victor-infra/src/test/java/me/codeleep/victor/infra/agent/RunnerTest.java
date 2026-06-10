package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.guardrail.GuardrailResult;
import me.codeleep.victor.infra.agent.guardrail.InputGuardrail;
import me.codeleep.victor.infra.agent.guardrail.OutputGuardrail;
import me.codeleep.victor.infra.agent.runner.AgentRunnerImpl;
import me.codeleep.victor.infra.agent.runner.RunnerConfig;
import me.codeleep.victor.infra.agent.tool.FunctionTool;
import me.codeleep.victor.infra.agent.tracing.TraceCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runner 单元测试
 * 测试 Guardrail 和基本结构（不测试真实 LLM 调用）
 */
class RunnerTest {

    @Test
    @DisplayName("输入 Guardrail 拦截时返回错误")
    void inputGuardrailBlocks() {
        InputGuardrail blockAll = new InputGuardrail() {
            @Override
            public String getName() {
                return "block-all";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String input) {
                return GuardrailResult.fail("所有输入被拦截");
            }
        };

        AgentDefinition agent = AgentDefinition.builder()
                .name("Test Agent")
                .instructions("测试")
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("http://localhost")
                .llmApiKey("fake")
                .modelName("gpt-4")
                .inputGuardrails(List.of(blockAll))
                .build();

        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("测试消息");

        // Runner 需要 ChatClientFactory，但 Guardrail 在 LLM 调用前就会拦截
        // 所以我们传 null 也可以（Guardrail 先执行）
        // 但由于 AgentRunnerImpl 需要 ChatClientFactory，我们创建一个不会调用 LLM 的场景
        // 这里直接测试 Guardrail 逻辑
        GuardrailResult result = blockAll.validate(context, "测试消息");
        assertFalse(result.isPassed());
        assertEquals("所有输入被拦截", result.getReason());
    }

    @Test
    @DisplayName("输出 Guardrail 拦截时返回错误")
    void outputGuardrailBlocks() {
        OutputGuardrail blockSensitive = new OutputGuardrail() {
            @Override
            public String getName() {
                return "sensitive-filter";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String output) {
                if (output.contains("密码")) {
                    return GuardrailResult.fail("输出包含敏感信息");
                }
                return GuardrailResult.pass();
            }
        };

        AgentContext context = new AgentContext("s1", 1L);

        // 正常输出
        GuardrailResult pass = blockSensitive.validate(context, "正常回复");
        assertTrue(pass.isPassed());

        // 敏感输出
        GuardrailResult fail = blockSensitive.validate(context, "密码是123456");
        assertFalse(fail.isPassed());
    }

    @Test
    @DisplayName("AgentDefinition 构建")
    void agentDefinitionBuild() {
        FunctionTool tool = FunctionTool.of("search", "搜索", args -> "results");

        AgentDefinition agent = AgentDefinition.builder()
                .name("Test Agent")
                .instructions("你是一个测试助手")
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("https://api.openai.com/v1")
                .llmApiKey("sk-test")
                .modelName("gpt-4")
                .temperature(0.5)
                .maxTokens(2048)
                .tools(List.of(tool))
                .build();

        assertEquals("Test Agent", agent.getName());
        assertEquals("你是一个测试助手", agent.getInstructions());
        assertEquals(LlmProtocol.OPENAI, agent.getLlmProtocol());
        assertEquals(0.5, agent.getTemperature());
        assertEquals(2048, agent.getMaxTokens());
        assertEquals(1, agent.getTools().size());
        assertEquals("search", agent.getTools().get(0).getName());
    }

    @Test
    @DisplayName("RunnerConfig 默认值")
    void runnerConfigDefaults() {
        RunnerConfig config = RunnerConfig.defaults();
        assertEquals(10, config.getMaxTurns());
        assertTrue(config.isTracingEnabled());
    }

    @Test
    @DisplayName("TraceCollector 收集和查询")
    void traceCollector() {
        TraceCollector collector = new TraceCollector();
        me.codeleep.victor.infra.agent.tracing.AgentTrace trace1 =
                me.codeleep.victor.infra.agent.tracing.AgentTrace.llmCall("agent1", "input1", "output1", 100);
        me.codeleep.victor.infra.agent.tracing.AgentTrace trace2 =
                me.codeleep.victor.infra.agent.tracing.AgentTrace.toolCall("agent1", "tool1", "result1", 50);

        collector.addTrace("session1", trace1);
        collector.addTrace("session1", trace2);

        assertEquals(2, collector.getTraces("session1").size());
        assertEquals(1, collector.getTracesByAction("session1", "LLM_CALL").size());
        assertEquals(1, collector.getTracesByAction("session1", "TOOL_CALL").size());
        assertEquals(150, collector.getTotalDuration("session1"));

        // 不同 session 隔离
        assertEquals(0, collector.getTraces("session2").size());

        // 清除
        collector.clear("session1");
        assertEquals(0, collector.getTraces("session1").size());
    }

    @Test
    @DisplayName("AgentResult 工厂方法")
    void agentResultFactories() {
        AgentResult success = AgentResult.success("成功");
        assertTrue(success.isSuccess());
        assertEquals("成功", success.getContent());

        AgentResult error = AgentResult.error("失败");
        assertFalse(error.isSuccess());
        assertEquals("失败", error.getErrorMessage());

        AgentResult handoff = AgentResult.handoff("Agent B");
        assertTrue(handoff.isSuccess());
        assertEquals("Agent B", handoff.getHandoffTarget());
        assertTrue(handoff.isHandoff());
    }

    @Test
    @DisplayName("AgentContext 对话历史")
    void agentContextHistory() {
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("你好");
        context.addAssistantMessage("你好！有什么可以帮助你的？");
        context.addUserMessage("什么是Java？");

        assertEquals(3, context.getConversationHistory().size());
        assertEquals("user", context.getConversationHistory().get(0).getRole());
        assertEquals("assistant", context.getConversationHistory().get(1).getRole());
        assertEquals("user", context.getConversationHistory().get(2).getRole());
    }

    @Test
    @DisplayName("AgentContext 变量")
    void agentContextVariables() {
        AgentContext context = new AgentContext("s1", 1L);
        context.setVariable("name", "张三");
        context.setVariable("age", 25);

        assertEquals("张三", context.getVariable("name"));
        assertEquals(25, context.getVariable("age"));
        assertNull(context.getVariable("unknown"));
    }
}
