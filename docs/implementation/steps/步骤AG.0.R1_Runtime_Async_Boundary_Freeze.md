# 步骤 AG.0.R1 — Runtime Async Boundary Freeze

> 本文档是 AG.0.R1 的 SSOT，同时是 AG.0 + AG.0.R1 冻结点的现行事实来源。
> `步骤AG.0_AG Core v0架构与最小Agent Loop.md` 是 AG.0 当时的历史记录，
> 其中被取代的部分已在该文 §15 明确标注。

## 1. Metadata

| 项 | 值 |
|---|---|
| 步骤 | `AG.0.R1` |
| 工程 | AndroidAgent（独立开发工程） |
| 根目录 | `D:\codebank\AndCode\AndroidAgent` |
| 前置 | `AG.0`（未 commit 状态） |
| 类型 | 补充审查 / 边界冻结，**非新功能步骤** |
| 结果 | `AG.0_READY_TO_FREEZE` |
| 冻结点 | tag `ag-0` |
| Push | **无** |

## 2. Previous state

进入本步骤时（AG.0 完成、未 commit）：

```text
HEAD                : 231b319 (ag-baseline-0)  ← 仍是基线，AG.0 未提交
production 文件      : 9 个新文件 + AgCoreMarker 注释更新
tests               : 32 passing
生产依赖            : kotlinx-coroutines-core (implementation)
入口签名            : fun run(...) = runBlocking { ... }
```

## 3. Review trigger

AG.0 实现完成后暴露出一个设计层面的疑问，需要在冻结前裁决：

> `AgentCore.run` 为何是**同步** `fun`，而内部 contract 全部是 `suspend`？
> 这个非对称是否有不可替代的理由？

本步骤只回答这一个边界问题，并据此决定依赖是否可移除。**不引入任何功能。**

## 4. Async boundary audit

### 4.1 冻结后的签名

```text
AgentCore.run            suspend fun run(sessionId, userInput, options): RunOutcome
ModelProvider.complete   suspend fun complete(request: ModelRequest): ModelTurn
Tool.execute             suspend fun execute(call: ToolCall): ToolResult
ToolRegistry.dispatch    suspend fun dispatch(call, policy): ToolResult   (internal)
```

三个 contract **全部为 suspend**。AG Core 不存在任何同步入口。

调用链：

```text
run (suspend)
  └─ executeRun (suspend)
       └─ driveLoop (suspend)
            ├─ modelProvider.complete (suspend)
            └─ toolRegistry.dispatch (suspend)
                 └─ tool.execute (suspend)
```

### 4.2 R1 之前 production 中 `kotlinx.coroutines` 的全部使用点

| # | import | call site | purpose | 性质 |
|---|---|---|---|---|
| 1 | `kotlinx.coroutines.runBlocking` | `AgentCore.kt:83` | 把 suspend 的 `executeRun` 包成阻塞 `fun run()` | **纯桥接** |
| 2 | `kotlinx.coroutines.CancellationException` | `AgentCore.kt:99, 136, 190` | 3 处重抛，避免把协程取消误判为 provider/tool 失败 | 真实需求，**但非库特有** |
| 3 | `kotlinx.coroutines.CancellationException` | `ToolRegistry.kt:70` | tool 抛取消时不吞掉 | 真实需求，**但非库特有** |

其余 `suspend` 关键字是 **Kotlin 语言特性**，不构成库依赖。

**关键事实**：`kotlinx.coroutines.CancellationException` 在 JVM 上是文档化的
typealias，指向 `java.util.concurrent.CancellationException`。因此第 2、3 项的
语义可用 JDK 类型表达，**不需要库**。

## 5. Decision: CHANGE_TO_SUSPEND

```text
CHANGE_TO_SUSPEND
```

**理由：**

1. **命中判定规则** —— production 对协程库的**唯一实质需求**就是 `runBlocking`
   这一层「把 suspend runtime 强行包装成 blocking 入口」的桥接。
2. **`runBlocking` 在库代码中是反模式** —— 它把调用线程整段钉死，使宿主无法取消、
   无法控制线程；Android 上直接调用会造成 ANR。
3. **职责边界** —— AG Core owns Agent runtime semantics；
   Host / AGA / AGC owns blocking / thread / dispatcher / lifecycle policy。
   同步入口把宿主策略泄漏进了库。
4. **无兼容负担** —— API 尚未发布，无需保留旧签名。
5. **客观验证** —— 移除后 `runtimeClasspath` 只剩 `kotlin-stdlib`，
   production 的 `kotlinx` 引用为 0，import 面仅 3 行纯 JDK。

因此 production dependency `kotlinx-coroutines-core` 被**删除**，
降级为 `testImplementation`（仅用于从 JVM 测试驱动 suspend 调用）。
未手写任何 coroutine scheduler。

## 6. Production changes

| 文件 | 变更 |
|---|---|
| `AgentCore.kt` | `fun run` → `suspend fun run`；删除 `runBlocking` import 与桥接体；`kotlinx.coroutines.CancellationException` → `java.util.concurrent.CancellationException`；KDoc 改写为职责边界说明 |
| `ToolRegistry.kt` | import 改为 `java.util.concurrent.CancellationException` |
| `Model.kt` | KDoc 中 `CancellationException` 改 JDK 全限定名 |
| `Session.kt` | KDoc 新增「aborted Run 的 history 语义」契约 |
| `ag-core/build.gradle.kts` | `implementation` → `testImplementation`；头部注释改写 |

AG Core **不提供** `runBlocking` / `blockingRun` / `runSync` 任何阻塞包装。
需要阻塞的主机自行桥接。

## 7. EventSink contract

现状**已满足**，本步骤未改生产逻辑，仅补文档与测试证据。

| 要求 | 机制 | 证据（测试） |
|---|---|---|
| 不导致 Session 永久 BUSY | `executeRun` 的 `finally { session.endRun() }` 覆盖所有退出路径 | `sink failure at RunStarted does not leave the session busy` |
| 不留下不可用 history | 结果**先记录后通知**，sink 故障无法孤立结果 | `sink failure mid tool loop leaves history faithful and still usable`；`a tool level failure never unbalances the tool call turn` |
| 二次 emit 不产生 uncontrolled exception | `emitTerminal` 吞掉 sink 异常 | `a sink that always throws cannot turn a failure into an escaping exception`（`emitCount == 3` 证明 RunStarted/RunFailed/RunFinished 三次尝试均发生且无一逃逸） |

### 7.1 审查中发现并修正的自身错误

初版测试断言「history 必须严格平衡」，这是**错误**的。真实不变量是：

- 记录的结果绝不引用未声明的调用；
- 中止的 Run 留下**忠实于实际执行**的部分完成 turn（不为未到达的调用伪造结果）；
- Session 与 agent 保持可用。

该语义已写入 `Session.kt` KDoc 并由测试固定。

## 8. Duplicate registration (T14 / T14b)

当前行为：**`require` 快速失败**，抛
`IllegalArgumentException("tool already registered: <name>")`，**无静默覆盖**。

| # | 测试 | 断言 |
|---|---|---|
| **T14** | `duplicate tool registration fails fast without silent overwrite` | 连续 3 次尝试**全部**被拒（确定性）；`specs().size == 1`；原注册仍在（`find("dup")` 返回原实例） |
| **T14b** | `a rejected duplicate never becomes reachable` | dispatch 解析到原工具，被拒工具永远不可达 |

理由：重复注册是 composition root 的编程错误，应快速失败并暴露，
而不是在 Run 中途静默覆盖掉一个工具。

## 9. Regression tests

```text
.\gradlew.bat clean              BUILD SUCCESSFUL
.\gradlew.bat :ag-core:test      BUILD SUCCESSFUL
.\gradlew.bat build              BUILD SUCCESSFUL

total   : 39
passed  : 39
failed  : 0
errors : 0
skipped : 0
```

相对 AG.0 的 32：新增 7 个（`EventSinkContractTest` 6 个 + `ToolRegistryTest`
的 T14b 1 个；T14 由原 `duplicate registration...` 强化而来）。

§重点语义复检逐项 PASS：same-session BUSY（T11/T11b）、independent sessions
（T12/T12b）、provider failure（T8）、tool exception（T5）、event sink failure
（3 项契约）、session continuity（T10）。

`SessionConcurrencyTest` + `EventSinkContractTest` 连续重跑 **5 次全部通过**。

## 10. Dependency audit

**production：无（仅 Kotlin 标准库）**

```text
runtimeClasspath
\--- org.jetbrains.kotlin:kotlin-stdlib:2.1.0
```

**production import 面（穷举，3 行，全为 JDK）：**

```text
AgentCore.kt:3     import java.util.concurrent.CancellationException
AgentCore.kt:4     import java.util.concurrent.ConcurrentHashMap
ToolRegistry.kt:3  import java.util.concurrent.CancellationException
```

`src/main` 全文扫描（排除注释）：

```text
android.*=0  androidx.*=0  com.androidterminal=0  AT Core=0  ADB=0  JNI=0
AdbManager=0  AdbClient=0  at_core=0  Activity=0  ProcessBuilder=0
kotlinx.coroutines=0   okhttp=0  gson=0  org.json=0  EMG=0  OpenAI=0  AIController=0
Concrete Tool / Concrete Provider 实现 = 0
```

**test：**

```text
testRuntimeClasspath
+--- kotlin-stdlib:2.1.0
+--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2   (test only)
+--- JUnit 5 (bom 5.11.4)
```

## 11. Changed files

**AG.0.R1 修改（5）：** `AgentCore.kt`、`ToolRegistry.kt`、`Model.kt`、
`Session.kt`、`ag-core/build.gradle.kts`

**测试修改（4）：** `AgentLoopTest.kt`、`SessionConcurrencyTest.kt`、
`TestDoubles.kt`、`ToolRegistryTest.kt`

**测试新增（1）：** `EventSinkContractTest.kt`（6 个测试）

**文档修改（3）：** `README.md`、`AndroidAgent_总体架构.md`、
`步骤AG.0_AG Core v0架构与最小Agent Loop.md`

**文档新增（1）：** 本文档

AG.0.R1 **未**修改 `Message.kt`、`Tool.kt`、`ToolApproval.kt`、`AgentEvent.kt`、
`RunOutcome.kt`、`AgCoreMarker.kt` 的行为。

## 12. Deferred / risks

### 12.1 Deferred

延续 AG.0 的全部 Deferred 项，**继续 Deferred**（本步骤不解决）：

```text
ModelTurn prose + tool calls 并存        （R1）
typed ToolCall arguments schema          （R2）
Real ModelProvider（OpenAI / EMG / AIController）
HTTP client / JSON protocol mapping / streaming
任何具体 Android Tool / AT Core 集成 / ADB / JNI / shell / PTY
AGA / Android App / Compose / AGC
Skills / MCP / multi-agent / plugin framework
persistent memory / database / RAG
parallel tool execution
```

### 12.2 新增 Deferred（本步骤记录，不解决）

```text
真实 Provider 可能对 partial tool-turn history 存在比 AG Core 更严格的
wire protocol 要求（例如要求每个 assistant tool_call 都有配对的 tool result，
不接受中止后未完成的 turn）。

AG.0 的选择是不伪造结果、忠实记录实际执行。该选择对真实 provider 是否可接受，
必须在首个真实 Provider integration 时验证。本步骤不解决。
```

### 12.3 仍然存在的风险

| # | 风险 |
|---|---|
| R1 | `ModelTurn.ToolCalls` 无 `content` 字段，真实 provider 常同时输出 prose，结构上无法记录 |
| R2 | `ToolCall.arguments` 为 opaque string，无 typed schema 与校验 |
| R3 | `Session` 身份按 `AgentCore` 实例隔离，同一 `sessionId` 在不同 core 下互不可见 |
| R4 | `AnswerProduced` / `RunFailed` 与随后的 `RunFinished` 信息高度重叠（依据已按 `AG.0.R1` 重述，见 AG.0 文档 §12） |
| R5 | 并发测试依赖 10 秒超时，正常瞬时完成，高负载下无法区分超时与真死锁 |
| R6 | `T12b` 位于并发测试类但实为顺序测试 |
| R7 | 上述 12.2 的 partial tool-turn 与真实 provider 的兼容性未知 |

## 13. Verdict

```text
AG.0_READY_TO_FREEZE
```

异步边界已冻结为全 `suspend`；production 依赖归零；EventSink 三项契约有测试
证据；T14/T14b 已入矩阵；39/39 通过；重点语义无回归；依赖审计零违规；
文档一致。

## 14. Handoff

```text
冻结不变式
  AgentCore.run         = suspend fun
  ModelProvider.complete = suspend fun
  Tool.execute          = suspend fun
  production dependency = kotlin-stdlib only
  production import 面   = 3 行 java.util.concurrent.*
  tests                 = 39 / 39
```

下一步：**PENDING ARCHITECTURE REVIEW**。
名称与范围尚未决定，不得默认视为 AG.0.1 或 AG.1，不得自行创建。

首个真实决策点：真实 ModelProvider 的落点（`:ag-core` 之外），
以及是否需要 structured arguments / prose+toolCall 并存。

不得自动开始下一步。
