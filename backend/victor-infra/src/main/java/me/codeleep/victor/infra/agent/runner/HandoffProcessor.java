package me.codeleep.victor.infra.agent.runner;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.handoff.HandoffInputData;

import java.util.ArrayList;
import java.util.List;

/**
 * Handoff 处理器，负责检测和执行 Agent 间的控制转移。
 *
 * <p>从 AgentRunnerImpl 中提取，职责包括：</p>
 * <ul>
 *   <li>判断工具调用是否为 Handoff</li>
 *   <li>查找 Handoff 目标 Agent</li>
 *   <li>应用 Input Filter 过滤对话历史</li>
 *   <li>执行 Agent 切换</li>
 * </ul>
 */
@Slf4j
public class HandoffProcessor {

    /**
     * 判断指定工具是否为 Handoff 工具。
     *
     * @param agent    当前 Agent 定义
     * @param toolName 工具名称
     * @return true 表示是 Handoff 工具
     */
    public boolean isHandoffTool(AgentDefinition agent, String toolName) {
        return agent.getHandoffs().stream()
                .anyMatch(h -> h.getToolName().equals(toolName));
    }

    /**
     * 查找匹配的 Handoff 定义。
     *
     * @param agent    当前 Agent 定义
     * @param toolName Handoff 工具名称
     * @return Handoff 定义，不存在则返回 null
     */
    public Handoff findHandoff(AgentDefinition agent, String toolName) {
        return agent.getHandoffs().stream()
                .filter(h -> h.getToolName().equals(toolName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 处理 Handoff 调用，执行 Agent 切换。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>查找目标 Agent</li>
     *   <li>应用 Input Filter（如有）</li>
     *   <li>返回切换后的 AgentDefinition</li>
     * </ol>
     *
     * @param handoff            Handoff 定义
     * @param toolCall           工具调用信息
     * @param context            执行上下文
     * @param handoffMessageIndex Handoff 前的消息索引，用于 Input Filter
     * @return 目标 AgentDefinition，不存在则返回 null
     */
    public AgentDefinition processHandoff(Handoff handoff, AgentResult.ToolCall toolCall,
                                           AgentContext context, int handoffMessageIndex) {
        AgentDefinition targetAgent = handoff.getTargetAgent();
        if (targetAgent == null) {
            return null;
        }

        // 应用 Input Filter
        if (handoff.getInputFilter() != null) {
            applyInputFilter(handoff, context, handoffMessageIndex);
        }

        log.info("Handoff 切换: {} → {}", toolCall.getName(), targetAgent.getName());
        return targetAgent;
    }

    /**
     * 应用 Input Filter，过滤对话历史。
     */
    private void applyInputFilter(Handoff handoff, AgentContext context, int handoffMessageIndex) {
        List<AgentContext.ChatMessage> allHistory = context.getConversationHistory();
        List<AgentContext.ChatMessage> preHandoff = new ArrayList<>(allHistory.subList(0, handoffMessageIndex));
        List<AgentContext.ChatMessage> newItems = new ArrayList<>(allHistory.subList(handoffMessageIndex, allHistory.size()));

        HandoffInputData inputData = new HandoffInputData(
                new ArrayList<>(allHistory), preHandoff, newItems, context);
        HandoffInputData filtered = handoff.getInputFilter().filter(inputData);

        context.getConversationHistory().clear();
        context.getConversationHistory().addAll(filtered.getInputHistory());
    }
}
