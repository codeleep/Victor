package me.codeleep.victor.core.service.interview;

import me.codeleep.victor.core.service.dto.InterviewReportVO;

/**
 * 面试报告服务(报告线)。
 * <p>
 * 负责评估报告的生成、查询与导出, 状态机:
 * <pre>
 *   PENDING -> EVALUATING -> COMPLETED / FAILED
 * </pre>
 * 异步评估由 {@link me.codeleep.victor.core.service.interview.impl.ReportEvaluationExecutor} 执行,
 * 评估卡死(REPORT_GENERATING 无在途任务)的自愈见 {@link #resumeIfStuck}。
 */
public interface InterviewReportService {

    /**
     * 生成面试报告
     *
     * @param sessionId 会话ID
     * @return 报告ID
     */
    Long generateReport(Long sessionId);

    /**
     * 重新生成(重试)失败的面试报告。
     *
     * @param sessionId 会话ID
     * @return 报告ID
     */
    Long regenerateReport(Long sessionId);

    /**
     * 状态驱动的自愈: 若会话处于评估中但无在途任务, 重新触发评估。
     *
     * @param sessionId 会话ID
     */
    void resumeIfStuck(Long sessionId);

    /**
     * 获取面试报告
     *
     * @param sessionId 会话ID
     * @return 报告VO
     */
    InterviewReportVO getReport(Long sessionId);

    /**
     * 根据会话ID获取面试报告
     *
     * @param sessionId 会话ID
     * @return 报告VO
     */
    InterviewReportVO getReportBySessionId(Long sessionId);

    /**
     * 获取报告详情
     *
     * @param id 报告ID
     * @return 报告VO
     */
    InterviewReportVO getReportById(Long id);

    /**
     * 导出报告为PDF
     *
     * @param sessionId 会话ID
     * @return PDF字节数组
     */
    byte[] exportPdf(Long sessionId);

    /**
     * 导出报告为Markdown
     *
     * @param sessionId 会话ID
     * @return Markdown文本
     */
    String exportMarkdown(Long sessionId);
}