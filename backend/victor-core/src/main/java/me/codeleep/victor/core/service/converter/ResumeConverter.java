package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.entity.Resume;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ResumeConverter {

    public abstract ResumeVO toVO(Resume resume);

    public abstract List<ResumeVO> toVOList(List<Resume> resumes);
}
