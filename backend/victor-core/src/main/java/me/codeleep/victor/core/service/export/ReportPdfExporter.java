package me.codeleep.victor.core.service.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将面试报告渲染为 PDF。
 *
 * <p>使用 OpenHTMLtoPDF(PDFBox) 将 HTML/CSS 转换为 PDF,并按需注册系统中的 CJK 字体,
 * 以保证中文正确显示。若运行环境未安装任何中文字体,中文将显示为方框(拉丁字符不受影响)。</p>
 */
@Slf4j
@Component
public class ReportPdfExporter {

    /** 候选 CJK 字体: 文件路径 -> CSS font-family 名称 */
    private static final LinkedHashMap<String, String> CJK_FONT_CANDIDATES = new LinkedHashMap<>();

    static {
        // Windows
        CJK_FONT_CANDIDATES.put("C:\\Windows\\Fonts\\msyh.ttc", "Microsoft YaHei");
        CJK_FONT_CANDIDATES.put("C:\\Windows\\Fonts\\simhei.ttf", "SimHei");
        CJK_FONT_CANDIDATES.put("C:\\Windows\\Fonts\\simsun.ttc", "SimSun");
        CJK_FONT_CANDIDATES.put("C:\\Windows\\Fonts\\SourceHanSansCN-Normal.ttf", "Source Han Sans CN");
        CJK_FONT_CANDIDATES.put("C:\\Windows\\Fonts\\Deng.ttf", "DengXian");
        // Linux 常见 CJK 字体
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "Noto Sans CJK SC");
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Regular.otf", "Noto Sans CJK SC");
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/noto-cjk/NotoSansCJKsc-Regular.otf", "Noto Sans CJK SC");
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc", "Noto Sans CJK SC");
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc", "WenQuanYi Zen Hei");
        CJK_FONT_CANDIDATES.put("/usr/share/fonts/wqy-microhei/wqy-microhei.ttc", "WenQuanYi Micro Hei");
    }

    /**
     * 渲染报告为 PDF 字节数组。
     */
    public byte[] render(InterviewReportVO report) {
        try {
            // PDFBox/Java2D 在无显示环境的服务器上需要 headless 模式
            System.setProperty("java.awt.headless", "true");

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            List<String> families = registerFonts(builder);
            String fontFamily = families.isEmpty()
                    ? "sans-serif"
                    : String.join(",", families) + ",sans-serif";

            String html = buildHtml(report, fontFamily);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                builder.withHtmlContent(html, null);
                builder.toStream(out);
                builder.run();
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.error("生成报告 PDF 失败: sessionId={}", report.getSessionId(), e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "导出 PDF 失败");
        }
    }

    /**
     * 注册候选 CJK 字体,返回成功注册的 font-family 名称列表(已带引号)。
     * 加载失败(如不支持的 ttc)会被跳过,不影响其余字体。
     */
    private List<String> registerFonts(PdfRendererBuilder builder) {
        List<String> registered = new ArrayList<>();
        for (Map.Entry<String, String> entry : CJK_FONT_CANDIDATES.entrySet()) {
            File file = new File(entry.getKey());
            if (!file.isFile()) {
                continue;
            }
            try {
                builder.useFont(file, entry.getValue());
                registered.add("'" + entry.getValue() + "'");
                log.debug("注册 PDF 字体: {} -> {}", entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.debug("跳过无法加载的字体 {}: {}", entry.getKey(), e.getMessage());
            }
        }
        if (registered.isEmpty()) {
            log.warn("未找到可用的 CJK 字体,PDF 中文可能显示为方框。请在系统中安装中文字体(如 SimHei/Noto Sans CJK)。");
        }
        return registered;
    }

    private String buildHtml(InterviewReportVO r, String fontFamily) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh\"><head><meta charset=\"UTF-8\"/>");
        h.append("<style>");
        h.append("@page { size: A4; margin: 18mm 16mm; }");
        h.append("body { font-family: ").append(fontFamily).append("; font-size: 11pt; color: #222; line-height: 1.6; }");
        h.append("h1 { font-size: 20pt; text-align: center; margin: 0 0 4pt; }");
        h.append(".meta { text-align: center; color: #666; font-size: 9.5pt; margin-bottom: 12pt; }");
        h.append("h2 { font-size: 14pt; border-left: 4px solid #4A9E6E; padding-left: 8pt; margin: 16pt 0 6pt; }");
        h.append("table { width: 100%; border-collapse: collapse; margin: 6pt 0; }");
        h.append("th, td { border: 1px solid #ddd; padding: 5pt 8pt; text-align: left; font-size: 10.5pt; }");
        h.append("th { background: #f5f7fa; }");
        h.append(".q { margin: 8pt 0; padding: 8pt 10pt; background: #fafafa; border-left: 3px solid #4A9E6E; }");
        h.append(".q-head { font-weight: bold; margin-bottom: 4pt; }");
        h.append(".label { color: #888; font-size: 9.5pt; }");
        h.append("code { background: #f0f0f0; padding: 0 2pt; }");
        h.append(".content { white-space: pre-wrap; word-wrap: break-word; margin: 2pt 0 4pt; }");
        h.append(".empty { color: #aaa; font-size: 9.5pt; }");
        h.append("</style></head><body>");

        h.append("<h1>面试评估报告</h1>");
        h.append("<div class=\"meta\">总分：").append(score(r.getOverallScore())).append(" 分</div>");

        // 维度评分
        h.append("<h2>维度评分</h2>");
        Map<String, Object> dims = r.getDimensionScores();
        if (dims != null && !dims.isEmpty()) {
            h.append("<table><tr><th>维度</th><th>得分</th></tr>");
            for (Map.Entry<String, Object> e : dims.entrySet()) {
                h.append("<tr><td>").append(esc(e.getKey())).append("</td><td>")
                        .append(esc(String.valueOf(extractScore(e.getValue())))).append("</td></tr>");
            }
            h.append("</table>");
        } else {
            h.append("<p class=\"empty\">暂无</p>");
        }

        h.append("<h2>优势</h2>").append(content(r.getStrengths()));
        h.append("<h2>不足</h2>").append(content(r.getWeaknesses()));
        h.append("<h2>建议</h2>").append(content(r.getSuggestions()));

        // 逐题评估
        List<Map<String, Object>> questions = r.getPerQuestionEvaluation();
        if (questions != null && !questions.isEmpty()) {
            h.append("<h2>逐题评估</h2>");
            for (Map<String, Object> item : questions) {
                h.append("<div class=\"q\">");
                h.append("<div class=\"q-head\">")
                        .append(esc(str(item.get("order")))).append(". ")
                        .append(esc(str(item.get("question")))).append("</div>");
                Object score = item.get("score");
                if (score != null) {
                    h.append("<div><span class=\"label\">评分：</span>")
                            .append(esc(str(score))).append(" 分</div>");
                }
                if (item.get("answer") != null) {
                    h.append("<div class=\"label\">回答：</div>")
                            .append(content(str(item.get("answer"))));
                }
                if (item.get("comment") != null) {
                    h.append("<div class=\"label\">点评：</div>")
                            .append(content(str(item.get("comment"))));
                }
                h.append("</div>");
            }
        }

        h.append("<h2>总结</h2>").append(content(r.getSummary()));
        h.append("</body></html>");
        return h.toString();
    }

    /** 渲染富文本字段: 转 HTML 转义后处理 Markdown 的加粗/行内代码/标题,并用 pre-wrap 保留换行。 */
    private String content(String text) {
        if (text == null || text.isBlank()) {
            return "<p class=\"empty\">暂无</p>";
        }
        String s = esc(text);
        // Markdown 标题行(#/##/###)转为加粗
        s = s.replaceAll("(?m)^#{1,6}\\s*(.+)$", "<strong>$1</strong>");
        // 加粗 **x**
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // 行内代码 `x`
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        return "<div class=\"content\">" + s + "</div>";
    }

    private Object extractScore(Object val) {
        if (val instanceof Map<?, ?> m) {
            Object score = m.get("score");
            return score != null ? score : val;
        }
        return val;
    }

    private String score(BigDecimal val) {
        return val == null ? "-" : val.toPlainString();
    }

    private String str(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}