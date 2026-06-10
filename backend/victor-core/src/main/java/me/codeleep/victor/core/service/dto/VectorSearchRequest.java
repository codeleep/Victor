package me.codeleep.victor.core.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 向量搜索请求
 */
@Data
public class VectorSearchRequest {

    /**
     * 查询文本
     */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /**
     * 返回数量
     */
    @Min(value = 1, message = "返回数量最小为1")
    @Max(value = 100, message = "返回数量最大为100")
    private Integer topK = 10;

    /**
     * 相似度阈值（0-1）
     */
    @Min(value = 0, message = "相似度阈值最小为0")
    @Max(value = 1, message = "相似度阈值最大为1")
    private Double threshold = 0.7;

    /**
     * 文档ID（可选，用于限定搜索范围）
     */
    private Long documentId;
}
