package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.engine.InterviewEngine;
import me.codeleep.victor.core.engine.InterviewEngine.EvaluationResult;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.core.service.converter.InterviewTurnConverter;
import me.codeleep.victor.core.service.dto.InterviewSessionVO;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import me.codeleep.victor.core.service.interview.InterviewSessionService;
import me.codeleep.victor.core.service.interview.InterviewReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试会话服务实现(面试线)。
 * <p>
 * 负责面试执行过程: 会话生命周期、问答推进、提示、总结、历史。
 * 结束面试时委托 {@link InterviewReportService} 异步生成评估报告;
 * 读取会话时对卡在 REPORT_GENERATING 的评估任务做自愈重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewEngine interviewEngine;
    private final InterviewTurnConverter turnConverter;
    private final InterviewReportService reportService;

    @Override
    @Transactional
    public Long createSession(Long configId) {
        InterviewConfig config = getConfigEntityOrThrow(configId);
        if (config.getStatus() == InterviewConfigStatus.COMPLETED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session is already completed");
        }
        if (config.getStatus() == InterviewConfigStatus.ABANDONED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session is already abandoned");
        }
        if (config.getStatus() == InterviewConfigStatus.IN_PROGRESS || config.getStatus() == InterviewConfigStatus.PAUSED) {
            return config.getId();
        }
        ensureConfigReadyWithQuestions(config);
        config.setStatus(InterviewConfigStatus.IN_PROGRESS);
        if (config.getStartedAt() == null) {
            config.setStartedAt(LocalDateTime.now());
        }
        interviewConfigMapper.updateById(config);
        log.info("Interview session started on config: configId={}", configId);
        return config.getId();
    }

    @Override
    public InterviewSessionVO getSession(Long id) {
        InterviewConfig config = interviewConfigMapper.selectById(id);
        if (config == null) {
            return null;
        }
        // 状态自愈: 评估卡在 REPORT_GENERATING 但无在途任务时重新触发
        if (config.getStatus() == InterviewConfigStatus.REPORT_GENERATING) {
            reportService.resumeIfStuck(id);
        }
        return toSessionVO(config);
    }

    @Override
    public List<InterviewSessionVO> listSessions() {
        Long userId = UserContext.getUserId();
        List<InterviewConfig> configs = interviewConfigMapper.selectList(
                new LambdaQueryWrapper<InterviewConfig>()
                        .eq(InterviewConfig::getUserId, userId)
                        .in(InterviewConfig::getStatus,
                                InterviewConfigStatus.IN_PROGRESS,
                                InterviewConfigStatus.PAUSED,
                                InterviewConfigStatus.COMPLETED,
                                InterviewConfigStatus.REPORT_GENERATING,
                                InterviewConfigStatus.REPORT_COMPLETED,
                                InterviewConfigStatus.REPORT_FAILED,
                                InterviewConfigStatus.ABANDONED)
                        .orderByDesc(InterviewConfig::getCreatedAt)
        );
        // 状态自愈: 批量恢复卡在 REPORT_GENERATING 的评估任务
        configs.stream()
                .filter(config -> config.getStatus() == InterviewConfigStatus.REPORT_GENERATING)
                .forEach(config -> reportService.resumeIfStuck(config.getId()));
        return configs.stream()
                .map(this::toSessionVO)
                .toList();
    }

    @Override
    @Transactional
    public void startInterview(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session is not in progress");
        }
        ensureConfigHasQuestions(config);
        if (config.getStartedAt() == null) {
            config.setStartedAt(LocalDateTime.now());
        }
        interviewConfigMapper.updateById(config);
        log.info("Interview started: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void pauseInterview(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session cannot be paused");
        }
        config.setStatus(InterviewConfigStatus.PAUSED);
        config.setPausedAt(LocalDateTime.now());
        interviewConfigMapper.updateById(config);
    }

    @Override
    @Transactional
    public void resumeInterview(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.PAUSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session cannot be resumed");
        }
        config.setStatus(InterviewConfigStatus.IN_PROGRESS);
        interviewConfigMapper.updateById(config);
    }

    @Override
    @Transactional
    public void completeInterview(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() == InterviewConfigStatus.ABANDONED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Abandoned interview cannot be completed");
        }
        config.setStatus(InterviewConfigStatus.COMPLETED);
        config.setCompletedAt(LocalDateTime.now());
        interviewConfigMapper.updateById(config);

        // 面试结束后异步生成评估报告,状态流转: COMPLETED -> REPORT_GENERATING -> REPORT_COMPLETED/REPORT_FAILED
        // 标记为报告生成中(随事务提交),让面试记录立即展示"生成中"状态
        interviewConfigMapper.update(
                null,
                new LambdaUpdateWrapper<InterviewConfig>()
                        .eq(InterviewConfig::getId, sessionId)
                        .set(InterviewConfig::getStatus, InterviewConfigStatus.REPORT_GENERATING));
        // 评估为异步流程,需在事务提交后触发,否则评估线程读不到未提交的报告记录
        triggerReportGeneration(sessionId);
    }

    /**
     * 事务提交后触发报告生成,失败则标记 REPORT_FAILED 供前端重试。
     */
    private void triggerReportGeneration(Long sessionId) {
        Runnable trigger = () -> {
            try {
                reportService.generateReport(sessionId);
            } catch (Exception e) {
                log.error("Trigger report generation failed: sessionId={}", sessionId, e);
                interviewConfigMapper.update(
                        null,
                        new LambdaUpdateWrapper<InterviewConfig>()
                                .eq(InterviewConfig::getId, sessionId)
                                .set(InterviewConfig::getStatus, InterviewConfigStatus.REPORT_FAILED));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
    }

    @Override
    @Transactional
    public void skipQuestion(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session is not in progress");
        }
        InterviewQuestion next = getQuestionAfter(config.getId(), config.getCurrentQuestionId());
        if (next == null) {
            completeInterview(sessionId);
            return;
        }
        config.setCurrentQuestionId(next.getId());
        interviewConfigMapper.updateById(config);
    }

    @Override
    @Transactional
    public void cancelInterview(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() == InterviewConfigStatus.COMPLETED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Completed interview cannot be cancelled");
        }
        config.setStatus(InterviewConfigStatus.ABANDONED);
        config.setCompletedAt(LocalDateTime.now());
        interviewConfigMapper.updateById(config);
    }

    @Override
    public String getNextQuestion(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        ensureSessionInProgress(config);
        return getPreparedQuestion(config).getQuestionText();
    }

    @Override
    public Flux<String> streamNextQuestion(Long sessionId) {
        try {
            return Flux.just(getNextQuestion(sessionId));
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    @Override
    @Transactional
    public String submitAnswer(Long sessionId, String answer) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        ensureSessionInProgress(config);
        EvaluationResult evaluation = interviewEngine.evaluateAnswer(sessionId, answer);
        return processEvaluationResult(sessionId, config, evaluation);
    }

    @Override
    public Flux<String> streamSubmitAnswer(Long sessionId, String answer) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        ensureSessionInProgress(config);
        return interviewEngine.streamEvaluateAnswer(sessionId, answer);
    }

    @Override
    @Transactional
    public ForceAdvanceResult forceAdvanceIfLimitReached(Long sessionId, int maxFollowUps) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            return new ForceAdvanceResult(false, false, config.getCurrentQuestionId());
        }
        InterviewQuestion current = getPreparedQuestion(config);
        int answeredCount = countUserAnswersForQuestion(sessionId, current.getId());
        if (answeredCount < maxFollowUps) {
            return new ForceAdvanceResult(false, false, current.getId());
        }
        // 已达追问上限,强制推进到下一题
        InterviewQuestion next = getQuestionAfter(config.getId(), current.getId());
        if (next == null) {
            completeInterview(sessionId);
            log.info("[Interview] 单题追问达上限且无下一题,结束面试: sessionId={}, questionId={}, answers={}",
                    sessionId, current.getId(), answeredCount);
            return new ForceAdvanceResult(true, true, null);
        }
        config.setCurrentQuestionId(next.getId());
        interviewConfigMapper.updateById(config);
        log.info("[Interview] 单题追问达上限,强制推进: sessionId={}, from={}, to={}, answers={}",
                sessionId, current.getId(), next.getId(), answeredCount);
        return new ForceAdvanceResult(true, false, next.getId());
    }

    private int countUserAnswersForQuestion(Long sessionId, Long questionId) {
        Long count = interviewTurnMapper.selectCount(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .eq(InterviewTurn::getQuestionId, questionId)
                        .eq(InterviewTurn::getSpeaker, Speaker.USER)
        );
        return count != null ? count.intValue() : 0;
    }

    @Override
    public String getHint(Long sessionId, String currentQuestion) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config == null || !Boolean.TRUE.equals(config.getHintEnabled())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Hint is not enabled for this interview");
        }
        return interviewEngine.generateHint(sessionId, currentQuestion);
    }

    @Override
    public Flux<String> streamGetHint(Long sessionId, String currentQuestion) {
        try {
            InterviewConfig config = getSessionConfigOrThrow(sessionId);
            if (config == null || !Boolean.TRUE.equals(config.getHintEnabled())) {
                return Flux.error(new BusinessException(ResultCode.BAD_REQUEST, "Hint is not enabled for this interview"));
            }
            return interviewEngine.streamGenerateHint(sessionId, currentQuestion);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    @Override
    public String getSummary(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.COMPLETED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview is not completed");
        }
        return interviewEngine.generateSummary(sessionId);
    }

    @Override
    public Flux<String> streamGetSummary(Long sessionId) {
        InterviewConfig config = getSessionConfigOrThrow(sessionId);
        if (config.getStatus() != InterviewConfigStatus.COMPLETED) {
            return Flux.error(new BusinessException(ResultCode.BAD_REQUEST, "Interview is not completed"));
        }
        return interviewEngine.streamGenerateSummary(sessionId);
    }

    @Override
    public List<InterviewTurnVO> getConversationHistory(Long sessionId) {
        List<InterviewTurn> turns = interviewTurnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, sessionId)
                        .orderByAsc(InterviewTurn::getCreatedAt)
        );
        return turnConverter.toVOList(turns);
    }

    // ==================== 内部辅助 ====================

    private String processEvaluationResult(Long sessionId, InterviewConfig config, EvaluationResult evaluation) {
        return switch (evaluation.nextStep()) {
            case FOLLOW_UP -> interviewEngine.generateFollowUp(sessionId, evaluation.content());
            case NEXT_QUESTION -> moveToNextQuestion(config);
            case END -> {
                completeInterview(sessionId);
                yield "Interview completed. " + (evaluation.content() != null ? evaluation.content() : "");
            }
        };
    }

    private String moveToNextQuestion(InterviewConfig config) {
        InterviewQuestion current = getPreparedQuestion(config);
        InterviewQuestion next = getQuestionAfter(config.getId(), current.getId());
        if (next == null) {
            completeInterview(config.getId());
            return "Interview completed.";
        }
        config.setCurrentQuestionId(next.getId());
        interviewConfigMapper.updateById(config);
        return next.getQuestionText();
    }

    private InterviewQuestion getQuestionAfter(Long configId, Long currentQuestionId) {
        if (currentQuestionId == null) {
            return getFirstQuestion(configId);
        }
        InterviewQuestion current = interviewQuestionMapper.selectById(currentQuestionId);
        if (current == null) {
            return getFirstQuestion(configId);
        }
        return interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getConfigId, configId)
                        .gt(InterviewQuestion::getOrderIndex, current.getOrderIndex())
                        .orderByAsc(InterviewQuestion::getOrderIndex)
                        .last("LIMIT 1")
        );
    }

    private InterviewQuestion getPreparedQuestion(InterviewConfig config) {
        InterviewQuestion question = null;
        if (config.getCurrentQuestionId() != null) {
            question = interviewQuestionMapper.selectById(config.getCurrentQuestionId());
        }
        if (question == null) {
            question = getFirstQuestion(config.getId());
        }
        if (question == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview questions are empty");
        }
        if (!question.getId().equals(config.getCurrentQuestionId())) {
            config.setCurrentQuestionId(question.getId());
            interviewConfigMapper.updateById(config);
        }
        return question;
    }

    private InterviewQuestion getFirstQuestion(Long configId) {
        return interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getConfigId, configId)
                        .orderByAsc(InterviewQuestion::getOrderIndex)
                        .last("LIMIT 1")
        );
    }

    private void ensureConfigReadyWithQuestions(InterviewConfig config) {
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Interview config not found");
        }
        if (config.getStatus() != InterviewConfigStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview questions are not ready");
        }
        ensureConfigHasQuestions(config);
    }

    private void ensureConfigHasQuestions(InterviewConfig config) {
        Long questionCount = interviewQuestionMapper.selectCount(
                new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getConfigId, config.getId())
        );
        if (questionCount == null || questionCount == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview questions are empty");
        }
    }

    private void ensureSessionInProgress(InterviewConfig config) {
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Interview session is not in progress");
        }
        ensureConfigHasQuestions(config);
    }

    private InterviewConfig getConfigEntityOrThrow(Long id) {
        InterviewConfig config = interviewConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Interview config not found");
        }
        return config;
    }

    private InterviewConfig getSessionConfigOrThrow(Long id) {
        InterviewConfig config = interviewConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Interview session not found");
        }
        return config;
    }

    private InterviewSessionVO toSessionVO(InterviewConfig config) {
        InterviewSessionVO vo = new InterviewSessionVO();
        vo.setId(config.getId());
        vo.setConfigId(config.getId());
        vo.setConfigName(config.getName());
        vo.setStatus(config.getStatus());
        vo.setCurrentQuestionId(config.getCurrentQuestionId());
        vo.setStartedAt(config.getStartedAt());
        vo.setPausedAt(config.getPausedAt());
        vo.setCompletedAt(config.getCompletedAt());
        vo.setCreatedAt(config.getCreatedAt());
        return vo;
    }
}