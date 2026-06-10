package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.AgentType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent视图对象
 */
@Data
public class AgentVO {

    /**
     * ID
     */
    private Long id;

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
     * LLM配置名称
     */
    private String llmConfigName;

    /**
     * 可用工具列表
     */
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
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
