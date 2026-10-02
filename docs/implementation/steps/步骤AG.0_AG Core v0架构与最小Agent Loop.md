# 步骤 AG.0 — AG Core v0 架构与最小 Agent Loop

## 1. Metadata

> **本文档记录 AG.0 的原始执行结果，是历史记录，不是当前状态。**
> 其中部分状态已被 `AG.0.R1` 取代（见文末「AG.0.R1 之后的最终状态」）。
> 当前真实状态以 `步骤AG.0.R1_Runtime_Async_Boundary_Freeze.md` 为准。

| 项 | 值 |
|---|---|
| 步骤 | `AG.0` |
| 工程 | AndroidAgent（独立开发工程） |
| 根目录 | `D:\codebank\AndCode\AndroidAgent` |
| 基线 | `231b319` / tag `ag-baseline-0` |
| 类型 | 首个功能开发步骤（AG Core v0） |
| 结果 | `AG.0_COMPLETE` |
| Commit | **无。** 全部改动保持未提交，等待架构审查 |
| 后续 | `AG.0.R1`（Runtime Async Boundary Freeze）已在其后执行，取代了本文中的同步入口设计 |

## 2. Previous state

进入本步骤前，仓库处于 `AG.BASELINE-0` 冻结状态：

```text
branch        : main
HEAD          : 231b319ae88284a01724bd9766013d0928a25cb6
tag           : ag-baseline-0 -> 231b319
commit message: baseline: initialize AndroidAgent project
working tree  : CLEAN
tracked files : 15
production    : 仅 AgCoreMarker.kt（无任何 Agent 行为）
tests         : 3 个基线冒烟测试
dependencies  : 仅 kotlin-stdlib（生产）
```

四项基线校验全部一致，因此按要求继续执行，未做任何 reset / checkout / stash / clean。

## 3. Problem / Goal

AG Core 此前只存在于文档中。`AG.0` 的唯一目标是：

> 在纯 JVM 中证明 `Model → Tool → Model → Final Answer` 这个闭环**真实可运行**，
> 并冻结其边界语义。

验证的是 **Agent Runtime 正确性**，不是 LLM 智能、网络正确性、
或 provider 协议正确性。

## 4. Scope

实现了第 ② 层（Agent Runtime）与第 ③ 层中属于纯 AG Core 的最小部分。

```text
实现：Agent Loop / Session / Run / Context(history) / Policy / Events
      ModelProvider contract、Tool contract、Tool Registry
明确不实现：任何具体 provider、任何具体 tool、任何 Integration
```

## 5. Architecture / Contract

### 5.1 最终结构

```text
com.androidagent.core
├── AgentCore.kt        AgentCore + RunOptions（Agent Loop 所在）
├── Session.kt          Session：history + per-session run ownership
├── Model.kt            ModelProvider / ModelRequest / ModelTurn
├── Tool.kt             Tool / ToolSpec / ToolCall / ToolResult
├── ToolRegistry.kt     register / find / specs / dispatch
├── ToolApproval.kt     ToolApprovalPolicy / ToolApproval(Allow|Deny)
├── Message.kt          UserMessage / ModelMessage / ToolResultMessage
├── AgentEvent.kt       AgentEvent + AgentEventSink
├── RunOutcome.kt       RunOutcome + RunFailure
└── AgCoreMarker.kt     基线遗留的 package 契约守卫
```

### 5.2 Agent Loop

```text
append UserMessage
loop:
  if completedTurns >= maxSteps -> MaxStepsReached
  step = completedTurns + 1
  emit ModelTurnStarted(step)
  turn = modelProvider.complete(request)     // 抛异常 -> ProviderFailure
  emit ModelTurnFinished(step, turn)
  completedTurns++
  if FinalAnswer -> 记录 ModelMessage -> AnswerProduced -> RunFinished -> 返回
  if ToolCalls   -> 记录 ModelMessage("", calls)
                   for (call in calls)  顺序执行，每个 call：
                       emit ToolCallStarted
                       result = registry.dispatch(call, policy)
                       记录 ToolResultMessage      <- 先记录
                       emit ToolCallFinished      <- 后通知
```

### 5.3 Session / Run

```text
Session  = 长期多轮会话，拥有自己的 message history
Run      = 一次用户请求触发的一次 Agent Loop
```

冻结语义：

- 一个 Session 同时最多一个 active Run；第二个返回 `SessionBusy`。
- 不同 Session 相互独立，不因彼此 BUSY。
- ownership 标志位于 Session 自身、用 Session 自己的 monitor 保护；
  **AgentCore 上没有任何锁**。
- ownership 在 `finally` 中释放，正常/失败/异常/取消都不会泄漏。
- 被拒绝的 Run **不发出任何事件**，也**不写入任何 history**。

### 5.4 依赖倒置

```text
AG Core 定义 contract  →  实现全部在 AG Core 之外（test 或未来 integration）
```

`src/main` 中**不存在**任何 concrete Tool 或 concrete ModelProvider。

## 6. Implementation

| 关注点 | 最终决定 |
|---|---|
| **maxSteps 计数** | **一次 model turn = 一个 step**；`maxSteps` 即 model turn 上限。预算在请求 turn **之前**检查，因此 provider 被调用次数恰好 ≤ maxSteps，不可能出现 maxSteps+1。`step` 事件字段为 1-based。 |
| **unknown tool** | `ToolRegistry.dispatch` 返回 `ToolResult(success=false, error="unknown tool: <name>")`，**不抛异常**。该结果被写入 history 交还 model，由 model 决定下一步。 |
| **tool 抛异常** | `dispatch` 捕获 `Exception` 并转成 `ToolResult(success=false, error="tool '<name>' failed: <ExceptionType>")`。**只报异常类型，不报 message/stack**，避免宿主细节泄漏进 history。`CancellationException` 显式重抛（协程控制流）；`InterruptedException` 单独处理并恢复中断标志。 |
| **policy deny** | `dispatch` 中**先查 policy 再查 registry**。拒绝时返回 `success=false`，`Tool.execute` **从不被调用**。拒绝理由（由宿主提供）会进入 error 文本。 |
| **per-session BUSY** | `Session.tryBeginRun()` 在 Session 自己的 monitor 内做 check-then-set；`AgentCore.run` 先 `tryBeginRun`，失败即返回 `SessionBusy`；`finally { session.endRun() }`。 |
| **避免全局锁** | `AgentCore` 用 `ConcurrentHashMap` 存 Session，**不持有任何 monitor**。`Session` 的 monitor 只保护它自己的 `activeRun` 与 `messages`，且每个临界区都很短、不 suspend。 |
| **ToolResult.success 语义** | 仅表示**工具调用机制**是否正常完成。领域失败（如 shell `exitCode=7`）仍是 `success=true`，失败信息在 `output` 中。只有 unknown tool / 审批拒绝 / 实现抛异常才是机制失败。 |
| **Tool 级 vs Run 级失败** | Tool 级失败（无论返回 `success=false` 还是抛异常被转换）一律**只写入 history**，不升级为 Run 失败。Run 失败仅 4 种：ProviderFailure / MaxStepsReached / SessionBusy / RuntimeFailure。 |
| **同步入口** | ⚠ **已被 `AG.0.R1` 取代。** 原设计为 `fun run()` + `runBlocking`，现已改为 `suspend fun run(...)`，AG Core 不再提供任何阻塞包装。详见 `AG.0.R1` 步骤报告。 |

### 6.1 实现过程中修正的真实缺陷

初版实现后由独立只读审查发现并修复：

| 缺陷 | 修复 |
|---|---|
| `failRun` 通过已损坏的 event sink 二次发送，导致异常二次逃逸 | 终态事件（RunFailed/RunFinished）改用防御式发送 |
| event sink 在 tool 循环中途抛异常，会留下"声明了 k 个 tool call 但只有 j 个 result"的**失衡 history**，而该 Session 同时被标记为可复用 | `ToolResultMessage` 改为**先记录再通知** |
| `ToolRegistry` 使用无同步的 `LinkedHashMap`，而 `specs()` 会被并发 Run 读取 | register/find/specs 全部加锁 |
| `simpleName` 对匿名类返回 null，会产出 `"failed: null"` | 加 `?: "unknown"` 兜底 |
| provider 返回的 `List` 被直接存入 history，可被事后篡改 | `turn.calls.toList()` 防御性拷贝 |
| 并发测试中竞争性 `run()` 调用**无超时**——若真的存在全局锁，测试 JVM 会**永久挂起**而不是失败 | 竞争调用改为提交到双线程池并 `get(timeout)` |
| `ToolCall.arguments` 全套测试**零覆盖** | T2 增加断言，验证参数确实送达 tool |
| T3 只证明了顺序，未证明串行 | 增加 completion 顺序断言 |

## 7. Tests

> **本节为 AG.0 完成时的历史记录，测试数量已被 `AG.0.R1` 取代。**
> 现行测试总数见下方「AG.0.R1 之后的最终状态」。

AG.0 完成时：32 个测试，全部通过。测试设施（`ScriptedModelProvider`、
`RecordingTool`、`ThrowingTool`、`RecordingEventSink`、`BlockingTool`）
**全部位于 test source set**，未污染 production API。

全部测试离线、确定性、无 sleep、无时钟、无随机数、无网络。
并发测试使用 latch 而非 sleep，并以 10 秒超时兜底；
`SessionConcurrencyTest` 连续重复执行 5 次全部通过。

| # | 场景 | 结果 |
|---|---|---|
| T1 | direct final answer | PASS |
| T2 | model → tool → model → final | PASS |
| T3 | 多 tool call 串行 | PASS |
| T4 | unknown tool | PASS |
| T5 | tool 抛异常 | PASS |
| T6 | policy deny | PASS |
| T7 | maxSteps reached | PASS |
| T8 | provider failure | PASS |
| T9 | event order | PASS |
| T10 | session continuity | PASS |
| T11 | same-session BUSY | PASS |
| T12 | independent sessions | PASS |
| T13 | ToolResult mechanism/domain 语义 | PASS |

关键断言：

- **T2**：`provider.callCount==2`、`tool.executeCount==1`、`ToolResultMessage`
  出现在**第二个** `ModelRequest` 中、`specs()` 正确传入、`arguments=="hello"`
  送达 tool、history 为精确 4 元素列表。
- **T3**：start 顺序与 completion 顺序**均为** `[alpha, beta, gamma]`
  （completion 顺序是串行性的证据）。
- **T5**：`tool.executeCount==1`（证明工具确实执行过），且错误文本
  **不含**异常 message 中的哨兵字符串 `sensitive-host-detail-abc123`。
- **T6**：`tool.executeCount==0`；registry 单测另行证明 policy 先于 lookup
  （对不存在的工具也会先返回 deny）。
- **T7**：`maxSteps=2` 时 `provider.callCount==2`（off-by-one 的直接杀手），
  `executeCount==2`，随后**同一 Session** 立即可再次运行。
- **T9**：断言**完整有序事件列表**（相等比较），direct 5 个事件、tool 9 个事件，
  含 step 编号与事件间位置。
- **T11**：精确 history 列表断言，证明被拒绝的 Run 未写入任何内容；
  另有关闭 T11b 证明被拒绝的 Run 不发任何事件。
- **T12**：Session A 停在 tool 中时，Session B **正常完成**，
  且两个 Session 的 history 各自完整正确。
- **T13**：`exitCode=7` 出现在 output 中但 `success==true`、`error==null`；
  T13b 反向验证真正的机制失败为 `success==false`。

另有 12 个支撑测试：registry 重复注册/空名/unknown/异常转换/policy 顺序、
默认 allow-all、instructions 与 specs 传递、event sink 抛异常、
空 registry 直接回答、`maxSteps<1` 拒绝、session 被拒无事件、三 Session 并存。

## 8. Dependency audit

对 `ag-core/src/main`（10 个 .kt）执行搜索，**排除注释行**后：

```text
android. = 0        androidx. = 0       com.androidterminal = 0
JNI = 0             AdbManager = 0     AdbClient = 0
AdbConnection = 0   shell_v2 = 0        TlsTransport = 0
at_core = 0         Activity = 0       ProcessBuilder = 0
javax.android = 0   okhttp = 0          retrofit = 0
gson = 0            jackson = 0         org.json = 0
kotlinx.serialization = 0              java.sql = 0
Room = 0            EMG = 0             OpenAI = 0
AIController = 0

code-line violations: 0
```

production 代码的**完整 import 面**（穷举）：

```text
AgentCore.kt:3     import kotlinx.coroutines.CancellationException
AgentCore.kt:4     import kotlinx.coroutines.runBlocking
AgentCore.kt:5     import java.util.concurrent.ConcurrentHashMap
ToolRegistry.kt:3  import kotlinx.coroutines.CancellationException
```

仅 4 条 import：2 条 `kotlinx.coroutines`、1 条 `java.util.concurrent`。
无 Android、无 AT Core、无 JSON、无 HTTP、无 DI、无 logging、无 reflection。

`src/main` 中 concrete Tool / concrete ModelProvider / EMG provider /
OpenAI provider / AIController 集成：**全部不存在**。

## 9. Build

```text
.\gradlew.bat :ag-core:test     BUILD SUCCESSFUL   32 passed / 0 failed / 0 skipped
.\gradlew.bat build            BUILD SUCCESSFUL
.\gradlew.bat clean :ag-core:test   BUILD SUCCESSFUL
```

环境：Gradle 8.7 / Kotlin 2.1.0 / JDK 21（bytecode target 17）。

AG.0 当时新增的生产依赖（**SUPERSEDED BY AG.0.R1**，下表仅作历史记录）：

| 项 | 值 |
|---|---|
| dependency | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` |
| scope | ~~`implementation`（生产）~~ → **已被 `AG.0.R1` 降级为 `testImplementation`** |
| why required | ~~`Tool.execute` 与 `ModelProvider.complete` 是 `suspend`；同步入口 `run()` 需要真实 dispatcher~~ **该理由已被推翻**：`AG.0.R1` 移除了阻塞入口后，production 不再需要此库 |
| why not smaller | 手写 `startCoroutine` 事件循环等于自造协程调度器；把 contract 改成阻塞 `fun` 则在第一个真实 provider（网络调用）落地时必然要改 contract |

> ⚠ **现行事实**：AG Core 当前**无任何生产依赖**（仅 `kotlin-stdlib`）。
> `kotlinx-coroutines-core` 仅为 `testImplementation`，用于测试驱动 suspend 调用。

未引入：serialization / JSON、DI、HTTP client、logging framework、reactive framework、
Android coroutine stack、任何 Android 依赖。

## 10. Changed files

新增 production（9）：

```text
ag-core/src/main/kotlin/com/androidagent/core/Message.kt
ag-core/src/main/kotlin/com/androidagent/core/Model.kt
ag-core/src/main/kotlin/com/androidagent/core/Tool.kt
ag-core/src/main/kotlin/com/androidagent/core/ToolApproval.kt
ag-core/src/main/kotlin/com/androidagent/core/ToolRegistry.kt
ag-core/src/main/kotlin/com/androidagent/core/AgentEvent.kt
ag-core/src/main/kotlin/com/androidagent/core/RunOutcome.kt
ag-core/src/main/kotlin/com/androidagent/core/Session.kt
ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
```

修改 production（1）：

```text
ag-core/src/main/kotlin/com/androidagent/core/AgCoreMarker.kt
    仅更新已过时的注释（原注释称 AG.0 关注点"不得在此预判"，现已实现）
```

修改构建（1）：

```text
ag-core/build.gradle.kts   新增 kotlinx-coroutines-core:1.10.2；更新头部注释
```

新增 test（4）：

```text
ag-core/src/test/kotlin/com/androidagent/core/TestDoubles.kt
ag-core/src/test/kotlin/com/androidagent/core/AgentLoopTest.kt
ag-core/src/test/kotlin/com/androidagent/core/ToolRegistryTest.kt
ag-core/src/test/kotlin/com/androidagent/core/SessionConcurrencyTest.kt
```

修改文档（3）：

```text
docs/architecture/AndroidAgent_总体架构.md   最小必要更新（见 10.1）
README.md                                    仅修正已失实的状态描述
docs/implementation/steps/步骤AG.0_...md      本文件
```

### 10.1 架构文档更新范围（最小必要）

`AG.0` 落地后架构文档有若干描述失实，按最小必要更新：

- 状态标记由 `AG.BASELINE-0` 改为 `AG.0`；§2/§5 的"未实现"改为已实现。
- 补上此前**完全缺失**的冻结 package：`com.androidagent.core`。
- 新增 Session / Run 语义段：明确 **per-Session 一个 active Run**，
  明确**不存在全局单 Session 限制**。
- 补上 Tool 与 ModelProvider 的**依赖倒置**（contract 在内、实现在外）。
- 明确 **Android Tools 可使用 AT Core，也可直接使用 Android API**（此前文档只把
  `Android Tools → AT Core` 列为 allowed，未说明非强制）。
- 依赖方向表补 `AG Core -X-> 具体 Tool/Provider 实现` 与
  `Android Tools → Android APIs (可选)`。
- 路线图 `AG.0` 改为 DONE；依赖段记录 coroutines 已引入、serialization 仍未引入。

未做全面重写，文档整体结构与篇幅保持不变。

## 11. Deferred

以下在本步骤中被识别但**明确未实现**：

```text
Real ModelProvider（OpenAI 兼容 / EMG / AIController）
HTTP client / JSON protocol mapping / streaming / token streaming
任何具体 Android Tool（Shell / Battery / File / Process）
AT Core 集成 / ADB / JNI / shell 执行 / PTY
AGA / Android App / Compose / Activity
AGC / CLI / TUI
Skills（SKILL.md / SkillRegistry / skill marketplace）
approval pause/resume 状态机与人工审批 UI
parallel tool execution / tool scheduler / worker pool
persistent memory / database / Room / RAG
multi-agent / sub-agent / delegation
plugin framework / dynamic tool loading / MCP
MCP / remote tools
model turn 中的 prose 与 tool call 并存（ModelTurn.ToolCalls 当前无 content 字段）
typed tool argument schema（当前 arguments 为 opaque string）
```

## 12. Risks

| # | 风险 | 说明 |
|---|---|---|
| R1 | `ModelTurn.ToolCalls` 无 `content` 字段 | 真实 provider 常在请求工具时同时输出 prose（如"让我先检查设备"）。当前结构上无法记录该 prose。AG.0 无真实 provider 故未暴露，但**第一个真实 provider 落地时需要处理**。 |
| R2 | `ToolCall.arguments` 为 opaque string | 无 typed schema、无校验。AG.0 无消费方。第一个真实工具需要结构化参数。 |
| R3 | `Session` 身份按 AgentCore 实例隔离 | 同一个 `sessionId` 在两个不同 `AgentCore` 实例下会得到两个独立 Session。`RunFailure.SessionBusy(sessionId)` 读起来像全局标识。当前单 AgentCore 场景无影响。 |
| R4 | `AgentEvent.AnswerProduced` / `RunFailed` 与随后的 `RunFinished` 信息重叠 | **原判断依据已失效。** 原表述「因 `run()` 是同步的，调用方本来就能从返回值拿到 outcome」不再成立：`AG.0.R1` 已将入口改为 `suspend fun run`，调用方拿到的是 suspend 的返回值而非同步阻塞结果。**该项不再以 sync run 为由成立。** 仍然成立的冗余判断是：`RunFinished` 的 payload 已完整包含 outcome，而 `AnswerProduced` / `RunFailed` 只提供「在终态之前发生了什么」的时间顺序信息。二者在事件流中位置相邻、信息高度重叠；只有在消费方需要按时间线重建过程时才有独立价值。若 sink 未来变异步消费，该冗余需要重新评估。状态：**RESOLVED AS SUPERSEDED BY AG.0.R1**（依据变更，结论按上述重述保留为观察项）。 |
| R5 | 并发测试依赖 10 秒超时 | 正常情况下瞬时完成；仅在真实死锁时超时。机器高负载下超时与真死锁不可区分，但已远优于永久挂起。 |
| R6 | `T12b` 位于并发测试类但实为顺序测试 | 测的是 history 隔离而非并发。分类略有不当，无功能影响。 |

## 13. Verdict

```text
AG.0_COMPLETE
```

`§37` 的 20 项完成条件逐条核对全部通过。核心闭环在纯 JVM 中真实运行，
13 项测试矩阵全部通过，依赖审计零违规，`:ag-core:test` 与 `build` 均
BUILD SUCCESSFUL，无 scope 扩张，无 commit / push。

存在的后续事项（§11 Deferred、§12 Risks）均不属于 AG.0 范围，
且不影响 AG.0 本身的正确性与完整性。

## 14. Handoff

```text
branch   : main
baseline : 231b319  (ag-baseline-0) —— 仍是 HEAD
改动     : 全部未提交（工作区 modified + untracked）
```

AG.0 引入的 contract 供后续步骤依赖：

```text
ModelProvider   complete(ModelRequest) -> ModelTurn
Tool            spec + suspend execute(ToolCall) -> ToolResult
ToolRegistry    register / find / specs / dispatch(call, policy)
ToolApproval    Allow | Deny(reason)
AgentCore       suspend run(sessionId, userInput, RunOptions) -> RunOutcome
```

下一步的第一个真实决策点：**第一个真实 ModelProvider 放在哪里**
（`:ag-core` 之外的 integration 层），以及它是否需要 structured arguments /
prose+toolCall 并存（§12 R1/R2）。

下一步的名称与范围**尚未确定**（`NEXT = PENDING ARCHITECTURE REVIEW`），
不得默认视为 AG.0.1 或 AG.1。

不得自动开始下一步。

---

## 15. AG.0.R1 之后的最终状态（SSOT 指向）

本文档上述内容为 **AG.0 完成当时**的历史记录。其中以下状态已被
`AG.0.R1 — Runtime Async Boundary Freeze` **取代（SUPERSEDED）**：

| 项 | AG.0 当时记录（历史，已失效） | R1 之后的现行事实 |
|---|---|---|
| 入口签名 | `fun run()` + `runBlocking` | **`suspend fun run(...)`** |
| 生产依赖 | `kotlinx-coroutines-core`（`implementation`） | **无**，仅 `kotlin-stdlib` |
| production kotlinx 引用 | `runBlocking` + `CancellationException` import | **0** |
| 测试数量 | 32 | **39 passed / 0 failed / 0 skipped** |
| §7 全部测试设施 | 见 §7 | 保留，另新增 `EventSinkContractTest`(6) 与 T14/T14b |
| §9 构建输出 | `32 passed` | `39 passed` |
| §12 R4 判断依据 | 以「run() 同步」为理由 | **已失效**，见 §12 R4 现行表述 |

现行 SSOT：`docs/implementation/steps/步骤AG.0.R1_Runtime_Async_Boundary_Freeze.md`

冻结后的不变式（AG.0 与 AG.0.R1 均不再变更）：

```text
AgentCore.run         = suspend fun
ModelProvider.complete = suspend fun
Tool.execute          = suspend fun

production dependency = kotlin-stdlib only
production import 面   = 3 行，全部 java.util.concurrent.*
tests                 = 39 / 39
```

Tag `ag-0` 标记该冻结点。