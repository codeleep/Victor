package me.codeleep.victor.infra.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 将业务层 {@link AgentMemoryRepository} 桥接为 AgentScope {@link io.agentscope.core.state.AgentStateStore}
 * <p>
 * AgentScope 的 ReActAgent 通过 AgentStateStore 持久化/恢复 AgentState。
 * 本适配器把 State 序列化为 JSON 字符串，委托给业务层仓库存储，
 * 从而让 Agent 记忆独立持久化，且业务层无需感知 AgentScope 类型。
 */
@Slf4j
public class RepositoryAgentStateStore implements io.agentscope.core.state.AgentStateStore {

    private static final String DEFAULT_KEY = "agent_state";

    private final AgentMemoryRepository repository;
    private final ObjectMapper objectMapper;

    public RepositoryAgentStateStore(AgentMemoryRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        try {
            String json = serialize(state);
            repository.save(userId, sessionId, normalizeKey(key), json);
        } catch (Exception e) {
            log.error("保存 AgentState 失败: userId={}, sessionId={}, key={}", userId, sessionId, key, e);
        }
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        save(userId, sessionId, key, list.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> clazz) {
        Optional<String> json = repository.get(userId, sessionId, normalizeKey(key));
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(json.get(), clazz));
        } catch (Exception e) {
            log.error("加载 AgentState 失败: userId={}, sessionId={}, key={}", userId, sessionId, key, e);
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> clazz) {
        return get(userId, sessionId, key, clazz)
                .map(s -> (List<T>) new ArrayList<>(List.of(s)))
                .orElseGet(List::of);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return repository.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        repository.delete(userId, sessionId);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return Set.copyOf(repository.listSessionIds(userId));
    }

    // ==================== 内部方法 ====================

    private String normalizeKey(String key) {
        return key != null ? key : DEFAULT_KEY;
    }

    private String serialize(State state) throws Exception {
        if (state instanceof AgentState as) {
            return as.toJson();
        }
        return objectMapper.writeValueAsString(state);
    }

    @SuppressWarnings("unchecked")
    private <T extends State> T deserialize(String json, Class<T> clazz) throws Exception {
        if (AgentState.class.isAssignableFrom(clazz)) {
            return (T) AgentState.fromJsonString(json);
        }
        return objectMapper.readValue(json, clazz);
    }
}
