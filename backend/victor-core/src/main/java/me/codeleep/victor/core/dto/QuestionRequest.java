package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.QuestionType;

import java.util.List;

/**
 * 题目请求DTO
 */
@Data
public class QuestionRequest {

    /**
     * 题目标题
     */
    @NotBlank(message = "题目标题不能为空")
    private String title;

    /**
     * 题目描述
     */
    private String description;

    /**
     * 类型
     */
    @NotNull(message = "题目类型不能为空")
    private QuestionType type;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 难度
     */
    @NotNull(message = "题目难度不能为空")
    private Difficulty difficulty;

    /**
     * 参考答案
     */
    private String referenceAnswer;

    /**
     * 来源文档ID
     */
    private Long sourceDocumentId;
}
