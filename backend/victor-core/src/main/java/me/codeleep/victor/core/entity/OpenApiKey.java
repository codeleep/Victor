package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.OpenApiKeyStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开放API Key实体
 */
@Data
@TableName(value = "open_api_key", autoResultMap = true)
public class OpenApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Key名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 明文API Key
     */
    private String apiKey;

    /**
     * 权限范围
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> scopes;

    /**
     * 默认导入状态
     */
    private String defaultIngestStatus;

    /**
     * 状态
     */
    private OpenApiKeyStatus status;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 最近使用时间
     */
    private LocalDateTime lastUsedAt;

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
