package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Interview config and runtime status.
 */
@Getter
@AllArgsConstructor
public enum InterviewConfigStatus {

    DRAFT("DRAFT", "Draft"),
    GENERATING("GENERATING", "Generating questions"),
    GENERATE_FAILED("GENERATE_FAILED", "Question generation failed"),
    READY("READY", "Questions ready"),
    IN_PROGRESS("IN_PROGRESS", "Interview in progress"),
    PAUSED("PAUSED", "Interview paused"),
    COMPLETED("COMPLETED", "Interview completed"),
    REPORT_GENERATING("REPORT_GENERATING", "Report generating"),
    REPORT_COMPLETED("REPORT_COMPLETED", "Report completed"),
    REPORT_FAILED("REPORT_FAILED", "Report generation failed"),
    ABANDONED("ABANDONED", "Interview abandoned"),
    ARCHIVED("ARCHIVED", "Archived");

    private final String value;
    private final String description;
}
