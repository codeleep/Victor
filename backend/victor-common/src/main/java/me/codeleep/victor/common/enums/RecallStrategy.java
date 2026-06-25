package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 召回策略枚举
 */
@Getter
@AllArgsConstructor
public enum RecallStrategy {

    VECTOR("VECTOR", "向量检索"),
    KEYWORD("KEYWORD", "关键词检索"),
    HYBRID("HYBRID", "混合检索"),
    AI("AI", "AI智能召回");

    private final String value;
    private final String description;
}
