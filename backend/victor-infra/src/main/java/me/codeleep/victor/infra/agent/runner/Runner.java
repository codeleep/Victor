package me.codeleep.victor.infra.agent.runner;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentResult;
import reactor.core.publisher.Flux;

/**
 * Runner 基础接口 - Agent 运行时核心抽象
 * 支持同步和流式两种执行模式
 *
 * @param <T> 输入类型（AgentDefinition 或 AgentTeam）
 */
public interface Runner<T> {

    /**
     * 同步执行
     *
     * @param input   执行输入（Agent 定义或 Agent 团队）
     * @param context 执行上下文
     * @return 执行结果
     */
    AgentResult run(T input, AgentContext context);

    /**
     * 流式执行
     *
     * @param input   执行输入（Agent 定义或 Agent 团队）
     * @param context 执行上下文
     * @return 执行结果流
     */
    Flux<AgentResult> streamRun(T input, AgentContext context);
}
