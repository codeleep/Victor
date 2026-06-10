package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.ResumeStatus;
import me.codeleep.victor.common.enums.SourceType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 简历实体
 */
@Data
@TableName(value = "res_resume", autoResultMap = true)
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 简历名称
     */
    private String name;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 简历原文
     */
    private String rawText;

    /**
     * 解析后的结构化内容
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> parsedContent;

    /**
     * 简历摘要
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> summary;

    /**
     * 状态
     */
    private ResumeStatus status;

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
     * 嵌入时间
     */
    private LocalDateTime embeddedAt;

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
