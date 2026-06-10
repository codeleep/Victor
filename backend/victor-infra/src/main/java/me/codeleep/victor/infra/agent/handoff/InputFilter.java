package me.codeleep.victor.infra.agent.handoff;

/**
 * Handoff 输入过滤器
 * 用于控制 Handoff 时下一个 Agent 能看到什么对话历史
 * 参考 OpenAI Agents SDK 的 HandoffInputFilter
 *
 * <p>使用场景：
 * <ul>
 *   <li>过滤敏感信息</li>
 *   <li>压缩历史为摘要以减少 token 消耗</li>
 *   <li>只保留与目标 Agent 相关的消息</li>
 * </ul>
 */
@FunctionalInterface
public interface InputFilter {

    /**
     * 过滤 Handoff 输入数据
     *
     * @param input 原始输入数据
     * @return 过滤后的输入数据
     */
    HandoffInputData filter(HandoffInputData input);
}
