package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.core.dto.AgentVO;
import me.codeleep.victor.core.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AgentConverter {

    @Mapping(target = "llmConfigName", ignore = true)
    public abstract AgentVO toVO(Agent agent);

    public abstract List<AgentVO> toVOList(List<Agent> agents);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Agent toEntity(AgentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(AgentRequest request, @MappingTarget Agent agent);
}
