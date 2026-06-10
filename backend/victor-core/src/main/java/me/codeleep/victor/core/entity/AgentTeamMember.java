package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent团队成员关联实体
 */
@Data
@TableName("agent_team_member")
public class AgentTeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 团队ID
     */
    private Long teamId;

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

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
