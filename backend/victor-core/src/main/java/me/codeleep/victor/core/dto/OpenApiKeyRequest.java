package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import me.codeleep.victor.common.enums.OpenApiKeyStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key请求DTO
 */
@Data
public class OpenApiKeyRequest {

    /**
     * Key名称
     */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 明文API Key
     */
    @NotBlank(message = "API Key不能为空")
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
}
