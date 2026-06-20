package me.codeleep.victor.infra.agent.core;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import me.codeleep.victor.common.enums.TeamExecutionMode;

import java.util.List;

/**
 * Agent 团队运行时定义 - 不可变配置
 * 主 Agent 通过 SubAgentTool 把子 Agent 编排为可调用工具
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
     * 执行模式：主 Agent 可通过 ReAct 决定调用子 Agent；
     * PARALLEL 允许并行调用多个子 Agent，SEQUENTIAL 由主 Agent 串行决策
     */
    @Builder.Default
    private TeamExecutionMode executionMode = TeamExecutionMode.SEQUENTIAL;

    /**
     * 主 Agent 定义
     */
    private AgentDefinition mainAgent;

    /**
     * 子 Agent 定义列表（不含主 Agent）
     */
    @Singular
    private List<SubAgentEntry> subAgents;

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
