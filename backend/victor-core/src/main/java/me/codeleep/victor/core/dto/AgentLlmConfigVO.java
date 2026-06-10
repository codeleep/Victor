package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.common.enums.ModelType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent LLM配置视图对象
 */
@Data
public class AgentLlmConfigVO {

    /**
     * ID
     */
    private Long id;

    /**
     * 配置名称
     */
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
    private String apiEndpoint;

    /**
     * 认证参数（脱敏）
     */
    private Map<String, Object> authParams;

    /**
     * 协议
     */
    private LlmProtocol protocol;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型类型
     */
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

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
