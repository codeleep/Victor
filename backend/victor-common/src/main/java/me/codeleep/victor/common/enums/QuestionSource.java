package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目来源枚举
 */
@Getter
@AllArgsConstructor
public enum QuestionSource {

    SYSTEM("SYSTEM", "系统"),
    USER("USER", "用户创建"),
    OPEN_API("OPEN_API", "开放接口导入");

    private final String value;
    private final String description;
}
