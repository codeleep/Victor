package me.codeleep.victor.web.controller;

import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.report.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 报告控制器
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 生成面试报告
     */
    @PostMapping("/generate/{sessionId}")
    public Result<Long> generateReport(@PathVariable Long sessionId) {
        Long reportId = reportService.generateReport(sessionId);
        return Result.success(reportId);
    }

    /**
     * 获取面试报告
     */
    @GetMapping("/session/{sessionId}")
    public Result<InterviewReportVO> getReport(@PathVariable Long sessionId) {
        InterviewReportVO report = reportService.getReport(sessionId);
        return Result.success(report);
    }

    /**
     * 获取报告详情
     */
    @GetMapping("/{id}")
    public Result<InterviewReportVO> getReportById(@PathVariable Long id) {
        InterviewReportVO report = reportService.getReportById(id);
        return Result.success(report);
    }

    /**
     * 导出报告为PDF
     */
    @GetMapping("/export/pdf/{sessionId}")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long sessionId) {
        byte[] pdfData = reportService.exportPdf(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .body(pdfData);
    }

    /**
     * 导出报告为Markdown
     */
    @GetMapping("/export/markdown/{sessionId}")
    public ResponseEntity<String> exportMarkdown(@PathVariable Long sessionId) {
        String markdown = reportService.exportMarkdown(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_MARKDOWN_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.md\"")
                .body(markdown);
    }
}
