package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.VoiceTtsConfig;
import me.codeleep.victor.core.mapper.VoiceTtsConfigMapper;
import me.codeleep.victor.core.service.VoiceTtsConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 语音合成配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTtsConfigServiceImpl implements VoiceTtsConfigService {

    private final VoiceTtsConfigMapper voiceTtsConfigMapper;

    @Override
    @Transactional
    public Long create(VoiceTtsConfig config) {
        log.info("创建TTS配置: name={}, provider={}", config.getName(), config.getProvider());

        config.setUserId(UserContext.getUserId());
        config.setIsEnabled(config.getIsEnabled() != null ? config.getIsEnabled() : true);
        config.setIsDefault(config.getIsDefault() != null ? config.getIsDefault() : false);

        voiceTtsConfigMapper.insert(config);
        log.info("TTS配置创建成功: id={}", config.getId());

        return config.getId();
    }

    @Override
    @Transactional
    public void update(Long id, VoiceTtsConfig config) {
        log.info("更新TTS配置: id={}", id);

        VoiceTtsConfig existing = getById(id);
        checkOwnership(existing);

        config.setId(id);
        config.setUserId(UserContext.getUserId());
        voiceTtsConfigMapper.updateById(config);

        log.info("TTS配置更新成功: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除TTS配置: id={}", id);

        VoiceTtsConfig config = getById(id);
        checkOwnership(config);

        voiceTtsConfigMapper.deleteById(id);
        log.info("TTS配置删除成功: id={}", id);
    }

    @Override
    public VoiceTtsConfig getById(Long id) {
        VoiceTtsConfig config = voiceTtsConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "TTS配置不存在");
        }
        return config;
    }

    @Override
    public List<VoiceTtsConfig> listByCurrentUser() {
        Long userId = UserContext.getUserId();
        return voiceTtsConfigMapper.selectList(
                new LambdaQueryWrapper<VoiceTtsConfig>()
                        .eq(VoiceTtsConfig::getUserId, userId)
                        .orderByDesc(VoiceTtsConfig::getCreatedAt)
        );
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long userId = UserContext.getUserId();

        // 取消当前用户的默认配置
        VoiceTtsConfig updateDefault = new VoiceTtsConfig();
        updateDefault.setIsDefault(false);
        voiceTtsConfigMapper.update(updateDefault,
                new LambdaQueryWrapper<VoiceTtsConfig>()
                        .eq(VoiceTtsConfig::getUserId, userId)
                        .eq(VoiceTtsConfig::getIsDefault, true)
        );

        // 设置新的默认配置
        VoiceTtsConfig config = getById(id);
        checkOwnership(config);
        config.setIsDefault(true);
        voiceTtsConfigMapper.updateById(config);

        log.info("设置默认TTS配置: id={}", id);
    }

    @Override
    public VoiceTtsConfig getDefault() {
        return getDefaultByUserId(UserContext.getUserId());
    }

    @Override
    public VoiceTtsConfig getDefaultByUserId(Long userId) {
        return voiceTtsConfigMapper.selectOne(
                new LambdaQueryWrapper<VoiceTtsConfig>()
                        .eq(VoiceTtsConfig::getUserId, userId)
                        .eq(VoiceTtsConfig::getIsDefault, true)
                        .eq(VoiceTtsConfig::getIsEnabled, true)
        );
    }

    private void checkOwnership(VoiceTtsConfig config) {
        if (!config.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该TTS配置");
        }
    }
}
