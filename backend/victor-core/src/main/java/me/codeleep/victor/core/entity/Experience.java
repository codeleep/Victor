package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.ExperienceType;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.SourceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 经历实体
 */
@Data
@TableName(value = "res_experience", autoResultMap = true)
public class Experience {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 经历类型
     */
    private ExperienceType type;

    /**
     * 标题
     */
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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> skills;

    /**
     * 附件列表
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> attachments;

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
