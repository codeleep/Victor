package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import me.codeleep.victor.infra.agent.runner.AgentRunnerImpl;
import me.codeleep.victor.infra.agent.tool.AgentToolAdapter;
import me.codeleep.victor.infra.agent.tool.AgentToolAdapterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent-as-Tool 单元测试
 */
class AgentToolAdapterTest {

    @Test
    @DisplayName("AgentToolAdapter getName/getDescription 正确")
    void adapterNameAndDescription() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("Search Agent")
                .instructions("搜索信息")
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("http://localhost")
                .llmApiKey("fake")
                .modelName("gpt-4")
                .build();

        // 创建一个简单的 Runner mock（不实际调用 LLM）
        AgentRunner runner = new AgentRunnerImpl(null);

        AgentToolAdapterConfig config = AgentToolAdapterConfig.builder()
                .toolName("search_tool")
                .toolDescription("搜索互联网信息")
                .build();

        AgentToolAdapter adapter = new AgentToolAdapter(agent, runner, config);

        assertEquals("search_tool", adapter.getName());
        assertEquals("搜索互联网信息", adapter.getDescription());
    }

    @Test
    @DisplayName("简化构造函数")
    void simplifiedConstructor() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("Agent B")
                .instructions("B")
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("http://localhost")
                .llmApiKey("fake")
                .modelName("gpt-4")
                .build();

        AgentToolAdapter adapter = new AgentToolAdapter(
                agent, new AgentRunnerImpl(null), "tool_b", "工具B");

        assertEquals("tool_b", adapter.getName());
        assertEquals("工具B", adapter.getDescription());
    }

    @Test
    @DisplayName("默认 inputSchema 包含 input 字段")
    void defaultInputSchema() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("Agent").instructions("A").build();

        AgentToolAdapter adapter = new AgentToolAdapter(
                agent, new AgentRunnerImpl(null), "tool", "desc");

        Map<String, Object> schema = adapter.getParametersSchema();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("input"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputField = (Map<String, Object>) props.get("input");
        assertEquals("string", inputField.get("type"));
    }

    @Test
    @DisplayName("AgentToolAdapterConfig 默认值")
    void configDefaults() {
        AgentToolAdapterConfig config = AgentToolAdapterConfig.builder()
                .toolName("test")
                .toolDescription("desc")
                .build();

        assertTrue(config.isCreateNewContext());
        assertNotNull(config.getOutputExtractor());
        assertNotNull(config.getInputSchema());
    }

    @Test
    @DisplayName("自定义 outputExtractor")
    void customOutputExtractor() {
        AgentToolAdapterConfig config = AgentToolAdapterConfig.builder()
                .toolName("test")
                .toolDescription("desc")
                .outputExtractor(result -> "自定义: " + result.getContent())
                .build();

        AgentResult result = AgentResult.success("原始内容");
        String output = config.getOutputExtractor().apply(result);
        assertEquals("自定义: 原始内容", output);
    }

    @Test
    @DisplayName("默认 outputExtractor 返回 content")
    void defaultOutputExtractor() {
        AgentToolAdapterConfig config = AgentToolAdapterConfig.builder()
                .toolName("test")
                .toolDescription("desc")
                .build();

        AgentResult result = AgentResult.success("Hello");
        assertEquals("Hello", config.getOutputExtractor().apply(result));
    }

    @Test
    @DisplayName("execute 遇到失败 Agent 时返回错误信息")
    void executeWithFailedAgent() {
        // 创建一个会返回错误的 Runner
        AgentRunner failingRunner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext context) {
                return AgentResult.error("LLM 不可用");
            }
        };

        AgentDefinition agent = AgentDefinition.builder()
                .name("Failing Agent")
                .instructions("会失败")
                .build();

        AgentToolAdapter adapter = new AgentToolAdapter(agent, failingRunner, "fail_tool", "会失败的工具");

        Object result = adapter.execute(Map.of("input", "测试"));
        assertEquals("执行错误: LLM 不可用", result);
    }
}
