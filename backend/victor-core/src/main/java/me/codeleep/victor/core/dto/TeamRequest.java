package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import me.codeleep.victor.common.enums.TeamExecutionMode;

import java.util.List;

/**
 * Agent团队请求
 */
@Data
public class TeamRequest {

    /**
     * 团队名称
     */
    @NotBlank(message = "团队名称不能为空")
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
     * 成员列表
     */
    private List<TeamMemberDTO> members;

    /**
     * 执行模式
     */
    private TeamExecutionMode executionMode;
}
