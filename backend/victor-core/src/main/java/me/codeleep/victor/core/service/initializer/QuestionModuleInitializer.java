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
 * 出题模块初始化器
 * 负责创建出题团队的所有 Agent 和 Team
 */
@Slf4j
@Component
@Order(2)
public class QuestionModuleInitializer extends BaseInitializer implements ModuleInitializer {

    public static final String KEY_QUESTION_MAIN = "question-main";
    public static final String KEY_QUESTION_GENERATOR = "question-generator";
    public static final String KEY_QUESTION_REVIEWER = "question-reviewer";
    public static final String KEY_QUESTION_SCORER_A = "question-scorer-a";
    public static final String KEY_QUESTION_SCORER_B = "question-scorer-b";
    public static final String KEY_TEAM_QUESTION = "system-team-question";

    private final AgentLlmConfigMapper agentLlmConfigMapper;

    public QuestionModuleInitializer(AgentMapper agentMapper, AgentTeamMapper agentTeamMapper,
                                      AgentLlmConfigMapper agentLlmConfigMapper) {
        super(agentMapper, agentTeamMapper);
        this.agentLlmConfigMapper = agentLlmConfigMapper;
    }

    @Override
    public Map<String, Object> init(Long userId) {
        int agentCreated = 0;
        int teamCreated = 0;
        Long llmConfigId = getDefaultLlmConfigId(userId);

        // 1. 创建出题相关 Agents
        Object[] r1 = ensureAgent(userId, KEY_QUESTION_MAIN, "出题主Agent",
                "负责编排和协调整个出题流程",
                loadPrompt(KEY_QUESTION_MAIN), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent mainAgent = (Agent) r1[0];
        if ((boolean) r1[1]) agentCreated++;

        Object[] r2 = ensureAgent(userId, KEY_QUESTION_GENERATOR, "出题Agent",
                "负责根据岗位要求和候选人简历生成面试题目",
                loadPrompt(KEY_QUESTION_GENERATOR), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent generator = (Agent) r2[0];
        if ((boolean) r2[1]) agentCreated++;

        Object[] r3 = ensureAgent(userId, KEY_QUESTION_REVIEWER, "题目审核Agent",
                "负责审核面试题目的质量",
                loadPrompt(KEY_QUESTION_REVIEWER), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent reviewer = (Agent) r3[0];
        if ((boolean) r3[1]) agentCreated++;

        Object[] r4 = ensureAgent(userId, KEY_QUESTION_SCORER_A, "评分AgentA",
                "从岗位匹配度维度对面试题目进行评分",
                loadPrompt(KEY_QUESTION_SCORER_A), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent scorerA = (Agent) r4[0];
        if ((boolean) r4[1]) agentCreated++;

        Object[] r5 = ensureAgent(userId, KEY_QUESTION_SCORER_B, "评分AgentB",
                "从区分度和实用性维度对面试题目进行评分",
                loadPrompt(KEY_QUESTION_SCORER_B), llmConfigId, AgentType.INTERVIEW, List.of());
        Agent scorerB = (Agent) r5[0];
        if ((boolean) r5[1]) agentCreated++;

        // 2. 创建出题团队
        Object[] rt = ensureTeam(userId, KEY_TEAM_QUESTION, "出题团队",
                "负责根据岗位要求和候选人简历生成高质量面试题目",
                TeamExecutionMode.SEQUENTIAL, mainAgent.getId(),
                List.of(
                        new TeamMemberInfo(mainAgent.getId(), mainAgent.getKey(), mainAgent.getName(), "main", 1),
                        new TeamMemberInfo(generator.getId(), generator.getKey(), generator.getName(), "generator", 2),
                        new TeamMemberInfo(reviewer.getId(), reviewer.getKey(), reviewer.getName(), "reviewer", 3),
                        new TeamMemberInfo(scorerA.getId(), scorerA.getKey(), scorerA.getName(), "scorer-a", 4),
                        new TeamMemberInfo(scorerB.getId(), scorerB.getKey(), scorerB.getName(), "scorer-b", 5)
                ));
        if ((boolean) rt[1]) teamCreated++;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionAgentCreated", agentCreated);
        result.put("questionTeamCreated", teamCreated);
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
