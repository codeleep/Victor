package me.codeleep.victor.infra.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 配置定义 - 不可变配置
 * 上层提供配置，infra 根据 {@link LlmProtocol} 自行创建对应的 AgentScope 模型包装器
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmDefinition {

    /**
     * LLM 协议类型，决定 infra 创建哪个 ModelWrapper
     */
    private LlmProtocol protocol;

    /**
     * LLM API 地址
     */
    private String baseUrl;

    /**
     * LLM API Key
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 温度参数
     */
    @Builder.Default
    private double temperature = 0.7;

    /**
     * 最大 Token
     */
    @Builder.Default
    private int maxTokens = 4096;
}
