package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewReportStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.engine.AgentTeamDefinitionFactory;
import me.codeleep.victor.core.entity.*;
import me.codeleep.victor.core.mapper.*;
import me.codeleep.victor.infra.agent.StructuredJsonParser;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.runner.AgentFactory;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import me.codeleep.victor.core.service.support.AsyncTaskRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 面试评估异步执行器。
 * 独立 bean 让 @Async 经由 Spring 代理生效，避免 ReportServiceImpl 自注入自身。
 * 评估流程: PENDING -> EVALUATING -> COMPLETED/FAILED，并同步面试会话状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportEvaluationExecutor {

    private static final String EVAL_TEAM_KEY = "system-team-evaluation";

    private final InterviewReportMapper interviewReportMapper;
    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewConfigMapper interviewConfigMapper;
    private final JobMapper jobMapper;
    private final AgentTeamMapper agentTeamMapper;
    private final AgentFactory agentFactory;
    private final AgentRunner agentRunner;
    private final AgentTeamDefinitionFactory teamDefinitionFactory;
    private final AsyncTaskRegistry asyncTaskRegistry;

    private final StructuredJsonParser<Map<String, Object>> reportJsonParser =
            new StructuredJsonParser<>(new TypeReference<>() {});

    /**
     * 异步执行评估:更新状态为 EVALUATING，调用评估团队，回填结果。
     * 同一会话不会重复触发(在途登记去重)。
     */
    @Async
    public void doEvaluateAsync(Long sessionId, Long reportId) {
        if (!asyncTaskRegistry.start(sessionId)) {
            log.info("Report evaluation already in flight, skip: sessionId={}", sessionId);
            return;
        }
        try {
            doEvaluate(sessionId, reportId);
        } finally {
            asyncTaskRegistry.finish(sessionId);
        }
    }

    /**
     * 评估主流程。拆分为状态准备、上下文构建、团队调用、结果落库四个阶段，
     * 任一阶段失败都会把报告标记为 FAILED 并记录原因，避免解析失败被静默吞掉后仍标记为 COMPLETED。
     */
    private void doEvaluate(Long sessionId, Long reportId) {
        InterviewReport report = interviewReportMapper.selectById(reportId);
        if (report == null) {
            return;
        }
        markEvaluating(report);

        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            failReport(report, sessionId, "面试会话不存在");
            return;
        }

        try {
            AgentContext context = buildEvalContext(config, sessionId);
            AgentResult result = runEvaluationTeam(config, context);

            if (!result.isSuccess() || result.getContent() == null) {
                failReport(report, sessionId, result.getErrorMessage() != null
                        ? result.getErrorMessage() : "评估团队未返回有效内容");
                return;
            }

            if (!applyEvaluationResult(report, sessionId, result.getContent())) {
                return;
            }
        } catch (Exception e) {
            log.error("调用评估团队生成报告失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            failReport(report, sessionId, e.getMessage());
            return;
        }

        finalizeReport(report, sessionId);
        log.info("面试报告生成完成: sessionId={}, reportId={}, reportStatus={}",
                sessionId, report.getId(), report.getStatus());
    }

    /** 标记报告进入评估中。 */
    private void markEvaluating(InterviewReport report) {
        report.setStatus(InterviewReportStatus.EVALUATING);
        interviewReportMapper.updateById(report);
    }

    /** 把报告标记为失败，写入错误原因并落默认值，同时同步会话状态。 */
    private void failReport(InterviewReport report, Long sessionId, String error) {
        report.setStatus(InterviewReportStatus.FAILED);
        report.setEvaluationError(error);
        setDefaultReportValues(report);
        interviewReportMapper.updateById(report);
        syncSessionStatus(sessionId, report.getStatus());
        log.warn("面试报告生成失败: sessionId={}, reportId={}, error={}",
                sessionId, report.getId(), error);
    }

    /** 拉取对话历史与岗位信息，组装评估上下文(user message)。 */
    private AgentContext buildEvalContext(InterviewConfig config, Long sessionId) {
        List<InterviewTurn> turns = interviewTurnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );

        String jobName = resolveJobName(config);
        int totalQuestions = (int) turns.stream()
                .filter(t -> t.getSpeaker() == Speaker.AI && !Boolean.TRUE.equals(t.getIsHint()))
                .count();
        String conversationHistory = formatConversationHistory(turns);

        String userMsg = String.format("""
                请根据以下面试对话记录，生成一份详细的面试评估报告。

                ## 面试信息
                - 岗位: %s
                - 总题目数: %d

                ## 完整对话记录
                %s
                """, jobName, totalQuestions, conversationHistory);

        AgentContext context = new AgentContext(String.valueOf(sessionId), config.getUserId());
        context.setInput(userMsg);
        return context;
    }

    private String resolveJobName(InterviewConfig config) {
        if (config.getJobId() == null) {
            return "通用岗位";
        }
        Job job = jobMapper.selectById(config.getJobId());
        return job != null ? job.getName() : "通用岗位";
    }

    /** 构建评估团队并同步执行，返回评估结果。 */
    private AgentResult runEvaluationTeam(InterviewConfig config, AgentContext context) {
        AgentTeamDefinition team = withJsonInstructions(getTeamOrThrow(config));
        return agentRunner.run(
                agentFactory.buildTeam(team, context.getSessionId(),
                        String.valueOf(context.getUserId()), null),
                context);
    }

    /**
     * 强制评估主 Agent 输出 JSON: 在主 Agent 系统提示词前追加严格 JSON 输出指令,
     * 覆盖其可能输出 Markdown 报告的默认行为(含历史/自定义提示词)。
     * 仅作用于本次调用, 不修改数据库中持久化的 Agent 配置。
     */
    private AgentTeamDefinition withJsonInstructions(AgentTeamDefinition team) {
        String jsonInstruction = """
                你的最终输出必须且只能是一个 JSON 对象, 禁止输出 Markdown、表格、标题或任何解释性文字。
                JSON 结构: {"overallScore": 数字0-100, "dimensionScores": {"语言组织": 数字, "答案质量": 数字, "语气气势": 数字, "节奏把控": 数字}, "strengths": "字符串", "weaknesses": "字符串", "suggestions": "字符串", "summary": "字符串"}
                直接以 { 开头、} 结尾, 不要包裹在代码块中, 不要有任何前后缀文字。
                """;
        String baseInstructions = team.getMainAgent().getInstructions();
        String merged = jsonInstruction + (baseInstructions != null ? baseInstructions : "");
        return team.toBuilder()
                .mainAgent(team.getMainAgent().toBuilder().instructions(merged).build())
                .build();
    }

    /** 解析评估团队原始输出并填充报告字段；解析失败则标记失败而非静默落默认值。 */
    /** 解析评估团队原始输出并填充报告字段；解析失败则标记失败而非静默落默认值。返回是否成功。 */
    private boolean applyEvaluationResult(InterviewReport report, Long sessionId, String rawContent) {
        log.info("评估团队原始输出: sessionId={}, contentLen={}, content=[[[{}]]]",
                sessionId, rawContent.length(), rawContent);

        Map<String, Object> evaluation = reportJsonParser.parse(rawContent);
        if (evaluation == null || evaluation.isEmpty()) {
            failReport(report, sessionId,
                    "评估团队输出无法解析为JSON，原始内容长度=" + rawContent.length()
                            + "，原始内容=" + rawContent);
            return false;
        }
        log.info("评估报告解析结果: sessionId={}, keys={}, evaluation={}",
                sessionId, evaluation.keySet(), evaluation);

        report.setStatus(InterviewReportStatus.COMPLETED);
        report.setOverallScore(toBigDecimal(evaluation.get("overallScore")));
        report.setDimensionScores(toDimensionScores(evaluation.get("dimensionScores")));
        report.setStrengths(toString(evaluation.get("strengths")));
        report.setWeaknesses(toString(evaluation.get("weaknesses")));
        report.setSuggestions(toString(evaluation.get("suggestions")));
        report.setSummary(toString(evaluation.get("summary")));
        report.setGeneratedAt(LocalDateTime.now());
        return true;
    }

    /** 持久化报告最终状态并同步面试会话状态。 */
    private void finalizeReport(InterviewReport report, Long sessionId) {
        interviewReportMapper.updateById(report);
        syncSessionStatus(sessionId, report.getStatus());
    }

    /**
     * 评估结束后同步面试会话状态: COMPLETED -> REPORT_COMPLETED, FAILED -> REPORT_FAILED。
     */
    private void syncSessionStatus(Long sessionId, InterviewReportStatus reportStatus) {
        InterviewConfigStatus configStatus = reportStatus == InterviewReportStatus.COMPLETED
                ? InterviewConfigStatus.REPORT_COMPLETED : InterviewConfigStatus.REPORT_FAILED;
        interviewConfigMapper.update(
                null,
                new LambdaUpdateWrapper<InterviewConfig>()
                        .eq(InterviewConfig::getId, sessionId)
                        .set(InterviewConfig::getStatus, configStatus));
    }

    /**
     * 获取评估团队运行时定义，从 teamConfig 中按 evaluation key 查找
     */
    private AgentTeamDefinition getTeamOrThrow(InterviewConfig config) {
        List<String> teamConfig = config.getTeamConfig();
        String matchedKey = resolveEvalTeamKey(teamConfig);

        AgentTeam team = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, config.getUserId())
                        .eq(AgentTeam::getKey, matchedKey)
                        .last("LIMIT 1")
        );
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评估团队不存在(key=" + matchedKey + ")，请先完成系统初始化");
        }
        AgentTeamDefinition teamDef = teamDefinitionFactory.build(team);
        if (teamDef == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "Agent团队定义构建失败");
        }
        return teamDef;
    }

    private String resolveEvalTeamKey(List<String> teamConfig) {
        if (teamConfig != null) {
            for (String key : teamConfig) {
                if (EVAL_TEAM_KEY.equals(key)) {
                    return key;
                }
            }
        }
        return EVAL_TEAM_KEY;
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.valueOf(70);
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.valueOf(70);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toDimensionScores(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new HashMap<>();
            ((Map<String, Object>) value).forEach((k, v) -> result.put(k, toBigDecimal(v)));
            return result;
        }
        return Map.of(
                "技术能力", BigDecimal.valueOf(70),
                "沟通能力", BigDecimal.valueOf(70),
                "问题解决", BigDecimal.valueOf(70)
        );
    }

    private String toString(Object value) {
        return value != null ? value.toString() : "";
    }

    private void setDefaultReportValues(InterviewReport report) {
        report.setOverallScore(BigDecimal.valueOf(70));
        report.setDimensionScores(Map.of(
                "技术能力", BigDecimal.valueOf(70),
                "沟通能力", BigDecimal.valueOf(70),
                "问题解决", BigDecimal.valueOf(70)
        ));
        report.setStrengths("暂无评估数据");
        report.setWeaknesses("暂无评估数据");
        report.setSuggestions("暂无评估数据");
        report.setSummary("评估过程出现异常，已生成默认报告");
        report.setGeneratedAt(LocalDateTime.now());
    }
}