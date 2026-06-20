package me.codeleep.victor.infra.agent.runner;

import io.agentscope.core.ReActAgent;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import reactor.core.publisher.Flux;

/**
 * Runner 基础接口 - Agent 运行时核心抽象
 * 接受已构建的 AgentScope ReActAgent 实例，驱动单次对话执行
 * 支持同步和流式两种执行模式
 */
public interface Runner {

    /**
     * 同步执行
     *
     * @param agent   已构建的 ReActAgent 实例
     * @param context 执行上下文
     * @return 执行结果
     */
    AgentResult run(ReActAgent agent, AgentContext context);

    /**
     * 流式执行
     *
     * @param agent   已构建的 ReActAgent 实例
     * @param context 执行上下文
     * @return 执行结果流（事件类型区分思考/工具调用/工具结果/最终回答）
     */
    Flux<AgentResult> streamRun(ReActAgent agent, AgentContext context);
}
