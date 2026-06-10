package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.core.handler.JsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent实体
 */
@Data
@TableName(value = "agent", autoResultMap = true)
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Agent唯一标识键
     */
    private String key;

    /**
     * Agent名称
     */
    private String name;

    /**
     * 角色
     */
    private String role;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * LLM配置ID
     */
    private Long llmConfigId;

    /**
     * 可用工具列表
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> availableTools;

    /**
     * 类型
     */
    private AgentType type;

    /**
     * 是否系统Agent
     */
    private Boolean isSystem;

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
