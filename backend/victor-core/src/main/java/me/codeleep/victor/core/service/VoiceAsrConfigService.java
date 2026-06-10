package me.codeleep.victor.core.service;

import me.codeleep.victor.core.entity.VoiceAsrConfig;

import java.util.List;

/**
 * 语音识别配置服务接口
 */
public interface VoiceAsrConfigService {

    /**
     * 创建配置
     */
    Long create(VoiceAsrConfig config);

    /**
     * 更新配置
     */
    void update(Long id, VoiceAsrConfig config);

    /**
     * 删除配置
     */
    void delete(Long id);

    /**
     * 根据ID获取配置
     */
    VoiceAsrConfig getById(Long id);

    /**
     * 获取当前用户的所有配置
     */
    List<VoiceAsrConfig> listByCurrentUser();

    /**
     * 设置默认配置
     */
    void setDefault(Long id);

    /**
     * 获取默认配置
     */
    VoiceAsrConfig getDefault();

    /**
     * 根据用户ID获取默认配置
     */
    VoiceAsrConfig getDefaultByUserId(Long userId);
}
