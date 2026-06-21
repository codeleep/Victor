package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;

import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.core.dto.AgentVO;
import me.codeleep.victor.core.dto.ToolVO;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.service.AgentService;
import me.codeleep.victor.core.service.converter.AgentConverter;
import me.codeleep.victor.core.engine.AgentDefinitionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentLlmConfigMapper agentLlmConfigMapper;
    private final AgentConverter agentConverter;
    private final AgentDefinitionFactory agentDefinitionFactory;

    @Override
    @Transactional
    public AgentVO create(AgentRequest request) {
        log.info("创建Agent: name={}, type={}", request.getName(), request.getType());

        // 验证LLM配置
        if (request.getLlmConfigId() != null) {
            AgentLlmConfig llmConfig = agentLlmConfigMapper.selectById(request.getLlmConfigId());
            if (llmConfig == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "LLM配置不存在");
            }
        }

        Agent agent = agentConverter.toEntity(request);
        agent.setUserId(UserContext.getUserId());
        agent.setKey(generateKey());
        agent.setType(request.getType() != null ? request.getType() : AgentType.INTERVIEW);
        agent.setIsSystem(false);

        agentMapper.insert(agent);
        log.info("Agent创建成功: id={}, key={}", agent.getId(), agent.getKey());

        return enrichAgentVO(agentConverter.toVO(agent));
    }

    @Override
    @Transactional
    public AgentVO update(Long id, AgentRequest request) {
        log.info("更新Agent: id={}", id);

        Agent agent = getById(id);
        checkOwnership(agent);

        if (request.getLlmConfigId() != null) {
            AgentLlmConfig llmConfig = agentLlmConfigMapper.selectById(request.getLlmConfigId());
            if (llmConfig == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "LLM配置不存在");
            }
        }

        if (Boolean.TRUE.equals(agent.getIsSystem())) {
            // 系统Agent只允许修改提示词和LLM配置
            if (request.getSystemPrompt() != null) {
                agent.setSystemPrompt(request.getSystemPrompt());
            }
            if (request.getLlmConfigId() != null) {
                agent.setLlmConfigId(request.getLlmConfigId());
            }
            if (request.getAvailableTools() != null) {
                agent.setAvailableTools(request.getAvailableTools());
            }
        } else {
            agentConverter.updateEntity(request, agent);
        }

        agentMapper.updateById(agent);
        log.info("Agent更新成功: id={}", id);

        return enrichAgentVO(agentConverter.toVO(agent));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除Agent: id={}", id);

        Agent agent = getById(id);
        checkOwnership(agent);

        if (Boolean.TRUE.equals(agent.getIsSystem())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统Agent不可删除");
        }

        agentMapper.deleteById(id);
        log.info("Agent删除成功: id={}", id);
    }

    @Override
    public Agent getById(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    @Override
    public AgentVO getVOById(Long id) {
        Agent agent = getById(id);
        checkOwnership(agent);
        return enrichAgentVO(agentConverter.toVO(agent));
    }

    @Override
    public Agent getByKey(String key) {
        Long userId = UserContext.getUserId();
        Agent agent = agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .eq(Agent::getKey, key)
        );
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    @Override
    public List<AgentVO> listByCurrentUser() {
        Long userId = UserContext.getUserId();

        List<Agent> agents = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .orderByDesc(Agent::getCreatedAt)
        );

        return agents.stream().map(a -> enrichAgentVO(agentConverter.toVO(a))).toList();
    }

    @Override
    public List<AgentVO> listByType(String type) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUserId, userId)
                .orderByDesc(Agent::getCreatedAt);

        if (type != null && !type.isEmpty()) {
            try {
                AgentType agentType = AgentType.valueOf(type);
                wrapper.eq(Agent::getType, agentType);
            } catch (IllegalArgumentException e) {
                log.warn("无效的Agent类型: {}", type);
            }
        }

        List<Agent> agents = agentMapper.selectList(wrapper);

        return agents.stream().map(a -> enrichAgentVO(agentConverter.toVO(a))).toList();
    }

    private String generateKey() {
        return "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void checkOwnership(Agent agent) {
        if (!agent.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该Agent");
        }
    }

    private AgentVO enrichAgentVO(AgentVO vo) {
        if (vo.getLlmConfigId() != null) {
            AgentLlmConfig llmConfig = agentLlmConfigMapper.selectById(vo.getLlmConfigId());
            if (llmConfig != null) {
                vo.setLlmConfigName(llmConfig.getName());
            }
        }
        return vo;
    }

    @Override
    public List<ToolVO> listAvailableTools() {
        return agentDefinitionFactory.getRegisteredTools().values().stream()
                .map(tool -> {
                    for (java.lang.reflect.Method method : tool.getClass().getMethods()) {
                        io.agentscope.core.tool.Tool annotation = method.getAnnotation(io.agentscope.core.tool.Tool.class);
                        if (annotation != null) {
                            return new ToolVO(annotation.name(), annotation.description());
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ToolVO::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

}
