package me.codeleep.victor.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队成员信息（存储在 AgentTeam.members JSON 中）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberInfo {

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * Agent唯一标识键
     */
    private String agentKey;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 团队内角色
     */
    private String role;

    /**
     * 执行优先级
     */
    private Integer priority;
}
