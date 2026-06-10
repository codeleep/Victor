package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.core.dto.JobVO;
import me.codeleep.victor.core.entity.Job;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class JobConverter {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "ingestStatus", ignore = true)
    @Mapping(target = "sourceType", ignore = true)
    @Mapping(target = "sourceApiKeyId", ignore = true)
    @Mapping(target = "sourceUri", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "rawPayload", ignore = true)
    @Mapping(target = "importError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Job toEntity(JobRequest request);

    public abstract JobVO toVO(Job job);

    public abstract List<JobVO> toVOList(List<Job> jobs);
}
