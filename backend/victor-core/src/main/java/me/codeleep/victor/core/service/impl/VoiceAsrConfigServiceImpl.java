package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.VoiceAsrConfig;
import me.codeleep.victor.core.mapper.VoiceAsrConfigMapper;
import me.codeleep.victor.core.service.VoiceAsrConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 语音识别配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceAsrConfigServiceImpl implements VoiceAsrConfigService {

    private final VoiceAsrConfigMapper voiceAsrConfigMapper;

    @Override
    @Transactional
    public Long create(VoiceAsrConfig config) {
        log.info("创建ASR配置: name={}, provider={}", config.getName(), config.getProvider());

        config.setUserId(UserContext.getUserId());
        config.setIsEnabled(config.getIsEnabled() != null ? config.getIsEnabled() : true);
        config.setIsDefault(config.getIsDefault() != null ? config.getIsDefault() : false);

        voiceAsrConfigMapper.insert(config);
        log.info("ASR配置创建成功: id={}", config.getId());

        return config.getId();
    }

    @Override
    @Transactional
    public void update(Long id, VoiceAsrConfig config) {
        log.info("更新ASR配置: id={}", id);

        VoiceAsrConfig existing = getById(id);
        checkOwnership(existing);

        config.setId(id);
        config.setUserId(UserContext.getUserId());
        voiceAsrConfigMapper.updateById(config);

        log.info("ASR配置更新成功: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除ASR配置: id={}", id);

        VoiceAsrConfig config = getById(id);
        checkOwnership(config);

        voiceAsrConfigMapper.deleteById(id);
        log.info("ASR配置删除成功: id={}", id);
    }

    @Override
    public VoiceAsrConfig getById(Long id) {
        VoiceAsrConfig config = voiceAsrConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "ASR配置不存在");
        }
        return config;
    }

    @Override
    public List<VoiceAsrConfig> listByCurrentUser() {
        Long userId = UserContext.getUserId();
        return voiceAsrConfigMapper.selectList(
                new LambdaQueryWrapper<VoiceAsrConfig>()
                        .eq(VoiceAsrConfig::getUserId, userId)
                        .orderByDesc(VoiceAsrConfig::getCreatedAt)
        );
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long userId = UserContext.getUserId();

        // 取消当前用户的默认配置
        VoiceAsrConfig updateDefault = new VoiceAsrConfig();
        updateDefault.setIsDefault(false);
        voiceAsrConfigMapper.update(updateDefault,
                new LambdaQueryWrapper<VoiceAsrConfig>()
                        .eq(VoiceAsrConfig::getUserId, userId)
                        .eq(VoiceAsrConfig::getIsDefault, true)
        );

        // 设置新的默认配置
        VoiceAsrConfig config = getById(id);
        checkOwnership(config);
        config.setIsDefault(true);
        voiceAsrConfigMapper.updateById(config);

        log.info("设置默认ASR配置: id={}", id);
    }

    @Override
    public VoiceAsrConfig getDefault() {
        return getDefaultByUserId(UserContext.getUserId());
    }

    @Override
    public VoiceAsrConfig getDefaultByUserId(Long userId) {
        return voiceAsrConfigMapper.selectOne(
                new LambdaQueryWrapper<VoiceAsrConfig>()
                        .eq(VoiceAsrConfig::getUserId, userId)
                        .eq(VoiceAsrConfig::getIsDefault, true)
                        .eq(VoiceAsrConfig::getIsEnabled, true)
        );
    }

    private void checkOwnership(VoiceAsrConfig config) {
        if (!config.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该ASR配置");
        }
    }
}
