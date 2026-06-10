package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.TeamMemberInfoListHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.TeamExecutionMode;

import me.codeleep.victor.core.dto.TeamMemberInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent团队实体
 */
@Data
@TableName(value = "agent_team", autoResultMap = true)
public class AgentTeam {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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
     * 成员配置
     */
    @TableField(typeHandler = TeamMemberInfoListHandler.class)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
