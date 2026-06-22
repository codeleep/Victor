package me.codeleep.victor.core.service.interview;

import me.codeleep.victor.core.service.dto.InterviewSessionVO;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 面试会话服务(面试线)。
 * <p>
 * 负责面试执行过程, 状态机:
 * <pre>
 *   READY -> IN_PROGRESS -> PAUSED / COMPLETED / ABANDONED
 *                         COMPLETED -> REPORT_GENERATING (交由报告线异步评估)
 * </pre>
 * 读取会话时对卡在 REPORT_GENERATING 的评估任务做自愈重试(委托报告线)。
 */
public interface InterviewSessionService {

    /** 创建/复用面试会话(READY -> IN_PROGRESS) */
    Long createSession(Long configId);

    /** 获取会话详情(读取时触发 REPORT_GENERATING 自愈) */
    InterviewSessionVO getSession(Long id);

    /** 获取用户的所有会话(读取时触发 REPORT_GENERATING 自愈) */
    List<InterviewSessionVO> listSessions();

    /** 启动面试 */
    void startInterview(Long sessionId);

    /** 暂停面试(IN_PROGRESS -> PAUSED) */
    void pauseInterview(Long sessionId);

    /** 恢复面试(PAUSED -> IN_PROGRESS) */
    void resumeInterview(Long sessionId);

    /** 结束面试(IN_PROGRESS -> COMPLETED -> REPORT_GENERATING, 触发异步评估) */
    void completeInterview(Long sessionId);

    /** 取消面试(任意状态 -> ABANDONED) */
    void cancelInterview(Long sessionId);

    /** 获取下一道题目 */
    String getNextQuestion(Long sessionId);

    /** 流式获取下一道题目 */
    Flux<String> streamNextQuestion(Long sessionId);

    /** 提交回答, 返回面试官下一句回应(追问/下一题/结束) */
    String submitAnswer(Long sessionId, String answer);

    /** 流式提交回答 */
    Flux<String> streamSubmitAnswer(Long sessionId, String answer);

    /** 跳过当前问题 */
    void skipQuestion(Long sessionId);

    /**
     * 单题追问次数兜底: 若当前题候选人回答次数已达上限(maxFollowUps),
     * 强制推进到下一道预备题(更新 currentQuestionId),避免面试官死磕一题。
     * 返回推进结果(是否推进、是否已结束面试)。
     */
    ForceAdvanceResult forceAdvanceIfLimitReached(Long sessionId, int maxFollowUps);

    /**
     * 强制推进结果
     *
     * @param advanced        是否执行了推进
     * @param finished        是否已结束面试(已到最后一题)
     * @param currentQuestionId 推进后的当前题目ID(结束时为 null)
     */
    record ForceAdvanceResult(boolean advanced, boolean finished, Long currentQuestionId) {}

    /** 获取提示 */
    String getHint(Long sessionId, String currentQuestion);

    /** 流式获取提示 */
    Flux<String> streamGetHint(Long sessionId, String currentQuestion);

    /** 获取面试总结 */
    String getSummary(Long sessionId);

    /** 流式获取面试总结 */
    Flux<String> streamGetSummary(Long sessionId);

    /** 获取对话历史 */
    List<InterviewTurnVO> getConversationHistory(Long sessionId);
}