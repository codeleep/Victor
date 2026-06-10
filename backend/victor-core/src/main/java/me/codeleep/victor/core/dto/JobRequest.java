package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 岗位请求DTO
 */
@Data
public class JobRequest {

    /**
     * 岗位名称
     */
    @NotBlank(message = "岗位名称不能为空")
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
}
