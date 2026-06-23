package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.AgentMemoryEntity;
import me.codeleep.victor.core.mapper.AgentMemoryMapper;
import me.codeleep.victor.infra.agent.memory.AgentMemoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AgentMemoryRepository 的 MyBatis 实现
 * 将 AgentScope AgentState 的 JSON 持久化到 agent_memory 表
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MyBatisAgentMemoryRepository implements AgentMemoryRepository {

    private final AgentMemoryMapper agentMemoryMapper;

    @Override
    public void save(String userId, String sessionId, String key, String stateJson) {
        AgentMemoryEntity existing = findEntity(userId, sessionId, key);
        if (existing != null) {
            existing.setStateJson(stateJson);
            agentMemoryMapper.updateById(existing);
        } else {
            AgentMemoryEntity entity = new AgentMemoryEntity();
            entity.setUserId(userId);
            entity.setSessionId(sessionId);
            entity.setStateKey(key);
            entity.setStateJson(stateJson);
            agentMemoryMapper.insert(entity);
        }
    }

    @Override
    public Optional<String> get(String userId, String sessionId, String key) {
        AgentMemoryEntity entity = findEntity(userId, sessionId, key);
        return entity != null ? Optional.ofNullable(entity.getStateJson()) : Optional.empty();
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        Long count = agentMemoryMapper.selectCount(
                new LambdaQueryWrapper<AgentMemoryEntity>()
                        .eq(AgentMemoryEntity::getUserId, userId)
                        .eq(AgentMemoryEntity::getSessionId, sessionId)
        );
        return count != null && count > 0;
    }

    @Override
    public void delete(String userId, String sessionId) {
        agentMemoryMapper.delete(
                new LambdaUpdateWrapper<AgentMemoryEntity>()
                        .eq(AgentMemoryEntity::getUserId, userId)
                        .eq(AgentMemoryEntity::getSessionId, sessionId)
        );
    }

    @Override
    public List<String> listSessionIds(String userId) {
        return agentMemoryMapper.selectList(
                        new LambdaQueryWrapper<AgentMemoryEntity>()
                                .eq(AgentMemoryEntity::getUserId, userId)
                                .select(AgentMemoryEntity::getSessionId)
                ).stream()
                .map(AgentMemoryEntity::getSessionId)
                .distinct()
                .collect(Collectors.toList());
    }

    private AgentMemoryEntity findEntity(String userId, String sessionId, String key) {
        return agentMemoryMapper.selectOne(
                new LambdaQueryWrapper<AgentMemoryEntity>()
                        .eq(AgentMemoryEntity::getUserId, userId)
                        .eq(AgentMemoryEntity::getSessionId, sessionId)
                        .eq(AgentMemoryEntity::getStateKey, key)
                        .last("LIMIT 1")
        );
    }
}
