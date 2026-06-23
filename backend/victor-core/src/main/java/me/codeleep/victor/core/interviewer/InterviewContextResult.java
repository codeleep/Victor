package me.codeleep.victor.core.interviewer;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 面试上下文恢复结果
 * 由 InterviewContextRestorer 产出，web 层据此填充 ProcessingContext
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewContextResult {

    /** 面试官实例（持有常驻 ReActAgent） */
    private Interviewer interviewer;

    /** Agent key */
    private String agentKey;

    /** 用户 ID */
    private Long userId;

    /** 面试会话 ID */
    private Long interviewSessionId;

    /** 当前题目 ID */
    private Long currentQuestionId;

    /** 对话轮数 */
    private int turnCount;

    /** 错误码（>=0 表示成功） */
    private int code;
}
