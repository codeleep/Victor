package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试模式枚举
 */
@Getter
@AllArgsConstructor
public enum InterviewMode {

    VOICE("VOICE", "语音"),
    TEXT("TEXT", "文字");

    private final String value;
    private final String description;
}
