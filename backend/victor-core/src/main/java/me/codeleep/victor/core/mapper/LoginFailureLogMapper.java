package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.LoginFailureLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录失败日志Mapper
 */
@Mapper
public interface LoginFailureLogMapper extends BaseMapper<LoginFailureLog> {
}
