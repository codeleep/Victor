package me.codeleep.victor.infra.agent.memory;

import java.util.List;
import java.util.Optional;

/**
 * Agent 记忆持久化仓库接口 - 由业务层实现
 * <p>
 * Agent 的对话记忆（AgentScope AgentState 的 JSON 序列化形式）独立存储，
 * 与用户级业务会话分开。Runner 在 run 结束时触发写回，冷启动时触发恢复。
 * 业务层只需实现基于字符串的存取，无需感知 AgentScope 类型。
 */
public interface AgentMemoryRepository {

    /**
     * 保存某个 Agent 会话的状态（JSON 字符串）
     *
     * @param userId      用户 ID
     * @param sessionId   Agent 会话 ID（agent 维度，非业务会话）
     * @param key         状态键（AgentScope 内部使用，如 memory slot）
     * @param stateJson   AgentState 序列化后的 JSON
     */
    void save(String userId, String sessionId, String key, String stateJson);

    /**
     * 加载某个 Agent 会话的状态
     *
     * @return 状态 JSON，不存在则空
     */
    Optional<String> get(String userId, String sessionId, String key);

    /**
     * 该 Agent 会话是否存在已持久化状态（用于冷启动恢复判断）
     */
    boolean exists(String userId, String sessionId);

    /**
     * 删除某个 Agent 会话的全部状态
     */
    void delete(String userId, String sessionId);

    /**
     * 列出某用户下所有 Agent 会话 ID
     */
    List<String> listSessionIds(String userId);
}
