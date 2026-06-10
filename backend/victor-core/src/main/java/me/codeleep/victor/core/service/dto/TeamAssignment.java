package me.codeleep.victor.core.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队分配项，用于面试配置中指定不同角色使用的团队
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamAssignment {

    /**
     * 角色标识：question / interview / evaluation
     */
    private String role;

    /**
     * 团队 ID
     */
    private Long teamId;

    /**
     * 团队名称（仅在 VO 中填充，不持久化）
     */
    private String teamName;
}
