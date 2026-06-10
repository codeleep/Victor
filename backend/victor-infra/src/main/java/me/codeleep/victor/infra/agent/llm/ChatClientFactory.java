package me.codeleep.victor.infra.agent.llm;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.llm.volcengine.VolcengineChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * ChatClient 工厂 - 基于 Spring AI
 * 根据协议类型动态创建 ChatClient
 */
@Slf4j
@Component
public class ChatClientFactory {

    /**
     * 创建 OpenAI 兼容的 ChatClient
     * 支持 OpenAI、Qwen、Doubao 等 OpenAI 兼容 API
     *
     * @param baseUrl   API 地址
     * @param apiKey    API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大 Token
     * @return ChatClient
     */
    public ChatClient createOpenAiClient(String baseUrl, String apiKey, String modelName,
                                          double temperature, int maxTokens) {
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(modelName)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建 Claude ChatClient
     *
     * @param baseUrl   API 地址
     * @param apiKey    API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大 Token
     * @return ChatClient
     */
    public ChatClient createClaudeClient(String baseUrl, String apiKey, String modelName,
                                          double temperature, int maxTokens) {
        AnthropicApi anthropicApi = new AnthropicApi(baseUrl, apiKey);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .withModel(modelName)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        AnthropicChatModel chatModel = new AnthropicChatModel(anthropicApi, options);
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建火山引擎 ChatClient
     *
     * @param baseUrl   API 地址
     * @param apiKey    API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大 Token
     * @return ChatClient
     */
    public ChatClient createVolcengineClient(String baseUrl, String apiKey, String modelName,
                                              double temperature, int maxTokens) {
        VolcengineChatModel chatModel = new VolcengineChatModel(baseUrl, apiKey, modelName, temperature, maxTokens);
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建火山引擎 ChatModel（可配置工具）
     * 返回原始 VolcengineChatModel，支持后续设置 tools
     */
    public VolcengineChatModel createVolcengineChatModel(String baseUrl, String apiKey, String modelName,
                                                          double temperature, int maxTokens) {
        return new VolcengineChatModel(baseUrl, apiKey, modelName, temperature, maxTokens);
    }

    /**
     * 通用创建方法 - 根据协议类型自动选择
     *
     * @param protocol  协议类型（OPENAI, CLAUDE, DOUBAO, QWEN）
     * @param baseUrl   API 地址
     * @param apiKey    API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大 Token
     * @return ChatClient
     */
    public ChatClient createChatClient(String protocol, String baseUrl, String apiKey,
                                        String modelName, double temperature, int maxTokens) {
        return createChatClient(LlmProtocol.fromValue(protocol), baseUrl, apiKey, modelName, temperature, maxTokens);
    }

    /**
     * 通用创建方法 - 使用 LlmProtocol 枚举
     */
    public ChatClient createChatClient(LlmProtocol protocol, String baseUrl, String apiKey,
                                        String modelName, double temperature, int maxTokens) {
        return switch (protocol) {
            case OPENAI, QWEN -> createOpenAiClient(baseUrl, apiKey, modelName, temperature, maxTokens);
            case DOUBAO -> createVolcengineClient(baseUrl, apiKey, modelName, temperature, maxTokens);
            case CLAUDE -> createClaudeClient(baseUrl, apiKey, modelName, temperature, maxTokens);
        };
    }
}
