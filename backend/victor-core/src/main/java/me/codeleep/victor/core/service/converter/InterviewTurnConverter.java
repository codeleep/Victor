package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class InterviewTurnConverter {

    @Mapping(source = "speaker", target = "speaker")
    public abstract InterviewTurnVO toVO(InterviewTurn turn);

    public abstract List<InterviewTurnVO> toVOList(List<InterviewTurn> turns);

    protected String mapSpeaker(Speaker speaker) {
        return speaker != null ? speaker.name() : null;
    }
}
