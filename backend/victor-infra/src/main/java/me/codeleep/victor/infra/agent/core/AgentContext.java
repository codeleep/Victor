package me.codeleep.victor.infra.agent.core;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 执行上下文
 * 只承载本次调用的输入与会话标识；Agent 的对话记忆由 AgentScope Memory 管理（独立持久化）
 */
@Data
public class AgentContext {

    /**
     * 业务会话 ID（用户级会话，与 Agent 记忆独立存储）
     */
    private String sessionId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 本次输入（用户指令/候选人回答等）
     */
    private String input;

    /**
     * 变量映射（可用于 prompt 模板替换）
     */
    private Map<String, Object> variables;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    public AgentContext() {
        this.variables = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    public AgentContext(String sessionId, Long userId, String input) {
        this();
        this.sessionId = sessionId;
        this.userId = userId;
        this.input = input;
    }

    public AgentContext(String sessionId, Long userId) {
        this();
        this.sessionId = sessionId;
        this.userId = userId;
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
}
