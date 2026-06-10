package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewMode;
import me.codeleep.victor.common.enums.RecallStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试配置实体
 */
@Data
@TableName(value = "interview_config", autoResultMap = true)
public class InterviewConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 配置名称
     */
    private String name;

    /**
     * 面试模式
     */
    private InterviewMode mode;

    /**
     * 岗位ID
     */
    private Long jobId;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 轮次配置
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> rounds;

    /**
     * 难度配置
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> difficultyConfig;

    /**
     * 时长（分钟）
     */
    private Integer durationMinutes;

    /**
     * 提示开关
     */
    private Boolean hintEnabled;

    /**
     * 团队配置，存储团队 key 列表：["system-team-question", "system-team-interview", "system-team-evaluation"]
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> teamConfig;

    /**
     * Agent模型映射
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> agentModelMapping;

    /**
     * 召回策略
     */
    private RecallStrategy recallStrategy;

    /**
     * 召回数量上限
     */
    private Integer maxRecallCount;

    /**
     * 召回列表工作区
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> recallItems;

    /**
     * 状态
     */
    private InterviewConfigStatus status;

    /**
     * 题目生成失败原因
     */
    private String generateError;

    /**
     * Current interview question id.
     */
    private Long currentQuestionId;

    /**
     * Interview started time.
     */
    private LocalDateTime startedAt;

    /**
     * Interview paused time.
     */
    private LocalDateTime pausedAt;

    /**
     * Interview completed time.
     */
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
