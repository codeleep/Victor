package me.codeleep.victor.infra.agent.core;

import lombok.Data;

import java.util.*;

/**
 * Agent 执行上下文
 * 包含会话信息、对话历史、变量等
 */
@Data
public class AgentContext {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 对话历史
     */
    private List<ChatMessage> conversationHistory;

    /**
     * 变量映射（可用于 prompt 模板替换）
     */
    private Map<String, Object> variables;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    public AgentContext() {
        this.conversationHistory = new ArrayList<>();
        this.variables = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    public AgentContext(String sessionId, Long userId) {
        this();
        this.sessionId = sessionId;
        this.userId = userId;
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        conversationHistory.add(new ChatMessage("user", content));
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        conversationHistory.add(new ChatMessage("assistant", content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        conversationHistory.add(new ChatMessage("system", content));
    }

    /**
     * 添加工具结果消息
     */
    public void addToolMessage(String toolCallId, String toolName, String content) {
        conversationHistory.add(new ChatMessage("tool", content, toolCallId, toolName));
    }

    /**
     * 设置变量
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * 获取变量
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * 聊天消息
     */
    @Data
    public static class ChatMessage {
        private String role;
        private String content;
        private String toolCallId;
        private String toolName;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public ChatMessage(String role, String content, String toolCallId, String toolName) {
            this.role = role;
            this.content = content;
            this.toolCallId = toolCallId;
            this.toolName = toolName;
        }

        public boolean isToolMessage() {
            return "tool".equals(role);
        }
    }
}
