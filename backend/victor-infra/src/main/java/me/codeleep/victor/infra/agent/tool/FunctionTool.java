package me.codeleep.victor.infra.agent.tool;

import lombok.Getter;

import java.util.Map;
import java.util.function.Function;

/**
 * 基于 Java Function 的工具实现
 * 将一个简单的函数包装为 AgentTool
 */
@Getter
public class FunctionTool implements AgentTool {

    private final String name;
    private final String description;
    private final Map<String, Object> parametersSchema;
    private final Function<Map<String, Object>, Object> function;

    public FunctionTool(String name, String description,
                        Map<String, Object> parametersSchema,
                        Function<Map<String, Object>, Object> function) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.function = function;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return function.apply(arguments);
    }

    /**
     * 创建一个简单的 FunctionTool
     */
    public static FunctionTool of(String name, String description,
                                   Function<Map<String, Object>, Object> function) {
        return new FunctionTool(name, description,
                Map.of("type", "object", "properties", Map.of()),
                function);
    }
}
