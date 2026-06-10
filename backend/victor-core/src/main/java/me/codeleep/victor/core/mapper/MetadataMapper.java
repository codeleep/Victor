package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.Metadata;
import org.apache.ibatis.annotations.Mapper;

/**
 * 元数据Mapper
 */
@Mapper
public interface MetadataMapper extends BaseMapper<Metadata> {
}
