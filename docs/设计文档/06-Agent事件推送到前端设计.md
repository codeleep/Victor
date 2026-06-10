# Agent 事件推送到前端设计文档

## 1. 概述

### 1.1 背景

当前 Agent 运行时已经具备完整的 Lifecycle Hooks 体系（`AgentLifecycleListener`），能够感知 LLM 调用、工具执行、Handoff、Guardrail 校验等所有关键事件。但 WebSocket 层只向前端推送 LLM 的最终文本输出（`stream_begin/chunk/end`），Agent 的**中间推理过程对前端完全不可见**。

前端无法知道：
- Agent 正在思考（LLM 调用中）
- Agent 调用了哪些工具、参数是什么
- 工具返回了什么结果
- 是否发生了 Agent 切换（Handoff）
- 是否触发了安全校验（Guardrail）
- 是否需要人类介入（Human-in-the-loop）

### 1.2 目标

建立一套 **Agent Event → WebSocket Message → 前端渲染** 的完整链路，让前端能够实时展示 Agent 的完整推理过程。

### 1.3 设计原则

- **事件驱动**：复用现有 `AgentLifecycleListener` 机制，不侵入 Runner 核心逻辑
- **渐进增强**：前端可以选择性渲染，不处理的事件类型忽略即可
- **协议兼容**：新增事件消息不影响现有 `stream_begin/chunk/end` 协议
- **序列号有序**：所有事件携带递增序列号，前端可排序、可检测丢失

---

## 2. 现有架构分析

### 2.1 Agent 生命周期事件（已实现）

```
victor-infra/.../agent/lifecycle/AgentLifecycleListener.java
```

| 回调方法 | 触发时机 | 携带数据 |
|---------|---------|---------|
| `onAgentStart` | Agent 开始执行 | agent, context |
| `onAgentEnd` | Agent 执行结束 | agent, context, result |
| `onHandoff` | Agent 切换 | fromAgent, toAgent, context |
| `onTurnStart` | Agentic Loop 新一轮开始 | agent, turn, maxTurns, context |
| `onToolStart` | 工具开始执行 | agent, toolName, **arguments**, context |
| `onToolEnd` | 工具执行结束 | agent, toolName, result, **success**, **errorMessage**, durationMs, context |
| `onLlmStart` | LLM 调用开始 | agent, context |
| `onLlmEnd` | LLM 调用结束（成功） | agent, result, durationMs |
| `onLlmError` | LLM 调用失败 | agent, errorMessage, durationMs, context |
| `onGuardrailCheck` | Guardrail 校验完成 | agent, stage, guardrailName, passed, reason, durationMs, context |
| `onHumanInputRequired` | 请求人类输入 | agent, requestId, prompt, options, context |

共 11 个事件回调，覆盖 Agent 执行全生命周期。

### 2.2 WebSocket 消息协议（已实现）

```
victor-web/.../websocket/protocol/ServerMessage.java
```

当前服务端消息类型：

| type | 用途 |
|------|------|
| `interview.status` | 状态通知（connected / listening） |
| `interview.stream_begin` | LLM 输出流开始 |
| `interview.stream_chunk` | LLM 输出流数据 |
| `interview.stream_end` | LLM 输出流结束 |
| `interview.error` | 错误通知 |
| `interview.reconnected` | 重连成功 |

### 2.3 断层分析

```
Runner (Agentic Loop)
  ├── fireAgentStart()       ──→  ❌ 前端无感知
  ├── fireGuardrailCheck()   ──→  ❌ 前端无感知  (新增)
  ├── fireTurnStart()        ──→  ❌ 前端无感知  (新增)
  ├── fireLlmStart()         ──→  ❌ 前端无感知
  ├── invokeLlm()            ──→  stream_chunk ✅ 前端能看到文本
  ├── fireLlmEnd()           ──→  ❌ 前端无感知
  ├── fireLlmError()         ──→  ❌ 前端无感知  (新增)
  ├── fireToolStart()        ──→  ❌ 前端无感知
  ├── executeTool()          ──→  ❌ 前端无感知
  ├── fireToolEnd()          ──→  ❌ 前端无感知
  ├── fireHandoff()          ──→  ❌ 前端无感知
  └── fireAgentEnd()         ──→  ❌ 前端无感知
```

核心问题：`InterviewSession.processText()` 只消费了 `streamRun()` 返回的文本 Flux，中间的所有生命周期事件没有桥接到 WebSocket。

---

## 3. 事件协议设计

### 3.1 协议总览

所有新增事件统一使用 `interview.event` 作为 type，通过 `event` 字段区分具体事件类型。

```json
{
  "type": "interview.event",
  "event": "llm_start",
  "seq": 1,
  "timestamp": 1717560000000,
  "data": { ... }
}
```

### 3.2 事件类型定义

#### 3.2.1 LLM 推理事件

**`llm_start`** — LLM 开始推理

```json
{
  "type": "interview.event",
  "event": "llm_start",
  "seq": 1,
  "timestamp": 1717560000000,
  "data": {
    "agentName": "interviewer",
    "turn": 2
  }
}
```

前端渲染：显示 "思考中..." 动画 / loading 指示器

---

**`llm_end`** — LLM 推理结束

```json
{
  "type": "interview.event",
  "event": "llm_end",
  "seq": 2,
  "timestamp": 1717560001500,
  "data": {
    "agentName": "interviewer",
    "durationMs": 1500,
    "hasToolCalls": true,
    "finishReason": "tool_calls"
  }
}
```

前端渲染：隐藏 loading，如果 `hasToolCalls=true` 则准备展示工具调用

---

**`llm_error`** — LLM 调用失败

```json
{
  "type": "interview.event",
  "event": "llm_error",
  "seq": 2,
  "timestamp": 1717560001500,
  "data": {
    "agentName": "interviewer",
    "errorMessage": "Connection timeout after 30000ms",
    "durationMs": 30000
  }
}
```

前端渲染：显示错误提示（如 "推理失败，请重试"），隐藏 loading

---

#### 3.2.2 工具调用事件

**`tool_start`** — 工具开始执行

```json
{
  "type": "interview.event",
  "event": "tool_start",
  "seq": 3,
  "timestamp": 1717560001600,
  "data": {
    "agentName": "interviewer",
    "toolName": "search_knowledge_base",
    "arguments": {
      "query": "Java 线程池原理"
    }
  }
}
```

前端渲染：显示工具调用卡片（工具名 + 参数），带 "执行中" 状态

---

**`tool_end`** — 工具执行结束

```json
{
  "type": "interview.event",
  "event": "tool_end",
  "seq": 4,
  "timestamp": 1717560002100,
  "data": {
    "agentName": "interviewer",
    "toolName": "search_knowledge_base",
    "durationMs": 500,
    "success": true,
    "resultPreview": "找到 3 条相关结果...",
    "errorMessage": ""
  }
}
```

前端渲染：更新工具调用卡片状态为 "完成"（成功）或 "失败"（显示 errorMessage），展示结果摘要（可折叠展开）

---

#### 3.2.3 Agent 切换事件

**`handoff`** — Agent 控制转移

```json
{
  "type": "interview.event",
  "event": "handoff",
  "seq": 5,
  "timestamp": 1717560002200,
  "data": {
    "fromAgent": "interviewer",
    "toAgent": "evaluator",
    "reason": "面试结束，切换到评估 Agent"
  }
}
```

前端渲染：显示 Agent 切换指示（如 "正在切换到评估阶段..."）

---

#### 3.2.4 轮次事件

**`turn_start`** — Agentic Loop 新一轮开始

```json
{
  "type": "interview.event",
  "event": "turn_start",
  "seq": 6,
  "timestamp": 1717560002250,
  "data": {
    "agentName": "interviewer",
    "turn": 2,
    "maxTurns": 10
  }
}
```

前端渲染：可选显示轮次指示（如 "第 2 轮推理"），或仅作为调试信息

---

#### 3.2.5 安全校验事件

**`guardrail_check`** — Guardrail 校验

```json
{
  "type": "interview.event",
  "event": "guardrail_check",
  "seq": 6,
  "timestamp": 1717560002300,
  "data": {
    "agentName": "interviewer",
    "guardrailName": "content_safety",
    "stage": "input",
    "passed": true,
    "reason": null
  }
}
```

前端渲染：通常不展示（仅日志），拦截时可显示提示

---

#### 3.2.6 人类介入事件

**`human_input_required`** — 需要人类输入

```json
{
  "type": "interview.event",
  "event": "human_input_required",
  "seq": 7,
  "timestamp": 1717560002400,
  "data": {
    "agentName": "interviewer",
    "prompt": "请确认是否继续面试？",
    "options": ["继续", "跳过", "终止"],
    "inputType": "choice"
  }
}
```

前端渲染：弹出交互式对话框，等待用户选择或输入

---

**`human_input_response`** — 人类输入响应（前端→后端）

```json
{
  "type": "interview.human_response",
  "event": "human_input_response",
  "data": {
    "response": "继续",
    "selectedIndex": 0
  }
}
```

---

#### 3.2.7 Agent 生命周期事件

**`agent_start`** — Agent 开始执行

```json
{
  "type": "interview.event",
  "event": "agent_start",
  "seq": 0,
  "timestamp": 1717559999000,
  "data": {
    "agentName": "interviewer"
  }
}
```

**`agent_end`** — Agent 执行结束

```json
{
  "type": "interview.event",
  "event": "agent_end",
  "seq": 8,
  "timestamp": 1717560003000,
  "data": {
    "agentName": "interviewer",
    "success": true,
    "turns": 3
  }
}
```

---

### 3.3 事件序列号

所有事件共享同一个递增序列号（`AtomicLong`），保证：
- 前端可以按序渲染
- 前端可以检测事件丢失（seq 不连续）
- 重连后可以知道哪些事件是新的

### 3.4 完整事件时序示例

一次典型的 Agent 执行周期，前端收到的事件序列：

```
seq=0  agent_start           "面试官 Agent 开始"
seq=1  guardrail_check       "输入校验通过 (input)"
seq=2  turn_start            "第 1 轮 / 最大 10 轮"
seq=3  llm_start             "思考中..."
       ── stream_chunk ×N ── "你好，请先做一下自我介绍。"  (LLM 文本输出)
seq=4  llm_end               "推理完成 (1.5s)"
seq=5  tool_start            "调用: save_answer({answer: '...'})"
seq=6  tool_end              "工具完成 (200ms, 成功)"
seq=7  turn_start            "第 2 轮 / 最大 10 轮"
seq=8  llm_start             "思考中..."
       ── stream_chunk ×N ── "好的，接下来我想问你..."
seq=9  llm_end               "推理完成 (1.2s)"
seq=10 guardrail_check       "输出校验通过 (output)"
seq=11 agent_end             "本轮结束"
```

异常路径示例：

```
seq=0  agent_start           "面试官 Agent 开始"
seq=1  guardrail_check       "输入校验未通过: 包含敏感内容"
seq=2  agent_end             "被 Guardrail 拦截"
```

LLM 失败示例：

```
seq=0  agent_start           "面试官 Agent 开始"
seq=1  turn_start            "第 1 轮 / 最大 10 轮"
seq=2  llm_start             "思考中..."
seq=3  llm_error             "Connection timeout (30s)"
seq=4  agent_end             "LLM 调用失败"
```

---

## 4. 后端实现方案

### 4.1 核心思路

在 `DefaultRunner` 已有的 `runListener`（`AgentLifecycleListener`）机制上，实现一个 **WebSocket 桥接监听器**，将生命周期事件转换为 WebSocket 消息发送给前端。

```
DefaultRunner
  │
  ├── fireLlmStart()  ──→  runListener.onLlmStart()
  │                              │
  │                              ▼
  │                    WebSocketBridgeListener
  │                              │
  │                              ▼
  │                    InterviewSession.sendMessage()
  │                              │
  │                              ▼
  │                         前端 WebSocket
  │
  ├── invokeLlm()    ──→  streamRun() Flux ──→  前端 (现有链路)
  │
  └── fireToolStart() ──→  runListener.onToolStart()
                                │
                                ▼
                         (同上桥接链路)
```

### 4.2 类设计

#### 4.2.1 新增事件消息类

```java
// victor-web/.../websocket/protocol/server/interview/InterviewServerEventMessage.java

public class InterviewServerEventMessage extends BaseServerMessage {

    private final String event;        // 事件类型: llm_start, tool_start, ...
    private final long seq;            // 序列号
    private final long timestamp;      // 毫秒时间戳
    private final Map<String, Object> data;  // 事件数据

    public InterviewServerEventMessage(String event, long seq, Map<String, Object> data) {
        super("interview.event");
        this.event = event;
        this.seq = seq;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
    }

    @Override
    protected Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", getType());
        map.put("event", event);
        map.put("seq", seq);
        map.put("timestamp", timestamp);
        map.put("data", data);
        return map;
    }
}
```

#### 4.2.2 WebSocket 桥接监听器

```java
// victor-web/.../websocket/session/WebSocketBridgeListener.java

public class WebSocketBridgeListener implements AgentLifecycleListener {

    private final InterviewSession session;
    private final AtomicLong sequence;

    public WebSocketBridgeListener(InterviewSession session, AtomicLong sequence) {
        this.session = session;
        this.sequence = sequence;
    }

    @Override
    public void onAgentStart(AgentDefinition agent, AgentContext context) {
        sendEvent("agent_start", Map.of("agentName", agent.getName()));
    }

    @Override
    public void onAgentEnd(AgentDefinition agent, AgentContext context, AgentResult result) {
        sendEvent("agent_end", Map.of(
            "agentName", agent.getName(),
            "success", result.isSuccess()
        ));
    }

    @Override
    public void onHandoff(AgentDefinition fromAgent, AgentDefinition toAgent, AgentContext context) {
        sendEvent("handoff", Map.of(
            "fromAgent", fromAgent.getName(),
            "toAgent", toAgent != null ? toAgent.getName() : "unknown"
        ));
    }

    @Override
    public void onTurnStart(AgentDefinition agent, int turn, int maxTurns, AgentContext context) {
        sendEvent("turn_start", Map.of(
            "agentName", agent.getName(),
            "turn", turn,
            "maxTurns", maxTurns
        ));
    }

    @Override
    public void onLlmStart(AgentDefinition agent, AgentContext context) {
        sendEvent("llm_start", Map.of("agentName", agent.getName()));
    }

    @Override
    public void onLlmEnd(AgentDefinition agent, AgentResult result, long durationMs, AgentContext context) {
        sendEvent("llm_end", Map.of(
            "agentName", agent.getName(),
            "durationMs", durationMs,
            "hasToolCalls", result.hasToolCalls(),
            "finishReason", result.getFinishReason() != null ? result.getFinishReason() : ""
        ));
    }

    @Override
    public void onLlmError(AgentDefinition agent, String errorMessage, long durationMs, AgentContext context) {
        sendEvent("llm_error", Map.of(
            "agentName", agent.getName(),
            "errorMessage", errorMessage,
            "durationMs", durationMs
        ));
    }

    @Override
    public void onToolStart(AgentDefinition agent, String toolName, Map<String, Object> arguments, AgentContext context) {
        sendEvent("tool_start", Map.of(
            "agentName", agent.getName(),
            "toolName", toolName,
            "arguments", arguments
        ));
    }

    @Override
    public void onToolEnd(AgentDefinition agent, String toolName, Object result,
                          boolean success, String errorMessage, long durationMs, AgentContext context) {
        String preview = result != null
            ? result.toString().substring(0, Math.min(200, result.toString().length()))
            : "";
        sendEvent("tool_end", Map.of(
            "agentName", agent.getName(),
            "toolName", toolName,
            "durationMs", durationMs,
            "success", success,
            "resultPreview", preview,
            "errorMessage", errorMessage != null ? errorMessage : ""
        ));
    }

    @Override
    public void onGuardrailCheck(AgentDefinition agent, String stage, String guardrailName,
                                  boolean passed, String reason, long durationMs, AgentContext context) {
        sendEvent("guardrail_check", Map.of(
            "agentName", agent.getName(),
            "stage", stage,
            "guardrailName", guardrailName,
            "passed", passed,
            "reason", reason != null ? reason : ""
        ));
    }

    @Override
    public void onHumanInputRequired(AgentDefinition agent, String requestId,
                                      String prompt, List<String> options, AgentContext context) {
        sendEvent("human_input_required", Map.of(
            "agentName", agent.getName(),
            "requestId", requestId,
            "prompt", prompt,
            "options", options,
            "inputType", options != null && !options.isEmpty() ? "choice" : "text"
        ));
    }

    private void sendEvent(String eventName, Map<String, Object> data) {
        long seq = sequence.incrementAndGet();
        session.sendMessage(new InterviewServerEventMessage(eventName, seq, data));
    }
}
```

#### 4.2.3 接入点：InterviewSession

在 `InterviewSession.submitProcessing()` 中，创建 `DefaultRunner` 时传入桥接监听器：

```java
// InterviewSession 中
private DefaultRunner createRunnerWithBridge() {
    WebSocketBridgeListener bridge = new WebSocketBridgeListener(this, eventSequence);
    return new DefaultRunner(chatClientFactory, RunnerConfig.defaults(), new TraceCollector(), bridge);
}
```

或者更优方案：让 `InterviewTextProcessor` 接收 `AgentLifecycleListener` 参数，在 `streamRun` 前设置。

### 4.3 与现有流式文本的关系

```
时间线 ──────────────────────────────────────────────────→

后端事件:  llm_start ── LLM流式输出 ── llm_end ── tool_start ── tool_end ── llm_start ── ...
               │         │                                          │
               │         ▼                                          │
WebSocket:  event_msg  stream_chunk×N    event_msg  event_msg  event_msg  event_msg  stream_chunk×N
               │         │                                          │
               ▼         ▼                                          ▼
前端渲染:   loading   逐步显示文字    隐藏loading  工具卡片    工具完成    loading    逐步显示文字
```

**关键**：`interview.event` 和 `interview.stream_chunk` 是**并行的两条消息流**，前端分别处理：
- `interview.event` → 更新 UI 状态（loading、工具卡片、切换提示）
- `interview.stream_chunk` → 累积显示 LLM 文本回复

---

## 5. 前端渲染方案

### 5.1 消息分发器

```typescript
// 前端 WebSocket 消息处理
ws.onmessage = (msg) => {
  const data = JSON.parse(msg.data);

  switch (data.type) {
    case 'interview.event':
      handleAgentEvent(data.event, data.data);
      break;
    case 'interview.stream_chunk':
      appendText(data.text);  // 现有逻辑
      break;
    case 'interview.status':
      updateStatus(data.state);  // 现有逻辑
      break;
    // ...
  }
};
```

### 5.2 事件处理映射

```typescript
function handleAgentEvent(event: string, data: any) {
  switch (event) {
    case 'agent_start':
      showAgentIndicator(data.agentName);
      break;

    case 'agent_end':
      hideAgentIndicator();
      break;

    case 'guardrail_check':
      if (!data.passed) {
        showWarning(`安全校验未通过: ${data.reason}`);
      }
      break;

    case 'turn_start':
      // 可选：调试模式下显示轮次
      updateTurnIndicator(data.turn, data.maxTurns);
      break;

    case 'llm_start':
      showThinkingIndicator(data.agentName);
      break;

    case 'llm_end':
      hideThinkingIndicator();
      break;

    case 'llm_error':
      hideThinkingIndicator();
      showError(`推理失败: ${data.errorMessage}`);
      break;

    case 'tool_start':
      showToolCard({
        toolName: data.toolName,
        arguments: data.arguments,
        status: 'running'
      });
      break;

    case 'tool_end':
      updateToolCard(data.toolName, {
        status: data.success ? 'done' : 'failed',
        duration: data.durationMs,
        result: data.resultPreview,
        error: data.errorMessage
      });
      break;

    case 'handoff':
      showAgentTransition(data.fromAgent, data.toAgent);
      break;

    case 'human_input_required':
      showInputDialog(data.requestId, data.prompt, data.options);
      break;
  }
}
```

### 5.3 UI 组件建议

| 事件 | UI 组件 | 说明 |
|------|---------|------|
| `agent_start` | 顶部 Agent 指示器 | 显示当前 Agent 名称 |
| `agent_end` | 隐藏 Agent 指示器 | — |
| `guardrail_check` | 仅日志 / 警告条 | 拦截时显示红色警告 |
| `turn_start` | 可选轮次指示 | "第 N 轮推理"（调试模式） |
| `llm_start` | 气泡 loading 动画 | 在对话区域显示 "正在思考..." |
| `stream_chunk` | 文本气泡（现有） | 逐步追加文字 |
| `llm_end` | 隐藏 loading | — |
| `llm_error` | 错误提示条 | "推理失败，请重试" + 错误原因 |
| `tool_start` | 工具调用卡片 | 折叠面板：工具名 + 参数 JSON |
| `tool_end` | 工具结果卡片 | 状态标记（成功/失败）+ 结果摘要（可展开） |
| `handoff` | 分隔线 + 提示文字 | "正在切换到 {toAgent}..." |
| `human_input_required` | 交互对话框 | 按钮选项 / 输入框 |

### 5.4 对话流渲染示例

```
┌─────────────────────────────────────────────┐
│  [Agent: 面试官]                             │
│                                             │
│  🤖 你好，请先做一下自我介绍。                │
│                                             │
│  ┌─ 💭 思考中... ──────────────────────┐    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─ 🔧 工具调用: save_answer ──────────┐    │
│  │  参数: {"answer": "我叫张三..."}      │    │
│  │  ✅ 完成 (120ms)                    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  🤖 好的，接下来我想问你关于 Java 的问题...   │
│                                             │
│  ┌─ 🔧 工具调用: search_knowledge ─────┐    │
│  │  参数: {"query": "Java线程池"}       │    │
│  │  ⏳ 执行中...                        │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

---

## 6. 人类介入（Human-in-the-loop）

### 6.1 触发机制

在 Agent 的工具列表中定义一个特殊工具 `request_human_input`：

```java
AgentTool humanInputTool = FunctionTool.builder()
    .name("request_human_input")
    .description("当需要人类确认或输入时调用")
    .parameter("prompt", String.class, "提示信息")
    .parameter("options", List.class, "可选项列表")
    .executor(args -> {
        String prompt = (String) args.get("prompt");
        List<String> options = (List<String>) args.get("options");
        // 通过 WebSocket 发送 human_input_required 事件
        // 阻塞等待前端响应
        return waitForHumanResponse(prompt, options);
    })
    .build();
```

### 6.2 阻塞等待响应

```java
// 在 InterviewSession 中维护一个 Map<requestId, CompletableFuture>
private final Map<String, CompletableFuture<String>> pendingHumanInputs = new ConcurrentHashMap<>();

// 工具执行时阻塞
private String waitForHumanResponse(String prompt, List<String> options) {
    String requestId = UUID.randomUUID().toString();
    CompletableFuture<String> future = new CompletableFuture<>();
    pendingHumanInputs.put(requestId, future);

    // 发送事件给前端
    sendEvent("human_input_required", Map.of(
        "requestId", requestId,
        "prompt", prompt,
        "options", options,
        "inputType", "choice"
    ));

    // 阻塞等待前端回复
    try {
        return future.get(5, TimeUnit.MINUTES);
    } catch (TimeoutException e) {
        return "timeout";
    }
}

// 前端回复时
public void handleHumanResponse(String requestId, String response) {
    CompletableFuture<String> future = pendingHumanInputs.remove(requestId);
    if (future != null) {
        future.complete(response);
    }
}
```

### 6.3 前端回复消息

前端通过 WebSocket 发送：

```json
{
  "type": "interview.human_response",
  "requestId": "uuid-xxx",
  "response": "继续"
}
```

---

## 7. 事件协议汇总

### 7.1 服务端 → 客户端

| type | event | 说明 | data 字段 |
|------|-------|------|----------|
| `interview.event` | `agent_start` | Agent 开始 | agentName |
| `interview.event` | `agent_end` | Agent 结束 | agentName, success |
| `interview.event` | `handoff` | Agent 切换 | fromAgent, toAgent |
| `interview.event` | `turn_start` | Agentic Loop 轮次开始 | agentName, turn, maxTurns |
| `interview.event` | `llm_start` | LLM 推理开始 | agentName |
| `interview.event` | `llm_end` | LLM 推理结束（成功） | agentName, durationMs, hasToolCalls, finishReason |
| `interview.event` | `llm_error` | LLM 推理失败 | agentName, errorMessage, durationMs |
| `interview.event` | `tool_start` | 工具开始执行 | agentName, toolName, arguments |
| `interview.event` | `tool_end` | 工具执行结束 | agentName, toolName, durationMs, success, resultPreview, errorMessage |
| `interview.event` | `guardrail_check` | 安全校验 | agentName, guardrailName, stage, passed, reason |
| `interview.event` | `human_input_required` | 需要人类输入 | requestId, prompt, options, inputType |

### 7.2 客户端 → 服务端

| type | 说明 | 字段 |
|------|------|------|
| `interview.human_response` | 人类输入响应 | requestId, response |

---

## 8. 实现路径

### Phase 1：基础事件推送

1. 新增 `InterviewServerEventMessage` 消息类
2. 新增 `WebSocketBridgeListener` 实现 `AgentLifecycleListener`
3. 在 `InterviewSession` / `InterviewTextProcessor` 中接入桥接监听器
4. 前端实现消息分发器和基础 UI（loading、工具卡片）

### Phase 2：人类介入

5. 实现 `request_human_input` 工具
6. 实现阻塞等待 + 前端回复机制
7. 前端实现交互对话框

### Phase 3：增强体验

8. 工具调用参数/结果的完整展示（折叠面板）
9. 事件序列号校验（检测丢失）
10. 重连后事件状态恢复

---

## 9. 待讨论问题

1. **工具参数是否需要脱敏**：`tool_start` 中的 `arguments` 可能包含敏感信息，是否需要过滤？
2. **工具结果大小限制**：`tool_end` 的 `resultPreview` 截取多长？是否需要前端按需请求完整结果？
3. **人类介入超时处理**：超时后 Agent 应该怎么做？自动跳过？终止？
4. **多 Agent 并行场景**：当前设计假设 Agent 串行执行，如果未来支持并行 Agent，事件需要增加 `agentId` 维度
5. **事件持久化**：是否需要将事件存入数据库，支持事后回放？
