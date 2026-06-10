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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionGenerationService {

    private final InterviewEngine interviewEngine;
    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void generateAsync(Long configId) {
        log.info("Start async interview question generation: configId={}", configId);
        try {
            List<GeneratedQuestion> questions = interviewEngine.generateQuestionsForConfig(configId);
            saveGeneratedQuestions(configId, questions);
            log.info("Async interview question generation succeeded: configId={}, questionCount={}",
                    configId, questions.size());
        } catch (Exception e) {
            log.error("Async interview question generation failed: configId={}", configId, e);
            markGenerateFailed(configId, e.getMessage());
        }
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
