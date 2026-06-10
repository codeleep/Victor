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
 * 评估模块初始化器
 * 负责创建评估团队的所有 Agent 和 Team
 */
@Slf4j
@Component
@Order(4)
public class EvaluationModuleInitializer extends BaseInitializer implements ModuleInitializer {

    public static final String KEY_EVALUATION_MAIN = "evaluation-main";
    public static final String KEY_EVAL_LANGUAGE = "eval-language";
    public static final String KEY_EVAL_QUALITY = "eval-quality";
    public static final String KEY_EVAL_TONE = "eval-tone";
    public static final String KEY_EVAL_PACING = "eval-pacing";
    public static final String KEY_TEAM_EVALUATION = "system-team-evaluation";

    private final AgentLlmConfigMapper agentLlmConfigMapper;

    public EvaluationModuleInitializer(AgentMapper agentMapper, AgentTeamMapper agentTeamMapper,
                                        AgentLlmConfigMapper agentLlmConfigMapper) {
        super(agentMapper, agentTeamMapper);
        this.agentLlmConfigMapper = agentLlmConfigMapper;
    }

    @Override
    public Map<String, Object> init(Long userId) {
        int agentCreated = 0;
        int teamCreated = 0;
        Long llmConfigId = getDefaultLlmConfigId(userId);

        // 1. 创建评估相关 Agents
        Object[] r1 = ensureAgent(userId, KEY_EVALUATION_MAIN, "评估主Agent",
                "负责汇总各维度评估结果并生成最终面试评估报告",
                loadPrompt(KEY_EVALUATION_MAIN), llmConfigId, AgentType.EVALUATION, List.of());
        Agent mainAgent = (Agent) r1[0];
        if ((boolean) r1[1]) agentCreated++;

        Object[] r2 = ensureAgent(userId, KEY_EVAL_LANGUAGE, "语言组织评估Agent",
                "负责评估候选人回答的语言表达和组织能力",
                loadPrompt(KEY_EVAL_LANGUAGE), llmConfigId, AgentType.EVALUATION, List.of());
        Agent langAgent = (Agent) r2[0];
        if ((boolean) r2[1]) agentCreated++;

        Object[] r3 = ensureAgent(userId, KEY_EVAL_QUALITY, "答案质量评估Agent",
                "负责评估候选人回答的内容质量和专业深度",
                loadPrompt(KEY_EVAL_QUALITY), llmConfigId, AgentType.EVALUATION, List.of());
        Agent qualityAgent = (Agent) r3[0];
        if ((boolean) r3[1]) agentCreated++;

        Object[] r4 = ensureAgent(userId, KEY_EVAL_TONE, "语气气势评估Agent",
                "负责评估候选人回答中的自信度和表达力",
                loadPrompt(KEY_EVAL_TONE), llmConfigId, AgentType.EVALUATION, List.of());
        Agent toneAgent = (Agent) r4[0];
        if ((boolean) r4[1]) agentCreated++;

        Object[] r5 = ensureAgent(userId, KEY_EVAL_PACING, "节奏把控评估Agent",
                "负责评估候选人回答的节奏和时间管理能力",
                loadPrompt(KEY_EVAL_PACING), llmConfigId, AgentType.EVALUATION, List.of());
        Agent pacingAgent = (Agent) r5[0];
        if ((boolean) r5[1]) agentCreated++;

        // 2. 创建评估团队
        Object[] rt = ensureTeam(userId, KEY_TEAM_EVALUATION, "面试分析团队",
                "负责多维度评估面试表现并生成分析报告",
                TeamExecutionMode.PARALLEL, mainAgent.getId(),
                List.of(
                        new TeamMemberInfo(mainAgent.getId(), mainAgent.getKey(), mainAgent.getName(), "evaluator", 1),
                        new TeamMemberInfo(langAgent.getId(), langAgent.getKey(), langAgent.getName(), "eval-language", 2),
                        new TeamMemberInfo(qualityAgent.getId(), qualityAgent.getKey(), qualityAgent.getName(), "eval-quality", 3),
                        new TeamMemberInfo(toneAgent.getId(), toneAgent.getKey(), toneAgent.getName(), "eval-tone", 4),
                        new TeamMemberInfo(pacingAgent.getId(), pacingAgent.getKey(), pacingAgent.getName(), "eval-pacing", 5)
                ));
        if ((boolean) rt[1]) teamCreated++;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluationAgentCreated", agentCreated);
        result.put("evaluationTeamCreated", teamCreated);
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
