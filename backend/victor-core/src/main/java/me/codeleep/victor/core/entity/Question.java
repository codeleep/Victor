package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.QuestionType;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.QuestionSource;
import me.codeleep.victor.common.enums.SourceType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 题目实体
 */
@Data
@TableName(value = "res_question", autoResultMap = true)
public class Question {

    @TableId(type = IdType.AUTO)
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
    @TableField(typeHandler = JsonbTypeHandler.class)
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
     * 来源
     */
    private QuestionSource source;

    /**
     * 采集状态
     */
    private IngestStatus ingestStatus;

    /**
     * 来源类型
     */
    private SourceType sourceType;

    /**
     * 开放API Key ID
     */
    private Long sourceApiKeyId;

    /**
     * 来源URL
     */
    private String sourceUri;

    /**
     * 外部ID
     */
    private String externalId;

    /**
     * 原始导入数据
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    /**
     * 导入错误
     */
    private String importError;

    /**
     * 用户ID
     */
    private Long userId;

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
