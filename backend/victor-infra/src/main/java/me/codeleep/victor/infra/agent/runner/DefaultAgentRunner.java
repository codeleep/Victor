package me.codeleep.victor.infra.agent.runner;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventSource;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 单 Agent 运行器实现 - 驱动已构建的 AgentScope ReActAgent
 * 只负责执行（agentic loop 由 ReActAgent 内部完成），不负责构建
 */
@Slf4j
@Component
public class DefaultAgentRunner implements AgentRunner {

    @Override
    public AgentResult run(ReActAgent agent, AgentContext context) {
        try {
            List<Msg> input = toInputMessages(context);
            RuntimeContext rc = buildRuntimeContext(context);

            Msg result = agent.call(input, rc).block();

            AgentResult r = AgentResult.answer(result != null ? result.getTextContent() : null);
            r.setSourceAgentKey(context.getMetadata() != null
                    ? (String) context.getMetadata().getOrDefault("agentKey", null) : null);
            r.setLast(true);
            return r;
        } catch (Exception e) {
            log.error("Agent 执行失败", e);
            return AgentResult.error(e.getMessage());
        }
    }

    @Override
    public Flux<AgentResult> streamRun(ReActAgent agent, AgentContext context) {
        List<Msg> input = toInputMessages(context);
        RuntimeContext rc = buildRuntimeContext(context);
        String agentKey = context.getMetadata() != null
                ? (String) context.getMetadata().getOrDefault("agentKey", null) : null;

        return agent.stream(input, StreamOptions.defaults(), rc)
                .map(event -> toEventResult(agentKey, event))
                .onErrorResume(e -> {
                    log.error("Agent 流式执行失败", e);
                    return Flux.just(AgentResult.error(e.getMessage()));
                })
                .concatWith(Mono.fromCallable(() -> {
                    AgentResult done = new AgentResult();
                    done.setType(AgentResult.EventType.DONE);
                    done.setLast(true);
                    done.setSourceAgentKey(agentKey);
                    return done;
                }));
    }

    // ==================== 内部方法 ====================

    private RuntimeContext buildRuntimeContext(AgentContext context) {
        RuntimeContext.Builder b = RuntimeContext.builder()
                .sessionId(context.getSessionId())
                .userId(context.getUserId() == null ? null : String.valueOf(context.getUserId()));
        if (context.getVariables() != null) {
            b.putAll(context.getVariables());
        }
        return b.build();
    }

    private List<Msg> toInputMessages(AgentContext context) {
        List<Msg> msgs = new ArrayList<>();
        if (context.getInput() != null && !context.getInput().isEmpty()) {
            msgs.add(Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(context.getInput())
                    .build());
        }
        return msgs;
    }

    private AgentResult toEventResult(String agentKey, Event event) {
        AgentResult r = new AgentResult();
        r.setSourceAgentKey(agentKey);
        r.setSuccess(true);
        r.setLast(event.isLast());

        // 透传事件来源(主/子 Agent)，供前端区分渲染与嵌套
        EventSource source = event.getSource();
        if (source != null) {
            // 主 Agent(sourceAgentKey 与顶层 agentKey 相同或 depth=0)不填充展示名,
            // 仅子 Agent(depth>=1)填充名称,前端据此独立成块。
            Integer depth = source.getDepth();
            r.setAgentDepth(depth);
            if (depth != null && depth >= 1) {
                r.setSourceAgentName(source.getAgentName());
                // 子 Agent 事件用其自身的 agentKey 作为来源,便于前端归类
                if (source.getAgentKey() != null) {
                    r.setSourceAgentKey(source.getAgentKey());
                }
            }
        }

        Msg msg = event.getMessage();
        EventType type = event.getType();
        if (type == EventType.REASONING) {
            r.setType(AgentResult.EventType.THINKING);
            r.setContent(extractThinking(msg));
            r.setToolEvents(extractToolCallEvents(msg));
        } else if (type == EventType.TOOL_RESULT) {
            r.setType(AgentResult.EventType.TOOL_RESULT);
            r.setContent(extractToolResult(msg));
            r.setToolEvents(extractToolResultEvents(msg));
        } else if (type == EventType.AGENT_RESULT || type == EventType.SUMMARY) {
            r.setType(AgentResult.EventType.ANSWER);
            // 最终回答只取纯文本,不再混入工具调用描述(工具由独立事件/ToolEvent 承载)
            r.setContent(msg != null ? msg.getTextContent() : null);
        } else {
            r.setType(AgentResult.EventType.THINKING);
            r.setContent(msg != null ? msg.getTextContent() : null);
        }
        return r;
    }

    private String extractThinking(Msg msg) {
        if (msg == null) {
            return null;
        }
        List<ThinkingBlock> thinking = msg.getContentBlocks(ThinkingBlock.class);
        if (thinking != null && !thinking.isEmpty()) {
            return thinking.get(0).getThinking();
        }
        return msg.getTextContent();
    }

    private String extractToolResult(Msg msg) {
        if (msg == null) {
            return null;
        }
        List<ToolResultBlock> blocks = msg.getContentBlocks(ToolResultBlock.class);
        if (blocks == null || blocks.isEmpty()) {
            return msg.getTextContent();
        }
        StringBuilder sb = new StringBuilder();
        for (ToolResultBlock tb : blocks) {
            sb.append(tb.getName()).append(": ")
              .append(tb.getOutput() == null ? "" : tb.getOutput().toString())
              .append("\n");
        }
        return sb.toString().trim();
    }


    /**
     * 提取结构化工具调用事件（工具名 + 入参），供前端卡片化展示。
     */
    private List<AgentResult.ToolEvent> extractToolCallEvents(Msg msg) {
        List<AgentResult.ToolEvent> events = new ArrayList<>();
        if (msg == null) {
            return events;
        }
        List<ToolUseBlock> blocks = msg.getContentBlocks(ToolUseBlock.class);
        if (blocks == null || blocks.isEmpty()) {
            return events;
        }
        for (ToolUseBlock tb : blocks) {
            AgentResult.ToolEvent e = new AgentResult.ToolEvent();
            e.setName(tb.getName());
            e.setArgs(tb.getInput());
            e.setResultEvent(false);
            e.setToolCallId(tb.getId());
            events.add(e);
        }
        return events;
    }

    /**
     * 提取结构化工具结果事件（工具名 + 结果文本），供前端卡片化展示。
     */
    private List<AgentResult.ToolEvent> extractToolResultEvents(Msg msg) {
        List<AgentResult.ToolEvent> events = new ArrayList<>();
        if (msg == null) {
            return events;
        }
        List<ToolResultBlock> blocks = msg.getContentBlocks(ToolResultBlock.class);
        if (blocks == null || blocks.isEmpty()) {
            return events;
        }
        for (ToolResultBlock tb : blocks) {
            AgentResult.ToolEvent e = new AgentResult.ToolEvent();
            e.setName(tb.getName());
            e.setResult(extractToolResultText(tb));
            e.setResultEvent(true);
            e.setToolCallId(tb.getId());
            events.add(e);
        }
        return events;
    }

    private String extractToolResultText(ToolResultBlock tr) {
        if (tr.getOutput() == null || tr.getOutput().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : tr.getOutput()) {
            if (b instanceof TextBlock tb) {
                sb.append(tb.getText());
            } else {
                sb.append(b.toString());
            }
        }
        return sb.toString();
    }
}
