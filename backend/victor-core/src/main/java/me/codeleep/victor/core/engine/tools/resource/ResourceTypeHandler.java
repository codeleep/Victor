package me.codeleep.victor.core.engine.tools.resource;

import java.util.Map;

/**
 * 资源类型查询处理器接口
 * 每种资源类型实现一个 Handler
 */
public interface ResourceTypeHandler {

    /**
     * 处理的资源类型
     */
    ResourceType getType();

    /**
     * 返回该类型专属的额外参数 schema
     * 会被合并到 ResourceQueryTool 的总 schema 中
     */
    Map<String, Object> getExtraParametersSchema();

    /**
     * 执行查询
     *
     * @param arguments 完整的参数 Map（包含 resource_type、keyword、limit 等通用参数）
     * @return 查询结果
     */
    Object query(Map<String, Object> arguments);
}
