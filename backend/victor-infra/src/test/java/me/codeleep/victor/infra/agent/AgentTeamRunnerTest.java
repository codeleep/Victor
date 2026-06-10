package me.codeleep.victor.infra.agent;

import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.AgentTeamDefinition;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import me.codeleep.victor.infra.agent.runner.AgentRunnerImpl;
import me.codeleep.victor.infra.agent.runner.AgentTeamRunnerImpl;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.infra.agent.tool.AgentToolAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentTeamRunnerImpl 单元测试
 */
class AgentTeamRunnerTest {

    // ==================== 辅助方法 ====================

    private AgentDefinition buildAgent(String name) {
        return AgentDefinition.builder()
                .name(name)
                .instructions("你是 " + name)
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("http://localhost")
                .llmApiKey("fake")
                .modelName("gpt-4")
                .build();
    }

    private AgentTeamDefinition buildTeamWithSubAgents() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        AgentDefinition subAgent1 = buildAgent("子Agent-出题");
        AgentDefinition subAgent2 = buildAgent("子Agent-评分");

        return AgentTeamDefinition.builder()
                .key("test-team")
                .name("测试团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of(
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent1)
                                .role("出题者")
                                .agentKey("question-generator")
                                .agentName("出题Agent")
                                .build(),
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent2)
                                .role("评分者")
                                .agentKey("scorer")
                                .agentName("评分Agent")
                                .build()
                ))
                .build();
    }

    private AgentTeamDefinition buildTeamWithoutSubAgents() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        return AgentTeamDefinition.builder()
                .key("solo-team")
                .name("单人团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of())
                .build();
    }

    // ==================== run() 测试 ====================

    @Test
    @DisplayName("run: 无子 Agent 时，直接委托给 agentRunner")
    void runWithoutSubAgents() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        AgentTeamDefinition team = buildTeamWithoutSubAgents();
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("测试");

        AgentResult expectedResult = AgentResult.success("主Agent 回复");

        // 用 spy 验证传入的 AgentDefinition
        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                assertEquals("主Agent", agent.getName());
                assertTrue(agent.getTools().isEmpty(), "无子 Agent 时工具列表应为空");
                return expectedResult;
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        AgentResult result = teamRunner.run(team, context);

        assertSame(expectedResult, result);
    }

    @Test
    @DisplayName("run: 有子 Agent 时，子 Agent 被包装为 AgentToolAdapter")
    void runWithSubAgents() {
        AgentTeamDefinition team = buildTeamWithSubAgents();
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("生成面试题");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                // 验证主 Agent 携带了子 Agent 工具
                assertEquals("主Agent", agent.getName());
                List<AgentTool> tools = agent.getTools();
                assertEquals(2, tools.size(), "应有 2 个子 Agent 工具");

                // 验证工具名称
                assertTrue(tools.stream().anyMatch(t -> t.getName().equals("call_question-generator")));
                assertTrue(tools.stream().anyMatch(t -> t.getName().equals("call_scorer")));

                // 验证工具类型是 AgentToolAdapter
                for (AgentTool tool : tools) {
                    assertInstanceOf(AgentToolAdapter.class, tool);
                }

                return AgentResult.success("完成");
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        AgentResult result = teamRunner.run(team, context);

        assertTrue(result.isSuccess());
        assertEquals("完成", result.getContent());
    }

    @Test
    @DisplayName("run: 子 Agent 无 agentKey 时，工具名使用 agentName")
    void runSubAgentToolNameUsesAgentNameWhenNoKey() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentTeamDefinition team = AgentTeamDefinition.builder()
                .key("team")
                .name("团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of(
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent)
                                .role("出题者")
                                .agentKey(null)  // 无 key
                                .agentName("出题Agent")
                                .build()
                ))
                .build();

        AgentContext context = new AgentContext("s1", 1L);

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                AgentTool tool = agent.getTools().get(0);
                // 无 agentKey 时，工具名应使用 agentDefinition.getName()
                assertEquals("call_出题Agent", tool.getName());
                return AgentResult.success("ok");
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        teamRunner.run(team, context);
    }

    @Test
    @DisplayName("run: 子 Agent 工具描述包含角色信息")
    void runSubAgentToolDescriptionWithRole() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentTeamDefinition team = AgentTeamDefinition.builder()
                .key("team")
                .name("团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of(
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent)
                                .role("出题者")
                                .agentKey("question-gen")
                                .agentName("出题Agent")
                                .build()
                ))
                .build();

        AgentContext context = new AgentContext("s1", 1L);

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                AgentTool tool = agent.getTools().get(0);
                // 描述应包含 agentName 和 role
                assertEquals("调用 出题Agent（出题者） 处理任务", tool.getDescription());
                return AgentResult.success("ok");
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        teamRunner.run(team, context);
    }

    @Test
    @DisplayName("run: 子 Agent 无角色时，工具描述不含角色信息")
    void runSubAgentToolDescriptionWithoutRole() {
        AgentDefinition mainAgent = buildAgent("主Agent");
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentTeamDefinition team = AgentTeamDefinition.builder()
                .key("team")
                .name("团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of(
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent)
                                .role(null)  // 无角色
                                .agentKey("question-gen")
                                .agentName("出题Agent")
                                .build()
                ))
                .build();

        AgentContext context = new AgentContext("s1", 1L);

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                AgentTool tool = agent.getTools().get(0);
                assertEquals("调用 出题Agent 处理任务", tool.getDescription());
                return AgentResult.success("ok");
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        teamRunner.run(team, context);
    }

    @Test
    @DisplayName("run: 主 Agent 原有工具应保留")
    void runMainAgentToolsPreserved() {
        AgentDefinition mainAgent = AgentDefinition.builder()
                .name("主Agent")
                .instructions("你是主Agent")
                .llmProtocol(LlmProtocol.OPENAI)
                .llmBaseUrl("http://localhost")
                .llmApiKey("fake")
                .modelName("gpt-4")
                .tools(List.of(
                        me.codeleep.victor.infra.agent.tool.FunctionTool.of("search", "搜索", args -> "result")
                ))
                .build();

        AgentDefinition subAgent = buildAgent("子Agent");

        AgentTeamDefinition team = AgentTeamDefinition.builder()
                .key("team")
                .name("团队")
                .executionMode("SEQUENTIAL")
                .mainAgent(mainAgent)
                .subAgents(List.of(
                        AgentTeamDefinition.SubAgentEntry.builder()
                                .agentDefinition(subAgent)
                                .agentKey("sub")
                                .agentName("子Agent")
                                .build()
                ))
                .build();

        AgentContext context = new AgentContext("s1", 1L);

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                // 原有的 search 工具 + 子 Agent 工具 = 2 个
                assertEquals(2, agent.getTools().size());
                assertTrue(agent.getTools().stream().anyMatch(t -> t.getName().equals("search")));
                assertTrue(agent.getTools().stream().anyMatch(t -> t.getName().equals("call_sub")));
                return AgentResult.success("ok");
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        teamRunner.run(team, context);
    }

    // ==================== streamRun() 测试 ====================

    @Test
    @DisplayName("streamRun: 无子 Agent 时，直接委托给 agentRunner")
    void streamRunWithoutSubAgents() {
        AgentTeamDefinition team = buildTeamWithoutSubAgents();
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("测试");

        AgentResult r1 = AgentResult.success("部分结果1");
        AgentResult r2 = AgentResult.success("最终结果");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public Flux<AgentResult> streamRun(AgentDefinition agent, AgentContext ctx) {
                assertEquals("主Agent", agent.getName());
                assertTrue(agent.getTools().isEmpty());
                return Flux.just(r1, r2);
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        Flux<AgentResult> flux = teamRunner.streamRun(team, context);

        List<AgentResult> results = flux.collectList().block();
        assertNotNull(results);
        assertEquals(2, results.size());
        assertSame(r1, results.get(0));
        assertSame(r2, results.get(1));
    }

    @Test
    @DisplayName("streamRun: 有子 Agent 时，子 Agent 被包装为工具")
    void streamRunWithSubAgents() {
        AgentTeamDefinition team = buildTeamWithSubAgents();
        AgentContext context = new AgentContext("s1", 1L);
        context.addUserMessage("生成面试题");

        AgentResult r1 = AgentResult.success("流式结果");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public Flux<AgentResult> streamRun(AgentDefinition agent, AgentContext ctx) {
                assertEquals("主Agent", agent.getName());
                assertEquals(2, agent.getTools().size());
                return Flux.just(r1);
            }
        };

        AgentTeamRunnerImpl teamRunner = new AgentTeamRunnerImpl(runner);
        List<AgentResult> results = teamRunner.streamRun(team, context).collectList().block();
        assertNotNull(results);
        assertEquals(1, results.size());
        assertSame(r1, results.get(0));
    }

    // ==================== 子 Agent 执行测试 ====================

    @Test
    @DisplayName("子 Agent 工具执行: 成功时返回内容")
    void subAgentToolExecuteSuccess() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                assertEquals("出题Agent", agent.getName());
                return AgentResult.success("生成了 5 道题");
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("input", "生成 Java 面试题"));

        assertEquals("生成了 5 道题", result);
    }

    @Test
    @DisplayName("子 Agent 工具执行: 失败时返回错误信息")
    void subAgentToolExecuteFailure() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                return AgentResult.error("LLM 超时");
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("input", "生成面试题"));

        assertEquals("执行错误: LLM 超时", result);
    }

    @Test
    @DisplayName("子 Agent 工具执行: 异常时返回异常信息")
    void subAgentToolExecuteException() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                throw new RuntimeException("连接超时");
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("input", "生成面试题"));

        assertEquals("执行失败: 连接超时", result);
    }

    @Test
    @DisplayName("子 Agent 工具执行: 异常 message 为 null 时返回异常类名")
    void subAgentToolExecuteNullExceptionMessage() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                throw new RuntimeException((String) null);
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("input", "生成面试题"));

        // 异常 message 为 null 时，使用异常类名作为兜底
        assertEquals("执行失败: RuntimeException", result);
    }

    @Test
    @DisplayName("子 Agent 工具执行: 结果 errorMessage 为 null 时返回兜底信息")
    void subAgentToolExecuteNullErrorMessage() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                // 构造一个 success=false 但 errorMessage=null 的结果
                AgentResult result = new AgentResult();
                result.setSuccess(false);
                // errorMessage 保持 null
                return result;
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("input", "生成面试题"));

        // errorMessage 为 null 时，使用 agentName 作为兜底
        assertEquals("执行错误: 出题Agent 执行失败", result);
    }

    @Test
    @DisplayName("子 Agent 工具执行: 无 input 参数时使用全部参数")
    void subAgentToolExecuteWithoutInputParam() {
        AgentDefinition subAgent = buildAgent("出题Agent");

        AgentRunner runner = new AgentRunnerImpl(null) {
            @Override
            public AgentResult run(AgentDefinition agent, AgentContext ctx) {
                // 验证上下文收到了参数的 toString
                String userMsg = ctx.getConversationHistory().get(0).getContent();
                assertTrue(userMsg.contains("topic"));
                return AgentResult.success("ok");
            }
        };

        AgentToolAdapter adapter = new AgentToolAdapter(subAgent, runner, "call_gen", "出题");
        Object result = adapter.execute(Map.of("topic", "Java", "count", "5"));

        assertEquals("ok", result);
    }
}
