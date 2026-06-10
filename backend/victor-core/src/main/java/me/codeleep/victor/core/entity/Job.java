package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.SourceType;
import me.codeleep.victor.core.handler.JsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 岗位实体
 */
@Data
@TableName(value = "res_job", autoResultMap = true)
public class Job {

    @TableId(type = IdType.AUTO)
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
    @TableField(typeHandler = JsonbTypeHandler.class)
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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> domains;

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
