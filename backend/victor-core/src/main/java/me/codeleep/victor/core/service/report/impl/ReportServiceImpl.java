package me.codeleep.victor.core.service.report.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.StructuredJsonParser;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.runner.AgentTeamRunner;
import me.codeleep.victor.common.enums.InterviewReportStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.*;
import me.codeleep.victor.core.engine.AgentTeamDefinitionFactory;
import me.codeleep.victor.core.mapper.*;
import me.codeleep.victor.core.service.converter.InterviewReportConverter;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.report.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 报告服务实现
 * 使用评估 Agent 团队生成面试报告，具体指令由团队 DB prompt 驱动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InterviewReportMapper interviewReportMapper;
    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewConfigMapper interviewConfigMapper;
    private final JobMapper jobMapper;
    private final AgentTeamMapper agentTeamMapper;
    private final AgentTeamRunner teamRunner;
    private final AgentTeamDefinitionFactory teamDefinitionFactory;
    private final InterviewReportConverter reportConverter;

    private final StructuredJsonParser<Map<String, Object>> reportJsonParser =
            new StructuredJsonParser<>(new TypeReference<>() {});

    @Override
    @Transactional
    public Long generateReport(Long sessionId) {
        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试会话不存在");
        }

        // 检查是否已有报告
        InterviewReport existingReport = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId)
        );
        if (existingReport != null) {
            return existingReport.getId();
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
        context.addUserMessage(userMsg);

        // 获取评估团队
        AgentTeamDefinition team = getTeamOrThrow(config);

        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setUserId(config.getUserId());
        report.setEvaluationRetryCount(0);

        try {
            AgentResult result = teamRunner.run(team, context);

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

        interviewReportMapper.insert(report);
        log.info("面试报告生成成功: sessionId={}, reportId={}", sessionId, report.getId());

        return report.getId();
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

    @Override
    public InterviewReportVO getReport(Long sessionId) {
        InterviewReport report = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId)
        );
        return report != null ? reportConverter.toVO(report) : null;
    }

    @Override
    public InterviewReportVO getReportBySessionId(Long sessionId) {
        return getReport(sessionId);
    }

    @Override
    public InterviewReportVO getReportById(Long id) {
        InterviewReport report = interviewReportMapper.selectById(id);
        return report != null ? reportConverter.toVO(report) : null;
    }

    @Override
    public byte[] exportPdf(Long sessionId) {
        InterviewReportVO report = getReport(sessionId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        return new byte[0];
    }

    @Override
    public String exportMarkdown(Long sessionId) {
        InterviewReportVO report = getReport(sessionId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }

        StringBuilder md = new StringBuilder();
        md.append("# 面试评估报告\n\n");
        md.append("**总分**: ").append(report.getOverallScore()).append("\n\n");

        md.append("## 维度评分\n\n");
        if (report.getDimensionScores() != null) {
            report.getDimensionScores().forEach((dim, score) -> {
                md.append("- **").append(dim).append("**: ").append(score).append("\n");
            });
        }

        md.append("\n## 优势\n\n");
        md.append(report.getStrengths()).append("\n");

        md.append("\n## 不足\n\n");
        md.append(report.getWeaknesses()).append("\n");

        md.append("\n## 建议\n\n");
        md.append(report.getSuggestions()).append("\n");

        md.append("\n## 总结\n\n");
        md.append(report.getSummary()).append("\n");

        return md.toString();
    }

}
