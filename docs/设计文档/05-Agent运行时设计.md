# Agent 运行时设计文档

## 1. 概述

### 1.1 背景

Victor 项目需要一套通用的 Agent 运行时，用于支撑面试、评估、检索等多种 Agent 场景。当前 `victor-service` 中的 Agent 引擎功能简单，缺少多 Agent 协作（Handoff）、安全校验（Guardrail）、可观测性（Tracing）等核心能力。

### 1.2 目标

参考 OpenAI Agents SDK 的架构设计，在 `victor-infra` 模块中实现一套通用的 Agent 运行时，基于 Spring AI，具备以下能力：

- **Agent 定义**：声明式定义 Agent 的指令、模型、工具
- **Tool Calling**：支持 LLM 调用外部工具
- **Handoff**：多 Agent 间控制转移
- **Input Filter**：Handoff 时控制下一个 Agent 能看到的对话历史
- **Agent-as-Tool**：将 Agent 包装为 Tool，调用方保持控制权
- **Lifecycle Hooks**：Agent 生命周期事件的观察回调
- **Guardrail**：输入/输出安全校验
- **Streaming**：流式输出
- **Tracing**：执行过程可观测

### 1.3 参考

- [OpenAI Agents SDK](https://github.com/openai/openai-agents-python)
- [Spring AI](https://docs.spring.io/spring-ai/reference/)

---

## 2. 架构设计

### 2.1 整体架构

```mermaid
graph TB
    subgraph "用户层"
        User[用户输入]
    end

    subgraph "Agent 运行时 (victor-infra)"
        subgraph "Runner 引擎"
            Runner[DefaultRunner<br/>Agentic Loop 编排]
            CMF[ChatModelFactory<br/>协议适配]
            HP[HandoffProcessor<br/>Agent 切换]
        end

        subgraph "安全层"
            IG[InputGuardrail]
            OG[OutputGuardrail]
        end

        subgraph "Agent 层"
            A1[Agent A]
            A2[Agent B]
            A3[Agent C]
        end

        subgraph "工具层"
            T1[FunctionTool]
            T2[SpringAiToolAdapter]
            HO[HandoffTool]
        end

        subgraph "LLM 屶"
            CF[ChatClientFactory]
            VCM[VolcengineChatModel]
            OCM[OpenAiChatModel]
            CCM[ClaudeChatModel]
        end

        subgraph "可观测层"
            TC[TraceCollector]
            AT[AgentTrace]
        end
    end

    subgraph "外部服务"
        Volcengine[火山引擎 Ark]
        OpenAI[OpenAI API]
        Claude[Claude API]
    end

    User --> IG
    IG --> Runner
    Runner --> CMF
    Runner --> HP
    Runner --> A1
    A1 --> CF
    CF --> VCM & OCM & CCM
    VCM --> Volcengine
    OCM --> OpenAI
    CCM --> Claude
    Runner --> T1 & T2 & HO
    HO -.->|Handoff| A2
    A2 -.->|Handoff| A3
    Runner --> OG
    OG --> User
    Runner --> TC
    TC --> AT
```

### 2.2 Agentic Loop 流程

```mermaid
flowchart TD
    Start([开始]) --> InputGuardrail{InputGuardrail<br/>校验}
    InputGuardrail -->|失败| Error1[返回错误]
    InputGuardrail -->|通过| BuildPrompt[构建 Prompt<br/>instructions + history]
    BuildPrompt --> CallLLM[调用 LLM]
    CallLLM --> HasToolCall{有 Tool Call?}

    HasToolCall -->|否| OutputGuardrail{OutputGuardrail<br/>校验}
    OutputGuardrail -->|失败| Error2[返回错误]
    OutputGuardrail -->|通过| ReturnResult[返回 AgentResult]

    HasToolCall -->|是| IsHandoff{是 Handoff?}
    IsHandoff -->|是| SwitchAgent[切换 Agent]
    SwitchAgent --> BuildPrompt

    IsHandoff -->|否| ExecTool[执行 Tool]
    ExecTool --> AddToHistory[将结果加入对话历史]
    AddToHistory --> MaxTurns{超过<br/>MaxTurns?}
    MaxTurns -->|是| Error3[返回超时错误]
    MaxTurns -->|否| BuildPrompt

    ReturnResult --> End([结束])
    Error1 --> End
    Error2 --> End
    Error3 --> End
```

---

## 3. 核心组件

### 3.1 组件关系图

```mermaid
classDiagram
    class AgentDefinition {
        +String name
        +String instructions
        +LlmProtocol llmProtocol
        +String llmBaseUrl
        +String llmApiKey
        +String modelName
        +double temperature
        +int maxTokens
        +List~AgentTool~ tools
        +List~Handoff~ handoffs
        +List~Guardrail~ inputGuardrails
        +List~Guardrail~ outputGuardrails
        +AgentLifecycleListener lifecycleListener
    }

    class AgentContext {
        +String sessionId
        +Long userId
        +List~ChatMessage~ conversationHistory
        +Map~String,Object~ variables
        +addUserMessage(content)
        +addAssistantMessage(content)
        +addToolMessage(toolCallId, toolName, content)
    }

    class AgentResult {
        +String content
        +List~ToolCall~ toolCalls
        +String handoffTarget
        +boolean success
        +String errorMessage
        +isHandoff()
        +hasToolCalls()
    }

    class Runner {
        <<interface>>
        +run(agent, context) AgentResult
        +streamRun(agent, context) Flux~AgentResult~
    }

    class DefaultRunner {
        -ChatModelFactory chatModelFactory
        -HandoffProcessor handoffProcessor
        -RunnerConfig config
        -TraceCollector traceCollector
        -AgentLifecycleListener runListener
        +run(agent, context) AgentResult
        +streamRun(agent, context) Flux~AgentResult~
    }

    class HandoffProcessor {
        +isHandoffTool(agent, toolName) boolean
        +findHandoff(agent, toolName) Handoff
        +processHandoff(handoff, toolCall, context, index) AgentDefinition
    }

    class ChatModelFactory {
        -ChatClientFactory chatClientFactory
        +isVolcengineProtocol(agent) boolean
        +createVolcengineModel(agent) VolcengineChatModel
        +createChatClient(agent) ChatClient
    }

    class AgentTool {
        <<interface>>
        +getName() String
        +getDescription() String
        +getParametersSchema() Map
        +execute(arguments) Object
        +toFunctionDefinition() Map
    }

    class FunctionTool {
        -String name
        -Function function
        +execute(arguments) Object
    }

    class HandoffTool {
        -Handoff handoff
        +execute(arguments) Object
    }

    class AgentToolAdapter {
        -AgentDefinition targetAgent
        -Runner runner
        -AgentToolAdapterConfig config
        +execute(arguments) Object
    }

    class Guardrail {
        <<interface>>
        +getName() String
        +validate(context, content) GuardrailResult
    }

    class InputGuardrail {
        <<interface>>
        +validate(context, input) GuardrailResult
    }

    class OutputGuardrail {
        <<interface>>
        +validate(context, output) GuardrailResult
    }

    class InputFilter {
        <<interface>>
        +filter(input) HandoffInputData
    }

    class AgentLifecycleListener {
        <<interface>>
        +onAgentStart(agent, context)
        +onAgentEnd(agent, context, result)
        +onHandoff(from, to, context)
        +onToolStart(agent, toolName, context)
        +onToolEnd(agent, toolName, result, duration, context)
        +onLlmStart(agent, context)
        +onLlmEnd(agent, result, duration, context)
    }

    class ChatClientFactory {
        +createChatClient(protocol, ...) ChatClient
        +createVolcengineChatModel(...) VolcengineChatModel
    }

    class VolcengineChatModel {
        -ArkService arkService
        -List~ChatTool~ tools
        +call(prompt) ChatResponse
        +stream(prompt) Flux~ChatResponse~
        +setTools(tools)
    }

    class TraceCollector {
        -Map tracesBySession
        +addTrace(sessionId, trace)
        +getTraces(sessionId) List
    }

    Runner <|.. DefaultRunner
    AgentTool <|.. FunctionTool
    AgentTool <|.. HandoffTool
    AgentTool <|.. AgentToolAdapter
    Guardrail <|.. InputGuardrail
    Guardrail <|.. OutputGuardrail
    InputFilter <|.. Handoff
    DefaultRunner --> ChatModelFactory
    DefaultRunner --> HandoffProcessor
    DefaultRunner --> TraceCollector
    DefaultRunner --> AgentLifecycleListener
    ChatModelFactory --> ChatClientFactory
    ChatModelFactory --> VolcengineChatModel
    HandoffProcessor --> Handoff
    AgentDefinition --> AgentTool
    AgentDefinition --> Handoff
    AgentDefinition --> Guardrail
    AgentDefinition --> AgentLifecycleListener
    HandoffTool --> Handoff
    Handoff --> AgentDefinition
    Handoff --> InputFilter
    AgentToolAdapter --> Runner
    AgentToolAdapter --> AgentDefinition
```

### 3.2 Agent 定义 (AgentDefinition)

Agent 是运行时的核心抽象，采用 Builder 模式构建：

```java
AgentDefinition agent = AgentDefinition.builder()
    .name("天气助手")
    .instructions("你是一个天气助手，当用户询问天气时使用工具查询。")
    .llmProtocol(LlmProtocol.DOUBAO)
    .llmBaseUrl("https://ark.cn-beijing.volces.com/api/v3")
    .llmApiKey("your-api-key")
    .modelName("ark-code-latest")
    .temperature(0.7)
    .maxTokens(4096)
    .tools(List.of(weatherTool))
    .handoffs(List.of(techSupportHandoff))
    .inputGuardrails(List.of(sensitiveFilter))
    .outputGuardrails(List.of(contentFilter))
    .build();
```

### 3.3 LLM 协议枚举 (LlmProtocol)

```mermaid
graph LR
    subgraph "LlmProtocol"
        OPENAI[OPENAI<br/>OpenAI API]
        CLAUDE[CLAUDE<br/>Anthropic API]
        QWEN[QWEN<br/>通义千问]
        DOUBAO[DOUBAO<br/>火山方舟]
    end

    OPENAI --> OpenAiChatModel
    CLAUDE --> AnthropicChatModel
    QWEN --> OpenAiChatModel
    DOUBAO --> VolcengineChatModel
```

| 枚举值 | 说明 | 默认 BaseURL | ChatModel |
|--------|------|-------------|-----------|
| OPENAI | OpenAI | https://api.openai.com/v1 | OpenAiChatModel |
| CLAUDE | Claude | https://api.anthropic.com | AnthropicChatModel |
| QWEN | 通义千问 | https://dashscope.aliyuncs.com/compatible-mode/v1 | OpenAiChatModel |
| DOUBAO | 火山方舟 | https://ark.cn-beijing.volces.com/api/v3 | VolcengineChatModel |

---

## 4. Tool 体系

### 4.1 Tool 类型

```mermaid
graph TD
    AgentTool[AgentTool 接口]
    AgentTool --> FunctionTool[FunctionTool<br/>Java Function 包装]
    AgentTool --> HandoffTool[HandoffTool<br/>Handoff 转移]
    AgentTool --> SpringAiToolAdapter[SpringAiToolAdapter<br/>Spring AI 适配]
```

### 4.2 FunctionTool

基于 `java.util.function.Function` 的简单工具：

```java
AgentTool weatherTool = new FunctionTool(
    "get_weather",
    "查询城市天气",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "city", Map.of("type", "string", "description", "城市名称")
        ),
        "required", List.of("city")
    ),
    args -> {
        String city = (String) args.get("city");
        return city + "：晴天，25°C";
    }
);
```

### 4.3 Tool Calling 时序

```mermaid
sequenceDiagram
    participant R as Runner
    participant LLM as LLM (Volcengine)
    participant T as Tool

    R->>LLM: 发送 Prompt + Tool Definitions
    LLM-->>R: 返回 tool_call: get_weather(city="北京")
    R->>T: execute({city: "北京"})
    T-->>R: "北京：晴天，25°C"
    R->>LLM: 发送 Tool Result
    LLM-->>R: 返回最终回复 "今天北京晴天..."
    R-->>R: OutputGuardrail 校验
```

### 4.4 Agent-as-Tool（Agent 作为工具）

将一个 Agent 包装为普通 Tool，调用方 Agent **保持控制权**，子 Agent 执行后返回结果。与 Handoff 的区别：

| 维度 | Handoff | Agent-as-Tool |
|------|---------|---------------|
| 控制权 | 转移给目标 Agent | 调用方保持控制权 |
| 输入 | 完整对话历史 | 结构化参数（tool_call 的 arguments） |
| 上下文 | 共享 | 默认独立（可配置） |
| 返回值 | Agent 接管对话 | 字符串结果返回给调用方 |

```mermaid
graph TD
    subgraph "调用方 Agent"
        CA[Agent A] --> CT[AgentToolAdapter<br/>包装 Agent B]
    end

    subgraph "子 Agent 执行"
        CT --> |创建子上下文| CB[Agent B]
        CB --> |Runner.run| LLM[LLM]
        LLM --> CB
        CB --> |返回结果| CT
    end

    CT --> |字符串结果| CA
    CA --> |继续处理| Output[最终输出]
```

**核心类**：

```java
// Agent-as-Tool 适配器
public class AgentToolAdapter implements AgentTool {
    private final AgentDefinition targetAgent;  // 目标 Agent
    private final Runner runner;                // Runner 实例
    private final AgentToolAdapterConfig config; // 配置

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 1. 从 arguments 提取输入消息
        // 2. 创建子 AgentContext（独立上下文）
        // 3. runner.run(targetAgent, subContext)
        // 4. 用 outputExtractor 提取结果
        // 5. 返回字符串
    }
}

// 配置
@Data @Builder
public class AgentToolAdapterConfig {
    private String toolName;                              // 工具名称
    private String toolDescription;                       // 工具描述
    private Map<String, Object> inputSchema;             // 输入参数 Schema
    private Function<AgentResult, String> outputExtractor; // 输出提取器
    @Builder.Default
    private boolean createNewContext = true;              // 是否创建新上下文
}
```

**使用示例**：

```java
// 定义专家 Agent
AgentDefinition expert = AgentDefinition.builder()
    .name("Java专家")
    .instructions("你是 Java 专家，回答 Java 相关问题。")
    .llmProtocol(LlmProtocol.DOUBAO)
    .llmBaseUrl(baseUrl).llmApiKey(apiKey).modelName(model)
    .build();

// 包装为 Tool
AgentToolAdapter expertTool = new AgentToolAdapter(
    expert, runner,
    AgentToolAdapterConfig.builder()
        .toolName("ask_java_expert")
        .toolDescription("询问 Java 专家关于 Java 的问题")
        .build()
);

// 主 Agent 使用该工具
AgentDefinition mainAgent = AgentDefinition.builder()
    .name("助手")
    .instructions("你是助手。Java 问题请使用 ask_java_expert 工具。")
    .tools(List.of(expertTool))
    .build();

AgentResult result = runner.run(mainAgent, context);
```

---

## 5. Handoff 机制

### 5.1 设计原理

Handoff 被设计为一种特殊的 Tool。LLM 通过 `tool_call` 触发 Handoff，Runner 检测到 Handoff 后切换 AgentDefinition，重新进入 Agentic Loop。

```mermaid
graph LR
    subgraph "Agent A (客服)"
        A_Def[AgentDefinition]
        A_Tools[普通 Tools]
        A_HO[HandoffTool<br/>→ Agent B]
    end

    subgraph "Agent B (技术支持)"
        B_Def[AgentDefinition]
        B_Tools[普通 Tools]
    end

    A_Def --> A_Tools
    A_Def --> A_HO
    A_HO -.->|LLM tool_call| B_Def
```

### 5.2 Handoff 时序

```mermaid
sequenceDiagram
    participant U as 用户
    participant R as Runner
    participant A as Agent A (客服)
    participant B as Agent B (技术支持)
    participant LLM as LLM

    U->>R: "我的服务器连不上了"
    R->>LLM: Agent A Prompt + HandoffTool
    LLM-->>R: tool_call: handoff_to_技术支持(reason="技术问题")
    R->>R: 检测到 Handoff，切换到 Agent B
    R->>LLM: Agent B Prompt + 对话历史
    LLM-->>R: "请检查服务器IP和端口..."
    R-->>U: 最终回复
```

### 5.3 代码示例

```java
// 定义技术支持 Agent
AgentDefinition techSupport = AgentDefinition.builder()
    .name("技术支持")
    .instructions("你是技术支持专员，回答技术问题。")
    .llmProtocol(LlmProtocol.DOUBAO)
    // ... 其他配置
    .build();

// 定义客服 Agent（可 Handoff 到技术支持）
AgentDefinition customerService = AgentDefinition.builder()
    .name("客服")
    .instructions("你是客服。技术问题请使用 handoff_to_技术支持 工具转移。")
    .handoffs(List.of(
        Handoff.builder()
            .targetAgent(techSupport)
            .description("当用户询问技术问题时转移")
            .build()
    ))
    .build();
```

### 5.4 Input Filter（输入过滤器）

Handoff 时，下一个 Agent 默认能看到完整的对话历史。Input Filter 允许在 Handoff 时过滤、压缩或转换历史，控制下一个 Agent 的可见范围。

```mermaid
sequenceDiagram
    participant U as 用户
    participant R as Runner
    participant A as Agent A
    participant F as InputFilter
    participant B as Agent B

    U->>R: "帮我查订单"
    R->>A: Agent A 处理
    A-->>R: tool_call: handoff_to_B
    R->>F: filter(HandoffInputData)
    F-->>R: 过滤后的历史（仅保留相关消息）
    R->>B: Agent B（仅看到过滤后的历史）
    B-->>R: 最终回复
    R-->>U: 结果
```

**核心接口**：

```java
// 输入过滤器（函数式接口）
@FunctionalInterface
public interface InputFilter {
    HandoffInputData filter(HandoffInputData input);
}

// 输入数据封装
@Data
@AllArgsConstructor
public class HandoffInputData {
    private List<AgentContext.ChatMessage> inputHistory;      // 当前完整历史
    private List<AgentContext.ChatMessage> preHandoffItems;   // handoff 前的消息
    private List<AgentContext.ChatMessage> newItems;          // handoff 后的新消息
    private AgentContext runContext;                           // 运行上下文
}
```

**使用示例**：

```java
// 示例1：只保留 user 消息
InputFilter userOnly = input -> {
    List<ChatMessage> filtered = input.getInputHistory().stream()
            .filter(msg -> "user".equals(msg.getRole()))
            .toList();
    return new HandoffInputData(filtered, input.getPreHandoffItems(),
            input.getNewItems(), input.getRunContext());
};

// 示例2：只保留最后 N 条消息（减少 token 消耗）
InputFilter keepLast5 = input -> {
    List<ChatMessage> history = input.getInputHistory();
    int keep = Math.min(5, history.size());
    List<ChatMessage> compressed = new ArrayList<>(
            history.subList(history.size() - keep, history.size()));
    return new HandoffInputData(compressed, input.getPreHandoffItems(),
            input.getNewItems(), input.getRunContext());
};

// 在 Handoff 中使用
Handoff handoff = Handoff.builder()
        .targetAgent(techSupport)
        .description("技术问题转移")
        .inputFilter(userOnly)  // 设置过滤器
        .build();
```

---

## 6. Guardrail 机制

### 6.1 Guardrail 流程

```mermaid
flowchart LR
    subgraph "输入 Guardrail"
        UI[用户输入] --> IG1[敏感词过滤]
        IG1 --> IG2[长度检查]
        IG2 --> IG3[格式校验]
    end

    subgraph "输出 Guardrail"
        OG1[内容过滤] --> OG2[格式检查]
        OG2 --> OG3[质量评估]
        OG3 --> Output[最终输出]
    end

    IG3 --> Runner[Runner / LLM]
    Runner --> OG1
```

### 6.2 代码示例

```java
// 敏感词过滤
InputGuardrail sensitiveFilter = new InputGuardrail() {
    @Override
    public String getName() { return "sensitive-filter"; }

    @Override
    public GuardrailResult validate(AgentContext ctx, String input) {
        if (input.contains("黑客")) {
            return GuardrailResult.fail("输入包含不允许的内容");
        }
        return GuardrailResult.pass();
    }
};

// 输出内容过滤
OutputGuardrail contentFilter = new OutputGuardrail() {
    @Override
    public String getName() { return "content-filter"; }

    @Override
    public GuardrailResult validate(AgentContext ctx, String output) {
        if (output.contains("密码")) {
            return GuardrailResult.fail("输出包含敏感信息");
        }
        return GuardrailResult.pass();
    }
};
```

### 6.3 GuardrailResult

```mermaid
graph TD
    GR[GuardrailResult]
    GR --> Pass[passed = true]
    GR --> Fail[passed = false]

    Fail --> F1[severity: ERROR]
    Fail --> F2[severity: WARNING]
    Fail --> F3[severity: INFO]
```

---

## 7. 流式输出

### 7.1 VolcengineChatModel 流式架构

```mermaid
sequenceDiagram
    participant R as DefaultRunner
    participant VCM as VolcengineChatModel
    participant SDK as Volcengine SDK
    participant API as Volcengine API

    R->>VCM: stream(prompt)
    VCM->>SDK: streamChatCompletion(request)
    SDK->>API: HTTP SSE 连接

    loop 每个 token
        API-->>SDK: ChatCompletionChunk
        SDK-->>VCM: Flowable<Chunk>
        VCM->>VCM: 转换为 ChatResponse
        VCM-->>R: Flux<ChatResponse>
        R-->>R: 转换为 AgentResult
    end

    API-->>SDK: [DONE]
    SDK-->>VCM: complete
    VCM-->>R: complete
```

### 7.2 桥接实现

火山引擎 SDK 使用 RxJava 的 `Flowable`，而 Spring 生态使用 Reactor 的 `Flux`。通过 `Flux.create()` 桥接：

```java
public Flux<ChatResponse> stream(Prompt prompt) {
    Flowable<ChatCompletionChunk> flowable = arkService.streamChatCompletion(request);

    return Flux.create(sink -> {
        flowable.subscribe(
            chunk -> sink.next(convertChunk(chunk)),
            error -> sink.error(error),
            () -> sink.complete()
        );
    });
}
```

---

## 8. Tracing 可观测性

### 8.1 Trace 记录类型

```mermaid
graph TD
    AT[AgentTrace] --> LLM[LLM_CALL<br/>LLM 调用]
    AT --> TOOL[TOOL_CALL<br/>工具执行]
    AT --> HANDOFF[HANDOFF<br/>Agent 转移]
    AT --> GUARD[GUARDRAIL_CHECK<br/>校验检查]
```

### 8.2 Trace 数据

| 字段 | 说明 |
|------|------|
| traceId | 追踪 ID |
| timestamp | 时间戳 |
| agentName | Agent 名称 |
| action | 动作类型 |
| input | 输入内容 |
| output | 输出内容 |
| durationMs | 耗时(毫秒) |
| status | 状态(SUCCESS/FAILED) |

### 8.3 输出示例

```
[LLM_CALL]       天气助手 → (tool_call)                   (2656ms)
[TOOL_CALL]       天气助手 → 北京：晴天，气温25°C，湿度40% (0ms)
[LLM_CALL]        天气助手 → 今天北京的天气很不错哦！...   (5926ms)
[GUARDRAIL_CHECK] 安全助手 → PASSED                        (1ms)
[HANDOFF]         客服    → 技术支持                        (0ms)
```

---

## 9. Lifecycle Hooks（生命周期钩子）

### 9.1 设计原理

Lifecycle Hooks 提供 Agent 执行过程中的观察回调，参考 OpenAI Agents SDK 的 `RunHooks` / `AgentHooks` 设计。

**两级监听**：
- **Run 级**：通过 `DefaultRunner` 构造函数传入，监听整个 Run 的所有事件
- **Agent 级**：通过 `AgentDefinition.lifecycleListener` 设置，仅监听该 Agent 的事件

```mermaid
sequenceDiagram
    participant U as 用户
    participant R as DefaultRunner
    participant RL as Run 级 Listener
    participant AL as Agent 级 Listener
    participant LLM as LLM
    participant T as Tool

    U->>R: run(agent, context)
    R->>RL: onAgentStart(agent, context)
    R->>AL: onAgentStart(agent, context)

    R->>RL: onLlmStart(agent, context)
    R->>AL: onLlmStart(agent, context)
    R->>LLM: 调用 LLM
    LLM-->>R: response
    R->>RL: onLlmEnd(agent, result, duration)
    R->>AL: onLlmEnd(agent, result, duration)

    alt 有 Tool Call
        R->>RL: onToolStart(agent, toolName, context)
        R->>AL: onToolStart(agent, toolName, context)
        R->>T: execute(arguments)
        T-->>R: result
        R->>RL: onToolEnd(agent, toolName, result, duration)
        R->>AL: onToolEnd(agent, toolName, result, duration)
    end

    alt Handoff
        R->>RL: onHandoff(fromAgent, toAgent, context)
        R->>AL: onHandoff(fromAgent, toAgent, context)
    end

    R->>RL: onAgentEnd(agent, context, result)
    R->>AL: onAgentEnd(agent, context, result)
    R-->>U: AgentResult
```

### 9.2 核心接口

```java
public interface AgentLifecycleListener {
    default void onAgentStart(AgentDefinition agent, AgentContext context) {}
    default void onAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {}
    default void onHandoff(AgentDefinition fromAgent, AgentDefinition toAgent, AgentContext context) {}
    default void onToolStart(AgentDefinition agent, String toolName, AgentContext context) {}
    default void onToolEnd(AgentDefinition agent, String toolName, Object result, long durationMs, AgentContext context) {}
    default void onLlmStart(AgentDefinition agent, AgentContext context) {}
    default void onLlmEnd(AgentDefinition agent, AgentResult result, long durationMs, AgentContext context) {}
}
```

### 9.3 LifecycleEvent 数据

```java
@Data @Builder
public class LifecycleEvent {
    private String agentName;
    private String sessionId;
    private Instant timestamp;
    private EventType eventType;  // AGENT_START, AGENT_END, HANDOFF, TOOL_START, TOOL_END, LLM_START, LLM_END
    private Map<String, Object> data;
}
```

### 9.4 使用示例

```java
// 日志监听器
AgentLifecycleListener logger = new AgentLifecycleListener() {
    @Override
    public void onAgentStart(AgentDefinition agent, AgentContext context) {
        log.info("[Lifecycle] Agent 开始: {}", agent.getName());
    }

    @Override
    public void onToolStart(AgentDefinition agent, String toolName, AgentContext context) {
        log.info("[Lifecycle] 工具调用: {} → {}", agent.getName(), toolName);
    }

    @Override
    public void onHandoff(AgentDefinition from, AgentDefinition to, AgentContext context) {
        log.info("[Lifecycle] Handoff: {} → {}", from.getName(), to.getName());
    }

    @Override
    public void onAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {
        log.info("[Lifecycle] Agent 结束: {}, success={}", agent.getName(), result.isSuccess());
    }
};

// Run 级：监听所有 Agent 的事件
DefaultRunner runner = new DefaultRunner(chatClientFactory, config, traceCollector, logger);

// Agent 级：仅监听特定 Agent 的事件
AgentDefinition agent = AgentDefinition.builder()
    .name("助手")
    .instructions("你是助手")
    .lifecycleListener(logger)
    .build();
```

---

## 10. 目录结构

```
victor-infra/src/main/java/me/codeleep/victor/agent/
├── core/
│   ├── AgentDefinition.java      # Agent 定义
│   ├── AgentContext.java         # 执行上下文
│   ├── AgentResult.java          # 执行结果
│   └── LlmProtocol.java          # LLM 协议枚举
├── runner/
│   ├── Runner.java               # Runner 接口
│   ├── DefaultRunner.java        # 默认实现（Agentic Loop 编排）
│   ├── ChatModelFactory.java     # ChatModel 工厂（协议适配）
│   ├── HandoffProcessor.java     # Handoff 处理器（Agent 切换）
│   └── RunnerConfig.java         # 配置
├── tool/
│   ├── AgentTool.java            # 工具接口
│   ├── FunctionTool.java         # Function 工具
│   ├── SpringAiToolAdapter.java  # Spring AI 适配
│   ├── AgentToolAdapter.java     # Agent-as-Tool 适配器
│   ├── AgentToolAdapterConfig.java # 适配器配置
│   └── ToolRegistry.java         # 工具注册表
├── handoff/
│   ├── Handoff.java              # Handoff 定义
│   ├── HandoffTool.java          # Handoff 工具
│   ├── InputFilter.java          # 输入过滤器接口
│   └── HandoffInputData.java     # 过滤器输入数据
├── guardrail/
│   ├── Guardrail.java            # Guardrail 接口
│   ├── InputGuardrail.java       # 输入校验
│   ├── OutputGuardrail.java      # 输出校验
│   └── GuardrailResult.java      # 校验结果
├── lifecycle/
│   ├── AgentLifecycleListener.java # 生命周期监听器接口
│   └── LifecycleEvent.java        # 生命周期事件数据
├── llm/
│   ├── ChatClientFactory.java    # ChatClient 工厂
│   └── volcengine/
│       └── VolcengineChatModel.java  # 火山引擎适配
└── tracing/
    ├── AgentTrace.java           # 追踪记录
    └── TraceCollector.java       # 追踪收集器
```

---

## 11. 使用示例

### 11.1 最简用法

```java
// 1. 定义 Agent
AgentDefinition agent = AgentDefinition.builder()
    .name("助手")
    .instructions("你是一个简洁的AI助手。")
    .llmProtocol(LlmProtocol.DOUBAO)
    .llmBaseUrl(baseUrl)
    .llmApiKey(apiKey)
    .modelName("ark-code-latest")
    .build();

// 2. 创建上下文
AgentContext context = new AgentContext("session-001", 1L);
context.addUserMessage("什么是多态？");

// 3. 执行
DefaultRunner runner = new DefaultRunner(chatClientFactory);
AgentResult result = runner.run(agent, context);

System.out.println(result.getContent());
```

### 11.2 带工具调用

```java
AgentTool searchTool = new FunctionTool("search", "搜索题库", schema, args -> {
    // 查询数据库...
    return results;
});

AgentDefinition agent = AgentDefinition.builder()
    .name("面试官")
    .instructions("你是面试官，使用 search 工具查询题库。")
    .tools(List.of(searchTool))
    // ... 其他配置
    .build();
```

### 11.3 多 Agent 协作

```java
AgentDefinition specialist = AgentDefinition.builder()
    .name("专家")
    .instructions("你是技术专家。")
    .build();

AgentDefinition router = AgentDefinition.builder()
    .name("路由器")
    .instructions("根据问题类型分配给专家。")
    .handoffs(List.of(
        Handoff.builder().targetAgent(specialist).description("技术问题").build()
    ))
    .build();
```

---

## 12. 测试覆盖

| 测试类 | 数量 | 类型 | 说明 |
|--------|------|------|------|
| ToolRegistryTest | 8 | 单元 | 工具注册、查询、执行 |
| GuardrailTest | 4 | 单元 | 输入/输出校验 |
| HandoffTest | 4 | 单元 | Handoff 定义和执行 |
| RunnerTest | 8 | 单元 | 核心组件 |
| InputFilterTest | 4 | 单元 | 输入过滤器 |
| LifecycleHooksTest | 6 | 单元 | 生命周期钩子 |
| AgentToolAdapterTest | 7 | 单元 | Agent-as-Tool |
| EndToEndRunnerTest | 7 | 端到端 | 真实 LLM 调用 |

**端到端场景**：简单对话、Tool Call、多轮对话、Input Guardrail 拦截、Output Guardrail 拦截、Handoff、流式输出。
