package me.codeleep.victor.core.dto;

import lombok.Data;

/**
 * 元数据VO
 */
@Data
public class MetadataVO {

    /**
     * ID
     */
    private Long id;

    /**
     * 分类
     */
    private String category;

    /**
     * 编码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 扩展数据
     */
    private Object extraData;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 是否启用
     */
    private Boolean isActive;
}
