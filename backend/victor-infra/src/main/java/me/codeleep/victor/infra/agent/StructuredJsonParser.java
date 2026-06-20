package me.codeleep.victor.infra.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 结构化 JSON 解析器
 * 从 LLM 输出文本中提取 JSON 片段并反序列化为目标类型
 */
@Slf4j
public class StructuredJsonParser<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> targetType;
    private final TypeReference<T> typeReference;

    public StructuredJsonParser(Class<T> targetType) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.targetType = targetType;
        this.typeReference = null;
    }

    public StructuredJsonParser(TypeReference<T> typeReference) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.targetType = null;
        this.typeReference = typeReference;
    }

    /**
     * 从文本中解析目标对象
     * 支持 {} 包裹的 JSON 对象，自动定位首尾花括号
     */
    public T parse(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String json = extractJsonObject(content);
        if (json == null) {
            log.warn("未在输出中找到 JSON 对象");
            return null;
        }
        try {
            if (typeReference != null) {
                return objectMapper.readValue(json, typeReference);
            }
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }
}
