package me.codeleep.victor.core.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import me.codeleep.victor.common.enums.InterviewMode;
import me.codeleep.victor.common.enums.RecallStrategy;

import java.util.List;
import java.util.Map;

/**
 * 面试配置创建请求
 */
@Data
public class InterviewConfigRequest {

    /**
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    private String name;

    /**
     * 面试模式
     */
    @NotNull(message = "面试模式不能为空")
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
    private List<Map<String, Object>> rounds;

    /**
     * 难度配置
     */
    private Map<String, Object> difficultyConfig;

    /**
     * 时长（分钟）
     */
    private Integer durationMinutes;

    /**
     * 提示开关
     */
    private Boolean hintEnabled = false;

    /**
     * 团队配置，团队 key 列表
     */
    private List<String> teamConfig;

    /**
     * Agent模型映射
     */
    private Map<String, Object> agentModelMapping;

    /**
     * 召回策略
     */
    private RecallStrategy recallStrategy;

    /**
     * 召回数量上限
     */
    private Integer maxRecallCount = 50;

    /**
     * 召回列表工作区
     */
    private List<Map<String, Object>> recallItems;
}
