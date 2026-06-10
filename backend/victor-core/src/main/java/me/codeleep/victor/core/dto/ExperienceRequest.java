package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import me.codeleep.victor.common.enums.ExperienceType;

import java.time.LocalDate;
import java.util.List;

/**
 * 经历请求DTO
 */
@Data
public class ExperienceRequest {

    /**
     * 经历类型
     */
    @NotNull(message = "经历类型不能为空")
    private ExperienceType type;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
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
}
