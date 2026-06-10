package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import me.codeleep.victor.core.handler.JsonbTypeHandler;
import lombok.Data;
import me.codeleep.victor.common.enums.Speaker;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试对话回合实体
 */
@Data
@TableName(value = "interview_turn", autoResultMap = true)
public class InterviewTurn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 题目ID
     */
    private Long questionId;

    /**
     * 题内对话顺序
     */
    private Integer turnIndex;

    /**
     * 第几次作答
     */
    private Integer attemptNo;

    /**
     * 说话人
     */
    private Speaker speaker;

    /**
     * 是否追问
     */
    private Boolean isFollowup;

    /**
     * 文字内容
     */
    private String content;

    /**
     * 附件列表：音频/图片/代码/Mermaid图等
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Object> attachments;

    /**
     * 是否提示
     */
    private Boolean isHint;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
