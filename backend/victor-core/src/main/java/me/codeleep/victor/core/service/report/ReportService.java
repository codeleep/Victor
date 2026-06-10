package me.codeleep.victor.core.service.report;

import me.codeleep.victor.core.service.dto.InterviewReportVO;

/**
 * 报告服务接口
 */
public interface ReportService {

    /**
     * 生成面试报告
     *
     * @param sessionId 会话ID
     * @return 报告ID
     */
    Long generateReport(Long sessionId);

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
