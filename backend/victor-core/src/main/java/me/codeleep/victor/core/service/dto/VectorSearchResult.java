package me.codeleep.victor.core.service.dto;

import lombok.Data;

import java.util.Map;

/**
 * 向量搜索结果
 */
@Data
public class VectorSearchResult {

    /**
     * 文档块ID
     */
    private Long chunkId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档名称
     */
    private String documentName;

    /**
     * 块内容
     */
    private String content;

    /**
     * 相似度分数
     */
    private Double score;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;
}
