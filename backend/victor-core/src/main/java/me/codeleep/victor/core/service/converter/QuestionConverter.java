package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.core.dto.QuestionVO;
import me.codeleep.victor.core.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class QuestionConverter {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "ingestStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Question toEntity(QuestionRequest request);

    public abstract QuestionVO toVO(Question question);

    public abstract List<QuestionVO> toVOList(List<Question> questions);
}
