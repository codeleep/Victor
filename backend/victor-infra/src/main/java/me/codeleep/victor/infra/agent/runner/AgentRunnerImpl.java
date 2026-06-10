package me.codeleep.victor.infra.agent.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.guardrail.Guardrail;
import me.codeleep.victor.infra.agent.guardrail.GuardrailResult;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.lifecycle.AgentLifecycleListener;
import me.codeleep.victor.infra.agent.llm.ChatClientFactory;
import me.codeleep.victor.infra.agent.llm.volcengine.VolcengineChatModel;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.infra.agent.tracing.AgentTrace;
import me.codeleep.victor.infra.agent.tracing.TraceCollector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 默认 Runner 实现。
 *
 * <p>实现完整的 Agentic Loop：LLM 调用 → 工具执行 → 结果回传 → 再次调用。</p>
 * <p>支持 Handoff、Guardrail、Tracing、Lifecycle Hooks。</p>
 *
 * <p>职责拆分：</p>
 * <ul>
 *   <li>Handoff 处理 → {@link HandoffProcessor}</li>
 *   <li>ChatModel 创建 → {@link ChatModelFactory}</li>
 *   <li>Guardrail 执行、工具执行、Lifecycle 回调 → 本类私有方法</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentRunnerImpl implements AgentRunner {

    private final ChatModelFactory chatModelFactory;
    private final HandoffProcessor handoffProcessor;
    private final RunnerConfig config;
    private final TraceCollector traceCollector;
    private final ObjectMapper objectMapper;
    private final AgentLifecycleListener runListener;

    @Autowired
    public AgentRunnerImpl(ChatClientFactory chatClientFactory) {
        this(chatClientFactory, RunnerConfig.defaults(), new TraceCollector(), null);
    }

    public AgentRunnerImpl(ChatClientFactory chatClientFactory, RunnerConfig config, TraceCollector traceCollector) {
        this(chatClientFactory, config, traceCollector, null);
    }

    public AgentRunnerImpl(ChatClientFactory chatClientFactory, RunnerConfig config,
                               TraceCollector traceCollector, AgentLifecycleListener runListener) {
        this.chatModelFactory = new ChatModelFactory(chatClientFactory);
        this.handoffProcessor = new HandoffProcessor();
        this.config = config;
        this.traceCollector = traceCollector;
        this.objectMapper = new ObjectMapper();
        this.runListener = runListener;
    }

    // ==================== 公共接口 ====================

    @Override
    public AgentResult run(AgentDefinition agent, AgentContext context) {
        log.info("Runner 开始执行: agent={}, sessionId={}", agent.getName(), context.getSessionId());
        fireAgentStart(agent, context);

        // 1. 输入 Guardrail
        GuardrailResult inputCheck = executeInputGuardrails(agent, context, getLastUserMessage(context));
        if (!inputCheck.isPassed()) {
            return guardedError(agent, context, "输入被拦截: " + inputCheck.getReason());
        }

        // 2. Agentic Loop
        AgentDefinition currentAgent = agent;
        int turn = 0;

        while (turn < config.getMaxTurns()) {
            turn++;
            log.debug("Agentic Loop 轮次: {}/{}, agent={}", turn, config.getMaxTurns(), currentAgent.getName());
            fireTurnStart(currentAgent, turn, config.getMaxTurns(), context);

            // 2a. 调用 LLM
            AgentResult result = invokeLlm(currentAgent, context);
            if (!result.isSuccess()) {
                return guardedError(agent, context, result.getErrorMessage());
            }

            // 2b. 无工具调用 → 返回结果
            if (!result.hasToolCalls()) {
                return finishWithResult(agent, currentAgent, context, result, turn);
            }

            // 2c. 处理工具调用
            List<AgentResult.ToolResult> toolResults = executeToolCalls(currentAgent, result, context);
            if (toolResults == null) {
                // Handoff 发生，currentAgent 已切换
                currentAgent = findHandoffTarget(currentAgent, result);
                if (currentAgent == null) {
                    return guardedError(agent, context, "Handoff 目标不存在");
                }
                continue;
            }

            // 2d. 将工具结果加入对话历史
            appendToolResults(context, toolResults);
            result.setToolResults(toolResults);
        }

        log.warn("Agentic Loop 达到最大轮次: agent={}, maxTurns={}", agent.getName(), config.getMaxTurns());
        return guardedError(agent, context, "达到最大轮次限制: " + config.getMaxTurns());
    }

    @Override
    public Flux<AgentResult> streamRun(AgentDefinition agent, AgentContext context) {
        log.info("Runner 流式执行: agent={}, sessionId={}", agent.getName(), context.getSessionId());

        // 输入 Guardrail
        GuardrailResult inputCheck = executeInputGuardrails(agent, context, getLastUserMessage(context));
        if (!inputCheck.isPassed()) {
            return Flux.just(AgentResult.error("输入被拦截: " + inputCheck.getReason()));
        }

        Prompt prompt = buildPrompt(agent, context);

        // 火山引擎协议：直接使用 VolcengineChatModel 的流式方法
        if (chatModelFactory.isVolcengineProtocol(agent)) {
            VolcengineChatModel model = chatModelFactory.createVolcengineModel(agent);
            return model.stream(prompt)
                    .map(this::parseResponse)
                    .filter(r -> r != null && (r.getContent() != null || r.hasToolCalls()))
                    .doOnComplete(() -> log.info("Runner 流式执行完成: agent={}", agent.getName()))
                    .onErrorResume(e -> {
                        log.error("Runner 流式执行失败: {}", e.getMessage());
                        return Flux.just(AgentResult.error("流式执行失败: " + e.getMessage()));
                    });
        }

        // 其他协议使用 ChatClient
        ChatClient chatClient = chatModelFactory.createChatClient(agent);
        return chatClient.prompt(prompt).stream().chatResponse()
                .map(this::parseResponse)
                .filter(r -> r != null && (r.getContent() != null || r.getFinishReason() != null))
                .doOnComplete(() -> log.info("Runner 流式执行完成: agent={}", agent.getName()))
                .onErrorResume(e -> {
                    log.error("Runner 流式执行失败: {}", e.getMessage());
                    return Flux.just(AgentResult.error("流式执行失败: " + e.getMessage()));
                });
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM 并返回解析后的结果，失败时返回 null。
     */
    private AgentResult invokeLlm(AgentDefinition agent, AgentContext context) {
        ChatClient chatClient = chatModelFactory.createChatClient(agent);
        Prompt prompt = buildPrompt(agent, context);

        fireLlmStart(agent, context);
        long startTime = System.currentTimeMillis();

        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String detail = buildErrorMessage(e);
            log.error("LLM 调用失败: agent={}, error={}", agent.getName(), detail);
            if (config.isTracingEnabled()) {
                traceCollector.addTrace(context.getSessionId(),
                        AgentTrace.llmCall(agent.getName(), "error", detail, duration));
            }
            fireLlmError(agent, detail, duration, context);
            return AgentResult.error("LLM 调用失败: " + detail);
        }

        long duration = System.currentTimeMillis() - startTime;
        AgentResult result = parseResponse(response);
        fireLlmEnd(agent, result, duration, context);

        if (config.isTracingEnabled()) {
            String outputPreview = result.getContent() != null
                    ? result.getContent().substring(0, Math.min(200, result.getContent().length()))
                    : "tool_call";
            traceCollector.addTrace(context.getSessionId(),
                    AgentTrace.llmCall(agent.getName(), prompt.getContents().toString(), outputPreview, duration));
        }

        return result;
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具调用列表。
     *
     * @return 工具结果列表；如果发生 Handoff 则返回 null（由调用方处理 Agent 切换）
     */
    private List<AgentResult.ToolResult> executeToolCalls(AgentDefinition agent, AgentResult result, AgentContext context) {
        List<AgentResult.ToolResult> toolResults = new ArrayList<>();

        for (AgentResult.ToolCall toolCall : result.getToolCalls()) {
            if (handoffProcessor.isHandoffTool(agent, toolCall.getName())) {
                return handleHandoff(agent, toolCall, context) ? null : toolResults;
            }

            // 普通工具执行
            fireToolStart(agent, toolCall.getName(), toolCall.getArguments(), context);
            long toolStart = System.currentTimeMillis();
            Object toolResult = executeTool(agent, toolCall, context);
            long toolDuration = System.currentTimeMillis() - toolStart;

            boolean success = !isToolError(toolResult);
            String errorMsg = success ? null : toolResult.toString();
            fireToolEnd(agent, toolCall.getName(), toolResult, success, errorMsg, toolDuration, context);

            if (config.isTracingEnabled()) {
                traceCollector.addTrace(context.getSessionId(),
                        AgentTrace.toolCall(agent.getName(), toolCall.getName(),
                                toolResult.toString(), toolDuration));
            }

            toolResults.add(new AgentResult.ToolResult(toolCall.getId(), toolCall.getName(), toolResult));
        }

        return toolResults;
    }

    /**
     * 处理 Handoff 调用。
     *
     * @return true 表示 Handoff 成功，调用方应继续循环
     */
    private boolean handleHandoff(AgentDefinition currentAgent, AgentResult.ToolCall toolCall, AgentContext context) {
        Handoff handoff = handoffProcessor.findHandoff(currentAgent, toolCall.getName());
        String targetAgentName = handoff != null && handoff.getTargetAgent() != null
                ? handoff.getTargetAgent().getName() : "unknown";

        log.info("Handoff 触发: {} → {}", currentAgent.getName(), targetAgentName);

        if (config.isTracingEnabled()) {
            traceCollector.addTrace(context.getSessionId(),
                    AgentTrace.handoff(currentAgent.getName(), targetAgentName, 0));
        }

        int handoffMessageIndex = context.getConversationHistory().size();
        fireHandoff(currentAgent, handoff != null ? handoff.getTargetAgent() : null, context);

        AgentDefinition target = handoffProcessor.processHandoff(handoff, toolCall, context, handoffMessageIndex);
        return target != null;
    }

    /**
     * 从 Handoff 列表中查找目标 Agent（用于循环中切换 currentAgent）。
     */
    private AgentDefinition findHandoffTarget(AgentDefinition agent, AgentResult result) {
        if (result.getToolResults() == null || result.getToolResults().isEmpty()) return null;
        String toolName = result.getToolResults().get(0).getToolName();
        Handoff handoff = handoffProcessor.findHandoff(agent, toolName);
        return handoff != null ? handoff.getTargetAgent() : null;
    }

    /**
     * 判断工具执行结果是否为错误
     */
    private boolean isToolError(Object toolResult) {
        if (toolResult instanceof String s) {
            return s.startsWith("工具") || s.startsWith("执行失败") || s.startsWith("执行错误");
        }
        return false;
    }

    private Object executeTool(AgentDefinition agent, AgentResult.ToolCall toolCall, AgentContext context) {
        for (AgentTool tool : agent.getTools()) {
            if (tool.getName().equals(toolCall.getName())) {
                try {
                    return tool.execute(toolCall.getArguments(), context);
                } catch (Exception e) {
                    log.error("工具执行异常: {} - {}", toolCall.getName(), e.getMessage(), e);
                    return "工具执行失败: " + e.getMessage();
                }
            }
        }
        return "工具不存在: " + toolCall.getName();
    }

    private void appendToolResults(AgentContext context, List<AgentResult.ToolResult> toolResults) {
        for (AgentResult.ToolResult tr : toolResults) {
            context.addToolMessage(tr.getToolCallId(), tr.getToolName(),
                    tr.isSuccess() ? tr.getResult().toString() : "错误: " + tr.getError());
        }
    }

    // ==================== Prompt 构建 ====================

    private Prompt buildPrompt(AgentDefinition agent, AgentContext context) {
        List<Message> messages = new ArrayList<>();

        if (agent.getInstructions() != null && !agent.getInstructions().isEmpty()) {
            messages.add(new SystemMessage(agent.getInstructions()));
        }

        for (AgentContext.ChatMessage msg : context.getConversationHistory()) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "tool" -> messages.add(new ToolResponseMessage(
                        List.of(new ToolResponseMessage.ToolResponse(
                                msg.getToolCallId() != null ? msg.getToolCallId() : "",
                                msg.getToolName() != null ? msg.getToolName() : "",
                                msg.getContent()))));
            }
        }

        return new Prompt(messages);
    }

    // ==================== 响应解析 ====================

    private AgentResult parseResponse(ChatResponse response) {
        AgentResult result = new AgentResult();
        if (response == null) return result;

        List<Generation> generations = response.getResults();
        if (generations != null && !generations.isEmpty()) {
            Generation generation = generations.get(0);
            if (generation.getOutput() != null) {
                result.setContent(generation.getOutput().getContent());

                AssistantMessage assistantMessage = generation.getOutput();
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    List<AgentResult.ToolCall> toolCalls = new ArrayList<>();
                    for (var tc : assistantMessage.getToolCalls()) {
                        toolCalls.add(new AgentResult.ToolCall(tc.id(), tc.name(), parseToolArguments(tc.arguments())));
                    }
                    result.setToolCalls(toolCalls);
                }
            }

            if (generation.getMetadata() != null) {
                result.setFinishReason(generation.getMetadata().getFinishReason());
            }
        }

        return result;
    }

    private Map<String, Object> parseToolArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // ==================== Guardrail ====================

    private GuardrailResult executeInputGuardrails(AgentDefinition agent, AgentContext context, String input) {
        for (Guardrail guardrail : agent.getInputGuardrails()) {
            long start = System.currentTimeMillis();
            GuardrailResult result = guardrail.validate(context, input);
            long duration = System.currentTimeMillis() - start;
            if (config.isTracingEnabled()) {
                traceCollector.addTrace(context.getSessionId(),
                        AgentTrace.guardrailCheck(agent.getName(), guardrail.getName(),
                                result.isPassed(), duration));
            }
            fireGuardrailCheck(agent, "input", guardrail.getName(),
                    result.isPassed(), result.getReason(), duration, context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return GuardrailResult.pass();
    }

    private GuardrailResult executeOutputGuardrails(AgentDefinition agent, AgentContext context, String output) {
        for (Guardrail guardrail : agent.getOutputGuardrails()) {
            long start = System.currentTimeMillis();
            GuardrailResult result = guardrail.validate(context, output);
            long duration = System.currentTimeMillis() - start;
            if (config.isTracingEnabled()) {
                traceCollector.addTrace(context.getSessionId(),
                        AgentTrace.guardrailCheck(agent.getName(), guardrail.getName(),
                                result.isPassed(), duration));
            }
            fireGuardrailCheck(agent, "output", guardrail.getName(),
                    result.isPassed(), result.getReason(), duration, context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return GuardrailResult.pass();
    }

    // ==================== 辅助方法 ====================

    private String buildErrorMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (!sb.isEmpty()) sb.append(" -> ");
                sb.append(msg);
            }
            cause = cause.getCause();
            if (cause != null && sb.length() > 500) break;
        }
        return sb.isEmpty() ? e.getClass().getSimpleName() : sb.toString();
    }

    private String getLastUserMessage(AgentContext context) {
        List<AgentContext.ChatMessage> history = context.getConversationHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).getRole())) {
                return history.get(i).getContent();
            }
        }
        return "";
    }

    /**
     * 输出 Guardrail 校验通过后，返回最终结果。
     */
    private AgentResult finishWithResult(AgentDefinition originalAgent, AgentDefinition currentAgent,
                                          AgentContext context, AgentResult result, int turn) {
        GuardrailResult outputCheck = executeOutputGuardrails(currentAgent, context, result.getContent());
        if (!outputCheck.isPassed()) {
            log.warn("输出 Guardrail 拦截: agent={}, reason={}", currentAgent.getName(), outputCheck.getReason());
            return guardedError(originalAgent, context, "输出被拦截: " + outputCheck.getReason());
        }

        context.addAssistantMessage(result.getContent());
        log.info("Runner 执行完成: agent={}, turns={}", currentAgent.getName(), turn);
        fireAgentEnd(originalAgent, context, result);
        return result;
    }

    /**
     * 生成错误结果并触发 AgentEnd 生命周期。
     */
    private AgentResult guardedError(AgentDefinition agent, AgentContext context, String message) {
        AgentResult errorResult = AgentResult.error(message);
        fireAgentEnd(agent, context, errorResult);
        return errorResult;
    }

    // ==================== Lifecycle Hook 调用 ====================

    private void fireAgentStart(AgentDefinition agent, AgentContext context) {
        if (runListener != null) {
            try { runListener.onAgentStart(agent, context); } catch (Exception e) { log.warn("onAgentStart 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onAgentStart(agent, context); } catch (Exception e) { log.warn("onAgentStart 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {
        if (runListener != null) {
            try { runListener.onAgentEnd(agent, context, result); } catch (Exception e) { log.warn("onAgentEnd 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onAgentEnd(agent, context, result); } catch (Exception e) { log.warn("onAgentEnd 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireHandoff(AgentDefinition fromAgent, AgentDefinition toAgent, AgentContext context) {
        if (runListener != null) {
            try { runListener.onHandoff(fromAgent, toAgent, context); } catch (Exception e) { log.warn("onHandoff 回调异常: {}", e.getMessage()); }
        }
        if (fromAgent.getLifecycleListener() != null) {
            try { fromAgent.getLifecycleListener().onHandoff(fromAgent, toAgent, context); } catch (Exception e) { log.warn("onHandoff 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireTurnStart(AgentDefinition agent, int turn, int maxTurns, AgentContext context) {
        if (runListener != null) {
            try { runListener.onTurnStart(agent, turn, maxTurns, context); } catch (Exception e) { log.warn("onTurnStart 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onTurnStart(agent, turn, maxTurns, context); } catch (Exception e) { log.warn("onTurnStart 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireToolStart(AgentDefinition agent, String toolName, Map<String, Object> arguments, AgentContext context) {
        if (runListener != null) {
            try { runListener.onToolStart(agent, toolName, arguments, context); } catch (Exception e) { log.warn("onToolStart 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onToolStart(agent, toolName, arguments, context); } catch (Exception e) { log.warn("onToolStart 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireToolEnd(AgentDefinition agent, String toolName, Object result,
                             boolean success, String errorMessage, long durationMs, AgentContext context) {
        if (runListener != null) {
            try { runListener.onToolEnd(agent, toolName, result, success, errorMessage, durationMs, context); } catch (Exception e) { log.warn("onToolEnd 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onToolEnd(agent, toolName, result, success, errorMessage, durationMs, context); } catch (Exception e) { log.warn("onToolEnd 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireLlmStart(AgentDefinition agent, AgentContext context) {
        if (runListener != null) {
            try { runListener.onLlmStart(agent, context); } catch (Exception e) { log.warn("onLlmStart 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onLlmStart(agent, context); } catch (Exception e) { log.warn("onLlmStart 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireLlmEnd(AgentDefinition agent, AgentResult result, long durationMs, AgentContext context) {
        if (runListener != null) {
            try { runListener.onLlmEnd(agent, result, durationMs, context); } catch (Exception e) { log.warn("onLlmEnd 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onLlmEnd(agent, result, durationMs, context); } catch (Exception e) { log.warn("onLlmEnd 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireLlmError(AgentDefinition agent, String errorMessage, long durationMs, AgentContext context) {
        if (runListener != null) {
            try { runListener.onLlmError(agent, errorMessage, durationMs, context); } catch (Exception e) { log.warn("onLlmError 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onLlmError(agent, errorMessage, durationMs, context); } catch (Exception e) { log.warn("onLlmError 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireGuardrailCheck(AgentDefinition agent, String stage, String guardrailName,
                                     boolean passed, String reason, long durationMs, AgentContext context) {
        if (runListener != null) {
            try { runListener.onGuardrailCheck(agent, stage, guardrailName, passed, reason, durationMs, context); } catch (Exception e) { log.warn("onGuardrailCheck 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onGuardrailCheck(agent, stage, guardrailName, passed, reason, durationMs, context); } catch (Exception e) { log.warn("onGuardrailCheck 回调异常: {}", e.getMessage()); }
        }
    }

    private void fireHumanInputRequired(AgentDefinition agent, String requestId,
                                         String prompt, List<String> options, AgentContext context) {
        if (runListener != null) {
            try { runListener.onHumanInputRequired(agent, requestId, prompt, options, context); } catch (Exception e) { log.warn("onHumanInputRequired 回调异常: {}", e.getMessage()); }
        }
        if (agent.getLifecycleListener() != null) {
            try { agent.getLifecycleListener().onHumanInputRequired(agent, requestId, prompt, options, context); } catch (Exception e) { log.warn("onHumanInputRequired 回调异常: {}", e.getMessage()); }
        }
    }
}
