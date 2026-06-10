package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目类型枚举
 */
@Getter
@AllArgsConstructor
public enum QuestionType {

    TECHNICAL("TECHNICAL", "技术题"),
    BEHAVIORAL("BEHAVIORAL", "行为题"),
    SHORT_ANSWER("SHORT_ANSWER", "简答题"),
    MULTIPLE_CHOICE("MULTIPLE_CHOICE", "选择题"),
    CODING("CODING", "编程题");

    private final String value;
    private final String description;
}
