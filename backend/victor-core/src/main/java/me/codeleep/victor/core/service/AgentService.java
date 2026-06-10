package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.core.dto.AgentVO;
import me.codeleep.victor.core.entity.Agent;

import java.util.List;

/**
 * Agent服务接口
 */
public interface AgentService {

    /**
     * 创建Agent
     *
     * @param request 请求
     * @return Agent视图对象
     */
    AgentVO create(AgentRequest request);

    /**
     * 更新Agent
     *
     * @param id ID
     * @param request 请求
     * @return Agent视图对象
     */
    AgentVO update(Long id, AgentRequest request);

    /**
     * 删除Agent
     *
     * @param id ID
     */
    void delete(Long id);

    /**
     * 根据ID获取Agent
     *
     * @param id ID
     * @return Agent实体
     */
    Agent getById(Long id);

    /**
     * 根据ID获取Agent VO
     *
     * @param id ID
     * @return Agent视图对象
     */
    AgentVO getVOById(Long id);

    /**
     * 根据Key获取Agent
     *
     * @param key Key
     * @return Agent实体
     */
    Agent getByKey(String key);

    /**
     * 获取当前用户的所有Agent
     *
     * @return Agent列表
     */
    List<AgentVO> listByCurrentUser();

    /**
     * 根据类型获取当前用户的Agent列表
     *
     * @param type 类型
     * @return Agent列表
     */
    List<AgentVO> listByType(String type);
}
