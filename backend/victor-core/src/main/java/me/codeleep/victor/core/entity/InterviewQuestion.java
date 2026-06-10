package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.InterviewQuestionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试题目实体
 */
@Data
@TableName(value = "interview_question", autoResultMap = true)
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置ID
     */
    private Long configId;

    /**
     * 题目顺序
     */
    private Integer orderIndex;

    /**
     * 题目类型
     */
    private InterviewQuestionType questionType;

    /**
     * 题干
     */
    private String questionText;

    /**
     * 期望答案/要点/考察意图
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> answerHint;

    /**
     * 生成依据的召回引用
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> sourceRecallRefs;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
