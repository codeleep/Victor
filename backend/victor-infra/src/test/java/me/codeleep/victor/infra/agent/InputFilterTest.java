package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.handoff.HandoffInputData;
import me.codeleep.victor.infra.agent.handoff.InputFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Input Filter 单元测试
 */
class InputFilterTest {

    @Test
    @DisplayName("无 filter 时历史完整传递")
    void noFilterHistoryPassesThrough() {
        AgentDefinition target = AgentDefinition.builder()
                .name("Target").instructions("B").build();

        Handoff handoff = Handoff.builder()
                .targetAgent(target)
                .description("转给B")
                .build();

        // 无 filter
        assertNull(handoff.getInputFilter());
    }

    @Test
    @DisplayName("有 filter 时历史被过滤")
    void filterModifiesHistory() {
        // 创建一个只保留 user 消息的 filter
        InputFilter userOnlyFilter = input -> {
            List<AgentContext.ChatMessage> filtered = input.getInputHistory().stream()
                    .filter(msg -> "user".equals(msg.getRole()))
                    .toList();
            return new HandoffInputData(filtered, input.getPreHandoffItems(), input.getNewItems(), input.getRunContext());
        };

        AgentDefinition target = AgentDefinition.builder()
                .name("Target").instructions("B").build();

        Handoff handoff = Handoff.builder()
                .targetAgent(target)
                .description("转给B")
                .inputFilter(userOnlyFilter)
                .build();

        assertNotNull(handoff.getInputFilter());

        // 构建测试数据
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("你好");
        context.addAssistantMessage("你好！有什么可以帮助你的？");
        context.addUserMessage("什么是Java？");
        context.addAssistantMessage("Java是一种编程语言");
        context.addUserMessage("谢谢");

        List<AgentContext.ChatMessage> allHistory = new ArrayList<>(context.getConversationHistory());

        HandoffInputData inputData = new HandoffInputData(
                allHistory,
                allHistory.subList(0, 3),
                allHistory.subList(3, allHistory.size()),
                context
        );

        HandoffInputData filtered = handoff.getInputFilter().filter(inputData);

        // 只保留了 user 消息
        assertEquals(3, filtered.getInputHistory().size());
        filtered.getInputHistory().forEach(msg -> assertEquals("user", msg.getRole()));
    }

    @Test
    @DisplayName("filter 压缩历史为摘要")
    void filterCompressHistory() {
        // 创建一个压缩历史的 filter：只保留最后 N 条消息
        InputFilter keepLastTwo = input -> {
            List<AgentContext.ChatMessage> history = input.getInputHistory();
            int keepCount = Math.min(2, history.size());
            List<AgentContext.ChatMessage> compressed = new ArrayList<>(
                    history.subList(history.size() - keepCount, history.size()));
            return new HandoffInputData(compressed, input.getPreHandoffItems(), input.getNewItems(), input.getRunContext());
        };

        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("消息1");
        context.addAssistantMessage("回复1");
        context.addUserMessage("消息2");
        context.addAssistantMessage("回复2");
        context.addUserMessage("消息3");

        HandoffInputData inputData = new HandoffInputData(
                new ArrayList<>(context.getConversationHistory()),
                List.of(), List.of(), context
        );

        HandoffInputData filtered = keepLastTwo.filter(inputData);

        // 只保留最后 2 条
        assertEquals(2, filtered.getInputHistory().size());
        assertEquals("回复2", filtered.getInputHistory().get(0).getContent());
        assertEquals("消息3", filtered.getInputHistory().get(1).getContent());
    }

    @Test
    @DisplayName("HandoffInputData 字段正确")
    void handoffInputDataFields() {
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("你好");
        context.addAssistantMessage("你好！");

        List<AgentContext.ChatMessage> history = new ArrayList<>(context.getConversationHistory());
        List<AgentContext.ChatMessage> pre = history.subList(0, 1);
        List<AgentContext.ChatMessage> post = history.subList(1, 2);

        HandoffInputData data = new HandoffInputData(history, pre, post, context);

        assertEquals(2, data.getInputHistory().size());
        assertEquals(1, data.getPreHandoffItems().size());
        assertEquals(1, data.getNewItems().size());
        assertSame(context, data.getRunContext());
    }
}
