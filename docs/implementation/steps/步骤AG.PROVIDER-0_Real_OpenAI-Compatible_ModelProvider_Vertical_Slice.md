# 步骤 AG.PROVIDER-0 — Real OpenAI-Compatible ModelProvider Vertical Slice

> ⚠ **本文档已由 `AG.PROVIDER-0.R1` 修订。**
> 下文 §12 GAP-1 已 **RESOLVED**：`ModelTurn.ToolCalls` 现携带 `content`。
> §10 / §17 的测试数量与 §21 的兼容性表述均已更正。
> 修订记录见文末 §19。

## 1. Metadata

| 项 | 值 |
|---|---|
| 步骤 | `AG.PROVIDER-0`（R1 修订见 §19） |
| 工程 | AndroidAgent |
| 根目录 | `D:\codebank\AndCode\AndroidAgent` |
| 基线 | `2174499`（parent `9d43598` = tag `ag-0`） |
| 类型 | 首个真实 Provider 纵向集成 |
| 结果 | **`AG.PROVIDER-0_LIVE_BLOCKED_CREDENTIALS`**（当前） |
| Commit | **无**（`NO COMMIT`） |
| Push | **无** |

## 2. Baseline

```text
branch   : main
HEAD     : 2174499210ed5bfca6ce0789cdf6974fc6693bda
parent   : 9d4359817413f5c30308ba5fad9f9e35e5f5beb2  (tag: ag-0)
status   : CLEAN
tags     : ag-0, ag-baseline-0
```

四项全部符合预期，未执行 reset / stash / checkout / clean。

## 3. Goal

用真实的 OpenAI-compatible Provider 验证冻结的 AG Core ModelProvider 边界
能否承载真实模型交互。

```text
Real Model API ↕ Concrete ModelProvider ↕ AG Core
```

## 4. Scope

实现：Provider module、wire 映射、错误分类、deterministic 测试套件、
live 测试入口。

未实现：ShellTool、Android Tools、AT 集成、AGA/AGC、streaming、Responses API、
framework 类抽象。

## 5. Existing Core Contracts

以下为 **R1 之后的当前真实状态**（读自 `ag-core/src/main`，未凭任务文本猜测字段）：

```text
ModelProvider  fun interface, suspend fun complete(ModelRequest): ModelTurn
ModelRequest   (sessionId: String, instructions: String,
                 history: List<Message>, tools: List<ToolSpec>)
ModelTurn      FinalAnswer(content)
               ToolCalls(
                   calls: List<ToolCall>,
                   content: String = "",      ← R1 新增
               )
ToolCall       (id: String, name: String, arguments: String = "")
ToolSpec       (name: String, description: String)
Message        UserMessage(content)
               ModelMessage(content, toolCalls)
               ToolResultMessage(toolCallId, toolName, result)
ToolResult     (success: Boolean, output: String, error: String?)
RunOutcome     Answer(content) | Failure(RunFailure)
```

> **PRE-R1 HISTORICAL STATE**：R1 之前 `ModelTurn.ToolCalls` 只有 `calls`，
> 无 `content` 字段。该状态已不存在，仅作历史记录。

`ModelRequest.instructions` 是**独立字段**，不是 Message —— 因此
provider 需要把它投影为开头的 `system` 消息。

### Current test state

```text
AG Core JUnit        41 / 41     (39 pre-R1 + T15 + T15b)
Provider offline     24 / 24
Provider live         6 tests defined (L1–L6, @Tag("live"))
```

> **PRE-R1 HISTORICAL STATE**：`39`（R1 前）、`16`、`19` 等数字均为历史值，
> 不再是当前状态。详见 §19.3 的数量矛盾根因。

## 6. Provider Architecture

```text
com.androidagent.provider.openai
├── OpenAiCompatibleConfig     baseUrl / apiKey / model / requestTimeout
├── OpenAiCompatibleProvider   实现冻结的 ModelProvider
├── OpenAiProviderException    Network/HttpStatus/Timeout/Protocol/Parse/
│                             InvalidToolCall（UnsupportedMixedTurn 已于 R1 删除）
└── ChatCompletionWire         全部 OpenAI wire 知识，只存在于此文件
```

依赖方向单向：

```text
:provider-openai-compatible  ──implements──▶  :ag-core  ModelProvider
:ag-core  ─X─▶  :provider-openai-compatible
```

HTTP 用 JDK `java.net.http.HttpClient`；JSON 用 kotlinx-serialization-json
的 **tree API**（`JsonElement`），**未引入 compiler serialization plugin**，
未建立 `@Serializable` DTO 层级。

## 7. Wire Mapping

| AG Core | OpenAI wire |
|---|---|
| `ModelRequest.instructions` | 前置 `{"role":"system","content":...}`（非空时才发） |
| `UserMessage` | `{"role":"user","content":...}` |
| `ModelMessage`（无 tool） | `{"role":"assistant","content":...}` |
| `ModelMessage`（有 tool） | assistant + `tool_calls[]`（id/type/function.name/arguments） |
| `ToolResultMessage` | `{"role":"tool","tool_call_id":...,"content":...}` |
| `ToolSpec` | `{"type":"function","function":{name,description,parameters}}` |
| — | `"stream": false` 固定 |

Tool result content：`output`；当 `error` 非空时追加 `\n[error] <error>`，
让模型能看到失败原因。`ToolResult.success` 是 AG Core 的机制标志，
wire 无此概念，因此不被序列化。

## 8. Tool Mapping

`ToolSpec` 只有 name + description，**没有 typed argument schema**。
因此使用 permissive projection：

```json
"parameters": { "type": "object", "additionalProperties": true }
```

> ⚠ 这是 **Provider vertical slice 的 permissive projection**，
> **不是**最终的 typed Tool schema。typed schema 继续 Deferred。

## 9. Error Mapping

| 类别 | 触发 |
|---|---|
| `Network` | IOException / InterruptedException |
| `HttpStatus` | 非 2xx（保留 status、safe endpoint、截断脱敏摘要） |
| `Timeout` | `HttpTimeoutException` |
| `Protocol` | HTTP 200 但 `choices`/`message` 缺失、choices 为空、content 与 tool_calls 皆空 |
| `Parse` | body 非合法 JSON |
| `InvalidToolCall` | tool call 缺 `id` / `function` / `name` / `arguments` |

> `UnsupportedMixedTurn` 曾用于「同一 turn 同时有 prose 与 tool_calls」，
> 已随 `AG.PROVIDER-0.R1` 的契约变更**删除**（见 §12、§19）。

`CancellationException` **原样重抛**，不包装为 provider failure。

实现过程中修复了 3 个**自查发现的真实缺陷**：

| 缺陷 | 影响 |
|---|---|
| `safeEndpoint()` 用 `uri.host` 丢失端口 | 诊断指向错误地址 |
| `data class` 自动 `toString()` 泄露 `apiKey` | 违反 §22 |
| `sendAsync` 把异常包成 `CompletionException` | `HttpTimeoutException` 永不匹配，timeout 被误分类 |

## 10. Deterministic Tests

`com.sun.net.httpserver.HttpServer`（JDK 自带），未引入 MockWebServer。
离线、确定性、随机端口。

| # | 用例 | 结果 |
|---|---|---|
| P1 | final answer content 精确映射 | PASS |
| P2 | 单 tool call，id/name/**opaque arguments 逐字节** | PASS |
| P3 | 出站消息顺序与字段（含 tool result 投影） | PASS |
| P3b | 无 instructions 则无 system 消息 | PASS |
| P4 | 多 tool call 保留数量/顺序/id/name/arguments | PASS |
| **P5** | **mixed content + tool_calls 无损保留**（R1 改写） | **PASS** |
| P6 | 401 → HttpStatus | PASS |
| P7 | 500 → HttpStatus | PASS |
| P7b | 429 独立状态码 | PASS |
| P8 | 非法 JSON → Parse | PASS |
| P9 | 缺 choices → Protocol | PASS |
| P9b | choices 为空 → Protocol | PASS |
| P9c | choice 无 message → Protocol | PASS |
| P9d | tool call 缺 id → InvalidToolCall | PASS |
| P9e | tool call 缺 arguments → InvalidToolCall（不填默认值） | PASS |
| P10 | timeout → Timeout（不是 Network） | PASS |
| P11 | cancellation 传播为 CancellationException | PASS |
| P12 | 异常不含 api key（但 header 确实发送） | PASS |
| P12b | config.toString() 不泄露 api key | PASS |
| V1 | **真实 provider→Tool→真实 provider 全链路** | PASS |
| V2 | tool 失败原因回传给模型 | PASS |
| V3 | unknown tool 结构化结果回传 | PASS |
| V4 | mixed prose + tool call → Tool → 第二次请求（R1 新增） | PASS |

### 测试数量（真实 JUnit 执行结果，R1 更正）

```text
AG Core JUnit executed tests : 41   (39 + R1 新增 T15/T15b)
Provider JUnit executed tests : 24   (ProviderProtocolTest 20 + VerticalSliceTest 4)
Named provider scenarios      : 24   (20 个 P 标签 + 4 个 V 场景，与 JUnit 测试 1:1)
```

> ⚠ **更正**：本文档初版曾写「19 个 deterministic 测试」。该数字有误。
> 更严重的是，AG.PROVIDER-0 报告写「16/16」也**偏少**：
> `P8`/`P9`/`P9b`/`P9c`/`P9d`/`P9e` 六个方法因返回类型非 `void`
> （`= runBlocking { … assertThrows(…) }` 使返回类型被推断为异常类型）
> 而被 **JUnit 静默跳过** —— 不报错，只是不执行。
> 该问题已于 R1 修复（显式 `: Unit`），六个用例现已真实运行。
> **教训**：JUnit 方法必须显式 `Unit` 返回，否则失败会被静默吞掉。

## 11. Live API Evidence

```text
L1 real final answer                NOT EXECUTED
L2 real tool call                   NOT EXECUTED
L3 real tool result continuation    NOT EXECUTED
L4 multiple tool call observation   NOT EXECUTED
L5 mixed prose + tool call          NOT EXECUTED
```

原因（实测确认，非推测）：

```text
AG_OPENAI_BASE_URL = NOT SET
AG_OPENAI_API_KEY  = NOT SET
AG_OPENAI_MODEL    = NOT SET
```

环境中不存在任何 OpenAI 类凭据（`OPENAI|ANTHROPIC|API_KEY|BASE_URL|LLM|MODEL|EMG`
全部未设置）。因此 **L1–L5 无法执行**。

live 测试已实现（`LiveApiTest`，`@Tag("live")`），由独立 task 执行：

```text
.\gradlew.bat :provider-openai-compatible:liveTest
```

缺少环境变量时该 task **快速失败并给出明确提示**，而不是静默跳过：

```text
liveTest requires environment variables: AG_OPENAI_BASE_URL, AG_OPENAI_MODEL
(and AG_OPENAI_API_KEY unless the endpoint needs no auth)
```

未伪造任何 live 结果。§25 L4/L5 要求的 `NOT OBSERVED` 记录同样无法产生，
因为连一次真实调用都没发生。

## 12. Contract Gaps

### GAP-1 ✅ **RESOLVED by AG.PROVIDER-0.R1** ModelTurn 无法表达 prose + tool_calls

**原始证据**（契约变更前的 `ag-core/src/main` 实际代码）：

```text
Model.kt:12   data class FinalAnswer(val content: String) : ModelTurn
Model.kt:19   data class ToolCalls(val calls: List<ToolCall>) : ModelTurn
              ← 无 content 字段

Message.kt:22 data class ModelMessage(val content: String,
                                      val toolCalls: List<ToolCall> = emptyList())
              ← 同时有 content 与 toolCalls
```

OpenAI-compatible `/chat/completions` 允许 assistant turn 同时返回
`content` 与 `tool_calls`。变更前 `ModelTurn` 二选一，无法无损承载。

矛盾所在：**AG Core 的 history 模型能表达两者，但 provider 边界不能。**

**AG.PROVIDER-0 当时的处理**：抛
`OpenAiProviderException.UnsupportedMixedTurn`，不静默丢弃。

**R1 已批准并实施的修复**：

```kotlin
data class ToolCalls(
    val calls: List<ToolCall>,
    val content: String = "",
) : ModelTurn
```

`AgentCore` 改为 `ModelMessage(content = turn.content, toolCalls = turn.calls)`。
`UnsupportedMixedTurn` 已**删除**（契约变更后无真实 consumer，不保留 dead error type）。
Provider 现在映射 `ModelTurn.ToolCalls(calls, content = assistantContent)`。

冻结证据：`AgentLoopTest T15 / T15b`（AG Core）、`P5 / P5b`（Provider）、`V4`（全链路）。

代价：真实模型若倾向先说一句再调工具，该轮 Run 直接失败。
Live L5 尚未执行，无法确认当前模型的实际倾向。

### GAP-2（Deferred）partial tool-turn history 未获真实 API 验证

AG.0.R1 已冻结：中止的 Run 可能留下部分完成的 tool turn。
真实 OpenAI-compatible API 是否接受这种 history，**尚无证据**。
本次未获得 live 证据，**未做任何伪造或推测**。

### 未触发的 Gap

- `ToolCall.arguments` opaque String：**P2 已证明可无损承载**真实形态参数
  （含空格、乱序 key、转义、非 ASCII），逐字节保持。
- `ToolSpec` 无 typed schema：permissive projection 可用，**不阻塞**。
- 多 tool call：P4 证明映射无损。

## 13. Dependencies

### ag-core（**未改变**）

```text
runtimeClasspath
\--- org.jetbrains.kotlin:kotlin-stdlib:2.1.0
```

扫描（排除注释）：`android.*` `androidx.*` `AT Core` `ADB` `JNI` `HttpClient`
`okhttp` `retrofit` `serialization` `org.json` `kotlinx.coroutines` `OpenAI`
`AdbClient` `ShellTool` —— **全部 0**。

### :provider-openai-compatible

```text
runtimeClasspath
+--- kotlin-stdlib:2.1.0
+--- project :ag-core
+--- kotlinx-serialization-json:1.8.0
+--- kotlinx-coroutines-core:1.10.2
+--- (JDK java.net.http，无外部 HTTP 库)
```

禁止项扫描：`android.` `androidx.` `AT Core` `ADB` `JNI` `AdbClient`
`ShellTool` `Compose` `Room` `retrofit` `okhttp` —— **全部 0**。

coroutines 仅用于把 `sendAsync` 桥接进冻结的 `suspend complete`，
**未重新引入 ag-core 生产依赖**，**未使用 `runBlocking`**。

## 14. Changed Files

### AG.PROVIDER-0 新增

```text
新增 module : provider-openai-compatible/
新增 production  OpenAiCompatibleConfig.kt / OpenAiCompatibleProvider.kt
                 OpenAiProviderException.kt / ChatCompletionWire.kt
新增 test       FixtureServer.kt / ProviderProtocolTest.kt
                 VerticalSliceTest.kt / LiveApiTest.kt
新增 build      provider-openai-compatible/build.gradle.kts
修改            settings.gradle.kts   (+1 module)
```

### R1 批准的 GAP-1 contract correction（已实施）

```text
M ag-core/src/main/kotlin/com/androidagent/core/Model.kt
    ModelTurn.ToolCalls 增加 content: String = ""

M ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
    ModelMessage(content = turn.content, toolCalls = turn.calls)  （原为硬编码 ""）

M ag-core/src/test/kotlin/com/androidagent/core/AgentLoopTest.kt
    新增 T15 / T15b 冻结 mixed-turn 语义
```

> **PRE-R1 HISTORICAL STATE**：R1 之前本文档写「未修改 `ag-core/**` 任何文件」。
> 该表述**已失效** —— `ag-core/**` 的上述三处改动属于**已批准的 GAP-1 contract
> correction**，是本 Step 累计结果的一部分，不是越权改动。

> `AG.PROVIDER-0.LIVE` 阶段未再修改任何 production/test 代码，
> 仅补齐 `LiveApiTest` 的 L6 测试（live-only，不影响 offline 数量）。

## 15. Deferred

```text
live L1–L7 真实 API 证据（缺凭据）
typed ToolCall arguments schema
partial tool-turn history 的真实 API 接受度（GAP-2）
Responses API / streaming / SSE
retry / rate-limit / token accounting
provider routing / fallback
EMG / AIController provider
ShellTool / Android Tools / AT 集成
AGA / AGC
Skills / MCP / RAG / persistence / multi-agent / plugin framework
UniversalProviderAdapter / ProviderBackend
```

> `ModelTurn prose+toolCalls 表达` 已于 `AG.PROVIDER-0.R1` **解决并移出 Deferred**（见 §19）。

## 16. Risks（R1 更新）

| # | 风险 | 状态 |
|---|---|---|
| R1 | GAP-1 未解前，倾向「先说一句再调工具」的模型会直接失败 | ✅ **已解除**（R1） |
| R2 | permissive tool schema 意味着模型可传任意结构参数，真实 Tool 需自行校验 | 仍存在 |
| R3 | GAP-2 未验证，partial history 可能被真实 API 拒绝 | 仍存在（live 待凭据） |
| R4 | 未实现 retry，429/5xx 直接失败（符合本 Step 范围） | 仍存在 |
| R5 | live 路径完全未执行，真实 endpoint 的 header/编码差异未知 | 仍存在 |
| R6 | JUnit 非 `void` 返回类型的方法被静默跳过（曾隐藏 6 个用例） | ✅ **已修**，后续新增测试须显式 `: Unit` |

## 17. Verdict

### 当前 verdict

```text
AG.PROVIDER-0_LIVE_BLOCKED_CREDENTIALS
```

原因：Live Gate 所需的三个环境变量在本机 **Process / User / Machine
三个作用域均不存在**，L1–L7 无法执行（0 / 7）。
因此 `AG.PROVIDER-0` **不能冻结**。

工程侧已就绪：module、wire 映射、错误分类、live 测试（L1–L6）齐备，
offline 41/41 + 24/24 全绿，依赖方向正确，凭据门禁可靠。
**唯一缺口是外部凭据。**

### 历史 verdict（PRE-R1 HISTORICAL STATE）

```text
AG.PROVIDER-0_CONTRACT_CHANGE_REQUIRED
```

这是 `AG.PROVIDER-0` **初次提交时**的 verdict，理由是：

```text
mixed content 证明 Core contract 需要变化
→ 只能选 C
```

该 verdict 已被 `AG.PROVIDER-0.R1` 消解：GAP-1 已批准并修复，
`mixed prose + tool_calls` 现在**无损保留**（T15 / P5 / V4 冻结）。
保留于此仅作审计记录，不再是当前状态。

## 18. Handoff

```text
GAP-1  RESOLVED
      AG Core 已按批准 proposal 做最小修改（ModelTurn.ToolCalls 增加 content）

LIVE GATE pending
      L1–L7 未执行：AG_OPENAI_BASE_URL / AG_OPENAI_MODEL / AG_OPENAI_API_KEY 缺失
      GAP-2（partial tool-turn 真实 API 接受度）未验证

已完成：
  :provider-openai-compatible module，依赖方向正确
  AG Core JUnit 41 / 41，Provider JUnit 24 / 24，含全链路 vertical slice
  普通 build 离线确定性，live 测试独立 task 且缺凭据时快速失败

AG Core 的改动仅为已批准的最小契约变更。无 NO COMMIT / NO PUSH。
```

---

# CONTRACT CHANGE PROPOSAL（历史审计记录）

> ⚠ **本节整体为 PRE-R1 HISTORICAL STATE。**
> 该 Proposal 已被 `AG.PROVIDER-0.R1` **批准并实施**。
> 下文所有代码片段、测试数字与行为描述均为 **Proposal 提交当时**的状态，
> 不代表当前事实。当前状态见 §5 与 §19。

## 1. Real evidence（proposal time）

来自当时 `ag-core/src/main` 的真实代码（非推测）：

```text
Model.kt:19    data class ToolCalls(val calls: List<ToolCall>) : ModelTurn
Message.kt:22  data class ModelMessage(val content: String,
                                       val toolCalls: List<ToolCall> = emptyList())
```

`ModelMessage` 同时持有 `content` 与 `toolCalls`；变更前 `ModelTurn.ToolCalls`
只有 `calls`。OpenAI-compatible `/chat/completions` 允许 assistant turn
同时返回 `content` 与 `tool_calls`，当时 Provider 无法无损表达。

**变更前** Provider 侧用 `P5` 证明该情形被显式拒绝
（`UnsupportedMixedTurn`），两个数量都被记录，没有任何一侧被静默丢弃。
**变更后**（`AG.PROVIDER-0.R1`）该情形被无损保留，见 §19。

## 2. Contract at proposal time

> **PRE-R1 HISTORICAL STATE** —— 以下是 Proposal 提交当时的 contract，
> **不再是当前 contract**。当前状态见 §5。

```kotlin
sealed interface ModelTurn {
    data class FinalAnswer(val content: String) : ModelTurn
    data class ToolCalls(val calls: List<ToolCall>) : ModelTurn   // 无 content
}
```

> 实测结果：R1 实施后该变更被证明是**必要**的 —— 当前 `ToolCalls` 已带
> `content: String = ""`，且 T3/T9/T9b 的期望值确实需要按真实 content 调整。
> AG Core 测试总数由 **39 增至 41**（新增 T15 / T15b），非减少。

## 3. Exact limitation

一个 assistant turn 只能表达「纯文本」或「纯 tool calls」二者之一。
prose 与 tool calls 并存时，Provider 必须在两者间取舍，
而两者都是模型真实输出，丢弃任一方都是信息损失。

## 4. Minimum proposed change

**最小方案**：给 `ModelTurn.ToolCalls` 增加一个可选 content 字段。

```kotlin
data class ToolCalls(
    val calls: List<ToolCall>,
    val content: String = "",        // 新增，默认空以保持现有构造点不变
) : ModelTurn
```

配套：`AgentCore.driveLoop` 在处理 ToolCalls 时，
`ModelMessage(turn.content, turn.calls)` 使用该字段而非硬编码 `""`。

该改动对 `ModelMessage` 无影响（history 模型本已能表达两者）。

## 5. Why provider-local adaptation is insufficient

Provider 侧只能选择：

| 方案 | 问题 |
|---|---|
| 丢弃 prose | 违反 §17「不得 silent drop content」 |
| 丢弃 tool_calls | 违反 §17「不得 silent drop tool calls」 |
| 把 prose 拼进第一个 ToolCall.arguments | 破坏 arguments 的 wire 保真；模型收到的参数被污染 |
| 把 prose 塞进 `FinalAnswer` 再单独请求一次 | 多一次计费请求、破坏单轮语义、且不是真实 turn |
| 抛异常（当前做法） | 真实模型若倾向先说话即整轮失败 |

这些都是信息损失或行为扭曲。表达 mixed turn 的能力必须存在于 contract 中。

## 6. Impact on AG Core tests

`ModelTurn.ToolCalls(calls)` 现有构造点全部因默认值而**无需修改**。
需要复核：

```text
AgentLoopTest T2/T3   构造 ToolCalls(listOf(...))，断言不变 → 应仍通过
AgentLoopTest T3      ModelMessage("", calls) 断言 → 会因新字段取真实 content 而可能变化
AgentLoopTest T9/T9b  ModelTurnFinished 事件断言 → 同上
```

预计 T3/T9/T9b 的**期望值**需从 `""` 改为实际 content，**测试数量不变（39）**。
需实跑确认。

## 7. Impact on future ShellTool

正面影响。Shell 场景（`shellTool(command="ls")`）中模型很可能先输出
「I'll list the directory」再调用工具。当前该轮会直接失败，
改后 prose 会进入 history，模型下一轮能看到自己说过什么。

对 ShellTool 的 `toolCallId` / `name` / `arguments` / `ToolResult` 关联**无影响**。

## 8. Compatibility impact（R1 更正）

```text
Existing Kotlin source call sites using ToolCalls(calls)
remain source-compatible when recompiled, because content has a default value.

JVM binary compatibility is NOT guaranteed by adding a constructor parameter.
A caller compiled against the previous single-argument constructor would not
find that constructor after recompilation.

No published binary consumer currently exists, so practical migration impact
is minimal.

AgentCore              reads the new field → behaviour change (prose now preserved)
:ag-core 依赖          不变（仍 kotlin-stdlib only）
其他 provider          尚不存在
ShellTool              尚未实现
```

> ⚠ **更正**：本文档初版写「源码与二进制向后兼容」，该表述不准确。
> 增加构造参数**不保证** JVM 二进制兼容；准确表述见上。

`:ag-core` 生产依赖**不增加**。

## 9. Alternatives considered

| 方案 | 评价 |
|---|---|
| 新增 `ModelTurn.FinalAnswerWithToolCalls` 变体 | 语义更显式，但增加第三种变体；且需 Agent Loop 三分支处理。改动面更大 |
| 把 `ToolCalls` 改为继承/组合 `FinalAnswer` | 破坏 sealed 简洁性，收益相同但更复杂 |
| 在 `ToolCall` 上加 `thought` 字段 | 语义错误：prose 属于 turn 而非单个 call |
| 维持现状，仅靠 Provider 抛异常 | 真实模型下会频繁失败，等于 Provider 不可用 |
| 把 prose 合并进 FinalAnswer 并丢弃 tool_calls | 违背 Agent Loop 基本流程 |

## 10. Recommended decision

**采纳「最小方案」：`ModelTurn.ToolCalls` 增加 `content: String = ""`。**

理由：

- 唯一能在**不丢信息**的前提下表达 mixed turn 的最小改动
- history 模型（`ModelMessage`）本已如此，contract 与之对齐而非背离
- existing Kotlin source call sites remain source-compatible when recompiled;
  JVM binary compatibility is not guaranteed（详见 §8）
- 不增加 AG Core 生产依赖
- 直接解除真实 Provider 的可用性阻塞

**同时需要**：补做 live L1–L7（需真实凭据），因为当前无任何真实 API 证据，
GAP-2 与风险 R2/R5 的实际严重程度仍未确定。

**STOP HERE — WAIT FOR ARCHITECTURE REVIEW.**
---

## 19. R1 修订记录（AG.PROVIDER-0.R1）

`AG.PROVIDER-0.R1 — Mixed-Turn Contract Alignment` 已批准并实施 §21 的
CONTRACT CHANGE PROPOSAL。本节记录实施结果。

### 19.1 契约变更（已实施）

```kotlin
// before
data class ToolCalls(val calls: List<ToolCall>) : ModelTurn

// after
data class ToolCalls(
    val calls: List<ToolCall>,
    val content: String = "",
) : ModelTurn
```

`AgentCore` 同步改为 `ModelMessage(content = turn.content, toolCalls = turn.calls)`。

未重新设计：`ModelTurn` hierarchy、`Message` hierarchy、`ToolCall`、`ToolResult`、
`Session`、`Run`、`AgentEvent`、provider error model。

### 19.2 Provider 行为变更

```text
before : mixed turn -> 抛 UnsupportedMixedTurn
after  : mixed turn -> ModelTurn.ToolCalls(calls, content = assistantContent)
         tool_calls 非空且 content=null -> content = ""
```

`OpenAiProviderException.UnsupportedMixedTurn` 已**删除** —— 契约变更后
无任何真实 consumer，不保留 dead error type。

### 19.3 测试数量矛盾已解决（§10）

根因：`P8/P9/P9b/P9c/P9d/P9e` 六个方法写作
`= runBlocking { … assertThrows(…) }`，末表达式使返回类型被推断为异常类型，
**非 `void`**。JUnit 5 会**静默跳过**非 `void` 测试方法 —— 不报错、不执行。
因此 AG.PROVIDER-0 报告的「16/16」偏少 6 个，步骤文档的「19 个」亦错误。

已修复：六个方法显式声明 `: Unit`。真实执行结果：

```text
AG Core JUnit executed tests : 41   (39 + T15 + T15b)
Provider JUnit executed tests : 24   (ProviderProtocolTest 20 + VerticalSliceTest 4)
Named provider scenarios      : 24   (20 P 标签 + 4 V 场景，与 JUnit 1:1)
```

**统计口径声明**：本工程中一个 `@Test` 方法对应一个具名场景，
因此 JUnit 测试数与具名场景数当前相等（1:1）。若将来一个方法覆盖多个
具名场景，必须分别列出两个数字，不得混用。

### 19.4 新增冻结证据

```text
AG Core   AgentLoopTest T15    mixed content + tool calls 同时进入 history 与下一次 request
AG Core   AgentLoopTest T15b   纯 tool call turn 的 prose 仍为空（未被填成占位文本）
Provider  P5                  mixed turn 无损：prose 精确、2 个 call、顺序/id/name/args 精确
Provider  P5b                 content=null -> content=""
Provider  V4                  mixed turn 经 Tool 后，第二次请求重放原 prose + 原 tool_calls + 匹配 tool_call_id
```

### 19.5 GAP 状态

```text
GAP-1  RESOLVED（R1 已修复并冻结测试）
GAP-2  LIVE VALIDATION PENDING —— 未验证，未伪造
```

### 19.6 Live 状态

```text
LIVE = PENDING_CREDENTIALS
AG_OPENAI_BASE_URL / AG_OPENAI_MODEL / AG_OPENAI_API_KEY 仍不存在
```

`AG.PROVIDER-0` 因此**仍不能判 COMPLETE**；live gate 留待下一步骤关闭。

### 19.7 未同步的文档（按 R1 §15 要求刻意保留）

```text
README.md
docs/architecture/AndroidAgent_总体架构.md
AGENTS.md
```

这三份仍描述「只有 :ag-core」「NEXT = PENDING ARCHITECTURE REVIEW」等
R1 之前的旧状态。按 R1 §15，等 `AG.PROVIDER-0` 真正通过 Live Gate 后统一更新。

---

## 20. Live Gate Evidence（AG.PROVIDER-0.LIVE）

本节记录 Live Gate 的执行状态。**未执行任何 live 调用，未伪造任何证据。**

### 20.1 Credential 状态

```text
AG_OPENAI_BASE_URL = ABSENT   (process / User / Machine 三处均无)
AG_OPENAI_MODEL    = ABSENT   (process / User / Machine 三处均无)
AG_OPENAI_API_KEY  = ABSENT   (process / User / Machine 三处均无)
```

环境中亦不存在任何其他 provider 类变量
（`OPENAI|ANTHROPIC|GEMINI|GROQ|API_KEY|BASE_URL|LLM|MODEL|EMG|AICONTROLLER`）。

### 20.2 各 Live 用例状态

```text
L1 real final answer              NOT EXECUTED  (no credentials)
L2 real tool call                 NOT EXECUTED  (no credentials)
L3 real tool result continuation  NOT EXECUTED  (no credentials)
L4 multiple tool calls observation NOT EXECUTED  (no credentials)
L5 mixed prose + tool call        NOT EXECUTED  (no credentials)
L6 partial history acceptance     NOT EXECUTED  (no credentials)
L7 real HTTP error smoke          NOT EXECUTED  (no credentials)
```

`OBSERVED` / `NOT OBSERVED` / `ACCEPTED` / `REJECTED` **均未产生**，
因为连一次真实请求都未发出。

### 20.3 本阶段实际完成的工作

```text
1. 步骤文档 5 项残留修正（§9 / §12 / §15 / §18 / §21）
2. 离线回归复验：AG Core 41/41，Provider 24/24
3. 新增 L6 live 测试（partial tool turn / GAP-2 验证），就绪待凭据
4. 确认 liveTest task 在缺凭据时快速失败，不会静默跳过
5. 未修改任何 AG Core contract
```

### 20.4 GAP-2 状态

```text
GAP-2 = LIVE VALIDATION PENDING
```

L6 测试已实现，可在凭据到位后立即执行。
**未伪造 callB 的 ToolResult**；若真实 endpoint 拒绝该 history，
后续应进入 provider projection review，而非改动 Session history 语义。

### 20.5 解封方式

```text
设置 AG_OPENAI_BASE_URL / AG_OPENAI_MODEL / AG_OPENAI_API_KEY
然后执行：
  .\gradlew.bat :provider-openai-compatible:liveTest
```

无需任何代码改动。`LiveApiTest` 已覆盖 L1–L6，offline 数量不受影响。

---

## 21. AG.PROVIDER-0.CFG — AG-Owned Local Provider Configuration

### 21.1 动机

Live Gate 唯一的外部阻塞是「Provider 配置来源」。AndroidAgent 必须维护
**自己的**开发期配置，**不读取、不依赖**任何其他工具的配置。

```text
禁止：AndroidAgent → OpenCode config.json
禁止：读取 OpenCode 用户/provider 配置、其他项目 credential、
      shell history、浏览器 secret、Git 历史、日志
```

### 21.2 文件

```text
config/provider.example.json   Git tracked，项目示例配置
config/provider.local.json     local only，Git ignored，永不提交
```

`.gitignore` 只排除 local 文件，**不**粗暴忽略整个 `config/`：

```gitignore
/config/provider.local.json
```

已验证：`git check-ignore -v` 命中该规则；`provider.example.json` 未被忽略；
`git status` 只出现 `config/provider.example.json`。

### 21.3 配置解析

解析顺序（**单一来源，绝不混合**）：

```text
1. AndroidAgent local JSON   config/provider.local.json   ← 首选
2. AG_OPENAI_* 环境变量                                ← 仅回退
两者都不可用 → BLOCKED_CONFIGURATION
```

一个运行实例必须只选一个来源提供**全部**字段，
不会出现「baseUrl 来自 JSON、key 来自 env、model 来自 JSON」的混合状态。

### 21.4 边界：AG Core 完全不知情

```text
AG Core  不知道 JSON / 不知道配置文件 / 不知道 API Key / 不知道 EMG
```

配置能力全部位于 AG Core 之外的 `:provider-openai-compatible`：

```text
OpenAiCompatibleConfigLoader   JSON file → OpenAiCompatibleConfig
ProviderConfigResolver        来源选择（JSON 优先，env 回退）
```

两者职责仅 parse / validate / map。不启动 Provider、不接触 AgentCore、
不注册 Tool、不读取任何 Android API。未建立 ConfigFramework / SecretManager /
SettingsRepository / UniversalConfig / DI framework。

### 21.5 校验规则

```text
provider 对象必须存在
type 必须 == "openai-compatible"（未知类型显式失败，绝不猜测）
baseUrl 必须非空
model 必须非空
apiKey 允许 ""（未来无认证 endpoint）
apiKey == "REPLACE_ME" → 判定为 configuration not ready，不得发真实请求
```

### 21.6 Secret 安全

```text
异常消息永不包含 apiKey 值
config.toString() 输出 apiKey=<redacted>
不打印整个 provider.local.json
允许输出：baseUrl 安全身份、model、API_KEY=PRESENT/ABSENT/PLACEHOLDER
```

C9 专项测试以哨兵密钥遍历 5 种失败场景，逐一断言密钥不出现在诊断中。

### 21.7 定位：仅开发期 bootstrap

```text
provider.local.json = development / bootstrap configuration
```

**不是**最终 Android 发布版凭据存储方案，**不得**据此宣称生产安全配置已完成。
未来 AGA 发布时预计替换为：

```text
UI / Settings → secure credential storage → Host / Composition Root
              → OpenAiCompatibleConfig
```

可能使用 Android Keystore / secure storage，**本 Step 不实现**。

### 21.8 测试

新增 `ProviderConfigTest`，13 个 JUnit 测试：

```text
C1  valid config 正确映射（含 /chat/completions 端点推导）
C2  missing file 明确失败
C3  malformed JSON 明确失败且不回显内容
C4  missing baseUrl 明确失败
C5  missing model 明确失败
C6  unsupported type 明确失败，不猜测
C7  REPLACE_ME 判定为 configuration not ready
C8  empty apiKey 允许（无认证 endpoint）
C9  密钥绝不出现在诊断中（5 场景遍历）
+  JSON 优先于 env
+  仅当 JSON 不可用时回退 env
+  两者皆不可用时失败
+  仓库 example 配置形状正确（仅差 placeholder）
```

### 21.9 依赖审计

```text
ag-core   runtimeClasspath = kotlin-stdlib only（未改动）
provider  kotlin-stdlib + project :ag-core
          + kotlinx-serialization-json:1.8.0（复用既有，未新增 JSON 库）
          + kotlinx-coroutines-core:1.10.2
```

未新增 Jackson / Gson / org.json。`ag-core/**` 本 Step **未修改**
（diff 仍为 R1 留下的 73+/6-）。

### 21.10 当前配置状态

```text
CONFIG_SOURCE = provider.local.json
BASE_URL      = https://api.emg.dpdns.org/v1
MODEL         = qwen3.8-local
API_KEY       = PLACEHOLDER（仍为 REPLACE_ME）
```

因此 Live Gate **未执行**，等待用户手动填入真实 Key。

### 21.11 测试数量变化

```text
Provider offline JUnit : 24 → 37   （新增 13 个 config 测试）
AG Core JUnit          : 41（未变）
Live defined           : 6（未变）
```

---

## 22. Live Gate Evidence（真实执行，EMG endpoint）

### 22.1 配置来源

```text
CONFIG_SOURCE = provider.local.json          （AG-owned，Git ignored）
BASE_URL      = https://api.emg.dpdns.org/v1
ENDPOINT      = https://api.emg.dpdns.org/v1/chat/completions
MODEL         = qwen3.8-local
API_KEY       = PRESENT（值从未被打印或写入任何文件）
```

前置探测（不使用生成）：

```text
TCP 443                      : reachable
GET  /v1/models             : HTTP 200 (5.7s)，model 列表 = [qwen3.8-local, test]
                             确认 qwen3.8-local 确实存在于该 endpoint
```

### 22.2 首次执行：全部超时（真实发现）

第一次 `liveTest`：L1 / L3 FAILED，L2 / L4 / L5 虽"PASS"但 outcome 均为
`ProviderFailure(Timeout)`，每个用例恰好耗时 ~60s = 配置的 requestTimeout。

诊断结论：**本地模型冷启动耗时超过 60 秒**。endpoint 本身健康
（直接 curl同一请求 HTTP 200 / 17.1s）。

模型预热后重跑：**6/6 全部 PASSED**（50s）。

> ⚠ **运维发现（非代码缺陷）**：EMG 后端是本地模型，冷启动可超过 60s。
> `OpenAiCompatibleConfig.requestTimeout` 默认 60s，对冷启动不足。
> AG Core 与 Provider contract 均未改动；是否提高默认值或改为可配置，
> 留待架构审查决定。

### 22.3 L1–L6 真实结果

| 用例 | 结果 | 真实证据 |
|---|---|---|
| **L1** Final Answer | **PASS** | 映射为 `FinalAnswer`，content 非空（length 15→25） |
| **L2** Tool Call | **PASS** | outcome=Answer，真实模型产生 tool_call 并被 registry 执行 |
| **L3** ToolResult Continuation | **PASS** | **`toolCalls=1`、`toolArguments={}`** —— Real Model → ToolCall → Tool → ToolResult → Real Model → FinalAnswer 全链路跑通 |
| **L4** Multiple Tool Calls | **OBSERVED** | 模型回答 "Both probes were called successfully: ..."，即同轮调用了 alpha_probe 与 beta_probe |
| **L5** Mixed Prose + Tool Calls | **PASS** | 模型在 tool 结果后继续叙述："The probe returned successfully (depth=1)..."，prose 与 tool result 共存 |
| **L6** Partial Tool History | **PARTIAL_HISTORY_ACCEPTED** | 声明 2 个 tool_calls、仅提供 1 个 tool result，endpoint **接受**该 history，返回 `FinalAnswer` |

L4 说明：模型自述调用了两个探针，但当前测试**未直接计数工具执行次数**，
故按 OBSERVED 记录并保留该证据强度说明，不夸大为「已断言 count=2」。

### 22.4 GAP-2 结论

```text
L6 = PARTIAL_HISTORY_ACCEPTED
→ GAP-2 CLOSED FOR CURRENT EMG ENDPOINT
```

**不扩大为「所有 OpenAI-compatible endpoint 都接受」。**
AG Core 的 partial tool-turn history 语义与 Agent Loop **未做任何修改**。

### 22.5 回归

```text
AG Core offline    41 / 41
Provider offline   37 / 37
Live                 6 / 6
build                BUILD SUCCESSFUL
```

### 22.6 本步骤修复的自身回归

审计时发现 `LiveApiTest` 的 **L6 用例在源文件中丢失**（文件 259 行，止于 L5；
`javap` 确认编译产物只有 L1–L5）。这是我此前编辑操作引入的回归，
导致上一轮报告中「live 6 tests」的说法与实际不符。

已恢复 L6 并复跑，L6 真实执行且通过。恢复 L6 不是为了迁就 endpoint，
而是修补自身丢失的必需证据。

### 22.7 Secret 审计

```text
provider.local.json 被 .gitignore:25 排除
git status 中 config/ 仅出现 provider.example.json
源码/文档中无 key material、无 .env、无 response dump、无 log
API key 值从未被打印
```

---

## 23. AG.PROVIDER-0.R2 — Timeout Configuration & Live Evidence Hardening

### 23.1 真实 cold-start timeout 证据

```text
第一次 Live Gate（requestTimeout = 60s）
  L1 / L3      FAILED，超时
  L2 / L4 / L5 outcome = ProviderFailure(Timeout)，每例约 60s

同时（不使用生成请求的探测）
  GET  /v1/models      HTTP 200 / 5.7s
  直接 chat completion  HTTP 200 / 17.1s，内容正确

模型预热后重跑          Live 6/6 PASS
```

**结论**：Provider 的协议与映射并未因此被证明有错误。60 秒对
「本地 EMG + Qwen3.8」冷启动场景**不够可靠**。当前证据**不支持**
「EMG 不稳定」或「Provider 有 bug」的结论。

### 23.2 Timeout 设计

```text
通用默认（未改动）  : OpenAiCompatibleConfig.DEFAULT_REQUEST_TIMEOUT = 60s
JSON 覆盖（新增）    : provider.requestTimeoutSeconds（可选，positive integer）
当前本地取值        : 120
```

通用默认**保持 60s 未变**。理由：`OpenAiCompatibleProvider` 是通用 Provider，
不能因为某一个本地部署的冷启动特征而改变所有 endpoint 的默认行为。
需要更长时间的部署在自己的 local JSON 中声明。

未新增 `ProviderTimeoutManager` / `TimeoutPolicy` / `ExecutionPolicy` / `RetryPolicy`。
只是给既有 `OpenAiCompatibleConfig.requestTimeout` 提供配置入口。

字段缺失时使用唯一的通用默认，**不存在第二套默认值**。
未设置主观上限（`C14d` 验证 86400s 合法）。

### 23.3 L4 证据强化

```text
强化前 : 仅依据模型自述「Both probes were called successfully」
         → 证据强度：MODEL-REPORTED

强化后 : test-only AtomicInteger 计数器，直接统计真实工具执行次数
         → 证据强度：DIRECTLY OBSERVED AND ASSERTED

真实结果 : alpha_executions=1  beta_executions=1
```

不再从模型叙述推断工具是否执行。

### 23.4 L5 证据审计（此前结论有误，已纠正）

审计发现：**先前把 L5 记为「PASS / mixed turn observed」是不正确的。**

原 L5 只依据最终回答判断，而：

```text
assistant tool_call → ToolResult → 下一轮 assistant prose
```

属于**普通 tool continuation**，**不是** mixed turn。真正的 mixed turn 必须是
**同一个 assistant response 同时**携带 `content != empty` 与 `tool_calls != empty`。

强化后：使用 test-only `AgentEventSink` 捕获真实 `ModelTurn`，
只在 `ModelTurn.ToolCalls.content` 非空时才判定为 mixed。

**真实结果：TRUE SAME-TURN MIXED OBSERVED**

```text
contentLength = 60
calls         = 1
preservedProse = "The morning sun cast long shadows across the quiet garden."
preservedCalls = [probe]
preservedIds   = [v2aO8ePk3QcFpyDpJauxwdFQcN09M3p4]
```

即 prose 与 tool_call 在**同一 turn** 内均被无损保留 ——
这正是 `AG.PROVIDER-0.R1` 契约变更要解决的问题，现已在真实 endpoint 上验证。

### 23.5 修复自身引入的结构缺陷

审计 `LiveApiTest.kt` 时发现文件结构损坏：先前的 L6 恢复操作匹配了错误的
闭合括号，导致

- L4 函数体被从中间截断
- 一个 **嵌套的重复 L6** 出现在 L4 内部（`@Test` 标注在局部函数上，
  会被 JUnit **静默跳过**）
- 类级别另有一份真实 L6

该文件当时**仍能编译并运行**，因此缺陷被掩盖 —— 这与此前「JUnit 静默跳过
非 void / 局部函数测试」是同一类陷阱。已完整重写该文件。

### 23.6 Live 重跑结果（timeout=120s）

```text
L1  PASS       FinalAnswer, contentLength=13
L2  PASS       outcome=Answer, toolExecutions=1, argumentsLength=2
L3  PASS       outcome=Answer, toolExecutions=1, arguments={}
L4  PASS       DIRECTLY OBSERVED AND ASSERTED  alpha=1 beta=1
L5  PASS       TRUE SAME-TURN MIXED OBSERVED  contentLength=60 calls=1
L6  PASS       PARTIAL_HISTORY_ACCEPTED       turnType=FinalAnswer
```

`120s CONFIG PATH VERIFIED`。本次运行模型处于预热状态，
`COLD START NOT REPRODUCED` —— 未伪造 cold-start 证据。

### 23.7 GAP-2

```text
L6 = PARTIAL_HISTORY_ACCEPTED
→ GAP-2 CLOSED FOR CURRENT EMG ENDPOINT
```

限定不变：这不是所有 OpenAI-compatible endpoint 的全局保证。
GAP-2 不再是 blocking gap。

### 23.8 回归与依赖

```text
AG Core offline      41 / 41   （未变，ag-core/** 本步骤零改动）
Provider offline     46 / 46   （37 → 46，新增 9 个 timeout 测试）
build                BUILD SUCCESSFUL

ag-core  runtimeClasspath = kotlin-stdlib only
provider stdlib + project :ag-core + kotlinx-serialization-json + kotlinx-coroutines-core
         未新增任何 production dependency
```

### 23.9 安全事件（必须记录）

在为 `config/provider.local.json` 插入 `requestTimeoutSeconds` 时，
一次失败的 JSON 解析使 PowerShell 将**文件内容（含真实 API key）回显到控制台**，
因而该 key 出现在了本次会话记录中。

- 该文件始终被 `.gitignore` 排除，**key 未进入任何 git 可跟踪文件**
- 但由于 key 已出现在会话记录中，**建议轮换该 EMG API Key**
- 已修正 JSON（补回逗号），字段与 key 均完好

教训：对含密文件执行 `ConvertFrom-Json` 等命令时，
失败路径会把原文带入输出。后续应避免在可能失败的解析中回显文件内容。

---

## 24. AG.PROVIDER-0.FREEZE — Final Freeze

### 24.1 Security incident final record

```text
LOCAL_SECRET_EXPOSURE                 = YES
EXTERNAL_SECRET_EXPOSURE_CONFIRMED    = NO
SECRET_TRACKED_IN_GIT                 = NO
SECRET_STAGED                         = NO
SECRET_UPLOADED_OR_SHARED             = NO
LOCAL_SECRET_IGNORED                  = YES
KEY_ROTATION_REQUIRED                 = NO（按本项目 local-only 威胁模型）
VALIDATION_PATH_REMEDIATION           = PASS
```

事件定性与范围：

```text
一次 JSON 校验失败，使 PowerShell 将含密文件原文回显到本地 console /
session 输出。

按本项目既定威胁模型（console 与 session 均为本地，未上传、未共享、
未同步外部、未进入 Git），该事件定性为 LOCAL_SECRET_EXPOSURE。

不描述为 API KEY COMPROMISED；无外部暴露证据。
```

**已冻结的新纪律**：含密 JSON 不得经由可能回显原文的错误路径校验。
禁止输出完整 config JSON、raw JSON 作为 parser error context、
`provider.local.json` 全文、Authorization header、API Key。
仅允许 `API_KEY = PRESENT / ABSENT`、safe endpoint identity、model id。
该规则已写入 `AGENTS.md`。

### 24.2 冻结的 Provider 边界

```text
provider-openai-compatible  ──implements──▶  ag-core ModelProvider
ag-core                    ─X─▶            provider-openai-compatible
```

AG Core 不���道：OpenAI / EMG / HTTP / JSON / API key /
`provider.local.json` / Android / AT Core / ADB / JNI。

### 24.3 冻结的 ModelTurn 契约

```kotlin
sealed interface ModelTurn {
    data class FinalAnswer(val content: String) : ModelTurn
    data class ToolCalls(
        val calls: List<ToolCall>,
        val content: String = "",
    ) : ModelTurn
}
```

已验证：pure final answer / single tool call / multiple tool calls /
same-turn prose + tool_calls / opaque arguments / tool result continuation。
无证据不得再修改。

### 24.4 冻结的其他边界

```text
ToolCall.arguments = opaque String   AG Core 不 parse / normalize；
                                    Concrete Tool 自行解析校验
ToolSpec           = name + description
Tool schema v0     = PERMISSIVE {"type":"object","additionalProperties":true}
                    非 typed tool schema
Timeout 通用默认   = 60s（未改）
       本地覆盖    = provider.local.json 的 requestTimeoutSeconds = 120
       规则        = absent 用默认 / positive integer 覆盖 / <=0 非法
Config             = AG-owned local JSON 优先，AG_OPENAI_* 仅回退，
                    一次 resolution 只用一个完整 source
Config 定位        = DEVELOPMENT / BOOTSTRAP，非 AGA 生产凭据存储
```

### 24.5 GAP 最终状态

```text
GAP-1 = CLOSED
       真实 endpoint 观测到 assistant.content != empty 且 tool_calls != empty，
       证明 ModelTurn.ToolCalls(content) 的 R1 修正确有需要且已生效。

GAP-2 = CLOSED FOR CURRENT EMG ENDPOINT
       声明 callA+callB、history 仅含 callA 结果、callB 无结果，
       下一请求被当前 EMG endpoint 接受。
       NOT A GLOBAL GUARANTEE FOR ALL OPENAI-COMPATIBLE ENDPOINTS。
       其他 endpoint 若拒绝 → 优先 Provider Projection Review，
       不得自动修改 Session history semantics 或 Agent Loop。
```

### 24.6 历史轨迹（保留作审计）

```text
AG.PROVIDER-0 初版实现
  -> CONTRACT_CHANGE_REQUIRED（mixed turn 缺口）
AG.PROVIDER-0.R1    批准的 ModelTurn 修正
AG.PROVIDER-0.LIVE  live 凭据缺失阻断 + L6 测试补齐
AG.PROVIDER-0.CFG   AG-owned local JSON 配置 + liveTest 首次真实执行
                    （冷启动 timeout 发现；L1-L6 全部 PASS）
AG.PROVIDER-0.R2    timeout 配置化 + L4/L5 证据强化 + 修复自身结构缺陷
AG.PROVIDER-0.FREEZE  Security closure + SSOT 同步 + 冻结 commit
```

### 24.7 最终结论

```text
AG.PROVIDER-0_COMPLETE / FROZEN
```

下一步：**First Real Android Tool Boundary**
（candidate: ShellTool → AT Runtime / AT Core），
状态 **PENDING ARCHITECTURE STEP DESIGN**，实现尚未开始。
