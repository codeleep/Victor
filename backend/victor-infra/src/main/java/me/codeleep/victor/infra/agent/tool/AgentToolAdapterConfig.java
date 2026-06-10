package me.codeleep.victor.infra.agent.tool;

import lombok.Builder;
import lombok.Data;
import me.codeleep.victor.infra.agent.core.AgentResult;

import java.util.Map;
import java.util.function.Function;

/**
 * Agent-as-Tool 适配器配置
 * 控制 Agent 如何被包装为 Tool
 */
@Data
@Builder
public class AgentToolAdapterConfig {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具描述（LLM 用于判断何时调用）
     */
    private String toolDescription;

    /**
     * 输入参数 Schema（JSON Schema 格式）
     */
    @Builder.Default
    private Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                    "input", Map.of(
                            "type", "string",
                            "description", "传递给子 Agent 的输入消息"
                    )
            ),
            "required", java.util.List.of("input")
    );

    /**
     * 自定义输出提取器
     * 从 AgentResult 中提取字符串返回给调用方
     * 默认返回 result.getContent()
     */
    @Builder.Default
    private Function<AgentResult, String> outputExtractor = AgentResult::getContent;

    /**
     * 是否为子 Agent 创建新的上下文
     * true: 子 Agent 有独立的对话历史（默认）
     * false: 子 Agent 共享调用方的上下文
     */
    @Builder.Default
    private boolean createNewContext = true;
}
