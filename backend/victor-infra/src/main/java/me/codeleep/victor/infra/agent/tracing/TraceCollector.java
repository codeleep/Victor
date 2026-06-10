package me.codeleep.victor.infra.agent.tracing;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪收集器
 * 收集和查询 Agent 执行过程中的追踪记录
 */
@Slf4j
public class TraceCollector {

    /**
     * 按会话 ID 存储追踪记录
     */
    private final Map<String, List<AgentTrace>> tracesBySession = new ConcurrentHashMap<>();

    /**
     * 添加追踪记录
     */
    public void addTrace(String sessionId, AgentTrace trace) {
        if (sessionId == null) {
            log.debug("[Trace] sessionId=null (跳过持久化), action={}, agent={}, duration={}ms",
                    trace.getAction(), trace.getAgentName(), trace.getDurationMs());
            return;
        }
        tracesBySession.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(trace);
        log.debug("[Trace] sessionId={}, action={}, agent={}, duration={}ms",
                sessionId, trace.getAction(), trace.getAgentName(), trace.getDurationMs());
    }

    /**
     * 获取会话的所有追踪记录
     */
    public List<AgentTrace> getTraces(String sessionId) {
        return tracesBySession.getOrDefault(sessionId, List.of());
    }

    /**
     * 获取会话的追踪记录（按动作类型筛选）
     */
    public List<AgentTrace> getTracesByAction(String sessionId, String action) {
        return getTraces(sessionId).stream()
                .filter(t -> action.equals(t.getAction()))
                .toList();
    }

    /**
     * 获取会话总耗时
     */
    public long getTotalDuration(String sessionId) {
        return getTraces(sessionId).stream()
                .mapToLong(AgentTrace::getDurationMs)
                .sum();
    }

    /**
     * 清除会话的追踪记录
     */
    public void clear(String sessionId) {
        tracesBySession.remove(sessionId);
    }

    /**
     * 清除所有追踪记录
     */
    public void clearAll() {
        tracesBySession.clear();
    }
}
