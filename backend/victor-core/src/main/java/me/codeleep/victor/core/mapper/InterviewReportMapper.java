package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.InterviewReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试报告Mapper
 */
@Mapper
public interface InterviewReportMapper extends BaseMapper<InterviewReport> {
}
