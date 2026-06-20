package me.codeleep.victor.core.interviewer;

import io.agentscope.core.ReActAgent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 面试官对象 - 每个面试会话独立实例（非 Spring 托管）
 * <p>
 * 持有一个常驻的 AgentScope ReActAgent（由 AgentFactory.buildTeam 一次性构建），
 * Agent 自带独立 Memory，对话记忆在内存中累积，并通过 AgentMemoryRepository 持久化。
 * 冷启动时从库恢复 Memory 重建 Agent。
 *
 * Memory 中含两类长期记忆：面试基础信息（不变）+ 当前题目（换题时通过 updateCurrentQuestion 替换）
 */
@Slf4j
@Getter
public class Interviewer {

    private final ReActAgent agent;
    private final AgentRunner agentRunner;
    private final String sessionId;
    private final Long userId;
    private final String agentKey;
    /** AgentScope 内部 session id（sessionId:agentKey），用于操作 AgentState */
    private final String agentSessionId;
    private final String userIdStr;

    public Interviewer(ReActAgent agent, AgentRunner agentRunner,
                       String sessionId, Long userId, String agentKey,
                       String agentSessionId, String userIdStr) {
        this.agent = agent;
        this.agentRunner = agentRunner;
        this.sessionId = sessionId;
        this.userId = userId;
        this.agentKey = agentKey;
        this.agentSessionId = agentSessionId;
        this.userIdStr = userIdStr;
        log.info("[Interviewer] 初始化完成: sessionId={}, agentKey={}", sessionId, agentKey);
    }

    /**
     * 流式对话
     */
    public Flux<AgentResult> chat(String input) {
        AgentContext context = buildContext(input);
        return agentRunner.streamRun(agent, context);
    }

    /**
     * 同步对话
     */
    public AgentResult chatSync(String input) {
        AgentContext context = buildContext(input);
        return agentRunner.run(agent, context);
    }

    /**
     * 更新当前题目（换题时调用）
     * 替换 Memory 中标记为 currentQuestion 的消息
     *
     * @param currentQuestionContext 新的题目/配置文本（由 InterviewContextBuilder.buildCurrentQuestionContext 拼好）
     */
    public void updateCurrentQuestion(String currentQuestionContext) {
        InterviewerFactory.replaceCurrentQuestion(agent, agentSessionId, userIdStr, currentQuestionContext);
    }

    /**
     * 释放 Agent 资源
     */
    public void close() {
        try {
            agent.close();
            log.info("[Interviewer] 已关闭: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[Interviewer] 关闭异常: sessionId={}", sessionId, e);
        }
    }

    private AgentContext buildContext(String input) {
        AgentContext context = new AgentContext(sessionId, userId, input);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentKey", agentKey);
        context.setMetadata(metadata);
        return context;
    }
}
