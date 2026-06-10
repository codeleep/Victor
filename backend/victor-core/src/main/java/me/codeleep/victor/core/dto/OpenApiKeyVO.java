package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.OpenApiKeyStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key响应VO
 */
@Data
public class OpenApiKeyVO {

    private Long id;

    /**
     * Key名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * API Key (脱敏)
     */
    private String apiKey;

    /**
     * 权限范围
     */
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
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
