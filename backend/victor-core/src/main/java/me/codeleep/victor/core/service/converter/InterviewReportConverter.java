package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.entity.InterviewReport;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class InterviewReportConverter {

    public abstract InterviewReportVO toVO(InterviewReport report);

    public abstract List<InterviewReportVO> toVOList(List<InterviewReport> reports);
}
