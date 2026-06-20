package me.codeleep.victor.core.engine.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.engine.tools.resource.ResourceType;
import me.codeleep.victor.core.engine.tools.resource.ResourceTypeHandler;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资料查询工具（分发器）
 * 统一入口，按 resource_type 分发到对应的子工具
 * 使用 AgentScope @Tool 注解，由 Toolkit 反射注册
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

    @Tool(name = "resource_query",
          description = "查询候选人资料，包括岗位(Job)、简历(Resume)、经历(Experience)。通过 resource_type 指定查询类型。")
    public Object query(
            @ToolParam(name = "resource_type", required = true,
                       description = "资料类型：job(岗位), resume(简历), experience(经历)") String resourceType,
            @ToolParam(name = "limit", required = false,
                       description = "返回结果数量，默认10") Integer limit) {

        if (resourceType == null || resourceType.isEmpty()) {
            return Map.of("error", "resource_type 不能为空");
        }

        ResourceType type;
        try {
            type = ResourceType.fromValue(resourceType);
        } catch (IllegalArgumentException e) {
            return Map.of("error", "不支持的资源类型: " + resourceType);
        }

        ResourceTypeHandler handler = handlerMap.get(type);
        if (handler == null) {
            return Map.of("error", "未找到资源类型的处理工具: " + resourceType);
        }

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("resource_type", resourceType);
        if (limit != null) {
            arguments.put("limit", limit);
        }

        log.info("资源查询分发: type={}, handler={}", resourceType, handler.getClass().getSimpleName());
        return handler.query(arguments);
    }
}
