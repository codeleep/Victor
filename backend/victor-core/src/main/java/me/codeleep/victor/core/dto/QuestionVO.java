package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.QuestionSource;
import me.codeleep.victor.common.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目VO
 */
@Data
public class QuestionVO {

    private Long id;

    /**
     * 题目标题
     */
    private String title;

    /**
     * 题目描述
     */
    private String description;

    /**
     * 类型
     */
    private QuestionType type;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 难度
     */
    private Difficulty difficulty;

    /**
     * 参考答案
     */
    private String referenceAnswer;

    /**
     * 来源文档ID
     */
    private Long sourceDocumentId;

    /**
     * 来源
     */
    private QuestionSource source;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
