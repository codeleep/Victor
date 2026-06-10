package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.common.enums.TeamExecutionMode;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模块初始化器基类，提供创建 Agent、Team 和加载 prompt 的公共方法
 */
@Slf4j
public abstract class BaseInitializer {

    protected final AgentMapper agentMapper;
    protected final AgentTeamMapper agentTeamMapper;

    protected BaseInitializer(AgentMapper agentMapper, AgentTeamMapper agentTeamMapper) {
        this.agentMapper = agentMapper;
        this.agentTeamMapper = agentTeamMapper;
    }

    /**
     * 确保 Agent 存在，不存在则创建
     * @return [Agent, 是否新创建]
     */
    protected Object[] ensureAgent(Long userId, String key, String name, String role,
                                    String systemPrompt, Long llmConfigId,
                                    AgentType type, List<String> tools) {
        Agent existing = agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .eq(Agent::getKey, key)
                        .last("LIMIT 1"));
        if (existing != null) {
            return new Object[]{existing, false};
        }

        Agent agent = new Agent();
        agent.setUserId(userId);
        agent.setKey(key);
        agent.setName(name);
        agent.setRole(role);
        agent.setSystemPrompt(systemPrompt);
        agent.setLlmConfigId(llmConfigId);
        agent.setAvailableTools(tools);
        agent.setType(type);
        agent.setIsSystem(true);
        agentMapper.insert(agent);
        log.info("[AgentInit] 创建Agent: key={}, id={}", key, agent.getId());
        return new Object[]{agent, true};
    }

    /**
     * 确保 Agent Team 存在，不存在则创建
     * @return [AgentTeam, 是否新创建]
     */
    protected Object[] ensureTeam(Long userId, String key, String name, String description,
                                   TeamExecutionMode mode, Long mainAgentId, List<TeamMemberInfo> members) {
        AgentTeam existing = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, userId)
                        .eq(AgentTeam::getKey, key)
                        .last("LIMIT 1"));
        if (existing != null) {
            return new Object[]{existing, false};
        }

        AgentTeam team = new AgentTeam();
        team.setUserId(userId);
        team.setKey(key);
        team.setName(name);
        team.setDescription(description);
        team.setExecutionMode(mode);
        team.setMainAgentId(mainAgentId);
        team.setIsSystem(true);
        team.setMembers(members);
        agentTeamMapper.insert(team);
        log.info("[AgentInit] 创建团队: key={}, id={}", key, team.getId());
        return new Object[]{team, true};
    }

    /**
     * 从 classpath 加载 prompt 文件
     */
    protected String loadPrompt(String key) {
        String path = "prompts/" + key + ".md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.warn("[AgentInit] 加载prompt文件失败: {}，使用默认prompt", path, e);
            return "你是一个专业的AI助手。";
        }
    }
}
