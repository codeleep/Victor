package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.InterviewReportStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试报告实体
 */
@Data
@TableName(value = "interview_report", autoResultMap = true)
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态
     */
    private InterviewReportStatus status;

    /**
     * 总分
     */
    private BigDecimal overallScore;

    /**
     * 维度评分
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> dimensionScores;

    /**
     * 逐题评估
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> perQuestionEvaluation;

    /**
     * 综合总结
     */
    private String summary;

    /**
     * 优势
     */
    private String strengths;

    /**
     * 不足
     */
    private String weaknesses;

    /**
     * 建议
     */
    private String suggestions;

    /**
     * 评估失败原因
     */
    private String evaluationError;

    /**
     * 重试次数
     */
    private Integer evaluationRetryCount;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
