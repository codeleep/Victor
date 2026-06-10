package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.lifecycle.AgentLifecycleListener;
import me.codeleep.victor.infra.agent.lifecycle.LifecycleEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle Hooks 单元测试
 */
class LifecycleHooksTest {

    @Test
    @DisplayName("LifecycleEvent 构建")
    void lifecycleEventBuild() {
        LifecycleEvent event = LifecycleEvent.builder()
                .agentName("TestAgent")
                .sessionId("s1")
                .eventType(LifecycleEvent.EventType.AGENT_START)
                .build();

        assertEquals("TestAgent", event.getAgentName());
        assertEquals("s1", event.getSessionId());
        assertEquals(LifecycleEvent.EventType.AGENT_START, event.getEventType());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("AgentLifecycleListener 默认方法不抛异常")
    void defaultMethodsNoop() {
        AgentLifecycleListener listener = new AgentLifecycleListener() {};

        AgentDefinition agent = AgentDefinition.builder()
                .name("Test").instructions("T").build();
        AgentContext context = new AgentContext("s1", 1L);
        AgentResult result = AgentResult.success("ok");

        // 所有默认方法都应该安全调用，不抛异常
        assertDoesNotThrow(() -> listener.onAgentStart(agent, context));
        assertDoesNotThrow(() -> listener.onAgentEnd(agent, context, result));
        assertDoesNotThrow(() -> listener.onHandoff(agent, agent, context));
        assertDoesNotThrow(() -> listener.onTurnStart(agent, 1, 10, context));
        assertDoesNotThrow(() -> listener.onLlmStart(agent, context));
        assertDoesNotThrow(() -> listener.onLlmEnd(agent, result, 500, context));
        assertDoesNotThrow(() -> listener.onLlmError(agent, "error", 100, context));
        assertDoesNotThrow(() -> listener.onToolStart(agent, "tool1", Map.of("key", "val"), context));
        assertDoesNotThrow(() -> listener.onToolEnd(agent, "tool1", "result", true, null, 100, context));
        assertDoesNotThrow(() -> listener.onGuardrailCheck(agent, "input", "safe", true, null, 10, context));
        assertDoesNotThrow(() -> listener.onHumanInputRequired(agent, "req-1", "confirm?", List.of("yes", "no"), context));
    }

    @Test
    @DisplayName("自定义 listener 收集事件")
    void customListenerCollectsEvents() {
        List<String> events = new ArrayList<>();

        AgentLifecycleListener collector = new AgentLifecycleListener() {
            @Override
            public void onAgentStart(AgentDefinition agent, AgentContext context) {
                events.add("AGENT_START:" + agent.getName());
            }

            @Override
            public void onAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {
                events.add("AGENT_END:" + agent.getName());
            }

            @Override
            public void onHandoff(AgentDefinition fromAgent, AgentDefinition toAgent, AgentContext context) {
                events.add("HANDOFF:" + fromAgent.getName() + "->" + toAgent.getName());
            }

            @Override
            public void onTurnStart(AgentDefinition agent, int turn, int maxTurns, AgentContext context) {
                events.add("TURN_START:" + turn);
            }

            @Override
            public void onToolStart(AgentDefinition agent, String toolName, Map<String, Object> arguments, AgentContext context) {
                events.add("TOOL_START:" + toolName);
            }

            @Override
            public void onToolEnd(AgentDefinition agent, String toolName, Object result,
                                  boolean success, String errorMessage, long durationMs, AgentContext context) {
                events.add("TOOL_END:" + toolName + ":" + (success ? "ok" : "fail"));
            }

            @Override
            public void onLlmStart(AgentDefinition agent, AgentContext context) {
                events.add("LLM_START:" + agent.getName());
            }

            @Override
            public void onLlmEnd(AgentDefinition agent, AgentResult result, long durationMs, AgentContext context) {
                events.add("LLM_END:" + agent.getName());
            }

            @Override
            public void onLlmError(AgentDefinition agent, String errorMessage, long durationMs, AgentContext context) {
                events.add("LLM_ERROR:" + errorMessage);
            }

            @Override
            public void onGuardrailCheck(AgentDefinition agent, String stage, String guardrailName,
                                          boolean passed, String reason, long durationMs, AgentContext context) {
                events.add("GUARDRAIL:" + stage + ":" + (passed ? "pass" : "block"));
            }
        };

        AgentDefinition agentA = AgentDefinition.builder()
                .name("A").instructions("A").lifecycleListener(collector).build();
        AgentDefinition agentB = AgentDefinition.builder()
                .name("B").instructions("B").build();
        AgentContext context = new AgentContext("s1", 1L);

        // 模拟完整的生命周期事件序列
        collector.onAgentStart(agentA, context);
        collector.onGuardrailCheck(agentA, "input", "content_safe", true, null, 5, context);
        collector.onTurnStart(agentA, 1, 10, context);
        collector.onLlmStart(agentA, context);
        collector.onLlmEnd(agentA, AgentResult.success("hi"), 100, context);
        collector.onToolStart(agentA, "search", Map.of("query", "test"), context);
        collector.onToolEnd(agentA, "search", "results", true, null, 50, context);
        collector.onTurnStart(agentA, 2, 10, context);
        collector.onLlmStart(agentA, context);
        collector.onLlmError(agentA, "timeout", 3000, context);
        collector.onHandoff(agentA, agentB, context);
        collector.onGuardrailCheck(agentB, "output", "tone_check", false, "too aggressive", 3, context);
        collector.onAgentEnd(agentA, context, AgentResult.success("done"));

        assertEquals(13, events.size());
        assertEquals("AGENT_START:A", events.get(0));
        assertEquals("GUARDRAIL:input:pass", events.get(1));
        assertEquals("TURN_START:1", events.get(2));
        assertEquals("LLM_START:A", events.get(3));
        assertEquals("LLM_END:A", events.get(4));
        assertEquals("TOOL_START:search", events.get(5));
        assertEquals("TOOL_END:search:ok", events.get(6));
        assertEquals("TURN_START:2", events.get(7));
        assertEquals("LLM_START:A", events.get(8));
        assertEquals("LLM_ERROR:timeout", events.get(9));
        assertEquals("HANDOFF:A->B", events.get(10));
        assertEquals("GUARDRAIL:output:block", events.get(11));
        assertEquals("AGENT_END:A", events.get(12));
    }

    @Test
    @DisplayName("AgentDefinition 可设置 lifecycleListener")
    void agentDefinitionWithListener() {
        AgentLifecycleListener listener = new AgentLifecycleListener() {};

        AgentDefinition agent = AgentDefinition.builder()
                .name("Test")
                .instructions("T")
                .lifecycleListener(listener)
                .build();

        assertNotNull(agent.getLifecycleListener());
        assertSame(listener, agent.getLifecycleListener());
    }

    @Test
    @DisplayName("AgentDefinition 无 lifecycleListener 时为 null")
    void agentDefinitionWithoutListener() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("Test")
                .instructions("T")
                .build();

        assertNull(agent.getLifecycleListener());
    }

    @Test
    @DisplayName("EventType 枚举值完整")
    void eventTypeValues() {
        LifecycleEvent.EventType[] types = LifecycleEvent.EventType.values();
        assertEquals(11, types.length);
        assertEquals(LifecycleEvent.EventType.AGENT_START, LifecycleEvent.EventType.valueOf("AGENT_START"));
        assertEquals(LifecycleEvent.EventType.AGENT_END, LifecycleEvent.EventType.valueOf("AGENT_END"));
        assertEquals(LifecycleEvent.EventType.HANDOFF, LifecycleEvent.EventType.valueOf("HANDOFF"));
        assertEquals(LifecycleEvent.EventType.TURN_START, LifecycleEvent.EventType.valueOf("TURN_START"));
        assertEquals(LifecycleEvent.EventType.TOOL_START, LifecycleEvent.EventType.valueOf("TOOL_START"));
        assertEquals(LifecycleEvent.EventType.TOOL_END, LifecycleEvent.EventType.valueOf("TOOL_END"));
        assertEquals(LifecycleEvent.EventType.LLM_START, LifecycleEvent.EventType.valueOf("LLM_START"));
        assertEquals(LifecycleEvent.EventType.LLM_END, LifecycleEvent.EventType.valueOf("LLM_END"));
        assertEquals(LifecycleEvent.EventType.LLM_ERROR, LifecycleEvent.EventType.valueOf("LLM_ERROR"));
        assertEquals(LifecycleEvent.EventType.GUARDRAIL_CHECK, LifecycleEvent.EventType.valueOf("GUARDRAIL_CHECK"));
        assertEquals(LifecycleEvent.EventType.HUMAN_INPUT_REQUIRED, LifecycleEvent.EventType.valueOf("HUMAN_INPUT_REQUIRED"));
    }
}
