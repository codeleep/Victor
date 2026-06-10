package me.codeleep.victor.infra.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * 结构化 JSON 解析器，封装 Spring AI 的 BeanOutputConverter。
 *
 * <p>用于将 LLM 返回的文本（可能包含 markdown、前后缀文字等）解析为指定类型的 Java 对象。
 * 替代之前基于正则的 parseSimpleJson 方式。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * // 方式 1: 使用 Class
 * StructuredJsonParser<EvaluationResult> parser = new StructuredJsonParser<>(EvaluationResult.class);
 * EvaluationResult result = parser.parse(llmResponseText);
 *
 * // 方式 2: 使用 TypeReference（泛型类型）
 * StructuredJsonParser<Map<String, Object>> parser = new StructuredJsonParser<>(new TypeReference<>() {});
 * Map<String, Object> result = parser.parse(llmResponseText);
 * }</pre>
 */
@Slf4j
public class StructuredJsonParser<T> {

    private final ObjectMapper objectMapper;
    private final JavaType javaType;
    private final BeanOutputConverter<T> converter;

    /**
     * 使用 Class 构造（支持 getFormatInstructions）
     */
    public StructuredJsonParser(Class<T> targetType) {
        this(targetType, new ObjectMapper());
    }

    public StructuredJsonParser(Class<T> targetType, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.javaType = objectMapper.getTypeFactory().constructType(targetType);
        this.converter = new BeanOutputConverter<>(targetType);
    }

    /**
     * 使用 TypeReference 构造（支持泛型类型，不支持 getFormatInstructions）
     */
    public StructuredJsonParser(TypeReference<T> typeRef) {
        this(typeRef, new ObjectMapper());
    }

    public StructuredJsonParser(TypeReference<T> typeRef, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.javaType = objectMapper.getTypeFactory().constructType(typeRef);
        this.converter = null;
    }

    /**
     * 使用 JavaType 构造
     */
    public StructuredJsonParser(JavaType javaType) {
        this(javaType, new ObjectMapper());
    }

    public StructuredJsonParser(JavaType javaType, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.javaType = javaType;
        this.converter = null;
    }

    /**
     * 从 LLM 返回的文本中解析结构化对象。
     *
     * @param llmResponseText LLM 返回的原始文本
     * @return 解析后的对象，解析失败返回 null
     */
    public T parse(String llmResponseText) {
        if (llmResponseText == null || llmResponseText.isBlank()) {
            return null;
        }

        String jsonContent = extractJsonContent(llmResponseText);
        if (jsonContent == null) {
            log.warn("无法从 LLM 响应中提取 JSON 内容: {}", abbreviate(llmResponseText, 200));
            return null;
        }

        try {
            return objectMapper.readValue(jsonContent, javaType);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 返回的文本中提取 JSON 字符串。
     *
     * @param llmResponseText LLM 返回的原始文本
     * @return 提取的 JSON 字符串，未找到返回 null
     */
    public String extractRawJson(String llmResponseText) {
        return extractJsonContent(llmResponseText);
    }

    /**
     * 获取 JSON Schema 格式指令，可追加到 prompt 中以引导 LLM 按格式输出。
     * 仅在使用 Class 构造时可用。
     *
     * @return 格式指令文本，不可用时返回 null
     */
    public String getFormatInstructions() {
        return converter != null ? converter.getFormat() : null;
    }

    /**
     * 从文本中提取 JSON 内容（对象或数组）。
     * 跳过 markdown 代码块标记和前后缀文字。
     */
    private String extractJsonContent(String text) {
        String cleaned = stripCodeFences(text);

        // 找到第一个 { 或 [
        int start = -1;
        char openChar = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                openChar = c;
                break;
            }
        }
        if (start < 0) return null;

        char closeChar = (openChar == '{') ? '}' : ']';

        // 从末尾向前找匹配的闭合字符
        for (int i = cleaned.length() - 1; i > start; i--) {
            if (cleaned.charAt(i) == closeChar) {
                return cleaned.substring(start, i + 1);
            }
        }

        return null;
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
