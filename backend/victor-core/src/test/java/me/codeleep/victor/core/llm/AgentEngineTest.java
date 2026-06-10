package me.codeleep.victor.core.llm;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.common.enums.ModelType;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Agent 引擎测试
 *
 * 需要设置以下环境变量才能运行：
 * - VOLCENGINE_BASE_URL: 火山引擎 API 地址
 * - VOLCENGINE_API_KEY: 火山引擎 API Key
 * - VOLCENGINE_MODEL_NAME: 模型名称或接入点 ID
 */
@Slf4j
class AgentEngineTest {

    private TestAgentEngine agentEngine;

    @BeforeEach
    void setUp() {
        me.codeleep.victor.infra.agent.llm.ChatClientFactory infraFactory =
                new me.codeleep.victor.infra.agent.llm.ChatClientFactory();
        agentEngine = new TestAgentEngine(infraFactory);
    }

    @Test
    @DisplayName("Agent 基本执行 - 无工具调用")
    void testAgentBasicExecution() {
        String baseUrl = System.getenv("VOLCENGINE_BASE_URL");
        String apiKey = System.getenv("VOLCENGINE_API_KEY");
        String modelName = System.getenv("VOLCENGINE_MODEL_NAME");

        assumeTrue(baseUrl != null && !baseUrl.isEmpty(), "跳过测试: 未设置 VOLCENGINE_BASE_URL 环境变量");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "跳过测试: 未设置 VOLCENGINE_API_KEY 环境变量");
        assumeTrue(modelName != null && !modelName.isEmpty(), "跳过测试: 未设置 VOLCENGINE_MODEL_NAME 环境变量");

        AgentLlmConfig llmConfig = createLlmConfig(baseUrl, apiKey, modelName);
        Agent agent = createAgent("你是一个友好的AI助手，请简洁回答问题。");

        AgentContext context = new AgentContext("test-session-001", 1L);
        context.addUserMessage("请用一句话介绍什么是Java");

        log.info("开始执行 Agent...");
        AgentResult result = agentEngine.execute(agent, llmConfig, context);

        assertNotNull(result, "Agent 结果不应为空");
        assertTrue(result.isSuccess(), "Agent 执行应成功");
        assertNotNull(result.getContent(), "响应内容不应为空");
        assertFalse(result.getContent().isBlank(), "响应内容不应为空白");

        log.info("Agent 执行成功，响应内容:\n{}", result.getContent());
    }

    @Test
    @DisplayName("Agent 多轮对话测试")
    void testAgentMultiTurnConversation() {
        String baseUrl = System.getenv("VOLCENGINE_BASE_URL");
        String apiKey = System.getenv("VOLCENGINE_API_KEY");
        String modelName = System.getenv("VOLCENGINE_MODEL_NAME");

        assumeTrue(baseUrl != null && !baseUrl.isEmpty(), "跳过测试: 未设置 VOLCENGINE_BASE_URL 环境变量");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "跳过测试: 未设置 VOLCENGINE_API_KEY 环境变量");
        assumeTrue(modelName != null && !modelName.isEmpty(), "跳过测试: 未设置 VOLCENGINE_MODEL_NAME 环境变量");

        AgentLlmConfig llmConfig = createLlmConfig(baseUrl, apiKey, modelName);
        Agent agent = createAgent("你是一个编程导师，用简单易懂的方式解释技术概念。");

        AgentContext context = new AgentContext("test-session-002", 1L);
        context.addUserMessage("什么是多态？");
        context.addAssistantMessage("多态是面向对象编程的核心概念之一，指的是同一个接口可以有不同的实现方式。");
        context.addUserMessage("能给一个简单的Java例子吗？");

        log.info("开始执行多轮对话测试...");
        AgentResult result = agentEngine.execute(agent, llmConfig, context);

        assertNotNull(result, "Agent 结果不应为空");
        assertTrue(result.isSuccess(), "Agent 执行应成功");
        assertNotNull(result.getContent(), "响应内容不应为空");

        log.info("多轮对话测试成功，响应内容:\n{}", result.getContent());
    }

    @Test
    @DisplayName("Agent 错误处理 - LLM 配置为空")
    void testAgentWithNullLlmConfig() {
        Agent agent = createAgent("测试提示词");

        AgentContext context = new AgentContext("test-session-003", 1L);
        context.addUserMessage("测试消息");

        AgentResult result = agentEngine.execute(agent, null, context);

        assertNotNull(result, "Agent 结果不应为空");
        assertFalse(result.isSuccess(), "Agent 执行应失败");
        assertNotNull(result.getErrorMessage(), "错误消息不应为空");
        log.info("错误处理测试通过，错误消息: {}", result.getErrorMessage());
    }

    @Test
    @DisplayName("Agent 上下文变量测试")
    void testAgentContextVariables() {
        String baseUrl = System.getenv("VOLCENGINE_BASE_URL");
        String apiKey = System.getenv("VOLCENGINE_API_KEY");
        String modelName = System.getenv("VOLCENGINE_MODEL_NAME");

        assumeTrue(baseUrl != null && !baseUrl.isEmpty(), "跳过测试: 未设置 VOLCENGINE_BASE_URL 环境变量");
        assumeTrue(apiKey != null && !apiKey.isEmpty(), "跳过测试: 未设置 VOLCENGINE_API_KEY 环境变量");
        assumeTrue(modelName != null && !modelName.isEmpty(), "跳过测试: 未设置 VOLCENGINE_MODEL_NAME 环境变量");

        AgentLlmConfig llmConfig = createLlmConfig(baseUrl, apiKey, modelName);
        Agent agent = createAgent("你是一个面试官，正在面试候选人。");

        AgentContext context = new AgentContext("test-session-004", 1L);
        context.setVariable("candidateName", "张三");
        context.setVariable("position", "Java开发工程师");
        context.addUserMessage("请简单介绍一下自己");

        assertEquals("张三", context.getVariable("candidateName"));
        assertEquals("Java开发工程师", context.getVariable("position"));

        AgentResult result = agentEngine.execute(agent, llmConfig, context);

        assertNotNull(result, "Agent 结果不应为空");
        assertTrue(result.isSuccess(), "Agent 执行应成功");
        log.info("上下文变量测试通过，响应内容:\n{}", result.getContent());
    }

    private AgentLlmConfig createLlmConfig(String baseUrl, String apiKey, String modelName) {
        AgentLlmConfig config = new AgentLlmConfig();
        config.setId(1L);
        config.setUserId(1L);
        config.setName("Test Volcengine Config");
        config.setDescription("测试用火山引擎配置");
        config.setProvider(LlmProtocol.DOUBAO.name());
        config.setApiEndpoint(baseUrl);
        config.setAuthParams(Map.of("apiKey", apiKey));
        config.setProtocol(LlmProtocol.DOUBAO);
        config.setModelName(modelName);
        config.setModelType(ModelType.INFERENCE);
        config.setTemperature(new BigDecimal("0.7"));
        config.setMaxTokens(4096);
        return config;
    }

    private Agent createAgent(String systemPrompt) {
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setUserId(1L);
        agent.setKey("test-agent");
        agent.setName("Test Agent");
        agent.setRole("AI助手");
        agent.setSystemPrompt(systemPrompt);
        agent.setType(AgentType.INTERVIEW);
        agent.setIsSystem(false);
        return agent;
    }
}
