# AndroidAgent 总体架构

> 状态：`AG.0` 已完成（AG Core v0 架构与最小 Agent Loop）。
> 本文只冻结**当前已确定**的内容。未实现的部分一律标注为"未实现"，
> 不得作为已完成能力描述。

## 1. Project positioning

AndroidAgent 是面向 Android 自主交互的 **Agent 智能 / 运行时**工程。

它不是一个终端应用，也不是一个 ADB 库。它是 Agent 的决策与运行时层，
负责理解用户意图、调用工具、综合结果；Android 的实际执行能力由外部提供。

```text
开发目录 : D:\codebank\AndCode\AndroidAgent          = source of truth
发布目录 : D:\codebank\AndCode\AndroidAgent-Public   = 仅下游导出
```

## 2. AG Core

AG Core 是 **Agent 智能内核**，也是产品无关的运行时。

职责范围（对应已冻结决策 A2）：

```text
用户输入 / 上下文
Model / API 抽象
Agent Loop
任务状态
Skills
Tool Registry
权限 / 审批
结果综合
与 GUI / TUI 无关的 event/result contract
```

当前实现状态：**AG Core v0 已实现**（`AG.0`）。位于
`com.androidagent.core`，包含 Agent Loop、Session/Run、ModelProvider 与 Tool
contract、Tool Registry、审批策略、事件出口、Run 结果模型。
Skills、真实 provider、具体 Android Tool 仍为**未实现**。

### 异步边界（AG.0.R1 冻结）

```text
AgentCore.run        suspend fun
ModelProvider.complete   suspend fun
Tool.execute             suspend fun
```

AG Core **不提供任何阻塞包装**（无 `runBlocking` / `blockingRun` / `runSync`）。

```text
AG Core      owns Agent runtime semantics
Host / AGA / AGC   owns blocking / thread / dispatcher / lifecycle policy
```

需要阻塞的主机自行桥接（`runBlocking { }` 等）。AG Core 因此**没有任何生产依赖**，
只依赖 Kotlin 标准库；`kotlinx-coroutines` 仅用于测试。

冻结的 package：

```text
com.androidagent.core      唯一 package，禁止 com.androidterminal.* / com.androidagent.app.*
```

### Session 与 Run

```text
Session = 多轮长期会话，拥有自己的 message history
Run     = 一次用户请求触发的一次 Agent Loop
```

并发语义（已由测试冻结）：

```text
同一个 Session      同时最多一个 active Run；第二个 Run 返回 SessionBusy
不同 Session        相互独立，不因彼此而 BUSY
Run ownership       归属于 Session，不存在全局 AgentCore 锁
Run 结束            无论成功/失败/异常，ownership 必定释放
```

AG Core **不是**"全局只能存在一个 Session"的单例模型；多个 Session 可长期并存。

AG Core 的边界规则：

```text
AG Core  -X->  AT Core
AG Core  -X->  Android framework (android.* / androidx.*)
AG Core  -X->  ADB / JNI
AG Core  -X->  com.androidterminal.*
```

这是**编译期强制**的，不只是约定：`:ag-core` 使用 `kotlin("jvm")` 插件，
`android.*` 无法解析；`settings.gradle.kts` 中刻意不声明 `google()`。

## 3. AGA

AGA 是 AndroidAgent 的 GUI 产品形态（未来 `AG.apk`）。

当前状态：**未实现**。本工程当前**没有** `:app` module，也不计划在
`AG.BASELINE-0` 预建。AGA 将消费 AG Core + Android Tools。

## 4. AGC

AGC 是未来的 CLI / TUI 前端，与 AGA **共享同一个 AG Core**。

当前状态：**未实现**。是否能让 stock Android 在无 root 条件下承载一个真正
独立的 `androidagent` CLI/TUI，属于独立的 host feasibility 轨道，
**不阻塞** AG Core 与 AGA。

## 5. Tool System

Tool System 是 AG Core 与执行层之间的唯一通道。

```text
Tool Registry   归属 AG Core —— 注册、查找、调用分发、ToolSpec 暴露
Concrete Tool   归属 Android Tools —— 具体 Android 能力实现
```

> `Tool Registry` 的职责中**不含 typed argument schema**。
> `ToolCall.arguments` 当前是不透明字符串，typed schema 属于 Deferred。

核心约束：

```text
AG Core 只知道 Tool 系统
AG Core 不知道 ADB 协议细节
```

因此 AG Core 中**不出现** `AdbClient`、`shell_v2`、WRTE/OKAY、TLS、PTY
等任何执行层概念。

### 依赖倒置（已实现并测试）

必须区分两件不同的事：

```text
CODE DEPENDENCY（编译期，谁需要谁）
  Concrete Tool      --implements-->  Tool contract      (AG Core)
  Concrete Provider  --implements-->  ModelProvider      (AG Core)
  Host/Composition Root --constructs / registers--> AG Core

  AG Core  -X->  concrete implementations

RUNTIME CALL / REGISTRATION FLOW（运行时，事件怎么走）
  Host 构造 provider 与 tools
    -> register/inject 进 AG Core
    -> Run 内 AG Core 通过 registry 调用已注册的 tool 实例
```

**注意**：Concrete 实现**依赖** AG Core 的 contract（它必须实现该接口），
而不是「不依赖 AG Core」。所谓"倒置"指的是**方向**：
依赖从实现指向 contract，AG Core 不指向实现。

当前状态：**contract 与 Tool Registry 已实现**（`AG.0`）。
Concrete Tool 实现仍为**未实现**。AG.0 的 production 代码中不存在任何
concrete Tool / concrete Provider；测试用的 fake 只存在于 test source set。

### ToolResult 语义（已冻结）

```text
ToolResult.success = 工具调用机制是否正常完成
                   ≠ 领域操作本身是否成功
```

例如 shell 工具命令 `exitCode=7`，只要命令确实执行并取回
stdout/stderr/exitCode，其 invocation 仍为 `success = true`。
只有 unknown tool、审批拒绝、工具实现自身抛异常，才算机制失败。

## 6. Android Tools

Concrete Tool 的实现层，例如未来的 shell tool、push tool、pull tool。

职责边界：

```text
把 AG Core 的 Tool 调用
        翻译为
具体的 Android 执行请求
```

Android Tools 是**唯一**允许同时依赖 AG Core 与 AT Core 的层。

```text
AG Core      ->  Tool Registry  ->  Android Tools  ->  AT Core
```

Android Tools 的执行后端**不限于** AT Core —— 它可以直接使用 Android API，
也可以使用 AT Core，由具体工具按需选择：

```text
Android Tools  ->  AT Core        （可选）
Android Tools  ->  Android APIs   （可选）
```

Integration（真实 provider、具体 tool、网络传输）全部位于 **AG Core 之外**。
AG Core 内不得出现任何具体实现。

当前状态：**未实现**。归属独立 integration 层，后续可先是 package，
在出现第二个真实执行后端时才考虑独立 module —— 单一后端不值得抽象。

## 7. AT Core relationship

AT Core 是 AndroidTerminal 工程的 **Android 执行内核**（ADB 连接 / 传输 /
协议、shell 执行）。

```text
AT Core 属于 AndroidTerminal 工程
AndroidAgent 不 import、不 vendor、不复制 AT Core
```

AG Core **不得**直接依赖 AT Core。若 AG Core 看起来需要它，说明设计错了 ——
正确做法是引入一个 tool。

注意：AndroidTerminal 侧的 AT Core 当前尚未完成物理模块化。
这不影响 AG Core —— AG Core 本来就不依赖它。

### ModelProvider

与 Tool 同样的依赖倒置：**AG Core 定义 contract，实现全部在外部**。

```text
AG Core 定义 ModelProvider contract
        ▲
        │ implements
  ┌─────┼────────────┐
OpenAI Provider  EMG Provider  AIController Provider
        （AG Core 外部实现）
```

AG Core 不含 HTTP client、不含 JSON protocol mapping、不认识任何具体 provider
的 wire schema。`ModelTurn` 保持 provider-neutral（final answer 或 tool calls），
不复制任何厂商 chat-completion 结构。

当前状态：**contract 已实现，真实 provider 未实现**（`AG.0`）。

## 8. Dependency direction

```text
ALLOWED
  AGA ---------→ AG Core
  AGC ---------→ AG Core
  AG Core ----→ Tool contract（AG Core 定义，AG Core 内部持有）
  AG Core ----→ ModelProvider contract（AG Core 定义，AG Core 内部持有）
  Android Tools → AT Core          (可选)
  Android Tools → Android APIs     (可选)
  AT Core ----→ adbd / Android

FORBIDDEN
  AG Core       -X-> AT Core
  AG Core       -X-> Android framework
  AG Core       -X-> ADB / JNI
  AG Core       -X-> 具体 Tool 实现
  AG Core       -X-> 具体 ModelProvider 实现
  AT Core       -X-> AG Core
  AGA           -X-> AT Core        (必须经 Android Tools)
```

代码依赖方向（**编译期**）：

```text
Concrete Tool      --implements / depends on-->  AG Core Tool contract
Concrete Provider  --implements / depends on-->  AG Core ModelProvider contract

Host / Composition Root
   --constructs / registers / injects-->  AG Core runtime

AG Core  -X->  concrete implementations
```

> **核心表述**：Concrete implementation **依赖** AG Core 中定义的 contract。
> 依赖倒置指的是**方向** —— AG Core 不反向依赖 concrete implementation。
> 外部实现之所以「倒置」，是因为它依赖 AG Core 的 contract，而 AG Core 不依赖它；
> 而不是「实现不依赖 AG Core」。

运行时注册与调用（**运行期**，与上面的代码依赖方向相反，不可混读）：

```text
Host 构造 provider 与 tools -> register / inject 进 AG Core
Run 内 AG Core 通过 registry 调用已注册的 tool 实例
```

架构图（**runtime call flow，NOT code dependency**）：

```text
            AGA
             |
             v
          AG Core
             |
             v
        Tool Registry
             |
             v
        Concrete Tools
             |
        Android Tools
             |
             v
       Stable AT Core
             |
             v
          Android

  AGC ---→ AG Core
```

代码依赖图（**CODE DEPENDENCY，编译期**）：

```text
Concrete Tool     --implements-->  Tool contract        (AG Core)
Concrete Provider --implements-->  ModelProvider        (AG Core)

Host / Composition Root
   ├─ constructs provider
   ├─ constructs tools
   └─ registers / injects them into AG Core

AG Core  -X->  concrete implementations
```

读图要点：上面那张是**运行时事件流向**——AG Core 在 Run 内经 registry 调用
已注册的 tool 实例。面那张是**编译期依赖**——方向相反，实现依赖 contract，
AG Core 不依赖实现。两张图不能混读。

## 9. Development / Public directory relationship

```text
AndroidAgent          开发目录，唯一 source of truth
AndroidAgent-Public   发布目录，只在明确 release 节点由开发目录导出
```

禁止在 `-Public` 直接开发；禁止在 `-Public` 修 bug 后反抄开发树；
禁止两个目录同时充当 source of truth。

第一次 Public 同步应发生在明确 release-ready 节点，例如 AG Core v0.1。

## 10. Current roadmap

```text
AG.BASELINE-0   独立开发工程初始化与基线冻结            DONE
   |
   v
AG.0            AG Core v0 架构与最小 Agent Loop       DONE
   |
   v
AG.0.R1         Runtime Async Boundary Freeze           DONE
   |
   v
NEXT            PENDING ARCHITECTURE REVIEW
```

> 后续步骤**尚未确定**。AG.0.R1 完成异步边界冻结后，下一步的名称与范围
> 待架构审查决定；不得默认视为 AG.0.1 或 AG.1，也不得据此预建任何 module。

本工程当前**仅存在** `:ag-core` 一个 module。以下均**不在**本工程基线内，
且未被预建：

```text
:app (AGA)   :android-tools   :agc   :at-core
```

依赖决策：

```text
生产依赖   无（仅 Kotlin 标准库）
测试依赖   kotlinx-coroutines-core（用于从 JVM 测试驱动 suspend 调用）
```

JSON / serialization、DI、HTTP client、logging framework、reactive framework
一律未引入，且在出现真实 consumer 前不应引入。