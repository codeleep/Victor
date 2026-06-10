package me.codeleep.victor.core.llm;

import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.llm.ChatClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChatClientFactory 单元测试
 */
class ChatClientFactoryTest {

    private ChatClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ChatClientFactory();
    }

    @Test
    @DisplayName("创建 OpenAI ChatClient")
    void createOpenAiChatClient() {
        ChatClient client = factory.createChatClient(LlmProtocol.OPENAI,
                "https://api.openai.com/v1", "test-api-key", "gpt-4", 0.7, 4096);
        assertNotNull(client);
    }

    @Test
    @DisplayName("创建 Claude ChatClient")
    void createClaudeChatClient() {
        ChatClient client = factory.createChatClient(LlmProtocol.CLAUDE,
                "https://api.anthropic.com", "test-api-key", "claude-3-opus-20240229", 0.7, 4096);
        assertNotNull(client);
    }

    @Test
    @DisplayName("创建 Qwen ChatClient (OpenAI兼容)")
    void createQwenChatClient() {
        ChatClient client = factory.createChatClient(LlmProtocol.QWEN,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "test-api-key", "qwen-max", 0.7, 4096);
        assertNotNull(client);
    }

    @Test
    @DisplayName("创建 Doubao ChatClient (火山引擎)")
    void createDoubaoChatClient() {
        ChatClient client = factory.createChatClient(LlmProtocol.DOUBAO,
                "https://ark.cn-beijing.volces.com/api/v3", "test-api-key", "doubao-pro-32k", 0.7, 4096);
        assertNotNull(client);
    }
}
