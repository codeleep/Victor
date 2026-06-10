package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历状态枚举
 */
@Getter
@AllArgsConstructor
public enum ResumeStatus {

    PENDING("PENDING", "待解析"),
    PARSED("PARSED", "已解析"),
    EMBEDDED("EMBEDDED", "已嵌入");

    private final String value;
    private final String description;
}
