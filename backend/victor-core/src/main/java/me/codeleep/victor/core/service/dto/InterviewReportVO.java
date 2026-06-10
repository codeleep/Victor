package me.codeleep.victor.core.service.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.InterviewReportStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试报告视图对象
 */
@Data
public class InterviewReportVO {

    private Long id;

    private Long sessionId;

    private Long userId;

    private InterviewReportStatus status;

    private BigDecimal overallScore;

    private Map<String, Object> dimensionScores;

    private List<Map<String, Object>> perQuestionEvaluation;

    private String summary;

    private String strengths;

    private String weaknesses;

    private String suggestions;

    private String evaluationError;

    private Integer evaluationRetryCount;

    private LocalDateTime generatedAt;

    private LocalDateTime createdAt;
}
