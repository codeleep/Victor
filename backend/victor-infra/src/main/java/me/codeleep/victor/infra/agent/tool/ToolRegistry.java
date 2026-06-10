package me.codeleep.victor.infra.agent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 * 管理所有可用的 AgentTool
 */
@Slf4j
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具
     */
    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
        log.debug("注册工具: {}", tool.getName());
    }

    /**
     * 批量注册
     */
    public void registerAll(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            register(tool);
        }
    }

    /**
     * 注销工具
     */
    public void unregister(String name) {
        tools.remove(name);
        log.debug("注销工具: {}", name);
    }

    /**
     * 获取工具
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 列出所有工具
     */
    public List<AgentTool> listTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取工具名称列表
     */
    public List<String> listToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 获取所有工具的 Function Definition
     */
    public List<Map<String, Object>> getFunctionDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            definitions.add(tool.toFunctionDefinition());
        }
        return definitions;
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
    }
}
