package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.AgentTeamVO;
import me.codeleep.victor.core.dto.TeamRequest;
import me.codeleep.victor.core.entity.AgentTeam;

import java.util.List;

/**
 * Agent团队服务接口
 */
public interface AgentTeamService {

    /**
     * 创建团队
     *
     * @param request 请求
     * @return 团队视图对象
     */
    AgentTeamVO create(TeamRequest request);

    /**
     * 更新团队
     *
     * @param id ID
     * @param request 请求
     * @return 团队视图对象
     */
    AgentTeamVO update(Long id, TeamRequest request);

    /**
     * 删除团队
     *
     * @param id ID
     */
    void delete(Long id);

    /**
     * 根据ID获取团队
     *
     * @param id ID
     * @return 团队实体
     */
    AgentTeam getById(Long id);

    /**
     * 根据ID获取团队VO
     *
     * @param id ID
     * @return 团队视图对象
     */
    AgentTeamVO getVOById(Long id);

    /**
     * 根据Key获取团队
     *
     * @param key Key
     * @return 团队实体
     */
    AgentTeam getByKey(String key);

    /**
     * 获取当前用户的所有团队
     *
     * @return 团队列表
     */
    List<AgentTeamVO> listByCurrentUser();
}
