package me.codeleep.victor.core.engine;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 面试引擎接口
 * 负责面试流程的核心逻辑：出题、评估、追问、提示
 */
public interface InterviewEngine {

    /**
     * 根据面试配置生成题目快照。
     *
     * @param configId 配置ID
     * @return 题目列表
     */
    List<GeneratedQuestion> generateQuestionsForConfig(Long configId);

    /**
     * 生成下一道题目
     *
     * @param sessionId 会话ID
     * @return 题目内容
     */
    String generateQuestion(Long sessionId);

    /**
     * 流式生成下一道题目
     *
     * @param sessionId 会话ID
     * @return 题目内容流
     */
    Flux<String> streamGenerateQuestion(Long sessionId);

    /**
     * 评估回答
     *
     * @param sessionId 会话ID
     * @param answer 回答内容
     * @return 评估结果
     */
    EvaluationResult evaluateAnswer(Long sessionId, String answer);

    /**
     * 流式评估回答
     *
     * @param sessionId 会话ID
     * @param answer 回答内容
     * @return 评估结果流
     */
    Flux<String> streamEvaluateAnswer(Long sessionId, String answer);

    /**
     * 生成追问
     *
     * @param sessionId 会话ID
     * @param previousAnswer 之前的回答
     * @return 追问内容
     */
    String generateFollowUp(Long sessionId, String previousAnswer);

    /**
     * 流式生成追问
     *
     * @param sessionId 会话ID
     * @param previousAnswer 之前的回答
     * @return 追问内容流
     */
    Flux<String> streamGenerateFollowUp(Long sessionId, String previousAnswer);

    /**
     * 生成提示
     *
     * @param sessionId 会话ID
     * @param currentQuestion 当前题目
     * @return 提示内容
     */
    String generateHint(Long sessionId, String currentQuestion);

    /**
     * 流式生成提示
     *
     * @param sessionId 会话ID
     * @param currentQuestion 当前题目
     * @return 提示内容流
     */
    Flux<String> streamGenerateHint(Long sessionId, String currentQuestion);

    /**
     * 生成面试总结
     *
     * @param sessionId 会话ID
     * @return 总结内容
     */
    String generateSummary(Long sessionId);

    /**
     * 流式生成面试总结
     *
     * @param sessionId 会话ID
     * @return 总结内容流
     */
    Flux<String> streamGenerateSummary(Long sessionId);

    /**
     * 评估结果
     */
    record EvaluationResult(
            NextStep nextStep,
            String content,
            Double score,
            String feedback,
            List<String> keyPoints
    ) {}

    /**
     * 生成的面试题目快照
     */
    record GeneratedQuestion(
            String questionText,
            Map<String, Object> answerHint,
            List<Map<String, Object>> sourceRecallRefs
    ) {}

    /**
     * 下一步动作
     */
    enum NextStep {
        /**
         * 继续追问
         */
        FOLLOW_UP,
        /**
         * 下一道题目
         */
        NEXT_QUESTION,
        /**
         * 结束面试
         */
        END
    }
}
