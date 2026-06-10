package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.common.enums.ModelType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent LLM配置实体
 */
@Data
@TableName(value = "agent_llm_config", autoResultMap = true)
public class AgentLlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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
     * 认证参数（加密存储）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
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
    @TableField(typeHandler = JsonbTypeHandler.class)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
