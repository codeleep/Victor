package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型类型枚举
 */
@Getter
@AllArgsConstructor
public enum ModelType {

    INFERENCE("INFERENCE", "推理模型"),
    EMBEDDING("EMBEDDING", "嵌入模型");

    private final String value;
    private final String description;
}
