package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.infra.agent.tool.FunctionTool;
import me.codeleep.victor.infra.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 单元测试
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("注册和获取工具")
    void registerAndGetTool() {
        AgentTool tool = FunctionTool.of("echo", "回显工具", args -> args.get("input"));
        registry.register(tool);

        AgentTool retrieved = registry.getTool("echo");
        assertNotNull(retrieved);
        assertEquals("echo", retrieved.getName());
        assertEquals("回显工具", retrieved.getDescription());
    }

    @Test
    @DisplayName("工具不存在时返回 null")
    void getNonExistentTool() {
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    @DisplayName("检查工具是否存在")
    void hasTool() {
        registry.register(FunctionTool.of("test", "测试", args -> "ok"));
        assertTrue(registry.hasTool("test"));
        assertFalse(registry.hasTool("other"));
    }

    @Test
    @DisplayName("执行工具")
    void executeTool() {
        AgentTool tool = FunctionTool.of("add", "加法", args -> {
            int a = ((Number) args.get("a")).intValue();
            int b = ((Number) args.get("b")).intValue();
            return a + b;
        });
        registry.register(tool);

        Object result = registry.getTool("add").execute(Map.of("a", 3, "b", 5));
        assertEquals(8, result);
    }

    @Test
    @DisplayName("批量注册工具")
    void registerAll() {
        List<AgentTool> tools = List.of(
                FunctionTool.of("tool1", "工具1", args -> "r1"),
                FunctionTool.of("tool2", "工具2", args -> "r2"),
                FunctionTool.of("tool3", "工具3", args -> "r3")
        );
        registry.registerAll(tools);

        assertEquals(3, registry.listTools().size());
        assertTrue(registry.hasTool("tool1"));
        assertTrue(registry.hasTool("tool2"));
        assertTrue(registry.hasTool("tool3"));
    }

    @Test
    @DisplayName("注销工具")
    void unregister() {
        registry.register(FunctionTool.of("temp", "临时", args -> "ok"));
        assertTrue(registry.hasTool("temp"));

        registry.unregister("temp");
        assertFalse(registry.hasTool("temp"));
    }

    @Test
    @DisplayName("获取 Function Definitions")
    void getFunctionDefinitions() {
        registry.register(FunctionTool.of("search", "搜索", args -> "results"));

        List<Map<String, Object>> defs = registry.getFunctionDefinitions();
        assertEquals(1, defs.size());

        Map<String, Object> def = defs.get(0);
        assertEquals("function", def.get("type"));
        Map<String, Object> func = (Map<String, Object>) def.get("function");
        assertEquals("search", func.get("name"));
    }

    @Test
    @DisplayName("清空所有工具")
    void clear() {
        registry.register(FunctionTool.of("a", "a", args -> "a"));
        registry.register(FunctionTool.of("b", "b", args -> "b"));
        assertEquals(2, registry.listTools().size());

        registry.clear();
        assertEquals(0, registry.listTools().size());
    }
}
