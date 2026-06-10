package me.codeleep.victor.infra.agent.lifecycle;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;

import java.util.List;
import java.util.Map;

/**
 * Agent 生命周期监听器
 * 参考 OpenAI Agents SDK 的 RunHooks / AgentHooks
 *
 * <p>两级监听：
 * <ul>
 *   <li>Run 级：通过 AgentRunnerImpl 构造函数传入，监听整个 Run 的所有事件</li>
 *   <li>Agent 级：通过 AgentDefinition.lifecycleListener 设置，仅监听该 Agent 的事件</li>
 * </ul>
 *
 * <p>所有方法都有默认空实现，使用者只需覆盖关心的方法。
 */
public interface AgentLifecycleListener {

    // ==================== Agent 生命周期 ====================

    /**
     * Agent 开始执行
     */
    default void onAgentStart(AgentDefinition agent, AgentContext context) {}

    /**
     * Agent 执行结束
     */
    default void onAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {}

    /**
     * Handoff 触发
     */
    default void onHandoff(AgentDefinition fromAgent, AgentDefinition toAgent, AgentContext context) {}

    // ==================== Agentic Loop ====================

    /**
     * Agentic Loop 新一轮开始
     *
     * @param agent     当前执行的 Agent（Handoff 后可能变化）
     * @param turn      当前轮次（从 1 开始）
     * @param maxTurns  最大轮次限制
     * @param context   执行上下文
     */
    default void onTurnStart(AgentDefinition agent, int turn, int maxTurns, AgentContext context) {}

    // ==================== LLM 调用 ====================

    /**
     * LLM 调用开始
     */
    default void onLlmStart(AgentDefinition agent, AgentContext context) {}

    /**
     * LLM 调用结束（成功）
     */
    default void onLlmEnd(AgentDefinition agent, AgentResult result, long durationMs, AgentContext context) {}

    /**
     * LLM 调用失败（网络异常、超时、解析错误等）
     *
     * <p>与 {@link #onLlmEnd} 的区别：
     * <ul>
     *   <li>onLlmEnd：LLM 正常返回，业务层判断结果</li>
     *   <li>onLlmError：LLM 调用本身抛出异常，无法获得 AgentResult</li>
     * </ul>
     *
     * @param agent        当前 Agent
     * @param errorMessage 错误信息
     * @param durationMs   耗时（毫秒）
     * @param context      执行上下文
     */
    default void onLlmError(AgentDefinition agent, String errorMessage, long durationMs, AgentContext context) {}

    // ==================== 工具调用 ====================

    /**
     * 工具开始执行
     *
     * @param agent     当前 Agent
     * @param toolName  工具名称
     * @param arguments 工具调用参数（LLM 传入的 JSON 参数）
     * @param context   执行上下文
     */
    default void onToolStart(AgentDefinition agent, String toolName, Map<String, Object> arguments, AgentContext context) {}

    /**
     * 工具执行结束
     *
     * @param agent        当前 Agent
     * @param toolName     工具名称
     * @param result       执行结果（成功时为返回值，失败时可能为 null）
     * @param success      是否执行成功
     * @param errorMessage 错误信息（成功时为 null）
     * @param durationMs   耗时（毫秒）
     * @param context      执行上下文
     */
    default void onToolEnd(AgentDefinition agent, String toolName, Object result,
                           boolean success, String errorMessage, long durationMs, AgentContext context) {}

    // ==================== Guardrail ====================

    /**
     * Guardrail 校验完成
     *
     * <p>在输入 Guardrail 和输出 Guardrail 执行完毕后触发，无论通过与否都会回调。
     * 前端可通过此事件展示安全校验状态。</p>
     *
     * @param agent         当前 Agent
     * @param stage         校验阶段："input" 或 "output"
     * @param guardrailName 校验器名称
     * @param passed        是否通过
     * @param reason        未通过时的原因（通过时为 null）
     * @param durationMs    校验耗时（毫秒）
     * @param context       执行上下文
     */
    default void onGuardrailCheck(AgentDefinition agent, String stage, String guardrailName,
                                  boolean passed, String reason, long durationMs, AgentContext context) {}

    // ==================== 人类介入 ====================

    /**
     * Agent 请求人类输入（Human-in-the-loop）
     *
     * <p>当 Agent 执行过程中需要人类确认、选择或输入时触发。
     * 通常由 {@code request_human_input} 特殊工具触发。</p>
     *
     * @param agent       当前 Agent
     * @param requestId   请求 ID，前端回复时需携带
     * @param prompt      提示文本
     * @param options     可选项列表（可为空，表示自由输入）
     * @param context     执行上下文
     */
    default void onHumanInputRequired(AgentDefinition agent, String requestId,
                                      String prompt, List<String> options, AgentContext context) {}
}
