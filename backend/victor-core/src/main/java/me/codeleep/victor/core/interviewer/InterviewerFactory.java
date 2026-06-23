package me.codeleep.victor.core.interviewer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.engine.AgentTeamDefinitionFactory;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.memory.AgentMemoryRepository;
import me.codeleep.victor.infra.agent.runner.AgentFactory;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 面试官工厂 - 根据团队 key 构建 Interviewer 实例
 * 注入两类长期记忆：面试基础信息（不变）+ 当前题目（换题时更新）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewerFactory {

    private static final String DEFAULT_INTERVIEW_TEAM_KEY = "system-team-interview";

    /** metadata key：标记 Memory 中的上下文消息类型 */
    public static final String META_CONTEXT_KIND = "interviewContextKind";
    /** 面试基础信息（岗位+简历），整场不变 */
    public static final String KIND_BACKGROUND = "background";
    /** 当前题目+配置，换题时替换 */
    public static final String KIND_CURRENT_QUESTION = "currentQuestion";

    private final AgentFactory agentFactory;
    private final AgentRunner agentRunner;
    private final AgentTeamDefinitionFactory teamDefinitionFactory;
    private final AgentTeamMapper agentTeamMapper;
    private final AgentMemoryRepository agentMemoryRepository;

    /**
     * 构建面试官实例
     *
     * @param userId                 用户 ID
     * @param interviewSessionId     面试会话 ID（作为 Agent 记忆的 sessionId）
     * @param teamKey                团队 key（为空时用默认面试团队 key）
     * @param background             面试基础信息（岗位/简历），长期记忆，仅首次注入
     * @param currentQuestionContext 当前题目/配置，长期记忆，仅首次注入（换题时由 Interviewer.updateCurrentQuestion 替换）
     * @return Interviewer 实例，构建失败返回 null
     */
    public Interviewer create(Long userId, String interviewSessionId, String teamKey,
                               String background, String currentQuestionContext) {
        String key = (teamKey == null || teamKey.isEmpty()) ? DEFAULT_INTERVIEW_TEAM_KEY : teamKey;

        AgentTeam team = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, userId)
                        .eq(AgentTeam::getKey, key)
                        .last("LIMIT 1")
        );
        if (team == null) {
            log.error("[InterviewerFactory] 面试团队未找到: userId={}, teamKey={}", userId, key);
            return null;
        }

        AgentTeamDefinition teamDef = teamDefinitionFactory.build(team);
        if (teamDef == null || teamDef.getMainAgent() == null) {
            log.error("[InterviewerFactory] 团队定义构建失败: teamId={}", team.getId());
            return null;
        }

        String agentSessionId = interviewSessionId + ":" + teamDef.getMainAgent().getKey();
        String userIdStr = String.valueOf(userId);

        // 传递裸 interviewSessionId，让 AgentFactory.buildTeam 拼接 ":agentKey" 作为 defaultSessionId，
        // 避免二次拼接导致 AgentState 读写 key 不一致
        ReActAgent agent = agentFactory.buildTeam(
                teamDef, interviewSessionId, userIdStr, agentMemoryRepository);

        // 仅首次（Memory 为空）注入 background + currentQuestion
        injectContextIfEmpty(agent, agentSessionId, userIdStr, background, currentQuestionContext);
        // 始终用 DB 中的当前题目同步 Agent 记忆（覆盖首次注入 + 冷启动恢复两种场景）
        if (currentQuestionContext != null && !currentQuestionContext.isBlank()) {
            replaceCurrentQuestion(agent, agentSessionId, userIdStr, currentQuestionContext);
        }

        return new Interviewer(agent, agentRunner, interviewSessionId, userId,
                teamDef.getMainAgent().getKey(), agentSessionId, userIdStr);
    }

    /**
     * 仅在 AgentState 为空（首次创建）时注入 background + currentQuestion
     */
    private void injectContextIfEmpty(ReActAgent agent, String sessionId, String userId,
                                       String background, String currentQuestionContext) {
        try {
            AgentState state = agent.getAgentState(userId, sessionId);
            List<Msg> context = state.getContext();
            if (context != null && !context.isEmpty()) {
                // 冷启动恢复，已有记忆，不重复注入
                return;
            }
            if (background != null && !background.isBlank()) {
                state.contextMutable().add(buildContextMsg(background, KIND_BACKGROUND));
            }
            if (currentQuestionContext != null && !currentQuestionContext.isBlank()) {
                state.contextMutable().add(buildContextMsg(currentQuestionContext, KIND_CURRENT_QUESTION));
            }
            saveState(agent, state, sessionId, userId);
            log.info("[InterviewerFactory] 上下文已注入长期记忆: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[InterviewerFactory] 注入上下文失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 构建带类型标记的 system 上下文消息
     */
    public static Msg buildContextMsg(String content, String kind) {
        return Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(content)
                .metadata(Map.of(META_CONTEXT_KIND, kind))
                .build();
    }

    /**
     * 持久化 AgentState
     */
    public static void saveState(ReActAgent agent, AgentState state, String sessionId, String userId) {
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .agentState(state)
                .build();
        agent.saveAgentState(rc);
    }

    /**
     * 替换 Memory 中的 currentQuestion 消息（换题时调用）
     *
     * @param agent          ReActAgent
     * @param sessionId      agent session id
     * @param userId         user id
     * @param newContent     新的题目/配置文本
     */
    public static void replaceCurrentQuestion(ReActAgent agent, String sessionId, String userId, String newContent) {
        try {
            AgentState state = agent.getAgentState(userId, sessionId);
            List<Msg> messages = state.contextMutable();
            int replaceIndex = -1;
            for (int i = 0; i < messages.size(); i++) {
                Msg m = messages.get(i);
                Object kind = m.getMetadata() != null ? m.getMetadata().get(META_CONTEXT_KIND) : null;
                if (KIND_CURRENT_QUESTION.equals(kind)) {
                    replaceIndex = i;
                    break;
                }
            }
            Msg newMsg = buildContextMsg(newContent, KIND_CURRENT_QUESTION);
            if (replaceIndex >= 0) {
                messages.set(replaceIndex, newMsg);
            } else {
                messages.add(newMsg);
            }
            saveState(agent, state, sessionId, userId);
            log.info("[InterviewerFactory] 当前题目已更新: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[InterviewerFactory] 更新当前题目失败: sessionId={}", sessionId, e);
        }
    }
}
