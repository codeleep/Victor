package me.codeleep.victor.infra.agent.llm;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.LlmDefinition;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.llm.volcengine.VolcengineModelWrapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AgentScope 模型包装器工厂
 * 根据 LlmProtocol 创建对应的 AgentScope Model 实现
 * 同时提供裸调模型的便捷方法，供非 Agent 场景使用
 */
@Slf4j
@Component
public class ModelWrapperFactory {

    /**
     * 根据 LLM 定义创建 AgentScope 模型包装器
     */
    public Model create(LlmDefinition llmDefinition) {
        LlmProtocol protocol = llmDefinition.getProtocol();
        String baseUrl = llmDefinition.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = protocol.getDefaultBaseUrl();
        }
        String apiKey = llmDefinition.getApiKey();
        String modelName = llmDefinition.getModelName();
        double temperature = llmDefinition.getTemperature();
        int maxTokens = llmDefinition.getMaxTokens();

        return switch (protocol) {
            case DOUBAO -> new VolcengineModelWrapper(baseUrl, apiKey, modelName, temperature, maxTokens);
            case OPENAI, QWEN, CLAUDE -> throw new UnsupportedOperationException(
                    "暂未实现的 LLM 协议: " + protocol + "，请补充对应 ModelWrapper");
        };
    }

    /**
     * 裸调模型 - 单次问答（非 Agent 场景）
     * 用传入的 prompt 作为单条 user message 调用模型，阻塞取完整文本
     *
     * @param llmDefinition LLM 配置
     * @param prompt        用户输入
     * @return 模型完整回答文本
     */
    public String generate(LlmDefinition llmDefinition, String prompt) {
        Model model = create(llmDefinition);
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(prompt)
                .build();
        GenerateOptions options = GenerateOptions.builder()
                .stream(false)
                .build();

        StringBuilder sb = new StringBuilder();
        model.stream(List.of(userMsg), List.of(), options)
                .doOnNext(resp -> {
                    if (resp.getContent() != null) {
                        for (ContentBlock block : resp.getContent()) {
                            if (block instanceof TextBlock tb) {
                                sb.append(tb.getText());
                            }
                        }
                    }
                })
                .blockLast();

        return sb.toString();
    }
}
