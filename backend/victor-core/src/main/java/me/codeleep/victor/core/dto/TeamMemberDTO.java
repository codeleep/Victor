package me.codeleep.victor.core.dto;

import lombok.Data;

/**
 * 团队成员DTO
 */
@Data
public class TeamMemberDTO {

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 团队内角色
     */
    private String role;

    /**
     * 执行优先级
     */
    private Integer priority;
}
