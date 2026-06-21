package me.codeleep.victor.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可用工具视图对象
 * 供前端 Agent 配置界面选择 Agent 可调用的工具
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolVO {

    /**
     * 工具名称（对应 @Tool 注解的 name，与 Agent.availableTools 中的元素一致）
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;
}