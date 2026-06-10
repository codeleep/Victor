package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent团队执行模式枚举
 */
@Getter
@AllArgsConstructor
public enum TeamExecutionMode {

    PARALLEL("PARALLEL", "并行"),
    SEQUENTIAL("SEQUENTIAL", "串行");

    private final String value;
    private final String description;
}
