package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.AgentLlmConfigRequest;
import me.codeleep.victor.core.dto.AgentLlmConfigVO;
import me.codeleep.victor.core.entity.AgentLlmConfig;

import java.util.List;

/**
 * Agent LLM配置服务接口
 */
public interface AgentLlmConfigService {

    /**
     * 创建LLM配置
     */
    AgentLlmConfigVO create(AgentLlmConfigRequest request);

    /**
     * 更新LLM配置
     */
    AgentLlmConfigVO update(Long id, AgentLlmConfigRequest request);

    /**
     * 删除LLM配置
     */
    void delete(Long id);

    /**
     * 根据ID获取LLM配置
     */
    AgentLlmConfig getById(Long id);

    /**
     * 根据ID获取LLM配置VO
     */
    AgentLlmConfigVO getVOById(Long id);

    /**
     * 获取当前用户的所有LLM配置
     */
    List<AgentLlmConfigVO> listByCurrentUser();

    /**
     * 设置默认配置
     */
    void setDefault(Long id);

    /**
     * 获取默认配置
     */
    AgentLlmConfig getDefault();

    /**
     * 测试连接，失败时抛出异常
     */
    void testConnection(Long id);
}
