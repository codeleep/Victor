package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 开放API Key状态枚举
 */
@Getter
@AllArgsConstructor
public enum OpenApiKeyStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "禁用");

    private final String value;
    private final String description;
}
