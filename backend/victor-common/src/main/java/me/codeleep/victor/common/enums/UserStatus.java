package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    ACTIVE("ACTIVE", "正常"),
    LOCKED("LOCKED", "锁定"),
    DELETED("DELETED", "已删除");

    private final String value;
    private final String description;
}
