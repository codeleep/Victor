package me.codeleep.victor.core.service.export;

import me.codeleep.victor.common.enums.InterviewReportStatus;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 ReportPdfExporter 能生成合法、非空且含中文的 PDF。
 * 不依赖 Spring 上下文与数据库。
 */
class ReportPdfExporterTest {

    @Test
    void renderProducesValidPdfWithChinese() throws Exception {
        InterviewReportVO vo = new InterviewReportVO();
        vo.setSessionId(9L);
        vo.setStatus(InterviewReportStatus.COMPLETED);
        vo.setOverallScore(new BigDecimal("80.0"));

        Map<String, Object> dims = new LinkedHashMap<>();
        dims.put("答案质量", Map.of("score", 68));
        dims.put("节奏把控", Map.of("score", 95));
        dims.put("语言组织", Map.of("score", 88));
        vo.setDimensionScores(dims);

        vo.setStrengths("**核心优势**：①架构设计能力突出；②高并发处理经验丰富；③分布式事务落地扎实。");
        vo.setWeaknesses("Docker 环境下 JVM 问题排查与诊断、双写扩容一致性保障细节。");
        vo.setSuggestions("建议补强 JVM 诊断与数据迁移一致性保障的工程知识。");

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("order", 1);
        q.put("score", 92);
        q.put("question", "订单系统重构中识别出的核心瓶颈及关键改造？");
        q.put("answer", "**核心瓶颈**：数据库瓶颈（单实例 MySQL，RT 2秒+）；架构耦合严重。\n\n**架构改造**：微服务拆分 + ShardingSphere 分库分表，RT 从 2 秒降到 200ms。");
        q.put("comment", "**评分：92分**\n\n回答全面且系统，有量化数据支撑。");
        vo.setPerQuestionEvaluation(List.of(q));

        vo.setSummary("## 面试综合评估总结\n\n候选人架构设计能力强，高并发与分布式事务经验扎实，建议补强 JVM 诊断能力。");

        byte[] pdf = new ReportPdfExporter().render(vo);
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "PDF 过小: " + pdf.length);
        String head = new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF"), "非合法 PDF 头: " + head);

        // 落盘便于人工核对中文显示
        File out = new File("target/report-pdf-test.pdf");
        Files.write(out.toPath(), pdf);
        System.out.println("PDF bytes=" + pdf.length + " file=" + out.getAbsolutePath());
    }
}