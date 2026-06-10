package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.handoff.HandoffTool;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Handoff 单元测试
 */
class HandoffTest {

    @Test
    @DisplayName("Handoff 工具名称生成")
    void handoffToolName() {
        AgentDefinition target = AgentDefinition.builder()
                .name("Billing Agent")
                .instructions("处理账单问题")
                .build();

        Handoff handoff = Handoff.builder()
                .targetAgent(target)
                .description("当用户询问账单问题时转移")
                .build();

        assertEquals("handoff_to_billing_agent", handoff.getToolName());
    }

    @Test
    @DisplayName("HandoffTool 执行返回转移信息")
    void handoffToolExecution() {
        AgentDefinition target = AgentDefinition.builder()
                .name("Tech Support")
                .instructions("处理技术支持")
                .build();

        Handoff handoff = Handoff.builder()
                .targetAgent(target)
                .description("技术支持转移")
                .build();

        HandoffTool tool = new HandoffTool(handoff);

        assertEquals("handoff_to_tech_support", tool.getName());
        assertNotNull(tool.getDescription());

        // 执行工具
        Object result = tool.execute(Map.of("reason", "用户需要技术支持"));
        assertInstanceOf(Map.class, result);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("handoff", resultMap.get("status"));
        assertEquals("Tech Support", resultMap.get("target"));
        assertEquals("用户需要技术支持", resultMap.get("reason"));
    }

    @Test
    @DisplayName("HandoffTool 参数 Schema")
    void handoffToolSchema() {
        AgentDefinition target = AgentDefinition.builder()
                .name("Agent B")
                .instructions("B")
                .build();

        HandoffTool tool = new HandoffTool(Handoff.builder()
                .targetAgent(target)
                .build());

        Map<String, Object> schema = tool.getParametersSchema();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
    }

    @Test
    @DisplayName("多 Agent Handoff 链")
    void handoffChain() {
        AgentDefinition agentC = AgentDefinition.builder()
                .name("Agent C")
                .instructions("最终处理")
                .build();

        AgentDefinition agentB = AgentDefinition.builder()
                .name("Agent B")
                .instructions("中间处理")
                .handoffs(java.util.List.of(
                        Handoff.builder()
                                .targetAgent(agentC)
                                .description("转给C")
                                .build()
                ))
                .build();

        AgentDefinition agentA = AgentDefinition.builder()
                .name("Agent A")
                .instructions("入口")
                .handoffs(java.util.List.of(
                        Handoff.builder()
                                .targetAgent(agentB)
                                .description("转给B")
                                .build()
                ))
                .build();

        // A 有 handoff 到 B
        assertEquals(1, agentA.getHandoffs().size());
        assertEquals("Agent B", agentA.getHandoffs().get(0).getTargetAgent().getName());

        // B 有 handoff 到 C
        assertEquals(1, agentB.getHandoffs().size());
        assertEquals("Agent C", agentB.getHandoffs().get(0).getTargetAgent().getName());

        // C 没有 handoff
        assertTrue(agentC.getHandoffs().isEmpty());
    }
}
