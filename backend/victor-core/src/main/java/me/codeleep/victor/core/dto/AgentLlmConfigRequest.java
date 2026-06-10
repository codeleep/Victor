package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.common.enums.ModelType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agent LLM配置请求
 */
@Data
public class AgentLlmConfigRequest {

    /**
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    private String name;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 提供商
     */
    private String provider;

    /**
     * API地址
     */
    @NotBlank(message = "API地址不能为空")
    private String apiEndpoint;

    /**
     * 认证参数
     */
    private Map<String, Object> authParams;

    /**
     * 协议
     */
    @NotNull(message = "协议不能为空")
    private LlmProtocol protocol;

    /**
     * 模型名称
     */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /**
     * 模型类型
     */
    @NotNull(message = "模型类型不能为空")
    private ModelType modelType;

    /**
     * 温度参数
     */
    private BigDecimal temperature;

    /**
     * 最大Token
     */
    private Integer maxTokens;

    /**
     * 额外参数
     */
    private Map<String, Object> extraParams;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    /**
     * 是否默认
     */
    private Boolean isDefault;
}
