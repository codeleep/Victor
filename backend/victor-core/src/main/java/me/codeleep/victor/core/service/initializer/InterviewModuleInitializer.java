package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.common.enums.TeamExecutionMode;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试模块初始化器
 * 负责创建面试团队的所有 Agent 和 Team
 */
@Slf4j
@Component
@Order(3)
public class InterviewModuleInitializer extends BaseInitializer implements ModuleInitializer {

    public static final String KEY_INTERVIEWER_MAIN = "interviewer-main";
    public static final String KEY_INTERVIEWER_ASSISTANT = "interviewer-assistant";
    public static final String KEY_TEAM_INTERVIEW = "system-team-interview";

    /** 面试官推进下一题工具名(对应 AdvanceQuestionTool 的 @Tool name) */
    public static final String TOOL_ADVANCE_QUESTION = "advance_to_next_question";
    /** 资料查询工具名(对应 ResourceQueryTool 的 @Tool name)，供面试官查询候选人岗位/简历/经历 */
    public static final String TOOL_RESOURCE_QUERY = "resource_query";

    private final AgentLlmConfigMapper agentLlmConfigMapper;

    public InterviewModuleInitializer(AgentMapper agentMapper, AgentTeamMapper agentTeamMapper,
                                       AgentLlmConfigMapper agentLlmConfigMapper) {
        super(agentMapper, agentTeamMapper);
        this.agentLlmConfigMapper = agentLlmConfigMapper;
    }

    @Override
    public Map<String, Object> init(Long userId) {
        int agentCreated = 0;
        int teamCreated = 0;
        Long llmConfigId = getDefaultLlmConfigId(userId);

        // 1. 创建面试相关 Agents
        Object[] r1 = ensureAgent(userId, KEY_INTERVIEWER_MAIN, "主面试官Agent",
                "负责主导面试流程，与候选人进行对话交互",
                loadPrompt(KEY_INTERVIEWER_MAIN), llmConfigId, AgentType.INTERVIEW, List.of(TOOL_ADVANCE_QUESTION, TOOL_RESOURCE_QUERY));
        Agent mainAgent = (Agent) r1[0];
        if ((boolean) r1[1]) agentCreated++;

        Object[] r2 = ensureAgent(userId, KEY_INTERVIEWER_ASSISTANT, "面试辅助Agent",
                "负责分析候选人表现并提供决策建议",
                loadPrompt(KEY_INTERVIEWER_ASSISTANT), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent assistant = (Agent) r2[0];
        if ((boolean) r2[1]) agentCreated++;

        // 2. 创建面试团队
        Object[] rt = ensureTeam(userId, KEY_TEAM_INTERVIEW, "面试团队",
                "负责面试提问、追问和互动",
                TeamExecutionMode.SEQUENTIAL, mainAgent.getId(),
                List.of(
                        new TeamMemberInfo(mainAgent.getId(), mainAgent.getKey(), mainAgent.getName(), "interviewer", 1),
                        new TeamMemberInfo(assistant.getId(), assistant.getKey(), assistant.getName(), "assistant", 2)
                ));
        if ((boolean) rt[1]) teamCreated++;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interviewAgentCreated", agentCreated);
        result.put("interviewTeamCreated", teamCreated);
        return result;
    }

    private Long getDefaultLlmConfigId(Long userId) {
        AgentLlmConfig config = agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .last("LIMIT 1"));
        return config != null ? config.getId() : null;
    }
}
