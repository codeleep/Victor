package me.codeleep.victor.infra.agent.tracing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 追踪记录
 * 记录 Agent 执行过程中的每一步
 */
@Data
@Builder
public class AgentTrace {

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 时间戳
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * 动作类型（LLM_CALL, TOOL_CALL, HANDOFF, GUARDRAIL_CHECK）
     */
    private String action;

    /**
     * 输入内容
     */
    private String input;

    /**
     * 输出内容
     */
    private String output;

    /**
     * 耗时（毫秒）
     */
    private long durationMs;

    /**
     * 状态（SUCCESS, FAILED, SKIPPED）
     */
    private String status;

    /**
     * 额外信息
     */
    private String extra;

    public static AgentTrace llmCall(String agentName, String input, String output, long durationMs) {
        return AgentTrace.builder()
                .traceId(java.util.UUID.randomUUID().toString().substring(0, 8))
                .agentName(agentName)
                .action("LLM_CALL")
                .input(input)
                .output(output)
                .durationMs(durationMs)
                .status("SUCCESS")
                .build();
    }

    public static AgentTrace toolCall(String agentName, String toolName, String result, long durationMs) {
        return AgentTrace.builder()
                .traceId(java.util.UUID.randomUUID().toString().substring(0, 8))
                .agentName(agentName)
                .action("TOOL_CALL")
                .input(toolName)
                .output(result)
                .durationMs(durationMs)
                .status("SUCCESS")
                .build();
    }

    public static AgentTrace handoff(String fromAgent, String toAgent, long durationMs) {
        return AgentTrace.builder()
                .traceId(java.util.UUID.randomUUID().toString().substring(0, 8))
                .agentName(fromAgent)
                .action("HANDOFF")
                .input(fromAgent)
                .output(toAgent)
                .durationMs(durationMs)
                .status("SUCCESS")
                .build();
    }

    public static AgentTrace guardrailCheck(String agentName, String guardrailName,
                                             boolean passed, long durationMs) {
        return AgentTrace.builder()
                .traceId(java.util.UUID.randomUUID().toString().substring(0, 8))
                .agentName(agentName)
                .action("GUARDRAIL_CHECK")
                .input(guardrailName)
                .output(passed ? "PASSED" : "FAILED")
                .durationMs(durationMs)
                .status(passed ? "SUCCESS" : "FAILED")
                .build();
    }
}
