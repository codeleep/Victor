package me.codeleep.victor.core.service;

import java.util.Map;

/**
 * 系统初始化服务
 */
public interface SystemInitService {

    /**
     * 检查当前用户是否已完成系统初始化
     */
    boolean isInitialized();

    /**
     * 为当前用户初始化系统 Agent、Agent Team 和默认 LLM 配置
     * 幂等操作：已初始化的用户会直接跳过
     */
    Map<String, Object> initSystemAgents();
}
