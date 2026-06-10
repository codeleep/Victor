package me.codeleep.victor.core.dto;

import lombok.Data;

/**
 * 岗位查询请求DTO
 */
@Data
public class JobQueryRequest {

    /**
     * 页码
     */
    private Integer page = 0;

    /**
     * 每页大小
     */
    private Integer size = 10;
}
