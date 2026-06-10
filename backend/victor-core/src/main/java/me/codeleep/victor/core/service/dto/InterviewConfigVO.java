package me.codeleep.victor.core.service.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewMode;
import me.codeleep.victor.common.enums.RecallStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试配置视图对象
 */
@Data
public class InterviewConfigVO {

    private Long id;

    private String name;

    private InterviewMode mode;

    private Long jobId;

    private String jobName;

    private Long resumeId;

    private String resumeName;

    private List<Map<String, Object>> rounds;

    private Map<String, Object> difficultyConfig;

    private Integer durationMinutes;

    private Boolean hintEnabled;

    /**
     * 团队配置，包含团队 key 和名称
     */
    private List<TeamAssignment> teamConfig;

    private Map<String, Object> agentModelMapping;

    private RecallStrategy recallStrategy;

    private Integer maxRecallCount;

    private List<Map<String, Object>> recallItems;

    private InterviewConfigStatus status;

    private String generateError;

    private Long currentQuestionId;

    private LocalDateTime startedAt;

    private LocalDateTime pausedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
