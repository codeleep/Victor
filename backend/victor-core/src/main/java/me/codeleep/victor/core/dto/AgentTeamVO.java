package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.TeamExecutionMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent团队视图对象
 */
@Data
public class AgentTeamVO {

    /**
     * ID
     */
    private Long id;

    /**
     * 团队唯一标识键
     */
    private String key;

    /**
     * 团队名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 主Agent ID
     */
    private Long mainAgentId;

    /**
     * 主Agent名称
     */
    private String mainAgentName;

    /**
     * 成员列表
     */
    private List<TeamMemberInfo> members;

    /**
     * 执行模式
     */
    private TeamExecutionMode executionMode;

    /**
     * 是否系统团队
     */
    private Boolean isSystem;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
