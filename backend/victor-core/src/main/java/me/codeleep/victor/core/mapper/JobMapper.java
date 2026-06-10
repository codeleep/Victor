package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.Job;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职位Mapper
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {
}
