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

    private void doEvaluate(Long sessionId, Long reportId) {
        InterviewReport report = interviewReportMapper.selectById(reportId);
        if (report == null) {
            return;
        }
        report.setStatus(InterviewReportStatus.EVALUATING);
        interviewReportMapper.updateById(report);

        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            report.setStatus(InterviewReportStatus.FAILED);
            report.setEvaluationError("面试会话不存在");
            interviewReportMapper.updateById(report);
            syncSessionStatus(sessionId, report.getStatus());
            return;
        }

        // 获取对话历史
        List<InterviewTurn> turns = interviewTurnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );

        // 获取岗位信息
        String jobName = "通用岗位";
        if (config.getJobId() != null) {
            Job job = jobMapper.selectById(config.getJobId());
            if (job != null) {
                jobName = job.getName();
            }
        }

        // 统计题目数
        int totalQuestions = (int) turns.stream()
                .filter(t -> t.getSpeaker() == Speaker.AI && !Boolean.TRUE.equals(t.getIsHint()))
                .count();

        // 格式化对话历史
        String conversationHistory = formatConversationHistory(turns);

        // 构建评估上下文
        AgentContext context = new AgentContext(String.valueOf(sessionId), config.getUserId());

        // 任务上下文作为 user message，具体报告指令由评估团队的 DB prompt 驱动
        String userMsg = String.format("""
                请根据以下面试对话记录，生成一份详细的面试评估报告。

                ## 面试信息
                - 岗位: %s
                - 总题目数: %d

                ## 完整对话记录
                %s
                """, jobName, totalQuestions, conversationHistory);
        context.setInput(userMsg);

        // 获取评估团队
        AgentTeamDefinition team = getTeamOrThrow(config);

        try {
            AgentResult result = agentRunner.run(agentFactory.buildTeam(team, context.getSessionId(), String.valueOf(context.getUserId()), null), context);

            if (result.isSuccess() && result.getContent() != null) {
                Map<String, Object> evaluation = parseReportJson(result.getContent());

                report.setStatus(InterviewReportStatus.COMPLETED);
                report.setOverallScore(toBigDecimal(evaluation.get("overallScore")));
                report.setDimensionScores(toDimensionScores(evaluation.get("dimensionScores")));
                report.setStrengths(toString(evaluation.get("strengths")));
                report.setWeaknesses(toString(evaluation.get("weaknesses")));
                report.setSuggestions(toString(evaluation.get("suggestions")));
                report.setSummary(toString(evaluation.get("summary")));
                report.setGeneratedAt(LocalDateTime.now());
            } else {
                report.setStatus(InterviewReportStatus.FAILED);
                report.setEvaluationError(result.getErrorMessage());
                setDefaultReportValues(report);
            }
        } catch (Exception e) {
            log.error("调用评估团队生成报告失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            report.setStatus(InterviewReportStatus.FAILED);
            report.setEvaluationError(e.getMessage());
            setDefaultReportValues(report);
        }

        interviewReportMapper.updateById(report);
        syncSessionStatus(sessionId, report.getStatus());
        log.info("面试报告生成完成: sessionId={}, reportId={}, reportStatus={}",
                sessionId, report.getId(), report.getStatus());
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
        String evalKey = "system-team-evaluation";

        // 从 teamConfig 中查找 evaluation key
        String matchedKey = null;
        if (teamConfig != null) {
            for (String key : teamConfig) {
                if (evalKey.equals(key)) {
                    matchedKey = key;
                    break;
                }
            }
        }
        if (matchedKey == null) {
            matchedKey = evalKey;
        }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseReportJson(String content) {
        Map<String, Object> result = reportJsonParser.parse(content);
        return result != null ? result : new HashMap<>();
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