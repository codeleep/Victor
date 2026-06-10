package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 说话人枚举
 */
@Getter
@AllArgsConstructor
public enum Speaker {

    AI("AI", "AI"),
    USER("USER", "用户"),
    CANDIDATE("CANDIDATE", "候选人"),
    INTERVIEWER("INTERVIEWER", "面试官");

    private final String value;
    private final String description;
}
