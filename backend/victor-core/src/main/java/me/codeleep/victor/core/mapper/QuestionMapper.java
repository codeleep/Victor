package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目Mapper
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
