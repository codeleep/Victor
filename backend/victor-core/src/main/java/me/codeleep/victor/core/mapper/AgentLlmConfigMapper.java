package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent LLM配置Mapper
 */
@Mapper
public interface AgentLlmConfigMapper extends BaseMapper<AgentLlmConfig> {
}
