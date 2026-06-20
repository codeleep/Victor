package me.codeleep.victor.core.interviewer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.core.engine.InterviewContextBuilder;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试上下文恢复器 - 构建 Interviewer 并恢复面试上下文
 * 返回 {@link InterviewContextResult}，不依赖 web 层类型
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewContextRestorer {

    private final InterviewConfigMapper configMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewerFactory interviewerFactory;
    private final InterviewContextBuilder contextBuilder;

    /**
     * 确保面试上下文就绪
     *
     * @param interviewSessionId 面试会话 ID
     * @return 上下文结果；code 为负数表示错误
     */
    public InterviewContextResult ensureContext(Long interviewSessionId) {
        if (interviewSessionId == null) {
            log.warn("[Restorer] InterviewSessionId is null");
            return error(-1);
        }

        InterviewConfig config = configMapper.selectById(interviewSessionId);
        if (config == null) {
            log.warn("[Restorer] Interview config not found: {}", interviewSessionId);
            return error(-1);
        }
        if (config.getStatus() == InterviewConfigStatus.COMPLETED || config.getStatus() == InterviewConfigStatus.ABANDONED) {
            log.warn("[Restorer] Interview config status not recoverable: {}, status={}", interviewSessionId, config.getStatus());
            return error(-2);
        }

        if (!isRunnableStatus(config.getStatus())) {
            log.warn("[Restorer] Interview config is not runnable: configId={}, status={}",
                    interviewSessionId, config.getStatus());
            return error(-4);
        }

        InterviewQuestion currentQuestion = resolveCurrentQuestion(config);
        if (currentQuestion == null) {
            log.warn("[Restorer] No generated interview question found: configId={}", interviewSessionId);
            return error(-4);
        }

        String teamKey = resolveInterviewTeamKey(config);
        String background = contextBuilder.buildInterviewBackground(config);
        String currentQuestionContext = contextBuilder.buildCurrentQuestionContext(config, currentQuestion);
        Interviewer interviewer = interviewerFactory.create(config.getUserId(),
                String.valueOf(interviewSessionId), teamKey, background, currentQuestionContext);
        if (interviewer == null) {
            log.error("[Restorer] 面试官实例构建失败: userId={}, teamKey={}", config.getUserId(), teamKey);
            return error(-3);
        }

        List<InterviewTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, interviewSessionId)
                        .orderByAsc(InterviewTurn::getTurnIndex)
        );

        if (config.getStatus() == InterviewConfigStatus.READY || config.getStatus() == InterviewConfigStatus.PAUSED) {
            config.setStatus(InterviewConfigStatus.IN_PROGRESS);
            if (config.getStartedAt() == null) {
                config.setStartedAt(LocalDateTime.now());
            }
            configMapper.updateById(config);
            log.info("[Restorer] Config status updated to IN_PROGRESS: {}", interviewSessionId);
        }

        int turnCount = turns != null ? turns.size() : 0;
        log.info("[Restorer] Context ready: session={}, agentKey={}, questionId={}, turns={}",
                interviewSessionId, interviewer.getAgentKey(), currentQuestion.getId(), turnCount);

        return InterviewContextResult.builder()
                .interviewer(interviewer)
                .agentKey(interviewer.getAgentKey())
                .userId(config.getUserId())
                .interviewSessionId(interviewSessionId)
                .currentQuestionId(currentQuestion.getId())
                .turnCount(turnCount)
                .code(turnCount)
                .build();
    }

    private InterviewContextResult error(int code) {
        InterviewContextResult r = new InterviewContextResult();
        r.setCode(code);
        return r;
    }

    private boolean isRunnableStatus(InterviewConfigStatus status) {
        return status == InterviewConfigStatus.READY
                || status == InterviewConfigStatus.IN_PROGRESS
                || status == InterviewConfigStatus.PAUSED;
    }

    private InterviewQuestion resolveCurrentQuestion(InterviewConfig config) {
        InterviewQuestion question = null;
        if (config.getCurrentQuestionId() != null) {
            question = questionMapper.selectById(config.getCurrentQuestionId());
        }
        if (question == null) {
            question = questionMapper.selectOne(
                    new LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getConfigId, config.getId())
                            .orderByAsc(InterviewQuestion::getOrderIndex)
                            .last("LIMIT 1")
            );
            if (question != null && !question.getId().equals(config.getCurrentQuestionId())) {
                InterviewConfig update = new InterviewConfig();
                update.setId(config.getId());
                update.setCurrentQuestionId(question.getId());
                configMapper.updateById(update);
                config.setCurrentQuestionId(question.getId());
            }
        }
        return question;
    }

    private String resolveInterviewTeamKey(InterviewConfig config) {
        List<String> teamConfig = config.getTeamConfig();
        String defaultKey = "system-team-interview";
        if (teamConfig != null) {
            for (String key : teamConfig) {
                if (defaultKey.equals(key)) {
                    return key;
                }
            }
            if (!teamConfig.isEmpty()) {
                return teamConfig.get(0);
            }
        }
        return defaultKey;
    }
}
