package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import me.codeleep.victor.common.enums.AgentType;

import java.util.List;

/**
 * Agent请求
 */
@Data
public class AgentRequest {

    /**
     * Agent名称
     */
    @NotBlank(message = "Agent名称不能为空")
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
    private List<String> availableTools;

    /**
     * 类型
     */
    private AgentType type;
}
