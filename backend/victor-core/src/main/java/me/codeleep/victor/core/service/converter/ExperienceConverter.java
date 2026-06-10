package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.ExperienceRequest;
import me.codeleep.victor.core.dto.ExperienceVO;
import me.codeleep.victor.core.entity.Experience;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ExperienceConverter {

    public abstract ExperienceVO toVO(Experience experience);

    public abstract List<ExperienceVO> toVOList(List<Experience> experiences);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "ingestStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Experience toEntity(ExperienceRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "ingestStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(ExperienceRequest request, @MappingTarget Experience experience);
}
