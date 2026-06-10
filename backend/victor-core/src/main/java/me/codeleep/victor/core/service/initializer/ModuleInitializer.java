package me.codeleep.victor.core.service.initializer;

import java.util.Map;

/**
 * 模块初始化器接口
 * 每个需要初始化的模块实现此接口
 */
public interface ModuleInitializer {

    /**
     * 初始化模块数据
     *
     * @param userId 用户ID
     * @return 初始化结果（如 created 数量等）
     */
    Map<String, Object> init(Long userId);
}
