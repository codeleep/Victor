package me.codeleep.victor.web.controller;

import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.interview.InterviewReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 面试报告控制器(报告线)。
 * 负责评估报告的生成、查询、重试与导出。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class InterviewReportController {

    private final InterviewReportService interviewReportService;

    /** 生成面试报告 */
    @PostMapping("/generate/{sessionId}")
    public Result<Long> generateReport(@PathVariable Long sessionId) {
        Long reportId = interviewReportService.generateReport(sessionId);
        return Result.success(reportId);
    }

    /** 按会话ID重新生成(重试)报告 */
    @PostMapping("/session/{sessionId}/regenerate")
    public Result<Long> regenerateReport(@PathVariable Long sessionId) {
        Long reportId = interviewReportService.regenerateReport(sessionId);
        return Result.success(reportId);
    }

    /** 按会话ID获取面试报告 */
    @GetMapping("/session/{sessionId}")
    public Result<InterviewReportVO> getReportBySessionId(@PathVariable Long sessionId) {
        InterviewReportVO report = interviewReportService.getReportBySessionId(sessionId);
        return Result.success(report);
    }

    /** 按报告ID获取报告详情 */
    @GetMapping("/{id}")
    public Result<InterviewReportVO> getReportById(@PathVariable Long id) {
        InterviewReportVO report = interviewReportService.getReportById(id);
        return Result.success(report);
    }

    /** 导出报告为PDF */
    @GetMapping("/export/pdf/{sessionId}")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long sessionId) {
        byte[] pdfData = interviewReportService.exportPdf(sessionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .body(pdfData);
    }

    /** 导出报告为Markdown */
    @GetMapping("/export/markdown/{sessionId}")
    public ResponseEntity<String> exportMarkdown(@PathVariable Long sessionId) {
        String markdown = interviewReportService.exportMarkdown(sessionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_MARKDOWN_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.md\"")
                .body(markdown);
    }
}