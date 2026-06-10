package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.OpenApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开放API Key Mapper
 */
@Mapper
public interface OpenApiKeyMapper extends BaseMapper<OpenApiKey> {
}
