package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.VoiceServiceProvider;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ASR配置实体
 */
@Data
@TableName(value = "voice_asr_config", autoResultMap = true)
public class VoiceAsrConfig {

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
    private VoiceServiceProvider provider;

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
     * 默认语言
     */
    private String language;

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
