package me.codeleep.victor.core.service.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.InterviewConfigStatus;

import java.time.LocalDateTime;

/**
 * 面试会话视图对象
 */
@Data
public class InterviewSessionVO {

    private Long id;

    private Long configId;

    private String configName;

    private InterviewConfigStatus status;

    private Long currentQuestionId;

    private LocalDateTime startedAt;

    private LocalDateTime pausedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
