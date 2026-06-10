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

    private List<Object> attachments;

    private Boolean isHint;

    private LocalDateTime createdAt;
}
