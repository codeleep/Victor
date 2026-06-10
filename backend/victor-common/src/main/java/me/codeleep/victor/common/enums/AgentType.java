package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent类型枚举
 */
@Getter
@AllArgsConstructor
public enum AgentType {

    INTERVIEW("INTERVIEW", "面试Agent"),
    EVALUATION("EVALUATION", "评估Agent"),
    SEARCH("SEARCH", "检索Agent");

    private final String value;
    private final String description;
}
