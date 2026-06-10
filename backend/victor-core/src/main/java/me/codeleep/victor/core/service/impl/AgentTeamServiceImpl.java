package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.dto.AgentTeamVO;
import me.codeleep.victor.core.dto.TeamMemberDTO;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.dto.TeamRequest;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.entity.AgentTeamMember;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import me.codeleep.victor.core.mapper.AgentTeamMemberMapper;
import me.codeleep.victor.core.service.AgentTeamService;
import me.codeleep.victor.core.service.converter.AgentTeamConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent团队服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTeamServiceImpl implements AgentTeamService {

    private final AgentTeamMapper agentTeamMapper;
    private final AgentTeamMemberMapper agentTeamMemberMapper;
    private final AgentMapper agentMapper;
    private final AgentTeamConverter agentTeamConverter;

    @Override
    @Transactional
    public AgentTeamVO create(TeamRequest request) {
        log.info("创建Agent团队: name={}", request.getName());

        Long userId = UserContext.getUserId();

        // 创建团队
        AgentTeam team = new AgentTeam();
        team.setUserId(userId);
        team.setKey(generateKey());
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setMainAgentId(request.getMainAgentId());
        team.setExecutionMode(request.getExecutionMode());
        team.setIsSystem(false);

        // 处理成员
        team.setMembers(buildMemberInfoList(request.getMembers()));

        agentTeamMapper.insert(team);
        log.info("Agent团队创建成功: id={}, key={}", team.getId(), team.getKey());

        // 创建团队成员关联记录
        createTeamMembers(team.getId(), request.getMembers());

        return enrichVO(agentTeamConverter.toVO(team));
    }

    @Override
    @Transactional
    public AgentTeamVO update(Long id, TeamRequest request) {
        log.info("更新Agent团队: id={}", id);

        AgentTeam team = getById(id);
        checkOwnership(team);

        if (Boolean.TRUE.equals(team.getIsSystem())) {
            // 系统团队只允许修改描述、主Agent和成员
            if (request.getDescription() != null) {
                team.setDescription(request.getDescription());
            }
            if (request.getMainAgentId() != null) {
                team.setMainAgentId(request.getMainAgentId());
            }
            if (request.getMembers() != null) {
                team.setMembers(buildMemberInfoList(request.getMembers()));
            }
        } else {
            team.setName(request.getName());
            team.setDescription(request.getDescription());
            team.setMainAgentId(request.getMainAgentId());
            team.setExecutionMode(request.getExecutionMode());
            team.setMembers(buildMemberInfoList(request.getMembers()));
        }

        agentTeamMapper.updateById(team);

        // 同步团队成员关联记录
        if (request.getMembers() != null) {
            syncTeamMembers(team.getId(), request.getMembers());
        }

        log.info("Agent团队更新成功: id={}", id);
        return enrichVO(agentTeamConverter.toVO(team));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除Agent团队: id={}", id);

        AgentTeam team = getById(id);
        checkOwnership(team);

        // 不能删除系统团队
        if (Boolean.TRUE.equals(team.getIsSystem())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统团队不可删除");
        }

        // 删除团队成员关联记录
        agentTeamMemberMapper.delete(
                new LambdaQueryWrapper<AgentTeamMember>().eq(AgentTeamMember::getTeamId, id)
        );

        // 删除团队
        agentTeamMapper.deleteById(id);
        log.info("Agent团队删除成功: id={}", id);
    }

    @Override
    public AgentTeam getById(Long id) {
        AgentTeam team = agentTeamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(ResultCode.AGENT_TEAM_NOT_FOUND);
        }
        return team;
    }

    @Override
    public AgentTeamVO getVOById(Long id) {
        AgentTeam team = getById(id);
        checkOwnership(team);
        return enrichVO(agentTeamConverter.toVO(team));
    }

    @Override
    public AgentTeam getByKey(String key) {
        Long userId = UserContext.getUserId();
        AgentTeam team = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, userId)
                        .eq(AgentTeam::getKey, key)
        );
        if (team == null) {
            throw new BusinessException(ResultCode.AGENT_TEAM_NOT_FOUND);
        }
        return team;
    }

    @Override
    public List<AgentTeamVO> listByCurrentUser() {
        Long userId = UserContext.getUserId();

        List<AgentTeam> teams = agentTeamMapper.selectList(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, userId)
                        .orderByDesc(AgentTeam::getCreatedAt)
        );

        return agentTeamConverter.toVOList(teams).stream().map(this::enrichVO).toList();
    }

    /**
     * 创建团队成员关联记录
     */
    private void createTeamMembers(Long teamId, List<TeamMemberDTO> members) {
        if (members == null || members.isEmpty()) {
            return;
        }

        for (TeamMemberDTO memberDTO : members) {
            AgentTeamMember member = new AgentTeamMember();
            member.setTeamId(teamId);
            member.setAgentId(memberDTO.getAgentId());
            member.setRole(memberDTO.getRole());
            member.setPriority(memberDTO.getPriority());
            agentTeamMemberMapper.insert(member);
        }
    }

    /**
     * 同步团队成员关联记录
     */
    private void syncTeamMembers(Long teamId, List<TeamMemberDTO> members) {
        // 删除原有成员
        agentTeamMemberMapper.delete(
                new LambdaQueryWrapper<AgentTeamMember>().eq(AgentTeamMember::getTeamId, teamId)
        );

        // 创建新成员
        createTeamMembers(teamId, members);
    }

    private List<TeamMemberInfo> buildMemberInfoList(List<TeamMemberDTO> members) {
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }

        List<TeamMemberInfo> result = new ArrayList<>();
        for (TeamMemberDTO dto : members) {
            Agent agent = agentMapper.selectById(dto.getAgentId());
            if (agent == null) {
                throw new BusinessException(ResultCode.AGENT_NOT_FOUND,
                        "Agent不存在: " + dto.getAgentId());
            }
            result.add(new TeamMemberInfo(dto.getAgentId(), agent.getKey(), agent.getName(),
                    dto.getRole(), dto.getPriority()));
        }
        return result;
    }

    /**
     * 生成唯一Key
     */
    private String generateKey() {
        return "team_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private AgentTeamVO enrichVO(AgentTeamVO vo) {
        if (vo.getMainAgentId() != null) {
            Agent agent = agentMapper.selectById(vo.getMainAgentId());
            if (agent != null) {
                vo.setMainAgentName(agent.getName());
            }
        }
        return vo;
    }

    /**
     * 检查所有权
     */
    private void checkOwnership(AgentTeam team) {
        if (!team.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该团队");
        }
    }

}
