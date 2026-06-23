package me.codeleep.victor.core.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试对话回合视图对象
 */
@Data
public class InterviewTurnVO {

    private Long id;

    private Long sessionId;

    private Long questionId;

    private Integer turnIndex;

    private Integer attemptNo;

    private String speaker;

    private Boolean isFollowup;

    private String content;

    /**
     * 推理过程文本（仅 AI turn），前端折叠展示
     */
    private String reasoning;

    /**
     * 结构化工具事件列表（仅 AI turn），前端渲染为任务块时间线
     */
    private List<Object> toolEvents;

    private List<Object> attachments;

    private Boolean isHint;

    private LocalDateTime createdAt;
}
