package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试题目Mapper
 */
@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
