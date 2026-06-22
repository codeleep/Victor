package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewReportStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewReport;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewReportMapper;
import me.codeleep.victor.core.service.converter.InterviewReportConverter;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.interview.InterviewReportService;
import me.codeleep.victor.core.service.support.AsyncTaskRegistry;
import org.springframework.stereotype.Service;

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
        if (existing.getStatus() != InterviewReportStatus.FAILED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前报告状态不支持重新生成");
        }
        // 重置为待评估并重新触发异步评估
        existing.setStatus(InterviewReportStatus.PENDING);
        existing.setEvaluationError(null);
        int retry = existing.getEvaluationRetryCount() == null ? 0 : existing.getEvaluationRetryCount();
        existing.setEvaluationRetryCount(retry + 1);
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