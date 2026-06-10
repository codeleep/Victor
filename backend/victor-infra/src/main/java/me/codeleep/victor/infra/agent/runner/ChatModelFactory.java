package me.codeleep.victor.infra.agent.runner;

import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.llm.ChatClientFactory;
import me.codeleep.victor.infra.agent.llm.volcengine.VolcengineChatModel;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

/**
 * ChatModel 工厂，封装火山引擎协议的特殊处理逻辑。
 *
 * <p>从 AgentRunnerImpl 中提取，消除 {@code createChatClient()} 和
 * {@code streamRun()} 中重复的火山引擎模型创建代码。</p>
 */
public class ChatModelFactory {

    private final ChatClientFactory chatClientFactory;

    public ChatModelFactory(ChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    /**
     * 判断是否为火山引擎协议。
     */
    public boolean isVolcengineProtocol(AgentDefinition agent) {
        return agent.getLlmProtocol() == LlmProtocol.DOUBAO;
    }

    /**
     * 创建火山引擎 ChatModel 并设置工具定义。
     *
     * <p>当 Agent 使用 DOUBAO 协议时调用，返回的 Model 可直接用于流式调用。</p>
     *
     * @param agent Agent 定义
     * @return 配置好的 VolcengineChatModel
     */
    public VolcengineChatModel createVolcengineModel(AgentDefinition agent) {
        VolcengineChatModel model = chatClientFactory.createVolcengineChatModel(
                agent.getLlmBaseUrl(), agent.getLlmApiKey(), agent.getModelName(),
                agent.getTemperature(), agent.getMaxTokens());

        if (agent.getTools() != null && !agent.getTools().isEmpty()) {
            List<Map<String, Object>> toolDefs = agent.getTools().stream()
                    .map(AgentTool::toFunctionDefinition)
                    .toList();
            model.setTools(VolcengineChatModel.convertToolDefinitions(toolDefs));
        }

        return model;
    }

    /**
     * 创建 ChatClient，自动根据协议类型选择实现。
     *
     * <p>DOUBAO 协议使用 VolcengineChatModel 包装为 ChatClient，
     * 其他协议使用通用的 ChatClientFactory。</p>
     *
     * @param agent Agent 定义
     * @return ChatClient 实例
     */
    public ChatClient createChatClient(AgentDefinition agent) {
        if (isVolcengineProtocol(agent)) {
            return ChatClient.builder(createVolcengineModel(agent)).build();
        }

        return chatClientFactory.createChatClient(
                agent.getLlmProtocol(),
                agent.getLlmBaseUrl(),
                agent.getLlmApiKey(),
                agent.getModelName(),
                agent.getTemperature(),
                agent.getMaxTokens()
        );
    }
}
