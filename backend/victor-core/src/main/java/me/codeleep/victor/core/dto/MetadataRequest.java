package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 元数据请求
 */
@Data
public class MetadataRequest {

    /**
     * 分类
     */
    @NotBlank(message = "分类不能为空")
    @Size(max = 100, message = "分类长度不能超过100")
    private String category;

    /**
     * 编码
     */
    @NotBlank(message = "编码不能为空")
    @Size(max = 50, message = "编码长度不能超过50")
    private String code;

    /**
     * 名称
     */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100")
    private String name;

    /**
     * 描述
     */
    @Size(max = 255, message = "描述长度不能超过255")
    private String description;

    /**
     * 扩展数据 (JSON格式)
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
