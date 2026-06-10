package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.AgentTeamVO;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.mapper.AgentMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AgentTeamConverter {

    @Autowired
    protected AgentMapper agentMapper;

    @Mapping(target = "mainAgentName", ignore = true)
    public abstract AgentTeamVO toVO(AgentTeam team);

    public abstract List<AgentTeamVO> toVOList(List<AgentTeam> teams);

    @AfterMapping
    protected void fillMainAgentName(AgentTeam team, @MappingTarget AgentTeamVO vo) {
        if (team.getMainAgentId() != null) {
            Agent agent = agentMapper.selectById(team.getMainAgentId());
            if (agent != null) {
                vo.setMainAgentName(agent.getName());
            }
        }
    }
}
