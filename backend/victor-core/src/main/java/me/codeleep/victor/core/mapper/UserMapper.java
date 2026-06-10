package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
