package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.InterviewQuestionType;
import me.codeleep.victor.core.engine.InterviewEngine;
import me.codeleep.victor.core.engine.InterviewEngine.GeneratedQuestion;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.service.support.AsyncTaskRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 面试出题异步执行器(与 {@link ReportEvaluationExecutor} 对称: 题目生成 + Executor)。
 * <p>
 * 负责 {@code DRAFT/GENERATE_FAILED -> GENERATING -> READY/GENERATE_FAILED} 阶段的异步出题。
 * 通过 {@link AsyncTaskRegistry} 登记在途任务, 支持状态驱动的自愈:
 * 若状态为 GENERATING 但无在途任务(如重启后线程丢失), 调用 {@link #resumeIfStuck} 重新触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationExecutor {

    private final InterviewEngine interviewEngine;
    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final TransactionTemplate transactionTemplate;
    private final AsyncTaskRegistry asyncTaskRegistry;

    /**
     * 异步生成面试题目。同一配置不会重复触发(在途登记去重)。
     *
     * @param configId 面试配置ID
     */
    @Async
    public void generateAsync(Long configId) {
        if (!asyncTaskRegistry.start(configId)) {
            log.info("Question generation already in flight, skip: configId={}", configId);
            return;
        }
        log.info("Start async interview question generation: configId={}", configId);
        try {
            List<GeneratedQuestion> questions = interviewEngine.generateQuestionsForConfig(configId);
            saveGeneratedQuestions(configId, questions);
            log.info("Async interview question generation succeeded: configId={}, questionCount={}",
                    configId, questions.size());
        } catch (Exception e) {
            log.error("Async interview question generation failed: configId={}", configId, e);
            markGenerateFailed(configId, e.getMessage());
        } finally {
            asyncTaskRegistry.finish(configId);
        }
    }

    /**
     * 状态驱动的自愈: 若配置处于 GENERATING 状态但无在途任务, 重新触发出题。
     * <p>
     * 典型场景: 服务重启后异步线程丢失, 状态停留在 GENERATING。
     * 应在读取配置列表/详情时调用, 让卡住的状态自动恢复。
     *
     * @param configId 面试配置ID
     */
    public void resumeIfStuck(Long configId) {
        InterviewConfig config = interviewConfigMapper.selectById(configId);
        if (config == null || config.getStatus() != InterviewConfigStatus.GENERATING) {
            return;
        }
        if (asyncTaskRegistry.isRunning(configId)) {
            return;
        }
        log.warn("Detect stuck question generation, re-trigger: configId={}", configId);
        generateAsync(configId);
    }

    public void saveGeneratedQuestions(Long configId, List<GeneratedQuestion> questions) {
        transactionTemplate.executeWithoutResult(status -> {
            interviewQuestionMapper.delete(
                    new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getConfigId, configId)
            );

            int order = 1;
            for (GeneratedQuestion generated : questions) {
                InterviewQuestion question = new InterviewQuestion();
                question.setConfigId(configId);
                question.setOrderIndex(order++);
                question.setQuestionType(InterviewQuestionType.GENERATED);
                question.setQuestionText(generated.questionText());
                question.setAnswerHint(generated.answerHint());
                question.setSourceRecallRefs(generated.sourceRecallRefs());
                interviewQuestionMapper.insert(question);
            }

            InterviewConfig ready = new InterviewConfig();
            ready.setId(configId);
            ready.setStatus(InterviewConfigStatus.READY);
            ready.setGenerateError(null);
            interviewConfigMapper.updateById(ready);
        });
    }

    public void markGenerateFailed(Long configId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            InterviewConfig failed = new InterviewConfig();
            failed.setId(configId);
            failed.setStatus(InterviewConfigStatus.GENERATE_FAILED);
            failed.setGenerateError(errorMessage);
            interviewConfigMapper.updateById(failed);
        });
    }
}