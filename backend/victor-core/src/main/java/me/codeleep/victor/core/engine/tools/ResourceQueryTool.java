package me.codeleep.victor.core.engine.tools;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.engine.tools.resource.ResourceType;
import me.codeleep.victor.core.engine.tools.resource.ResourceTypeHandler;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资料查询工具（分发器）
 * 统一入口，按 resource_type 分发到对应的子工具
 */
@Slf4j
@Component
public class ResourceQueryTool implements AgentTool {

    private final Map<ResourceType, ResourceTypeHandler> handlerMap;

    public ResourceQueryTool(List<ResourceTypeHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(ResourceTypeHandler::getType, Function.identity()));
        log.info("ResourceQueryTool 初始化，已注册子工具: {}", handlerMap.keySet());
    }

    @Override
    public String getName() {
        return "resource_query";
    }

    @Override
    public String getDescription() {
        return "查询候选人资料，包括岗位(Job)、简历(Resume)、经历(Experience)。通过 resource_type 指定查询类型。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> resourceType = new LinkedHashMap<>();
        resourceType.put("type", "string");
        resourceType.put("description", "资料类型：job(岗位), resume(简历), experience(经历)");
        resourceType.put("enum", Arrays.stream(ResourceType.values()).map(ResourceType::getValue).toList());
        properties.put("resource_type", resourceType);

        // 合并所有子工具的参数 schema
        for (ResourceTypeHandler handler : handlerMap.values()) {
            properties.putAll(handler.getExtraParametersSchema());
        }

        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回结果数量，默认10");
        properties.put("limit", limit);

        params.put("properties", properties);
        params.put("required", List.of("resource_type"));

        return params;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String typeStr = (String) arguments.get("resource_type");
        if (typeStr == null || typeStr.isEmpty()) {
            return Map.of("error", "resource_type 不能为空");
        }

        ResourceType type;
        try {
            type = ResourceType.fromValue(typeStr);
        } catch (IllegalArgumentException e) {
            return Map.of("error", "不支持的资源类型: " + typeStr);
        }

        ResourceTypeHandler handler = handlerMap.get(type);
        if (handler == null) {
            return Map.of("error", "未找到资源类型的处理工具: " + typeStr);
        }

        log.info("资源查询分发: type={}, handler={}", typeStr, handler.getClass().getSimpleName());
        return handler.query(arguments);
    }

    @Override
    public Map<String, Object> toFunctionDefinition() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", getName());
        definition.put("description", getDescription());
        definition.put("parameters", getParametersSchema());
        return definition;
    }
}
