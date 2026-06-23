package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewReportStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewReport;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewReportMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.core.service.converter.InterviewReportConverter;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.interview.InterviewReportService;
import me.codeleep.victor.core.service.support.AsyncTaskRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报告服务实现。
 * 负责报告记录的创建、状态流转编排、查询与导出;
 * 实际异步评估由 {@link ReportEvaluationExecutor} 执行，注入独立 bean 使 @Async 经代理生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReportServiceImpl implements InterviewReportService {

    private final InterviewReportMapper interviewReportMapper;
    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewReportConverter reportConverter;
    private final ReportEvaluationExecutor reportEvaluationExecutor;
    private final AsyncTaskRegistry asyncTaskRegistry;

    @Override
    public Long generateReport(Long sessionId) {
        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试会话不存在");
        }

        // 检查是否已有报告(避免重复生成)
        InterviewReport existingReport = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId)
        );
        if (existingReport != null) {
            return existingReport.getId();
        }

        // 先同步创建 PENDING 记录,让前端能立即感知到"生成中"状态
        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setUserId(config.getUserId());
        report.setStatus(InterviewReportStatus.PENDING);
        report.setEvaluationRetryCount(0);
        interviewReportMapper.insert(report);

        // 异步执行评估(评估团队调用耗时较长),经由独立 bean 的代理触发 @Async
        reportEvaluationExecutor.doEvaluateAsync(sessionId, report.getId());
        return report.getId();
    }

    @Override
    public Long regenerateReport(Long sessionId) {
        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试会话不存在");
        }
        InterviewReport existing = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId));
        if (existing == null) {
            // 尚未创建报告记录,直接走正常生成流程
            return generateReport(sessionId);
        }
        InterviewReportStatus prevStatus = existing.getStatus();
        if (prevStatus != InterviewReportStatus.FAILED && prevStatus != InterviewReportStatus.COMPLETED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前报告状态不支持重新生成");
        }
        // 重置为待评估并重新触发异步评估；仅失败重试累计重试次数
        existing.setStatus(InterviewReportStatus.PENDING);
        existing.setEvaluationError(null);
        if (prevStatus == InterviewReportStatus.FAILED) {
            int retry = existing.getEvaluationRetryCount() == null ? 0 : existing.getEvaluationRetryCount();
            existing.setEvaluationRetryCount(retry + 1);
        }
        interviewReportMapper.updateById(existing);
        interviewConfigMapper.update(null,
                new LambdaUpdateWrapper<InterviewConfig>()
                        .eq(InterviewConfig::getId, sessionId)
                        .set(InterviewConfig::getStatus, InterviewConfigStatus.REPORT_GENERATING));
        reportEvaluationExecutor.doEvaluateAsync(sessionId, existing.getId());
        return existing.getId();
    }

    @Override
    public void resumeIfStuck(Long sessionId) {
        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null || config.getStatus() != InterviewConfigStatus.REPORT_GENERATING) {
            return;
        }
        if (asyncTaskRegistry.isRunning(sessionId)) {
            return;
        }
        InterviewReport report = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId));
        if (report == null) {
            // 状态卡在评估中但无报告记录: 补建 PENDING 记录并触发异步评估
            log.warn("Stuck report status but no report record, seed and re-trigger: sessionId={}", sessionId);
            generateReport(sessionId);
            return;
        }
        log.warn("Detect stuck report evaluation, re-trigger: sessionId={}, reportId={}", sessionId, report.getId());
        reportEvaluationExecutor.doEvaluateAsync(sessionId, report.getId());
    }

    @Override
    public InterviewReportVO getReport(Long sessionId) {
        InterviewReport report = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getSessionId, sessionId)
        );
        if (report == null) {
            return null;
        }
        InterviewReportVO vo = reportConverter.toVO(report);
        enrichPerQuestionEvaluation(vo);
        return vo;
    }

    @Override
    public InterviewReportVO getReportBySessionId(Long sessionId) {
        return getReport(sessionId);
    }

    @Override
    public InterviewReportVO getReportById(Long id) {
        InterviewReport report = interviewReportMapper.selectById(id);
        if (report == null) {
            return null;
        }
        InterviewReportVO vo = reportConverter.toVO(report);
        enrichPerQuestionEvaluation(vo);
        return vo;
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

        appendPerQuestionMarkdown(md, report);

        return md.toString();
    }

    /**
     * 将逐题点评与真实对话记录合并：以题目为维度，把追问及对应回答归入原题，
     * 并叠加评估团队给出的单题评分与 Markdown 点评，供前端逐题展示。
     */
    private void enrichPerQuestionEvaluation(InterviewReportVO vo) {
        if (vo == null || vo.getSessionId() == null
                || vo.getStatus() != InterviewReportStatus.COMPLETED) {
            return;
        }
        Long sessionId = vo.getSessionId();

        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getConfigId, sessionId)
                        .orderByAsc(InterviewQuestion::getOrderIndex)
        );
        List<InterviewTurn> turns = interviewTurnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );

        Map<Long, Map<String, Object>> evalByQuestion = new LinkedHashMap<>();
        Map<Integer, Map<String, Object>> evalByIndex = new LinkedHashMap<>();
        if (vo.getPerQuestionEvaluation() != null) {
            for (Map<String, Object> item : vo.getPerQuestionEvaluation()) {
                Long qid = toLong(item.get("questionId"));
                if (qid != null) {
                    evalByQuestion.put(qid, item);
                }
                Integer qidx = toInt(item.get("questionIndex"));
                if (qidx != null) {
                    evalByIndex.put(qidx, item);
                }
            }
        }

        Map<Long, List<InterviewTurn>> turnsByQuestion = new LinkedHashMap<>();
        List<InterviewTurn> ungrouped = new ArrayList<>();
        for (InterviewTurn turn : turns) {
            if (turn.getQuestionId() == null) {
                ungrouped.add(turn);
            } else {
                turnsByQuestion.computeIfAbsent(turn.getQuestionId(), k -> new ArrayList<>()).add(turn);
            }
        }

        List<InterviewQuestion> ordered = new ArrayList<>(questions);
        Set<Long> knownIds = new LinkedHashSet<>();
        for (InterviewQuestion q : questions) {
            if (q.getId() != null) {
                knownIds.add(q.getId());
            }
        }
        for (Long qid : turnsByQuestion.keySet()) {
            if (!knownIds.contains(qid)) {
                InterviewQuestion placeholder = new InterviewQuestion();
                placeholder.setId(qid);
                placeholder.setOrderIndex(Integer.MAX_VALUE);
                ordered.add(placeholder);
            }
        }
        ordered.sort(Comparator.comparingInt(q -> q.getOrderIndex() == null ? Integer.MAX_VALUE : q.getOrderIndex()));

        List<Map<String, Object>> merged = new ArrayList<>();
        int index = 0;
        for (InterviewQuestion q : ordered) {
            List<InterviewTurn> qTurns = turnsByQuestion.get(q.getId());
            Map<String, Object> eval = evalByQuestion.get(q.getId());
            boolean hasTurns = qTurns != null && !qTurns.isEmpty();
            if (!hasTurns && eval == null) {
                continue;
            }
            index++;
            if (eval == null) {
                eval = evalByIndex.get(index);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("questionId", q.getId());
            entry.put("questionIndex", index);
            entry.put("questionText", q.getQuestionText() != null ? q.getQuestionText()
                    : (eval != null ? toStr(eval.get("questionText")) : null));
            entry.put("score", eval != null ? eval.get("score") : null);
            entry.put("feedback", eval != null ? toStr(eval.get("feedback")) : null);
            entry.put("interactions", buildInteractions(qTurns));
            merged.add(entry);
        }
        vo.setPerQuestionEvaluation(merged);
    }

    private List<Map<String, Object>> buildInteractions(List<InterviewTurn> turns) {
        List<Map<String, Object>> interactions = new ArrayList<>();
        if (turns == null) {
            return interactions;
        }
        int aiCount = 0;
        for (InterviewTurn turn : turns) {
            if (Boolean.TRUE.equals(turn.getIsHint())) {
                continue;
            }
            boolean isAi = turn.getSpeaker() == Speaker.AI;
            if (isAi) {
                aiCount++;
            }
            Map<String, Object> interaction = new LinkedHashMap<>();
            interaction.put("speaker", turn.getSpeaker() != null ? turn.getSpeaker().getValue() : null);
            interaction.put("role", isAi ? "面试官" : "候选人");
            interaction.put("content", turn.getContent());
            interaction.put("isFollowup", isAi && aiCount > 1);
            interactions.add(interaction);
        }
        return interactions;
    }

    private void appendPerQuestionMarkdown(StringBuilder md, InterviewReportVO report) {
        if (report.getPerQuestionEvaluation() == null || report.getPerQuestionEvaluation().isEmpty()) {
            return;
        }
        md.append("\n## 逐题点评\n\n");
        for (Map<String, Object> q : report.getPerQuestionEvaluation()) {
            md.append("### 题目 ").append(q.get("questionIndex"));
            Object qText = q.get("questionText");
            if (qText != null && !qText.toString().isBlank()) {
                md.append("：").append(qText);
            }
            md.append("\n\n");
            Object score = q.get("score");
            if (score != null) {
                md.append("**单题评分**: ").append(score).append("\n\n");
            }
            Object interactions = q.get("interactions");
            if (interactions instanceof List && !((List<?>) interactions).isEmpty()) {
                md.append("**对话记录**:\n\n");
                for (Object item : (List<?>) interactions) {
                    if (item instanceof Map) {
                        Map<?, ?> it = (Map<?, ?>) item;
                        Object role = it.get("role");
                        Object content = it.get("content");
                        boolean followup = Boolean.TRUE.equals(it.get("isFollowup"));
                        md.append("> **").append(role);
                        if (followup) {
                            md.append("（追问）");
                        }
                        md.append("**: ").append(content).append("\n\n");
                    }
                }
            }
            Object feedback = q.get("feedback");
            if (feedback != null && !feedback.toString().isBlank()) {
                md.append("**点评**:\n\n").append(feedback).append("\n\n");
            }
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toStr(Object value) {
        return value != null ? value.toString() : null;
    }
}