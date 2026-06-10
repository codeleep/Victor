package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.ExperienceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 经历VO
 */
@Data
public class ExperienceVO {

    private Long id;

    /**
     * 经历类型
     */
    private ExperienceType type;

    /**
     * 标题
     */
    private String title;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 描述
     */
    private String description;

    /**
     * 技能列表
     */
    private List<String> skills;

    /**
     * 附件列表
     */
    private List<String> attachments;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
