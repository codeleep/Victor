package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.UserExtConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户扩展配置Mapper
 */
@Mapper
public interface UserExtConfigMapper extends BaseMapper<UserExtConfig> {
}
