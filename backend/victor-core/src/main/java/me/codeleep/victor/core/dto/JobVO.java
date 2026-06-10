package me.codeleep.victor.core.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位VO
 */
@Data
public class JobVO {

    private Long id;

    /**
     * 岗位名称
     */
    private String name;

    /**
     * JD原文
     */
    private String description;

    /**
     * 技能要求
     */
    private List<String> requiredSkills;

    /**
     * 经验年限
     */
    private Integer experienceYears;

    /**
     * 学历要求
     */
    private String education;

    /**
     * 薪资范围
     */
    private String salaryRange;

    /**
     * 领域列表
     */
    private List<String> domains;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
