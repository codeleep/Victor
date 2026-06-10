package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 难度枚举
 */
@Getter
@AllArgsConstructor
public enum Difficulty {

    EASY("EASY", "初级"),
    MEDIUM("MEDIUM", "中级"),
    HARD("HARD", "高级");

    private final String value;
    private final String description;
}
