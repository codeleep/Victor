package me.codeleep.victor.infra.agent.llm.volcengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.reactivestreams.Subscription;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 火山引擎 Ark ChatModel 实现
 * 适配 Spring AI ChatModel 接口，支持 Tool Calling 和流式输出
 */
@Slf4j
public class VolcengineChatModel implements ChatModel {

    private final ArkService arkService;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper objectMapper;

    /**
     * 工具定义列表（可动态设置）
     * -- SETTER --
     *  设置工具定义

     */
    @Setter
    @Getter
    private List<ChatTool> tools;

    public VolcengineChatModel(String baseUrl, String apiKey, String modelName,
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

        log.info("VolcengineChatModel 初始化完成: baseUrl={}, modelName={}", baseUrl, modelName);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        List<ChatMessage> arkMessages = convertMessages(messages);

        ChatCompletionRequest request = buildRequest(arkMessages, false);

        log.debug("调用火山引擎 Ark API: model={}, messages={}, tools={}",
                modelName, messages.size(), tools != null ? tools.size() : 0);

        ChatCompletionResult completion = arkService.createChatCompletion(request);

        List<Generation> generations = completion.getChoices().stream()
                .map(this::convertChoice)
                .collect(Collectors.toList());

        return new ChatResponse(generations);
    }

    /**
     * 流式调用
     * 将 Volcengine 的 Flowable<ChatCompletionChunk> 桥接为 Reactor Flux<ChatResponse>
     */
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        List<ChatMessage> arkMessages = convertMessages(messages);

        ChatCompletionRequest request = buildRequest(arkMessages, true);

        log.debug("流式调用火山引擎 Ark API: model={}, messages={}, tools={}",
                modelName, messages.size(), tools != null ? tools.size() : 0);

        Flowable<ChatCompletionChunk> flowable = arkService.streamChatCompletion(request);

        return Flux.create(sink -> {
            flowable.subscribe(
                    chunk -> {
                        try {
                            ChatResponse response = convertChunk(chunk);
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
                    () -> sink.complete()
            );

            sink.onDispose(() -> {
                // 清理资源
            });
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建请求
     */
    private ChatCompletionRequest buildRequest(List<ChatMessage> messages, boolean stream) {
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens);

        if (stream) {
            builder.stream(true);
        }

        // 传递工具定义
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }

        return builder.build();
    }

    /**
     * 转换流式 Chunk 为 ChatResponse
     */
    private ChatResponse convertChunk(ChatCompletionChunk chunk) {
        if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return null;
        }

        ChatCompletionChoice choice = chunk.getChoices().get(0);
        ChatMessage message = choice.getMessage();
        if (message == null) {
            return null;
        }

        String content = message.stringContent();

        // 流式响应中也可能包含 tool calls
        List<AssistantMessage.ToolCall> toolCalls = null;
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            toolCalls = message.getToolCalls().stream()
                    .map(tc -> new AssistantMessage.ToolCall(
                            tc.getId(),
                            "function",
                            tc.getFunction().getName(),
                            tc.getFunction().getArguments()
                    ))
                    .collect(Collectors.toList());
        }

        AssistantMessage assistantMessage;
        if (toolCalls != null && !toolCalls.isEmpty()) {
            assistantMessage = new AssistantMessage(content != null ? content : "", Map.of(), toolCalls);
        } else {
            assistantMessage = new AssistantMessage(content);
        }

        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    /**
     * 转换 Choice 为 Generation（支持 Tool Call 解析）
     */
    private Generation convertChoice(ChatCompletionChoice choice) {
        ChatMessage message = choice.getMessage();
        String content = message.stringContent();

        // 解析 Tool Calls
        List<AssistantMessage.ToolCall> toolCalls = null;
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            toolCalls = message.getToolCalls().stream()
                    .map(tc -> new AssistantMessage.ToolCall(
                            tc.getId(),
                            "function",
                            tc.getFunction().getName(),
                            tc.getFunction().getArguments()
                    ))
                    .collect(Collectors.toList());
        }

        AssistantMessage assistantMessage;
        if (toolCalls != null && !toolCalls.isEmpty()) {
            assistantMessage = new AssistantMessage(content != null ? content : "", Map.of(), toolCalls);
        } else {
            assistantMessage = new AssistantMessage(content);
        }

        return new Generation(assistantMessage);
    }

    /**
     * 转换 Spring AI Message 为 Volcengine ChatMessage
     */
    private List<ChatMessage> convertMessages(List<Message> messages) {
        List<ChatMessage> arkMessages = new ArrayList<>();

        for (Message message : messages) {
            switch (message.getMessageType()) {
                case USER -> {
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(ChatMessageRole.USER);
                    msg.setContent(message.getContent());
                    arkMessages.add(msg);
                }
                case ASSISTANT -> {
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(ChatMessageRole.ASSISTANT);
                    msg.setContent(message.getContent());

                    if (message instanceof AssistantMessage am && am.getToolCalls() != null
                            && !am.getToolCalls().isEmpty()) {
                        List<ChatToolCall> tcList = am.getToolCalls().stream()
                                .map(tc -> new ChatToolCall(tc.id(), "function",
                                        new ChatFunctionCall(tc.name(), tc.arguments())))
                                .collect(Collectors.toList());
                        msg.setToolCalls(tcList);
                    }

                    arkMessages.add(msg);
                }
                case SYSTEM -> {
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(ChatMessageRole.SYSTEM);
                    msg.setContent(message.getContent());
                    arkMessages.add(msg);
                }
                case TOOL -> {
                    if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage trm) {
                        for (var resp : trm.getResponses()) {
                            ChatMessage msg = new ChatMessage();
                            msg.setRole(ChatMessageRole.TOOL);
                            msg.setContent(resp.responseData());
                            msg.setToolCallId(resp.id());
                            arkMessages.add(msg);
                        }
                    }
                }
                default -> {
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(ChatMessageRole.USER);
                    msg.setContent(message.getContent());
                    arkMessages.add(msg);
                }
            }
        }

        return arkMessages;
    }

    /**
     * 将工具定义 Map 列表转换为 Volcengine ChatTool 列表
     */
    public static List<ChatTool> convertToolDefinitions(List<Map<String, Object>> toolDefs) {
        if (toolDefs == null || toolDefs.isEmpty()) return List.of();

        ObjectMapper mapper = new ObjectMapper();
        return toolDefs.stream().map(def -> {
            try {
                Map<String, Object> funcDef = (Map<String, Object>) def.get("function");
                ChatFunction function = new ChatFunction();
                function.setName((String) funcDef.get("name"));
                function.setDescription((String) funcDef.get("description"));
                function.setParameters(mapper.valueToTree(funcDef.get("parameters")));

                ChatTool tool = new ChatTool();
                tool.setType("function");
                tool.setFunction(function);
                return tool;
            } catch (Exception e) {
                log.warn("转换工具定义失败: {}", e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 关闭 ArkService 线程池
     */
    public void shutdown() {
        arkService.shutdownExecutor();
    }
}
