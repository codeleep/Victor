package me.codeleep.victor.infra.agent.core;

import lombok.Data;

import java.util.*;

/**
 * Agent 执行结果
 */
@Data
public class AgentResult {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 工具调用列表
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具执行结果
     */
    private List<ToolResult> toolResults;

    /**
     * 结束原因
     */
    private String finishReason;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * Handoff 目标 Agent（如果不为空，表示需要切换 Agent）
     */
    private String handoffTarget;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    public AgentResult() {
        this.toolCalls = new ArrayList<>();
        this.toolResults = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.success = true;
    }

    public AgentResult(String content) {
        this();
        this.content = content;
    }

    public static AgentResult success(String content) {
        AgentResult result = new AgentResult();
        result.setContent(content);
        result.setSuccess(true);
        return result;
    }

    public static AgentResult error(String errorMessage) {
        AgentResult result = new AgentResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static AgentResult handoff(String targetAgent) {
        AgentResult result = new AgentResult();
        result.setSuccess(true);
        result.setHandoffTarget(targetAgent);
        return result;
    }

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 是否为 Handoff 结果
     */
    public boolean isHandoff() {
        return handoffTarget != null && !handoffTarget.isEmpty();
    }

    /**
     * 工具调用信息
     */
    @Data
    public static class ToolCall {
        private String id;
        private String name;
        private String type;
        private Map<String, Object> arguments;

        public ToolCall() {
            this.arguments = new HashMap<>();
            this.type = "function";
        }

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.type = "function";
            this.arguments = arguments != null ? arguments : new HashMap<>();
        }
    }

    /**
     * 工具执行结果
     */
    @Data
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private Object result;
        private boolean success;
        private String error;

        public ToolResult(String toolCallId, String toolName, Object result) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.result = result;
            this.success = true;
        }

        public ToolResult(String toolCallId, String toolName, String error, boolean failed) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.error = error;
            this.success = false;
        }
    }
}
