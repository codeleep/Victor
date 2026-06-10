package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试题目类型枚举
 */
@Getter
@AllArgsConstructor
public enum InterviewQuestionType {

    BANK("BANK", "题库题"),
    GENERATED("GENERATED", "AI生成题");

    private final String value;
    private final String description;
}
