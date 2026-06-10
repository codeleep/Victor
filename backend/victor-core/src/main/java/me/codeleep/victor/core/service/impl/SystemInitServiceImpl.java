package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.entity.UserExtConfig;
import me.codeleep.victor.core.mapper.UserExtConfigMapper;
import me.codeleep.victor.core.service.SystemInitService;
import me.codeleep.victor.core.service.initializer.ModuleInitializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统初始化服务 - 协调各模块初始化器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemInitServiceImpl implements SystemInitService {

    private final UserExtConfigMapper userExtConfigMapper;
    private final List<ModuleInitializer> initializers;

    private static final String EXT_KEY_SYSTEM_INIT = "system_initialized";

    @Override
    public boolean isInitialized() {
        Long userId = UserContext.getUserId();
        return checkExtConfig(userId, EXT_KEY_SYSTEM_INIT);
    }

    @Override
    @Transactional
    public Map<String, Object> initSystemAgents() {
        Long userId = UserContext.getUserId();

        if (checkExtConfig(userId, EXT_KEY_SYSTEM_INIT)) {
            log.info("[SystemInit] 用户 {} 已初始化，跳过", userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("skipped", true);
            return result;
        }

        log.info("[SystemInit] 开始为用户 {} 初始化系统配置，共 {} 个模块", userId, initializers.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skipped", false);

        for (ModuleInitializer initializer : initializers) {
            String moduleName = initializer.getClass().getSimpleName();
            try {
                Map<String, Object> moduleResult = initializer.init(userId);
                result.putAll(moduleResult);
                log.info("[SystemInit] 模块 {} 初始化完成: {}", moduleName, moduleResult);
            } catch (Exception e) {
                log.error("[SystemInit] 模块 {} 初始化失败", moduleName, e);
                throw e;
            }
        }

        setExtConfig(userId, EXT_KEY_SYSTEM_INIT, "true");
        log.info("[SystemInit] 初始化完成: userId={}, result={}", userId, result);
        return result;
    }

    private boolean checkExtConfig(Long userId, String key) {
        UserExtConfig config = userExtConfigMapper.selectOne(
                new LambdaQueryWrapper<UserExtConfig>()
                        .eq(UserExtConfig::getUserId, userId)
                        .eq(UserExtConfig::getConfigKey, key)
                        .last("LIMIT 1"));
        return config != null && "true".equals(config.getConfigValue());
    }

    private void setExtConfig(Long userId, String key, String value) {
        UserExtConfig existing = userExtConfigMapper.selectOne(
                new LambdaQueryWrapper<UserExtConfig>()
                        .eq(UserExtConfig::getUserId, userId)
                        .eq(UserExtConfig::getConfigKey, key)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setConfigValue(value);
            userExtConfigMapper.updateById(existing);
        } else {
            UserExtConfig config = new UserExtConfig();
            config.setUserId(userId);
            config.setConfigKey(key);
            config.setConfigValue(value);
            userExtConfigMapper.insert(config);
        }
    }
}
