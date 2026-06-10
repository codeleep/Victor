package me.codeleep.victor.infra.agent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI 适配器
 * 将 Spring AI 的 Function Callback 适配为 AgentTool
 *
 * 注意：Spring AI 1.0.0-M4 使用 Function Callback 模式，
 * 后续版本引入了 ToolCallback。此类提供通用适配能力。
 */
@Slf4j
public class SpringAiToolAdapter implements AgentTool {

    private final String name;
    private final String description;
    private final Map<String, Object> parametersSchema;
    private final Function<String, String> function;

    public SpringAiToolAdapter(String name, String description,
                                Map<String, Object> parametersSchema,
                                Function<String, String> function) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.function = function;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return parametersSchema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String argsJson = mapper.writeValueAsString(arguments);
            return function.apply(argsJson);
        } catch (Exception e) {
            log.error("执行 Spring AI 工具失败: {} - {}", name, e.getMessage());
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 从 Spring AI @Bean 函数创建适配器
     */
    public static SpringAiToolAdapter of(String name, String description,
                                          Map<String, Object> parametersSchema,
                                          Function<String, String> function) {
        return new SpringAiToolAdapter(name, description, parametersSchema, function);
    }
}
