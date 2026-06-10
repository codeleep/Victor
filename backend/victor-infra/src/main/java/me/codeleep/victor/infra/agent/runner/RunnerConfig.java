package me.codeleep.victor.infra.agent.runner;

import lombok.Builder;
import lombok.Data;

/**
 * Runner 配置
 */
@Data
@Builder
public class RunnerConfig {

    /**
     * 最大轮次（防止无限循环）
     */
    @Builder.Default
    private int maxTurns = 10;

    /**
     * 是否启用追踪
     */
    @Builder.Default
    private boolean tracingEnabled = true;

    /**
     * 默认 Runner 配置
     */
    public static RunnerConfig defaults() {
        return RunnerConfig.builder().build();
    }
}
