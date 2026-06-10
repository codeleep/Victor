package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * TTS配置Mapper
 */
@Mapper
public interface VoiceTtsConfigMapper extends BaseMapper<VoiceTtsConfig> {
}
