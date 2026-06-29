package me.codeleep.victor.core.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.StructuredJsonParser;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.runner.AgentFactory;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import io.agentscope.core.ReActAgent;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.*;
import me.codeleep.victor.core.mapper.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 面试引擎实现
 * 所有 Agent 均从数据库加载，systemPrompt 来自数据库中的 Agent 配置。
 * 任务上下文通过 user message 传递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewEngineImpl implements InterviewEngine {

    private final AgentFactory agentFactory;
    private final AgentRunner agentRunner;
    private final AgentTeamDefinitionFactory teamDefinitionFactory;
    private final InterviewConfigMapper configMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AgentTeamMapper agentTeamMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final ObjectMapper objectMapper;
    private final InterviewContextBuilder contextBuilder;
    private final StructuredJsonParser<EvaluationResult> evaluationParser = new StructuredJsonParser<>(EvaluationResult.class);

    // ==================== 出题（配置级批量生成） ====================

    @Override
    public List<GeneratedQuestion> generateQuestionsForConfig(Long configId) {
        InterviewConfig config = getConfigOrThrow(configId);
        Job job = getJobOrNull(config.getJobId());
        Resume resume = getResumeOrNull(config.getResumeId());
        int questionCount = resolveQuestionCount(config);

        AgentContext context = new AgentContext("config-" + config.getId(), config.getUserId());
        context.setVariable("configId", config.getId());

        // 任务上下文作为 user message，具体出题指令由出题团队的 DB prompt 驱动
        String userMsg = String.format("""
                请为本次面试生成 %d 道正式面试题。

                ## 岗位信息
                %s

                ## 简历信息
                %s

                ## 面试轮次配置
                %s

                ## 召回资料
                %s
                """,
                questionCount,
                contextBuilder.formatJob(job),
                contextBuilder.formatResume(resume),
                config.getRounds() != null ? config.getRounds().toString() : "[]",
                config.getRecallItems() != null ? config.getRecallItems().toString() : "[]");
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "question");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成面试题失败: " + result.getErrorMessage());
        }

        List<GeneratedQuestion> questions = parseGeneratedQuestions(result.getContent());
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成面试题失败: Agent未返回有效题目");
        }
        return normalizeQuestionCount(questions, questionCount);
    }

    // ==================== 出题（会话级单题生成） ====================

    @Override
    public String generateQuestion(Long sessionId) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        Resume resume = getResumeOrNull(config.getResumeId());

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("请为候选人生成一道面试题目。\n岗位: %s\n简历: %s\n历史对话:\n%s",
                contextBuilder.formatJobBrief(job), contextBuilder.formatResumeBrief(resume), getConversationHistory(sessionId));
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成题目失败: " + result.getErrorMessage());
        }
        saveTurn(sessionId, null, Speaker.AI, false, result.getContent());
        return result.getContent();
    }

    @Override
    public Flux<String> streamGenerateQuestion(Long sessionId) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        Resume resume = getResumeOrNull(config.getResumeId());

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("请为候选人生成一道面试题目。\n岗位: %s\n简历: %s\n历史对话:\n%s",
                contextBuilder.formatJobBrief(job), contextBuilder.formatResumeBrief(resume), getConversationHistory(sessionId));
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        StringBuilder contentBuilder = new StringBuilder();
        return agentRunner.streamRun(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context)
                .map(result -> {
                    if (result.getContent() != null) {
                        contentBuilder.append(result.getContent());
                    }
                    return result.getContent() != null ? result.getContent() : "";
                })
                .doOnComplete(() -> {
                    saveTurn(sessionId, null, Speaker.AI, false, contentBuilder.toString());
                });
    }

    // ==================== 评估回答 ====================

    @Override
    public EvaluationResult evaluateAnswer(Long sessionId, String answer) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        String currentQuestion = getCurrentQuestion(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("请评估候选人的回答。\n岗位: %s\n当前题目: %s\n候选人回答: %s",
                job != null ? job.getName() : "通用岗位", currentQuestion, answer);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "evaluation");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "评估回答失败: " + result.getErrorMessage());
        }
        return parseEvaluationResult(result.getContent());
    }

    @Override
    public Flux<String> streamEvaluateAnswer(Long sessionId, String answer) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        String currentQuestion = getCurrentQuestion(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("请评估候选人的回答。\n岗位: %s\n当前题目: %s\n候选人回答: %s",
                job != null ? job.getName() : "通用岗位", currentQuestion, answer);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "evaluation");
        return agentRunner.streamRun(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context)
                .map(result -> result.getContent() != null ? result.getContent() : "");
    }

    // ==================== 追问 ====================

    @Override
    public String generateFollowUp(Long sessionId, String previousAnswer) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        String currentQuestion = getCurrentQuestion(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("候选人对上一个问题的回答不够完整，请生成一个追问。\n岗位: %s\n原问题: %s\n候选人回答: %s",
                job != null ? job.getName() : "通用岗位", currentQuestion, previousAnswer);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成追问失败: " + result.getErrorMessage());
        }
        saveTurn(sessionId, config.getCurrentQuestionId(), Speaker.AI, true, result.getContent());
        return result.getContent();
    }

    @Override
    public Flux<String> streamGenerateFollowUp(Long sessionId, String previousAnswer) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());
        String currentQuestion = getCurrentQuestion(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("候选人对上一个问题的回答不够完整，请生成一个追问。\n岗位: %s\n原问题: %s\n候选人回答: %s",
                job != null ? job.getName() : "通用岗位", currentQuestion, previousAnswer);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        StringBuilder contentBuilder = new StringBuilder();
        return agentRunner.streamRun(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context)
                .map(result -> {
                    if (result.getContent() != null) {
                        contentBuilder.append(result.getContent());
                    }
                    return result.getContent() != null ? result.getContent() : "";
                })
                .doOnComplete(() -> {
                    saveTurn(sessionId, config.getCurrentQuestionId(), Speaker.AI, true, contentBuilder.toString());
                });
    }

    // ==================== 提示 ====================

    @Override
    public String generateHint(Long sessionId, String currentQuestion) {
        InterviewConfig config = getSessionOrThrow(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("候选人请求提示，请为以下题目生成一个不直接给出答案的提示。\n当前题目: %s", currentQuestion);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成提示失败: " + result.getErrorMessage());
        }
        saveTurn(sessionId, config.getCurrentQuestionId(), Speaker.AI, false, result.getContent());
        return result.getContent();
    }

    @Override
    public Flux<String> streamGenerateHint(Long sessionId, String currentQuestion) {
        InterviewConfig config = getSessionOrThrow(sessionId);

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("候选人请求提示，请为以下题目生成一个不直接给出答案的提示。\n当前题目: %s", currentQuestion);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "interview");
        StringBuilder contentBuilder = new StringBuilder();
        return agentRunner.streamRun(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context)
                .map(result -> {
                    if (result.getContent() != null) {
                        contentBuilder.append(result.getContent());
                    }
                    return result.getContent() != null ? result.getContent() : "";
                })
                .doOnComplete(() -> {
                    saveTurn(sessionId, config.getCurrentQuestionId(), Speaker.AI, false, contentBuilder.toString());
                });
    }

    // ==================== 总结 ====================

    @Override
    public String generateSummary(Long sessionId) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());

        List<InterviewTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );
        String conversationHistory = formatConversationHistory(turns);
        int totalQuestions = (int) turns.stream()
                .filter(c -> c.getSpeaker() == Speaker.AI && !Boolean.TRUE.equals(c.getIsHint()))
                .count();

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("面试已结束，请生成面试总结报告。\n岗位: %s\n总题目数: %d\n完整对话记录:\n%s",
                job != null ? job.getName() : "通用岗位", totalQuestions, conversationHistory);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "evaluation");
        AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);
        if (!result.isSuccess()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "生成总结失败: " + result.getErrorMessage());
        }
        return result.getContent();
    }

    @Override
    public Flux<String> streamGenerateSummary(Long sessionId) {
        InterviewConfig config = getSessionOrThrow(sessionId);
        Job job = getJobOrNull(config.getJobId());

        List<InterviewTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );
        String conversationHistory = formatConversationHistory(turns);
        int totalQuestions = (int) turns.stream()
                .filter(c -> c.getSpeaker() == Speaker.AI && !Boolean.TRUE.equals(c.getIsHint()))
                .count();

        AgentContext context = buildAgentContext(config);

        String userMsg = String.format("面试已结束，请生成面试总结报告。\n岗位: %s\n总题目数: %d\n完整对话记录:\n%s",
                job != null ? job.getName() : "通用岗位", totalQuestions, conversationHistory);
        context.setInput(userMsg);

        AgentTeamDefinition team = getTeamByRoleOrThrow(config, "evaluation");
        return agentRunner.streamRun(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context)
                .map(result -> result.getContent() != null ? result.getContent() : "");
    }

    // ==================== 辅助方法 ====================

    /**
     * 团队角色到默认 key 的映射
     */
    private static final Map<String, String> ROLE_TO_TEAM_KEY = Map.of(
            "question", "system-team-question",
            "interview", "system-team-interview",
            "evaluation", "system-team-evaluation"
    );

    /**
     * 按角色获取团队运行时定义
     * 优先从 teamConfig 中按 key 匹配，不存在则用默认 key 查找
     */
    private AgentTeamDefinition getTeamByRoleOrThrow(InterviewConfig config, String role) {
        List<String> teamConfig = config.getTeamConfig();
        String defaultKey = ROLE_TO_TEAM_KEY.getOrDefault(role, role);

        // 1. 从 teamConfig 中查找匹配的 key
        String matchedKey = null;
        if (teamConfig != null) {
            for (String key : teamConfig) {
                if (defaultKey.equals(key)) {
                    matchedKey = key;
                    break;
                }
            }
        }
        if (matchedKey == null) {
            matchedKey = defaultKey;
        }

        // 2. 按 key 从数据库查找团队
        AgentTeam team = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, config.getUserId())
                        .eq(AgentTeam::getKey, matchedKey)
                        .last("LIMIT 1")
        );
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Agent团队不存在(key=" + matchedKey + ")，请先完成系统初始化");
        }
        AgentTeamDefinition teamDef = teamDefinitionFactory.build(team);
        if (teamDef == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "Agent团队定义构建失败");
        }
        return teamDef;
    }

    private InterviewConfig getSessionOrThrow(Long sessionId) {
        InterviewConfig config = configMapper.selectById(sessionId);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试会话不存在");
        }
        return config;
    }

    private InterviewConfig getConfigOrThrow(Long configId) {
        InterviewConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试配置不存在");
        }
        return config;
    }

    private Job getJobOrNull(Long jobId) {
        if (jobId == null) return null;
        return jobMapper.selectById(jobId);
    }

    private Resume getResumeOrNull(Long resumeId) {
        if (resumeId == null) return null;
        return resumeMapper.selectById(resumeId);
    }

    private AgentContext buildAgentContext(InterviewConfig config) {
        AgentContext context = new AgentContext(String.valueOf(config.getId()), config.getUserId());
        context.setVariable("configId", config.getId());
        return context;
    }

    private int resolveQuestionCount(InterviewConfig config) {
        if (config.getRounds() == null || config.getRounds().isEmpty()) {
            return 5;
        }
        int total = 0;
        for (Map<String, Object> round : config.getRounds()) {
            Object value = round.get("questionCount");
            if (value instanceof Number number) {
                total += number.intValue();
            } else if (value instanceof String text) {
                try {
                    total += Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    total += 1;
                }
            }
        }
        return Math.max(1, Math.min(total, 50));
    }

    private String getConversationHistory(Long sessionId) {
        List<InterviewTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );
        return formatConversationHistory(turns);
    }

    private String formatConversationHistory(List<InterviewTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "暂无对话历史";
        }

        StringBuilder sb = new StringBuilder();
        for (InterviewTurn turn : turns) {
            String speaker = turn.getSpeaker() == Speaker.AI ? "面试官" : "候选人";
            sb.append(speaker).append(": ").append(turn.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String getCurrentQuestion(Long sessionId) {
        InterviewTurn lastQuestion = turnMapper.selectOne(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .eq(InterviewTurn::getSpeaker, Speaker.AI)
                        .eq(InterviewTurn::getIsHint, false)
                        .orderByDesc(InterviewTurn::getCreatedAt)
                        .last("LIMIT 1")
        );
        return lastQuestion != null ? lastQuestion.getContent() : "";
    }

    private void saveTurn(Long sessionId, Long questionId,
                          Speaker speaker, Boolean isFollowup, String content) {
        InterviewTurn turn = new InterviewTurn();
        turn.setSessionId(sessionId);
        turn.setQuestionId(questionId);
        turn.setTurnIndex(nextTurnIndex(sessionId));
        turn.setSpeaker(speaker);
        turn.setIsFollowup(isFollowup);
        turn.setContent(content);
        turn.setIsHint(false);
        turnMapper.insert(turn);
    }

    private int nextTurnIndex(Long sessionId) {
        InterviewTurn last = turnMapper.selectOne(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByDesc(InterviewTurn::getTurnIndex)
                        .last("LIMIT 1")
        );
        return last != null && last.getTurnIndex() != null ? last.getTurnIndex() + 1 : 1;
    }

    private List<GeneratedQuestion> parseGeneratedQuestions(String content) {
        String json = extractJsonArray(content);
        if (json == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            List<GeneratedQuestion> questions = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String questionText = Objects.toString(item.get("questionText"), "").trim();
                if (questionText.isEmpty()) {
                    continue;
                }
                Map<String, Object> answerHint = asMap(item.get("answerHint"));
                List<Map<String, Object>> refs = asMapList(item.get("sourceRecallRefs"));
                questions.add(new GeneratedQuestion(questionText, answerHint, refs));
            }
            return questions;
        } catch (Exception e) {
            log.warn("解析生成题目失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf("[");
        int end = content.lastIndexOf("]");
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private List<GeneratedQuestion> normalizeQuestionCount(List<GeneratedQuestion> questions, int expectedCount) {
        if (questions.size() <= expectedCount) {
            return questions;
        }
        return new ArrayList<>(questions.subList(0, expectedCount));
    }

    private EvaluationResult parseEvaluationResult(String content) {
        EvaluationResult result = evaluationParser.parse(content);
        if (result != null) {
            return result;
        }
        log.warn("解析评估结果失败，使用默认值");
        return new EvaluationResult(NextStep.NEXT_QUESTION, content, 70.0, "继续下一题", List.of());
    }
}