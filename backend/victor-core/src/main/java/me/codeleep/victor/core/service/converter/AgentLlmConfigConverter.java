package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.AgentLlmConfigRequest;
import me.codeleep.victor.core.dto.AgentLlmConfigVO;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AgentLlmConfigConverter {

    public abstract AgentLlmConfigVO toVO(AgentLlmConfig config);

    public abstract List<AgentLlmConfigVO> toVOList(List<AgentLlmConfig> configs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract AgentLlmConfig toEntity(AgentLlmConfigRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(AgentLlmConfigRequest request, @MappingTarget AgentLlmConfig config);
}
