package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.QuestionType;

/**
 * 题目查询请求DTO
 */
@Data
public class QuestionQueryRequest {

    /**
     * 类型
     */
    private QuestionType type;

    /**
     * 难度
     */
    private Difficulty difficulty;

    /**
     * 标签
     */
    private String tag;

    /**
     * 页码
     */
    private Integer page = 0;

    /**
     * 每页大小
     */
    private Integer size = 10;
}
