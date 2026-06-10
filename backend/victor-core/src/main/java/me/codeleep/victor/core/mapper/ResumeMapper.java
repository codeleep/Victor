package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历Mapper
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
