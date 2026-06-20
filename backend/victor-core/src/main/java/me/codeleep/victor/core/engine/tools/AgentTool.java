package me.codeleep.victor.core.engine.tools;

import me.codeleep.victor.core.engine.AgentDefinitionFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Agent 工具标记接口
 * <p>
 * 实现该接口并标注 {@link io.agentscope.core.tool.Tool @Tool} 注解的 Spring Bean，
 * 会被 {@code AgentDefinitionFactory} 按工具名注册，供 Agent 调用。
 * <p>
 * 工具自注册：容器初始化完成（{@link ContextRefreshedEvent}）时，每个 AgentTool Bean
 * 通过下面的 default 方法主动调用 {@code AgentDefinitionFactory.registerTool(this)}。
 * 这样工厂无需在构造期注入工具集合，也无需感知工具的存在，避免循环依赖。
 */
public interface AgentTool {

    /**
     * 容器就绪后自注册到 AgentDefinitionFactory。
     * <p>
     * 通过事件上下文获取工厂单例（此时所有 Bean 已初始化完成），调用 registerTool(this)。
     * 子类无需重写；如需自定义注册时机可覆盖本方法。
     */
    @EventListener(ContextRefreshedEvent.class)
    default void registerSelf(ContextRefreshedEvent event) {
        event.getApplicationContext().getBean(AgentDefinitionFactory.class).registerTool(this);
    }
}