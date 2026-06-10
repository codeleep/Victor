package me.codeleep.victor.infra.agent.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 团队运行时定义 - 不可变配置
 * 对应数据库中的 AgentTeam 实体，由工厂转换而来
 * 与 AgentDefinition 平级，解耦 infra 对 core 实体的依赖
 */
@Data
@Builder(toBuilder = true)
public class AgentTeamDefinition {

    /**
     * 团队标识
     */
    private String key;

    /**
     * 团队名称
     */
    private String name;

    /**
     * 执行模式：SEQUENTIAL / PARALLEL
     */
    private String executionMode;

    /**
     * 主 Agent 定义
     */
    private AgentDefinition mainAgent;

    /**
     * 子 Agent 定义列表（不含主 Agent）
     */
    @Builder.Default
    private List<SubAgentEntry> subAgents = List.of();

    /**
     * 子 Agent 条目
     */
    @Data
    @Builder
    public static class SubAgentEntry {
        /**
         * Agent 定义
         */
        private AgentDefinition agentDefinition;

        /**
         * 成员角色
         */
        private String role;

        /**
         * 成员 key（用于生成工具名）
         */
        private String agentKey;

        /**
         * 成员名称（用于生成工具描述）
         */
        private String agentName;
    }
}
