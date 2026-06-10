package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * ASR配置Mapper
 */
@Mapper
public interface VoiceAsrConfigMapper extends BaseMapper<VoiceAsrConfig> {
}
