package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试报告状态枚举
 */
@Getter
@AllArgsConstructor
public enum InterviewReportStatus {

    PENDING("PENDING", "待评估"),
    EVALUATING("EVALUATING", "评估中"),
    COMPLETED("COMPLETED", "评估完成"),
    FAILED("FAILED", "评估失败");

    private final String value;
    private final String description;
}
