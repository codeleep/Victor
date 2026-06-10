package me.codeleep.victor.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 简历更新请求
 */
@Data
public class ResumeUpdateRequest {

    /**
     * 解析后的文本内容（Markdown格式）
     */
    @NotBlank(message = "文本内容不能为空")
    private String rawText;
}
