package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 经历类型枚举
 */
@Getter
@AllArgsConstructor
public enum ExperienceType {

    PROJECT("PROJECT", "项目经历"),
    WORK("WORK", "工作经历"),
    EDUCATION("EDUCATION", "教育经历"),
    OTHER("OTHER", "其他经历");

    private final String value;
    private final String description;
}
