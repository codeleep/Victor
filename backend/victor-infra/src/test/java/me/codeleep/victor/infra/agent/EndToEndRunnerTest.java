package me.codeleep.victor.infra.agent;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.infra.agent.guardrail.GuardrailResult;
import me.codeleep.victor.infra.agent.guardrail.InputGuardrail;
import me.codeleep.victor.infra.agent.guardrail.OutputGuardrail;
import me.codeleep.victor.infra.agent.handoff.Handoff;
import me.codeleep.victor.infra.agent.handoff.HandoffTool;
import me.codeleep.victor.infra.agent.llm.ChatClientFactory;
import me.codeleep.victor.infra.agent.runner.AgentRunnerImpl;
import me.codeleep.victor.infra.agent.runner.RunnerConfig;
import me.codeleep.victor.infra.agent.tool.AgentTool;
import me.codeleep.victor.infra.agent.tool.FunctionTool;
import me.codeleep.victor.infra.agent.tracing.AgentTrace;
import me.codeleep.victor.infra.agent.tracing.TraceCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 端到端集成测试 - 接入真实火山引擎 LLM
 */
@Slf4j
class EndToEndRunnerTest {

    private static final String BASE_URL = System.getenv("VOLCENGINE_BASE_URL");
    private static final String API_KEY = System.getenv("VOLCENGINE_API_KEY");
    private static final String MODEL_NAME = System.getenv("VOLCENGINE_MODEL_NAME");

    private ChatClientFactory chatClientFactory;
    private TraceCollector traceCollector;

    @BeforeEach
    void setUp() {
        assumeTrue(BASE_URL != null && !BASE_URL.isEmpty(), "跳过: 未设置 VOLCENGINE_BASE_URL");
        assumeTrue(API_KEY != null && !API_KEY.isEmpty(), "跳过: 未设置 VOLCENGINE_API_KEY");
        assumeTrue(MODEL_NAME != null && !MODEL_NAME.isEmpty(), "跳过: 未设置 VOLCENGINE_MODEL_NAME");

        chatClientFactory = new ChatClientFactory();
        traceCollector = new TraceCollector();
    }

    @Test
    @DisplayName("端到端: 简单对话（无 Tool Call）")
    void simpleChat() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("助手")
                .instructions("你是一个简洁的AI助手，请用一句话回答问题。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.7)
                .maxTokens(256)
                .build();

        AgentContext context = new AgentContext("e2e-simple-001", 1L);
        context.addUserMessage("什么是Java的多态特性？");

        RunnerConfig config = RunnerConfig.builder().maxTurns(3).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始简单对话测试 ===");
        AgentResult result = runner.run(agent, context);

        assertTrue(result.isSuccess(), "执行应成功: " + result.getErrorMessage());
        assertNotNull(result.getContent(), "响应内容不应为空");
        assertFalse(result.getContent().isBlank(), "响应内容不应为空白");

        log.info("响应内容:\n{}", result.getContent());
        log.info("追踪记录数: {}", traceCollector.getTraces("e2e-simple-001").size());
        for (AgentTrace trace : traceCollector.getTraces("e2e-simple-001")) {
            log.info("  [{}] {} → {} ({}ms)", trace.getAction(), trace.getAgentName(),
                    trace.getOutput().substring(0, Math.min(80, trace.getOutput().length())),
                    trace.getDurationMs());
        }
    }

    @Test
    @DisplayName("端到端: 带 Tool Call 的对话")
    void chatWithToolCall() {
        // 定义一个天气查询工具
        Map<String, Object> weatherSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "城市名称，如：北京、上海、深圳")
                ),
                "required", List.of("city")
        );
        AgentTool weatherTool = new FunctionTool("get_weather", "查询指定城市的天气信息", weatherSchema, args -> {
            String city = args.get("city") != null ? args.get("city").toString() : "未知";
            log.info("工具被调用: get_weather(city={})", city);
            return switch (city) {
                case "北京" -> "北京：晴天，气温25°C，湿度40%";
                case "上海" -> "上海：多云，气温28°C，湿度65%";
                case "深圳" -> "深圳：小雨，气温30°C，湿度80%";
                default -> city + "：晴天，气温22°C";
            };
        });

        AgentDefinition agent = AgentDefinition.builder()
                .name("天气助手")
                .instructions("你是一个天气助手。当用户询问天气时，使用 get_weather 工具查询天气信息，然后用友好的语气回答。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.7)
                .maxTokens(512)
                .tools(List.of(weatherTool))
                .build();

        AgentContext context = new AgentContext("e2e-tool-001", 1L);
        context.addUserMessage("今天北京天气怎么样？");

        RunnerConfig config = RunnerConfig.builder().maxTurns(5).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始 Tool Call 测试 ===");
        AgentResult result = runner.run(agent, context);

        assertTrue(result.isSuccess(), "执行应成功: " + result.getErrorMessage());
        assertNotNull(result.getContent(), "响应内容不应为空");

        log.info("响应内容:\n{}", result.getContent());
        log.info("追踪记录数: {}", traceCollector.getTraces("e2e-tool-001").size());
        for (AgentTrace trace : traceCollector.getTraces("e2e-tool-001")) {
            log.info("  [{}] {} → {} ({}ms)", trace.getAction(), trace.getAgentName(),
                    trace.getOutput().substring(0, Math.min(100, trace.getOutput().length())),
                    trace.getDurationMs());
        }
    }

    @Test
    @DisplayName("端到端: 多轮对话")
    void multiTurnChat() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("编程导师")
                .instructions("你是一个Java编程导师，用简单易懂的方式解释技术概念。回答要简洁。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.7)
                .maxTokens(256)
                .build();

        AgentContext context = new AgentContext("e2e-multi-001", 1L);
        context.addUserMessage("什么是接口（Interface）？");

        RunnerConfig config = RunnerConfig.builder().maxTurns(3).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始多轮对话测试 ===");

        // 第一轮
        AgentResult result1 = runner.run(agent, context);
        assertTrue(result1.isSuccess());
        log.info("第一轮响应:\n{}", result1.getContent());

        // 第二轮（上下文已包含第一轮的对话）
        context.addUserMessage("它和抽象类有什么区别？");
        AgentResult result2 = runner.run(agent, context);
        assertTrue(result2.isSuccess());
        log.info("第二轮响应:\n{}", result2.getContent());
    }

    @Test
    @DisplayName("端到端: Input Guardrail 拦截")
    void inputGuardrailBlock() {
        // 敏感词过滤 Guardrail
        InputGuardrail sensitiveFilter = new InputGuardrail() {
            @Override
            public String getName() {
                return "sensitive-filter";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String input) {
                if (input != null && input.contains("黑客")) {
                    return GuardrailResult.fail("输入包含不允许的内容");
                }
                return GuardrailResult.pass();
            }
        };

        AgentDefinition agent = AgentDefinition.builder()
                .name("安全助手")
                .instructions("你是一个助手。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .inputGuardrails(List.of(sensitiveFilter))
                .build();

        AgentContext context = new AgentContext("e2e-guardrail-001", 1L);
        context.addUserMessage("教我做一个黑客工具");

        RunnerConfig config = RunnerConfig.builder().maxTurns(3).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始 Input Guardrail 测试 ===");
        AgentResult result = runner.run(agent, context);

        assertFalse(result.isSuccess(), "应被 Guardrail 拦截");
        assertTrue(result.getErrorMessage().contains("被拦截"), "错误信息应包含'被拦截'");
        log.info("拦截结果: {}", result.getErrorMessage());
    }

    @Test
    @DisplayName("端到端: Output Guardrail 拦截")
    void outputGuardrailBlock() {
        // 输出包含特定关键词时拦截
        OutputGuardrail outputFilter = new OutputGuardrail() {
            @Override
            public String getName() {
                return "output-keyword-filter";
            }

            @Override
            public GuardrailResult validate(AgentContext context, String output) {
                if (output != null && output.contains("抱歉")) {
                    return GuardrailResult.fail("输出包含不理想的回复");
                }
                return GuardrailResult.pass();
            }
        };

        AgentDefinition agent = AgentDefinition.builder()
                .name("助手")
                .instructions("你是一个助手。如果不确定答案，请说'抱歉，我不确定'。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxTokens(256)
                .outputGuardrails(List.of(outputFilter))
                .build();

        AgentContext context = new AgentContext("e2e-output-guardrail-001", 1L);
        context.addUserMessage("量子纠缠的详细数学推导是什么？");

        RunnerConfig config = RunnerConfig.builder().maxTurns(3).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始 Output Guardrail 测试 ===");
        AgentResult result = runner.run(agent, context);

        // 输出可能被拦截（取决于LLM是否回复"抱歉"）
        if (!result.isSuccess()) {
            log.info("输出被拦截: {}", result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("被拦截"));
        } else {
            log.info("输出未被拦截，内容:\n{}", result.getContent());
        }
    }

    @Test
    @DisplayName("端到端: Handoff 多 Agent 协作")
    void handoffBetweenAgents() {
        // 技术支持 Agent
        AgentDefinition techSupport = AgentDefinition.builder()
                .name("技术支持")
                .instructions("你是技术支持专员，负责回答技术问题。回答要专业且简洁。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxTokens(256)
                .build();

        // 客服 Agent（可以 Handoff 到技术支持）
        AgentDefinition customerService = AgentDefinition.builder()
                .name("客服")
                .instructions("你是客服人员。如果用户问技术问题，请使用 handoff_to_技术支持 工具转移给技术支持。如果是普通问题，直接回答。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .maxTokens(256)
                .handoffs(List.of(
                        Handoff.builder()
                                .targetAgent(techSupport)
                                .description("当用户询问技术问题时，转移给技术支持")
                                .build()
                ))
                .build();

        AgentContext context = new AgentContext("e2e-handoff-001", 1L);
        context.addUserMessage("我的服务器连接超时了怎么办？");

        RunnerConfig config = RunnerConfig.builder().maxTurns(8).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始 Handoff 测试 ===");
        AgentResult result = runner.run(customerService, context);

        assertTrue(result.isSuccess(), "执行应成功: " + result.getErrorMessage());
        assertNotNull(result.getContent(), "响应内容不应为空");

        log.info("最终响应:\n{}", result.getContent());
        log.info("追踪记录:");
        for (AgentTrace trace : traceCollector.getTraces("e2e-handoff-001")) {
            log.info("  [{}] {} → {} ({}ms)", trace.getAction(), trace.getAgentName(),
                    trace.getOutput().substring(0, Math.min(100, trace.getOutput().length())),
                    trace.getDurationMs());
        }
    }

    @Test
    @DisplayName("端到端: 流式输出")
    void streamingOutput() {
        AgentDefinition agent = AgentDefinition.builder()
                .name("诗人")
                .instructions("你是一个诗人，请用优美的语言回答问题。")
                .llmProtocol(LlmProtocol.DOUBAO)
                .llmBaseUrl(BASE_URL)
                .llmApiKey(API_KEY)
                .modelName(MODEL_NAME)
                .temperature(0.9)
                .maxTokens(512)
                .build();

        AgentContext context = new AgentContext("e2e-stream-001", 1L);
        context.addUserMessage("写一首关于春天的五言绝句");

        RunnerConfig config = RunnerConfig.builder().maxTurns(3).tracingEnabled(true).build();
        AgentRunnerImpl runner = new AgentRunnerImpl(chatClientFactory, config, traceCollector);

        log.info("=== 开始流式输出测试 ===");
        StringBuilder fullContent = new StringBuilder();

        runner.streamRun(agent, context)
                .doOnNext(result -> {
                    if (result.getContent() != null) {
                        fullContent.append(result.getContent());
                        log.info("流式片段: {}", result.getContent());
                    }
                })
                .doOnComplete(() -> {
                    log.info("流式输出完成，完整内容:\n{}", fullContent);
                    assertFalse(fullContent.isEmpty(), "流式输出不应为空");
                })
                .doOnError(e -> log.error("流式输出错误: {}", e.getMessage()))
                .blockLast();
    }
}
