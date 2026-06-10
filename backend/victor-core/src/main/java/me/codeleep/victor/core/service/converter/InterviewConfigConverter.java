package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class InterviewConfigConverter {

    @Mapping(target = "jobName", ignore = true)
    @Mapping(target = "resumeName", ignore = true)
    @Mapping(target = "teamConfig", ignore = true)
    public abstract InterviewConfigVO toVO(InterviewConfig config);

    public abstract List<InterviewConfigVO> toVOList(List<InterviewConfig> configs);

    public abstract InterviewConfig toEntity(InterviewConfigRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentQuestionId", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "pausedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "generateError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(InterviewConfigRequest request, @MappingTarget InterviewConfig config);
}
