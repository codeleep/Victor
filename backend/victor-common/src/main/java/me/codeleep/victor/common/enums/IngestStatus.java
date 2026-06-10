package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采集状态枚举
 */
@Getter
@AllArgsConstructor
public enum IngestStatus {

    ACTIVE("ACTIVE", "有效"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REJECTED("REJECTED", "已拒绝"),
    FAILED("FAILED", "导入失败");

    private final String value;
    private final String description;
}
