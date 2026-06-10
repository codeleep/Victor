package me.codeleep.victor.core.dto;

import lombok.Data;

/**
 * 元数据查询请求DTO
 */
@Data
public class MetadataQueryRequest {

    /**
     * 分类
     */
    private String category;

    /**
     * 编码（模糊搜索）
     */
    private String code;

    /**
     * 名称（模糊搜索）
     */
    private String name;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 页码
     */
    private Integer page = 0;

    /**
     * 每页大小
     */
    private Integer size = 10;
}
