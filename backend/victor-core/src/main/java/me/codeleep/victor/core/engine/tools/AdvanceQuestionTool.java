package me.codeleep.victor.core.engine.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.core.engine.InterviewContextBuilder;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.interviewer.Interviewer;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推进到下一题工具。
 * <p>
 * 面试官 Agent 自主评估候选人回答后,若认为当前题已无追问价值(候选人理解较浅、
 * 无深挖潜力),调用本工具切换到 interview_question 表中的下一道预备题。
 * <p>
 * 工具职责(严格基于预备题表,不即时生成新题):
 * <ul>
 *   <li>按 order_index 取当前题的下一道预备题</li>
 *   <li>更新 interview_config.current_question_id</li>
 *   <li>同步面试官 Agent 的"当前题目"记忆</li>
 *   <li>返回下一题题干,供面试官用自然口吻提问</li>
 * </ul>
 * 已是最后一题时返回结束信号,面试官应据此收尾面试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvanceQuestionTool implements AgentTool {

    /** RuntimeContext.getExtra() 中的 key:当前会话的 Interviewer 实例(由 Interviewer 透传) */
    public static final String EXTRA_INTERVIEWER = "__interviewer";

    /** interview_config 中暂存"是否已到最后一题"等推进结果,供处理器兜底读取 */
    public static final String EXTRA_INTERVIEW_FINISHED = "__interviewFinished";

    private final InterviewConfigMapper interviewConfigMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewContextBuilder interviewContextBuilder;

    @Tool(name = "advance_to_next_question",
          description = "推进到下一道预备面试题。当候选人当前题已无追问价值(理解较浅或已充分展示深度)时调用。"
                  + "调用后系统自动从预备题库取出下一题并更新面试官记忆,你只需依据返回的题干用自然口吻提问。"
                  + "若返回 finished=true 表示已到最后一题,请据此结束本次面试。")
    public Object advanceToNextQuestion(RuntimeContext runtimeContext) {
        if (runtimeContext == null || runtimeContext.getSessionId() == null) {
            return errorResult("面试上下文缺失,无法推进");
        }
        long sessionId;
        try {
            sessionId = Long.parseLong(runtimeContext.getSessionId());
        } catch (NumberFormatException e) {
            return errorResult("面试会话ID无效: " + runtimeContext.getSessionId());
        }

        InterviewConfig config = interviewConfigMapper.selectById(sessionId);
        if (config == null) {
            return errorResult("面试会话不存在: " + sessionId);
        }
        if (config.getStatus() != InterviewConfigStatus.IN_PROGRESS) {
            return errorResult("面试不在进行中,当前状态: " + config.getStatus());
        }

        InterviewQuestion next = getQuestionAfter(config);
        if (next == null) {
            runtimeContext.getExtra().put(EXTRA_INTERVIEW_FINISHED, Boolean.TRUE);
            log.info("[AdvanceQuestionTool] 已到最后一题,面试将结束: sessionId={}", sessionId);
            return finishedResult();
        }

        config.setCurrentQuestionId(next.getId());
        interviewConfigMapper.updateById(config);

        // 同步面试官 Agent 的当前题目记忆(避免 Agent 仍以为在旧题上)
        Interviewer interviewer = extractInterviewer(runtimeContext);
        if (interviewer != null) {
            try {
                interviewer.updateCurrentQuestion(
                        interviewContextBuilder.buildCurrentQuestionContext(config, next));
            } catch (Exception e) {
                log.warn("[AdvanceQuestionTool] 更新面试官题目记忆失败: sessionId={}", sessionId, e);
            }
        }

        log.info("[AdvanceQuestionTool] 推进到下一题: sessionId={}, questionId={}, order={}",
                sessionId, next.getId(), next.getOrderIndex());
        return nextQuestionResult(next);
    }

    private InterviewQuestion getQuestionAfter(InterviewConfig config) {
        InterviewQuestion current = null;
        if (config.getCurrentQuestionId() != null) {
            current = interviewQuestionMapper.selectById(config.getCurrentQuestionId());
        }
        Integer currentOrder = current != null ? current.getOrderIndex() : 0;
        return interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getConfigId, config.getId())
                        .gt(InterviewQuestion::getOrderIndex, currentOrder)
                        .orderByAsc(InterviewQuestion::getOrderIndex)
                        .last("LIMIT 1")
        );
    }

    @SuppressWarnings("unchecked")
    private Interviewer extractInterviewer(RuntimeContext runtimeContext) {
        Object obj = runtimeContext.getExtra().get(EXTRA_INTERVIEWER);
        return obj instanceof Interviewer ? (Interviewer) obj : null;
    }

    private Map<String, Object> nextQuestionResult(InterviewQuestion next) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanced", true);
        result.put("finished", false);
        result.put("questionId", next.getId());
        result.put("orderIndex", next.getOrderIndex());
        result.put("questionText", next.getQuestionText());
        result.put("instruction", "请依据上述题干,用自然口吻向候选人提出这道新题,不要照念题干也不要自行改写为其它题目。");
        return result;
    }

    private Map<String, Object> finishedResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanced", false);
        result.put("finished", true);
        result.put("instruction", "已无更多预备题。请对本次面试做简短总结并礼貌结束,不要再提出新问题。");
        return result;
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanced", false);
        result.put("finished", false);
        result.put("error", message);
        return result;
    }
}