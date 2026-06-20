package me.codeleep.victor.infra.agent.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LLM 协议枚举
 * 决定 infra 创建哪个 AgentScope ModelWrapper
 */
@Getter
@AllArgsConstructor
public enum LlmProtocol {

    OPENAI("OPENAI", "OpenAI", "https://api.openai.com/v1"),
    CLAUDE("CLAUDE", "Claude", "https://api.anthropic.com"),
    QWEN("QWEN", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    VOLCENGINE("VOLCENGINE", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3");

    private final String value;
    private final String description;
    private final String defaultBaseUrl;

    /**
     * 根据 value 查找枚举
     */
    public static LlmProtocol fromValue(String value) {
        for (LlmProtocol protocol : values()) {
            if (protocol.value.equalsIgnoreCase(value)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("不支持的 LLM 协议: " + value);
    }
}
