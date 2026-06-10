package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 来源类型枚举
 */
@Getter
@AllArgsConstructor
public enum SourceType {

    USER("USER", "用户创建"),
    SYSTEM("SYSTEM", "系统"),
    OPEN_API("OPEN_API", "开放接口导入");

    private final String value;
    private final String description;
}
