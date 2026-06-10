package me.codeleep.victor.core.service;

import me.codeleep.victor.core.entity.VoiceTtsConfig;

import java.util.List;

/**
 * 语音合成配置服务接口
 */
public interface VoiceTtsConfigService {

    /**
     * 创建配置
     */
    Long create(VoiceTtsConfig config);

    /**
     * 更新配置
     */
    void update(Long id, VoiceTtsConfig config);

    /**
     * 删除配置
     */
    void delete(Long id);

    /**
     * 根据ID获取配置
     */
    VoiceTtsConfig getById(Long id);

    /**
     * 获取当前用户的所有配置
     */
    List<VoiceTtsConfig> listByCurrentUser();

    /**
     * 设置默认配置
     */
    void setDefault(Long id);

    /**
     * 获取默认配置
     */
    VoiceTtsConfig getDefault();

    /**
     * 根据用户ID获取默认配置
     */
    VoiceTtsConfig getDefaultByUserId(Long userId);
}
