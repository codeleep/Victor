package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.dto.AgentLlmConfigRequest;
import me.codeleep.victor.core.dto.AgentLlmConfigVO;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.infra.agent.core.LlmDefinition;
import me.codeleep.victor.infra.agent.llm.ModelWrapperFactory;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.service.AgentLlmConfigService;
import me.codeleep.victor.core.service.converter.AgentLlmConfigConverter;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent LLM配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLlmConfigServiceImpl implements AgentLlmConfigService {

    private final AgentLlmConfigMapper agentLlmConfigMapper;
    private final ModelWrapperFactory modelWrapperFactory;
    private final AgentLlmConfigConverter llmConfigConverter;

    @Override
    @Transactional
    public AgentLlmConfigVO create(AgentLlmConfigRequest request) {
        log.info("创建LLM配置: name={}, provider={}", request.getName(), request.getProvider());

        AgentLlmConfig config = llmConfigConverter.toEntity(request);
        config.setUserId(UserContext.getUserId());
        config.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        config.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);

        agentLlmConfigMapper.insert(config);
        log.info("LLM配置创建成功: id={}", config.getId());

        return llmConfigConverter.toVO(config);
    }

    @Override
    @Transactional
    public AgentLlmConfigVO update(Long id, AgentLlmConfigRequest request) {
        log.info("更新LLM配置: id={}", id);

        AgentLlmConfig config = getById(id);
        checkOwnership(config);

        llmConfigConverter.updateEntity(request, config);
        if (request.getIsEnabled() != null) {
            config.setIsEnabled(request.getIsEnabled());
        }
        if (request.getIsDefault() != null) {
            config.setIsDefault(request.getIsDefault());
        }

        agentLlmConfigMapper.updateById(config);
        log.info("LLM配置更新成功: id={}", id);

        return llmConfigConverter.toVO(config);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除LLM配置: id={}", id);

        AgentLlmConfig config = getById(id);
        checkOwnership(config);

        agentLlmConfigMapper.deleteById(id);
        log.info("LLM配置删除成功: id={}", id);
    }

    @Override
    public AgentLlmConfig getById(Long id) {
        AgentLlmConfig config = agentLlmConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "LLM配置不存在");
        }
        return config;
    }

    @Override
    public AgentLlmConfigVO getVOById(Long id) {
        AgentLlmConfig config = getById(id);
        checkOwnership(config);
        return llmConfigConverter.toVO(config);
    }

    @Override
    public List<AgentLlmConfigVO> listByCurrentUser() {
        Long userId = UserContext.getUserId();

        List<AgentLlmConfig> configs = agentLlmConfigMapper.selectList(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .orderByDesc(AgentLlmConfig::getCreatedAt)
        );

        return llmConfigConverter.toVOList(configs);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long userId = UserContext.getUserId();

        // 取消当前用户的默认配置
        AgentLlmConfig updateDefault = new AgentLlmConfig();
        updateDefault.setIsDefault(false);
        agentLlmConfigMapper.update(updateDefault,
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .eq(AgentLlmConfig::getIsDefault, true)
        );

        // 设置新的默认配置
        AgentLlmConfig config = getById(id);
        checkOwnership(config);
        config.setIsDefault(true);
        agentLlmConfigMapper.updateById(config);

        log.info("设置默认LLM配置: id={}", id);
    }

    @Override
    public AgentLlmConfig getDefault() {
        Long userId = UserContext.getUserId();
        return agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .eq(AgentLlmConfig::getIsEnabled, true)
        );
    }

    @Override
    public void testConnection(Long id) {
        AgentLlmConfig config = getById(id);
        checkOwnership(config);

        log.info("测试LLM配置连接: id={}, provider={}", id, config.getProvider());
        try {
            String apiKey = config.getAuthParams() != null
                    ? (String) config.getAuthParams().getOrDefault("apiKey", "") : "";
            LlmDefinition llm = LlmDefinition.builder()
                    .protocol(config.getProtocol())
                    .baseUrl(config.getApiEndpoint())
                    .apiKey(apiKey)
                    .modelName(config.getModelName())
                    .temperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7)
                    .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 4096)
                    .build();
            String reply = modelWrapperFactory.generate(llm, "Hi");
            if (reply == null || reply.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "LLM 返回为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM配置连接测试失败: id={}, error={}", id, e.getMessage());
            String detail = buildErrorMessage(e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "连接测试失败: " + detail);
        }
    }

    private String buildErrorMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(msg);
            }
            cause = cause.getCause();
            if (sb.length() > 500) break;
        }
        return sb.isEmpty() ? e.getClass().getSimpleName() : sb.toString();
    }

    private void checkOwnership(AgentLlmConfig config) {
        if (!config.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该LLM配置");
        }
    }

}
