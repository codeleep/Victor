package me.codeleep.victor.infra.agent.llm.volcengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatTool;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.volcengine.ark.runtime.service.ArkService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.reactivex.Flowable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 火山引擎 Ark AgentScope 模型包装器
 * 直接对接火山 SDK（ArkService），产出 AgentScope ChatResponse
 * 替代旧的基于 Spring AI ChatModel 的实现
 */
@Slf4j
public class VolcengineModelWrapper implements Model {

    private final ArkService arkService;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper objectMapper;

    @Getter
    private List<ChatTool> tools;

    public VolcengineModelWrapper(String baseUrl, String apiKey, String modelName,
                                   double temperature, int maxTokens) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.objectMapper = new ObjectMapper();

        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();

        this.arkService = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        log.info("VolcengineModelWrapper 初始化完成: baseUrl={}, modelName={}", baseUrl, modelName);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> toolSchemas, GenerateOptions options) {
        List<ChatMessage> arkMessages = convertMessages(messages);
        List<ChatTool> arkTools = convertToolSchemas(toolSchemas);
        this.tools = arkTools;

        ChatCompletionRequest request = buildRequest(arkMessages, arkTools, true, options);

        log.debug("流式调用火山引擎 Ark API: model={}, messages={}, tools={}",
                modelName, messages.size(), arkTools.size());

        Flowable<ChatCompletionChunk> flowable = arkService.streamChatCompletion(request);

        return Flux.create(sink -> {
            StringBuilder textBuf = new StringBuilder();
            flowable.subscribe(
                    chunk -> {
                        try {
                            ChatResponse response = convertChunk(chunk, textBuf);
                            if (response != null) {
                                sink.next(response);
                            }
                        } catch (Exception e) {
                            log.warn("转换流式 chunk 失败: {}", e.getMessage());
                        }
                    },
                    error -> {
                        log.error("流式调用失败: {}", error.getMessage());
                        sink.error(error);
                    },
                    sink::complete
            );
        });
    }

    /**
     * 关闭 ArkService 线程池
     */
    public void shutdown() {
        arkService.shutdownExecutor();
    }

    // ==================== 内部方法 ====================

    private ChatCompletionRequest buildRequest(List<ChatMessage> messages, List<ChatTool> tools,
                                                boolean stream, GenerateOptions options) {
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(stream);

        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }

        return builder.build();
    }

    /**
     * 转换 AgentScope Msg 为火山 ChatMessage
     */
    private List<ChatMessage> convertMessages(List<Msg> messages) {
        List<ChatMessage> arkMessages = new ArrayList<>();
        for (Msg msg : messages) {
            MsgRole role = msg.getRole();
            String text = msg.getTextContent();

            if (role == MsgRole.SYSTEM) {
                ChatMessage m = new ChatMessage();
                m.setRole(ChatMessageRole.SYSTEM);
                m.setContent(text);
                arkMessages.add(m);
            } else if (role == MsgRole.USER) {
                ChatMessage m = new ChatMessage();
                m.setRole(ChatMessageRole.USER);
                m.setContent(text);
                arkMessages.add(m);
            } else if (role == MsgRole.ASSISTANT) {
                ChatMessage m = new ChatMessage();
                m.setRole(ChatMessageRole.ASSISTANT);
                m.setContent(text);

                // 工具调用
                List<ToolUseBlock> toolUseBlocks = msg.getContentBlocks(ToolUseBlock.class);
                if (toolUseBlocks != null && !toolUseBlocks.isEmpty()) {
                    List<ChatToolCall> tcList = toolUseBlocks.stream()
                            .map(tb -> new ChatToolCall(tb.getId(), "function",
                                    new ChatFunctionCall(tb.getName(), toJson(tb.getInput()))))
                            .collect(Collectors.toList());
                    m.setToolCalls(tcList);
                }
                arkMessages.add(m);
            } else if (role == MsgRole.TOOL) {
                // 工具结果消息：AgentScope 中工具结果以 ToolResultBlock 形式存在于消息内容里
                ChatMessage m = new ChatMessage();
                m.setRole(ChatMessageRole.TOOL);
                m.setContent(text);
                arkMessages.add(m);
            }
        }
        return arkMessages;
    }

    /**
     * 转换 AgentScope ToolSchema 为火山 ChatTool
     */
    private List<ChatTool> convertToolSchemas(List<ToolSchema> toolSchemas) {
        if (toolSchemas == null || toolSchemas.isEmpty()) {
            return List.of();
        }
        return toolSchemas.stream().map(schema -> {
            ChatTool tool = new ChatTool();
            tool.setType("function");
            com.volcengine.ark.runtime.model.completion.chat.ChatFunction function =
                    new com.volcengine.ark.runtime.model.completion.chat.ChatFunction();
            function.setName(schema.getName());
            function.setDescription(schema.getDescription());
            function.setParameters(objectMapper.valueToTree(schema.getParameters()));
            tool.setFunction(function);
            return tool;
        }).collect(Collectors.toList());
    }

    /**
     * 将流式 chunk 转换为 AgentScope ChatResponse
     */
    private ChatResponse convertChunk(ChatCompletionChunk chunk, StringBuilder textBuf) {
        if (chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return null;
        }

        var choice = chunk.getChoices().get(0);
        var delta = choice.getMessage();
        String finishReason = choice.getFinishReason() != null ? choice.getFinishReason().toString() : null;

        List<ContentBlock> blocks = new ArrayList<>();
        String deltaText = delta != null ? delta.stringContent() : null;

        // 增量文本
        if (deltaText != null && !deltaText.isEmpty()) {
            textBuf.append(deltaText);
            blocks.add(TextBlock.builder().text(deltaText).build());
        }

        // 工具调用增量
        if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
            for (ChatToolCall tc : delta.getToolCalls()) {
                Map<String, Object> input = new HashMap<>();
                if (tc.getFunction() != null && tc.getFunction().getArguments() != null) {
                    input = parseArgs(tc.getFunction().getArguments());
                }
                ToolUseBlock tub = ToolUseBlock.builder()
                        .id(tc.getId() != null ? tc.getId() : "")
                        .name(tc.getFunction() != null ? tc.getFunction().getName() : "")
                        .input(input)
                        .build();
                blocks.add(tub);
            }
        }

        if (blocks.isEmpty() && finishReason == null) {
            return null;
        }

        ChatResponse.Builder b = ChatResponse.builder()
                .id(chunk.getId())
                .content(blocks);

        if (finishReason != null) {
            b.finishReason(finishReason);
            if (chunk.getUsage() != null) {
                b.usage(new ChatUsage(
                        (int) chunk.getUsage().getPromptTokens(),
                        (int) chunk.getUsage().getCompletionTokens(),
                        0.0));
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String args) {
        if (args == null || args.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(args, Map.class);
        } catch (Exception e) {
            Map<String, Object> m = new HashMap<>();
            m.put("raw", args);
            return m;
        }
    }

    private String toJson(Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }
}
