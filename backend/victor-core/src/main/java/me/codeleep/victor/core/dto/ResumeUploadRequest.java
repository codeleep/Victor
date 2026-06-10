package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 简历上传请求DTO
 */
@Data
public class ResumeUploadRequest {

    /**
     * 简历名称
     */
    @NotBlank(message = "简历名称不能为空")
    private String name;
}
