package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.ResumeStatus;

import java.time.LocalDateTime;

/**
 * 简历VO
 */
@Data
public class ResumeVO {

    private Long id;

    /**
     * 简历名称
     */
    private String name;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 解析后的文本内容（Markdown格式）
     */
    private String rawText;

    /**
     * 状态
     */
    private ResumeStatus status;

    /**
     * 嵌入时间
     */
    private LocalDateTime embeddedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
