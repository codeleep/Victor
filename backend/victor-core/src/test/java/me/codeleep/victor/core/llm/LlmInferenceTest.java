package me.codeleep.victor.core.llm;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.common.enums.ModelType;
import me.codeleep.victor.infra.agent.llm.volcengine.VolcengineChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LLM 推理测试
 * 使用火山引擎官方 SDK 调用 LLM
 *
 * 需要设置以下环境变量才能运行：
 * - VOLCENGINE_BASE_URL: 火山引擎 API 地址
 * - VOLCENGINE_API_KEY: 火山引擎 API Key
 * - VOLCENGINE_MODEL_NAME: 模型名称或接入点 ID
 */
@Slf4j
class LlmInferenceTest {

    @Test
    @DisplayName("使用火山引擎 SDK 调用 ark-code-latest 模型")
    void testVolcengineArkCodeLlmInference() {
        // 1. 从环境变量读取配置
        String baseUrl = System.getenv("VOLCENGINE_BASE_URL");
        String apiKey = System.getenv("VOLCENGINE_API_KEY");
        String modelName = System.getenv("VOLCENGINE_MODEL_NAME");

        // 如果环境变量不存在，跳过测试
        assumeTrue(baseUrl != null && !baseUrl.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_BASE_URL 环境变量");
        assumeTrue(apiKey != null && !apiKey.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_API_KEY 环境变量");
        assumeTrue(modelName != null && !modelName.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_MODEL_NAME 环境变量");

        log.info("火山引擎 LLM 配置: baseUrl={}, modelName={}", baseUrl, modelName);

        // 2. 创建 VolcengineChatModel
        VolcengineChatModel chatModel = new VolcengineChatModel(
                baseUrl, apiKey, modelName, 0.7, 4096);

        // 3. 创建 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        assertNotNull(chatClient, "ChatClient 不应为空");
        log.info("ChatClient 创建成功");

        // 4. 调用 LLM 推理
        String userMessage = "请用一句话介绍什么是 Java 的多态特性";
        log.info("发送推理请求: {}", userMessage);

        Prompt prompt = new Prompt(List.of(new UserMessage(userMessage)));
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        // 5. 验证结果
        assertNotNull(response, "LLM 响应不应为空");
        AssistantMessage assistantMessage = response.getResult().getOutput();
        String content = assistantMessage.getContent();
        assertNotNull(content, "响应内容不应为空");
        assertFalse(content.isBlank(), "响应内容不应为空白");

        log.info("LLM 推理成功，响应内容:\n{}", content);

        // 6. 关闭线程池
        chatModel.shutdown();
    }

    @Test
    @DisplayName("使用 ChatClientFactory 创建火山引擎 ChatClient")
    void testChatClientFactoryWithVolcengine() {
        // 1. 从环境变量读取配置
        String baseUrl = System.getenv("VOLCENGINE_BASE_URL");
        String apiKey = System.getenv("VOLCENGINE_API_KEY");
        String modelName = System.getenv("VOLCENGINE_MODEL_NAME");

        // 如果环境变量不存在，跳过测试
        assumeTrue(baseUrl != null && !baseUrl.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_BASE_URL 环境变量");
        assumeTrue(apiKey != null && !apiKey.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_API_KEY 环境变量");
        assumeTrue(modelName != null && !modelName.isEmpty(),
                "跳过测试: 未设置 VOLCENGINE_MODEL_NAME 环境变量");

        log.info("火山引擎 LLM 配置: baseUrl={}, modelName={}", baseUrl, modelName);
        // 1. 构建 LLM 配置（与数据库中的配置一致）
        AgentLlmConfig config = new AgentLlmConfig();
        config.setId(2L);
        config.setUserId(1L);
        config.setName("火山方舟 Coding");
        config.setDescription("火山方舟 Code 模型 (ark-code-latest)");
        config.setProvider(LlmProtocol.DOUBAO.name());
        config.setApiEndpoint(baseUrl);
        config.setAuthParams(Map.of("apiKey", apiKey));
        config.setProtocol(LlmProtocol.DOUBAO);
        config.setModelName(modelName);
        config.setModelType(ModelType.INFERENCE);
        config.setTemperature(new BigDecimal("0.7"));
        config.setMaxTokens(4096);

        log.info("LLM 配置: name={}, provider={}, model={}, endpoint={}",
                config.getName(), config.getProvider(), config.getModelName(), config.getApiEndpoint());

        // 2. 使用 ChatClientFactory 创建 ChatClient
        me.codeleep.victor.infra.agent.llm.ChatClientFactory factory =
                new me.codeleep.victor.infra.agent.llm.ChatClientFactory();
        String cfgApiKey = config.getAuthParams() != null ? (String) config.getAuthParams().getOrDefault("apiKey", "") : "";
        ChatClient chatClient = factory.createChatClient(
                config.getProtocol(), config.getApiEndpoint(), cfgApiKey,
                config.getModelName(),
                config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7,
                config.getMaxTokens() != null ? config.getMaxTokens() : 4096);
        assertNotNull(chatClient, "ChatClient 不应为空");
        log.info("ChatClient 创建成功");

        // 3. 调用 LLM 推理
        String userMessage = "请用一句话介绍什么是 Java 的多态特性";
        log.info("发送推理请求: {}", userMessage);

        Prompt prompt = new Prompt(List.of(new UserMessage(userMessage)));
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        // 4. 验证结果
        assertNotNull(response, "LLM 响应不应为空");
        AssistantMessage assistantMessage = response.getResult().getOutput();
        String content = assistantMessage.getContent();
        assertNotNull(content, "响应内容不应为空");
        assertFalse(content.isBlank(), "响应内容不应为空白");

        log.info("LLM 推理成功，响应内容:\n{}", content);
    }
}
