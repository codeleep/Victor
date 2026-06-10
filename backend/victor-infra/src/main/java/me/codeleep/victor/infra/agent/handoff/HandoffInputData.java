package me.codeleep.victor.infra.agent.handoff;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.codeleep.victor.infra.agent.core.AgentContext;

import java.util.List;

/**
 * Handoff 输入数据
 * 封装 Handoff 时传递给 InputFilter 的上下文信息
 * 参考 OpenAI Agents SDK 的 HandoffInputData
 */
@Data
@AllArgsConstructor
public class HandoffInputData {

    /**
     * 当前完整对话历史
     */
    private List<AgentContext.ChatMessage> inputHistory;

    /**
     * Handoff 之前的消息（触发 handoff 的消息之前）
     */
    private List<AgentContext.ChatMessage> preHandoffItems;

    /**
     * Handoff 之后的新消息（包含 handoff tool call 和 result）
     */
    private List<AgentContext.ChatMessage> newItems;

    /**
     * 运行上下文
     */
    private AgentContext runContext;
}
