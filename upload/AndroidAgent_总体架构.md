# AndroidAgent 总体架构

> 状态：`AG.0` **FROZEN**（tag `ag-0`）、`AG.PROVIDER-0` **FROZEN**（tag `ag-provider-0`）。
> 首个真实 Provider 已实现并在真实 endpoint 上验证。
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

当前实现状态：**AG Core v0 已实现并冻结**（`AG.0`，tag `ag-0`）。位于
`com.androidagent.core`，包含 Agent Loop、Session/Run、ModelProvider 与 Tool
contract、Tool Registry、审批策略、事件出口、Run 结果模型。

- **Skills** 与**具体 Android Tool** 仍为**未实现**。
- 首个真实 **OpenAI-compatible Provider** 已在 **AG Core 之外**实现、验证并冻结
  （`:provider-openai-compatible`，tag `ag-provider-0`）。

> 区分两件不同的事：
> `:ag-core` 模块内**没有**任何 concrete Provider；
> 但 AndroidAgent repository **已经有**一个位于 Core 之外的 concrete Provider module。

### 四层边界（AGA-0.ARCH 冻结）

```text
AG Core        = Agent 语义核心
                 Agent Loop / Session-Run / History / contracts / Events / RunOutcome

Agent Runtime  = Host-neutral 的可运行 Agent 生命周期（:ag-runtime）
                 当前 Session 选择 + 单 active Run 串行化
                 不是第二个智能核心：history SSOT 仍在 AG Core Session

Composition Root = environment-specific assembly
                 选择 Provider / Transport / Tool 实现并装配

Frontend (AGA / AGC) = 输入、显示、平台 lifecycle、配置输入、render
```

```text
:app(AGA) ──▶ :ag-runtime ──▶ :ag-core
   │
   └────────▶ :provider-openai-compatible ──▶ :ag-core

:ag-runtime -X-> Android / UI / concrete Provider / transport
:ag-core     -X-> :ag-runtime / :app / provider 实现
```

依赖方向是单向的：可复用的 Session/Run host 生命周期属于 `:ag-runtime`，
frontend 保持轻薄。

**运行时调用流（与代码依赖图不同，不可混读）：**

```text
AGA
 ↓
Agent Runtime
 ↓
AG Core
 ↓
Tool Registry
 ↓
Concrete Tools
```

AGC 走同一条路径（见 §4）。

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

### ModelTurn 冻结契约

```kotlin
sealed interface ModelTurn {
    data class FinalAnswer(val content: String) : ModelTurn
    data class ToolCalls(
        val calls: List<ToolCall>,
        val content: String = "",   // 同一 turn 的 prose
    ) : ModelTurn
}
```

已由真实 endpoint 验证的能力：

```text
pure final answer                YES
single tool call                 YES
multiple tool calls              YES
same-turn prose + tool_calls     YES   ← TRUE SAME-TURN MIXED OBSERVED
opaque tool arguments            YES
tool result continuation         YES
```

`AG.PROVIDER-0` 之后不得再无证据修改该契约。

### 已实现状态

```text
AG Core（:ag-core，已 FROZEN）
  Agent Loop / Session / Run / Context / History
  ModelProvider contract / Tool contract / Tool Registry
  Allow-Deny Policy / Events / RunOutcome

Concrete Provider（:provider-openai-compatible，已 FROZEN）
  OpenAiCompatibleProvider —— 首个真实验证的 concrete provider
  AG-owned 开发期 Provider 配置（loader / resolver / local JSON）
  真实链路：Model -> ToolCall -> Tool -> ToolResult -> Model -> Final
```

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

AGA 是 AndroidAgent 的 Android GUI / Host 产品形态。

当前状态：

```text
:app                              已实现
AGA-0                             DONE / FROZEN（tag ag-aga-0）
Android build / install / launch   已验证
real Android chat                 PASS
strict real multi-turn memory     PENDING
Android Tools / AT integration    尚未开始
```

AGA 是**前端**：只负责输入、显示、Android lifecycle bridge 与开发期配置输入。
它**不拥有** Agent 业务生命周期（Session 身份 / Run 驱动 / busy 语义）——
那些在 `:ag-runtime`。

## 4. AGC

AGC 是未来的 CLI / TUI 前端，与 AGA **共享同一个 Agent Runtime**：

```text
AGC
 ↓
Agent Runtime (:ag-runtime)
 ↓
AG Core (:ag-core)
```

AGC 尚未开始。

是否能让 stock Android 在无 root 条件下承载一个真正独立的 `androidagent`
CLI/TUI，属于独立的 host feasibility 轨道，**不阻塞** AG Core、Agent Runtime 与 AGA。

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

当前状态：

```text
Tool contract + Tool Registry（:ag-core）   IMPLEMENTED / FROZEN
Concrete Android Tool                      NOT IMPLEMENTED
```

`AG Core（:ag-core）` 自身**不包含**任何 concrete Tool 或 concrete Provider；
测试用的 fake tool 只存在于 test source set。

> 注意区分模块范围与仓库范围：
> 当前 repository **已包含**位于 Core 之外的 `:provider-openai-compatible`，
> 即已有一个 concrete **Provider**；但 **concrete Android Tool 仍未实现**。

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

**Concrete Android Tools 是 AndroidAgent 中唯一允许跨越 Agent execution boundary
的实现层。** 具体 Tool 可按真实需求三选一或组合：

```text
1. 直接使用 Android API
2. 接入 AndroidTerminal 的 execution boundary
3. 同时组合两者
```

> **不要**写成「所有 Android Tools 必须直接依赖 AT Core」——这是错误的。
> AG Core / Agent Runtime 不知道这些实现细节。

```text
AG Core            -X-> AT implementation
Agent Runtime      -X-> AT implementation
AGA                -X-> AT implementation
Concrete Tool       允许接入（机制尚未冻结）
```

Integration（provider、具体 tool、网络传输）全部位于 **AG Core 之外**。
AG Core 内不得出现任何具体实现。

当前状态**必须分开陈述**，不可用统一的「未实现」覆盖两者：

```text
Provider integration            IMPLEMENTED / FROZEN
                                  :provider-openai-compatible（tag ag-provider-0）
                                  真实 endpoint 已验证


Android Tool / execution integ. NOT IMPLEMENTED
```

Android Tools 归属独立 integration 层；在出现第二个真实执行后端之前，
不值得再抽象出通用 framework 层。

## 7. AT Core relationship

AT Core 是 AndroidTerminal 工程的 **Android 执行内核**（ADB 连接 / 传输 /
协议、shell 执行）。

```text
AT Core 属于 AndroidTerminal 工程
```

AndroidAgent **不得**：

```text
vendor AT source
copy AT source
reimplement AT Core
把 AT implementation 塞进 AG Core / Agent Runtime / AGA
```

```text
AG Core       -X-> AT implementation
Agent Runtime -X-> AT implementation
AGA           -X-> AT implementation
```

只有 **Concrete Android Tools** 允许接入 AndroidTerminal 的 execution boundary。

> **接入机制尚未冻结。** 以下都留待 `AG.SHELL-0.DESIGN` 决定，本阶段不预设：
> direct Android library · Android runtime service · Binder / AIDL · 其他 IPC。

AT 的一个重要性质（不是本阶段决定，仅记录）：

```text
AT 不要求脱离 Android device，
但其执行能力最终必须能够脱离 AT.apk 的 UI，
被其他 Android consumer 复用。
```

AG Core **不得**直接依赖 AT 实现。若 AG Core 看起来需要它，说明设计错了 ——
正确做法是引入一个 tool。

注意：AndroidTerminal 侧的 AT Core 当前尚未完成物理模块化。
这不影响 AG Core —— AG Core 本来就不依赖它。

### ModelProvider

与 Tool 同样的依赖倒置：**AG Core 定义 contract，实现全部在外部**。

```text
AG Core 定义 ModelProvider contract
        ▲
        │ implements
        │
OpenAiCompatibleProvider        IMPLEMENTED / VERIFIED / FROZEN
        │                         (:provider-openai-compatible)
        └── 可指向 EMG 的 OpenAI-compatible endpoint

Future provider implementations
        ├── AIController Provider   NOT IMPLEMENTED
        └── 其他 Provider           NOT IMPLEMENTED
```

> **不要误读**：当前**不存在** `EMGProvider` 类。
> EMG 是 `OpenAiCompatibleProvider` 所连接的 **OpenAI-compatible endpoint**，
> 而不是一个已实现的独立 provider 实现。

AG Core 不含 HTTP client、不含 JSON protocol mapping、不认识任何具体 provider
的 wire schema。`ModelTurn` 保持 provider-neutral（final answer 或 tool calls），
不复制任何厂商 chat-completion 结构。HTTP 与 JSON 全部位于 AG Core 之外，
按模块分列：

```text
JSON / OpenAI wire      -> :provider-openai-compatible
OpenAiHttpTransport    -> :provider-openai-compatible   仅 contract
JDK HTTP 实现           -> :provider-openai-compatible-jdk   java.net.http
Android HTTP 实现       -> :app   AndroidOpenAiHttpTransport / HttpsURLConnection
```

当前状态：

```text
ModelProvider contract
= IMPLEMENTED / FROZEN  in :ag-core

OpenAI-compatible concrete Provider
= IMPLEMENTED / VERIFIED / FROZEN  in :provider-openai-compatible

Other concrete Providers
= NOT IMPLEMENTED
```

## 8. Dependency direction

```text
ALLOWED
  AGA ---------→ Agent Runtime (:ag-runtime)
  AGC ---------→ Agent Runtime (:ag-runtime)
  Agent Runtime → AG Core
  AG Core ----→ Tool contract（AG Core 定义，AG Core 内部持有）
  AG Core ----→ ModelProvider contract（AG Core 定义，AG Core 内部持有）
  Concrete Android Tool → Android APIs              (可选)
  Concrete Android Tool → AndroidTerminal
                            execution boundary      (可选；机制 NOT FROZEN)
  AT Core ----→ adbd / Android

FORBIDDEN
  AG Core       -X-> AT implementation
  Agent Runtime -X-> AT implementation
  AGA           -X-> AT implementation
  AG Core       -X-> Android framework
  AG Core       -X-> ADB / JNI
  AG Core       -X-> 具体 Tool 实现
  AG Core       -X-> 具体 ModelProvider 实现
  AT Core       -X-> AG Core
```

> `:app` 的 Composition Root **确实**构造 `AgentCore`、`AgentRuntime` 与 Provider，
> 这属于 module 依赖，成立。上面禁止的是把 **AT 实现**引入这些层。
> `Concrete Android Tool → AndroidTerminal execution boundary` 是**可能的未来机制**，
> **尚未冻结**，不得当作已确定的 direct code dependency。

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
            Agent Runtime
                  |
                  v
               AG Core
                  |
                  v
            Tool Registry
                  |
                  v
        Concrete Android Tool
           /              \
          v                v
   Android APIs     AndroidTerminal
                    execution boundary
                           |
                           v
                        Android
```

```text
AGC
 |
 v
Agent Runtime
 |
 v
AG Core
```

> 上述两张图是 **runtime call flow**，**不是 code dependency graph**。
> `AndroidTerminal integration mechanism` **NOT FROZEN**。

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
已注册的 tool 实例。下面那张是**编译期依赖**——方向相反，实现依赖 contract，
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
AG.BASELINE-0   独立开发工程初始化与基线冻结            DONE / FROZEN
   |
   v
AG.0            AG Core v0 架构与 Agent Loop          DONE / FROZEN  (tag ag-0)
   |
   v
AG.PROVIDER-0   真实验证的 OpenAI-compatible Provider  DONE / FROZEN  (tag ag-provider-0)
   |
   v
AGA-0           DONE / FROZEN  (tag ag-aga-0)
   ├─ Android Host（:app）
   ├─ Android-compatible Provider Transport（HttpsURLConnection）
   └─ Agent Runtime 与 UI 物理分离（:ag-runtime）
   |
   v
NEXT            Android Tool / AT Integration Architecture Design
                 （设计阶段，尚未开始实现）
```

本工程当前存在的 module：

```text
:ag-core                       已实现 / 已冻结
:ag-runtime                    已实现（Host-neutral Agent lifecycle）
:provider-openai-compatible    已实现 / 已冻结
:provider-openai-compatible-jdk  已实现（JDK transport）
:app                           已实现（AGA Host / frontend）
```

以下均**不在**本工程内，且未被预建：

```text
:agc           :android-tools   :at-core   :skills
:provider-openai-compatible-android   （等第二个 Android consumer）
```

依赖决策：

```text
:ag-core  生产依赖   无（仅 Kotlin 标准库）
          测试依赖   kotlinx-coroutines-core（用于从 JVM 测试驱动 suspend 调用）

:provider-openai-compatible
          允许       project(:ag-core)
                     kotlinx-serialization-json
                     kotlinx-coroutines-core
          禁止       JDK java.net.http（已移至 :provider-openai-compatible-jdk）
                     Android / AT Core / ADB / JNI / ShellTool
```

依赖按模块区分，不做 repository-wide 断言：

```text
:ag-core
  生产  无（仅 Kotlin 标准库）
  测试  kotlinx-coroutines-core（从 JVM 测试驱动 suspend 调用）
  不引入 JSON / serialization、HTTP client、DI、logging 或 reactive framework。

:ag-runtime
  生产  project(:ag-core) + kotlin-stdlib
  不引入 Android / AndroidX / provider 实现 / HTTP / JSON

:provider-openai-compatible
  生产  project(:ag-core)
        kotlinx-serialization-json
        kotlinx-coroutines-core
        （仅 OpenAiHttpTransport **contract**，不含任何平台 HTTP 实现）
  java.net.http = NO
  Android       = NO
  禁止          Android / AT Core / ADB / JNI / ShellTool

:provider-openai-compatible-jdk
  生产  project(:provider-openai-compatible)
        JDK java.net.http（唯一的 JDK HTTP 实现）
  禁止  依赖 :app / Android

:app
  生产  project(:ag-core) + project(:ag-runtime)
        project(:provider-openai-compatible)
        AndroidOpenAiHttpTransport（基于 HttpsURLConnection）
  禁止  依赖 :provider-openai-compatible-jdk
```

除上述各模块的真实依赖外，当前未引入：
其它 HTTP client（OkHttp / Retrofit 等）· DI framework · logging framework · reactive framework。