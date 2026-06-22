package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.core.engine.InterviewEngine;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.core.service.converter.InterviewTurnConverter;
import me.codeleep.victor.core.service.interview.InterviewSessionService;
import me.codeleep.victor.core.service.interview.InterviewReportService;
import me.codeleep.victor.core.service.interview.impl.InterviewSessionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试会话服务单元测试 - 验证单题追问次数兜底推进
 * {@link InterviewSessionServiceImpl#forceAdvanceIfLimitReached}
 */
class InterviewSessionServiceImplTest extends BaseServiceTest {

    private static final Long SESSION_ID = 100L;
    private static final Long CURRENT_QUESTION_ID = 10L;

    @Mock
    private InterviewConfigMapper interviewConfigMapper;
    @Mock
    private InterviewTurnMapper interviewTurnMapper;
    @Mock
    private InterviewQuestionMapper interviewQuestionMapper;
    @Mock
    private InterviewEngine interviewEngine;
    @Mock
    private InterviewTurnConverter turnConverter;
    @Mock
    private InterviewReportService reportService;

    @InjectMocks
    private InterviewSessionServiceImpl interviewService;

    private InterviewConfig inProgressConfig() {
        InterviewConfig config = new InterviewConfig();
        config.setId(SESSION_ID);
        config.setStatus(InterviewConfigStatus.IN_PROGRESS);
        config.setCurrentQuestionId(CURRENT_QUESTION_ID);
        when(interviewConfigMapper.selectById(SESSION_ID)).thenReturn(config);
        when(interviewConfigMapper.updateById(any(InterviewConfig.class))).thenReturn(1);
        when(interviewQuestionMapper.selectCount(any())).thenReturn(5L);
        return config;
    }

    private InterviewQuestion question(long id, int orderIndex) {
        InterviewQuestion q = new InterviewQuestion();
        q.setId(id);
        q.setConfigId(SESSION_ID);
        q.setOrderIndex(orderIndex);
        q.setQuestionText("Q" + orderIndex);
        return q;
    }

    @Test
    @DisplayName("未达追问上限:不推进")
    void forceAdvance_belowLimit_notAdvanced() {
        inProgressConfig();
        when(interviewQuestionMapper.selectById(CURRENT_QUESTION_ID)).thenReturn(question(10L, 1));
        when(interviewTurnMapper.selectCount(any())).thenReturn(3L);

        InterviewSessionService.ForceAdvanceResult result =
                interviewService.forceAdvanceIfLimitReached(SESSION_ID, 5);

        assertFalse(result.advanced());
        assertFalse(result.finished());
        assertEquals(10L, result.currentQuestionId());
        verify(interviewConfigMapper, never()).updateById(any(InterviewConfig.class));
    }

    @Test
    @DisplayName("达追问上限且有下一题:强制推进到下一道预备题")
    void forceAdvance_atLimitWithNext_advanced() {
        inProgressConfig();
        when(interviewQuestionMapper.selectById(CURRENT_QUESTION_ID)).thenReturn(question(10L, 1));
        when(interviewTurnMapper.selectCount(any())).thenReturn(5L);
        when(interviewQuestionMapper.selectOne(any())).thenReturn(question(20L, 2));

        InterviewSessionService.ForceAdvanceResult result =
                interviewService.forceAdvanceIfLimitReached(SESSION_ID, 5);

        assertTrue(result.advanced());
        assertFalse(result.finished());
        assertEquals(20L, result.currentQuestionId());
    }

    @Test
    @DisplayName("达追问上限且已是最后一题:结束面试")
    void forceAdvance_atLimitNoNext_finished() {
        InterviewConfig config = inProgressConfig();
        when(interviewQuestionMapper.selectById(CURRENT_QUESTION_ID)).thenReturn(question(10L, 1));
        when(interviewTurnMapper.selectCount(any())).thenReturn(5L);
        when(interviewQuestionMapper.selectOne(any())).thenReturn(null);

        InterviewSessionService.ForceAdvanceResult result =
                interviewService.forceAdvanceIfLimitReached(SESSION_ID, 5);

        assertTrue(result.advanced());
        assertTrue(result.finished());
        assertEquals(null, result.currentQuestionId());
        assertEquals(InterviewConfigStatus.COMPLETED, config.getStatus());
    }
}