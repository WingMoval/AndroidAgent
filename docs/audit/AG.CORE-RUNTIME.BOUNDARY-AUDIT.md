# AG.CORE-RUNTIME.BOUNDARY-AUDIT — Core / Runtime / Integration / Host 职责边界审计

> 基线锚点：`ag-stable-1` / `401a365f43a38c82966acc88599b7b789ef362aa`（95/95 PASS，:app:assembleDebug PASS）
> 类型：SOURCE AUDIT / DESIGN & CLASSIFICATION ONLY。production code change = 0。
> 原则：**semantic responsibility determines ownership**，不是 module name determines ownership；
> 且不预设「需要重构」—— 允许且如实报告「当前边界已经正确」。

---

## 1. Git Preflight

```text
branch          : main
HEAD            : 401a365f43a38c82966acc88599b7b789ef362aa
HEAD == ag-stable-1 : YES（锚点未动）
status          : CLEAN（无外部并发变更、无未提交用户文件）
remote          : none
tags            : ag-0, ag-aga-0, ag-baseline-0, ag-provider-0, ag-stable-0, ag-stable-1
ag-stable-1     : 不移动、不删除、不覆盖 —— baseline rollback anchor 保持完整
```

## 2. Module Inventory（实测）

```text
:ag-core                          kotlin("jvm")  jvmTarget 17  无 Android/ADB/JNI/序列化/DI/HTTP 依赖
:ag-runtime                       kotlin("jvm")  jvmTarget 17  同上
:provider-openai-compatible       kotlin("jvm")  + kotlinx-serialization-json + coroutines
:provider-openai-compatible-jdk   kotlin("jvm")  + coroutines（java.net.http）
:app                              com.android.application  compileSdk 34 / minSdk 26 / target 34
production .kt 文件总数 = 22，全部逐一读完
```

## 3. Current Dependency Graph（源码实测）

```text
:app ──implementation──▶ :ag-core
:app ──implementation──▶ :ag-runtime
:app ──implementation──▶ :provider-openai-compatible
:app ──(未依赖)───────── :provider-openai-compatible-jdk      ← 正确：java.net.http 在 Android 不可用
:ag-runtime ───────────▶ :ag-core
:provider-openai-compatible ─▶ :ag-core
:provider-jdk ───────────▶ :provider-openai-compatible
:ag-core ──────────────▶ （无）
```

```text
CURRENT
                    ┌──────────────────────────────┐
                    │  :app (AGA, Android Host)    │
                    │  ChatActivity / Composition  │
                    └───┬──────────┬──────────┬────┘
                         │          │          │
                    ┌────▼───┐ ┌────▼────┐ ┌───▼────────────────────┐
                    │:ag-runtime│ :ag-core │ :provider-openai-compatible │
                    └────┬─────┘ └───▲────┘ └───▲────────────────────┘
                         │           │           │
                         └───────────┴────┬──────┘
                              :ag-runtime ─▶ :ag-core
                              provider ─▶ :ag-core（实现 ModelProvider）

TARGET LOGICAL BOUNDARY
   Host(AGA / 未来 AGCLI / JVM headless)
     │  UI · 用户输入 · 平台生命周期 · 配置/凭据输入 · Composition
     ▼
   AG Engine = :ag-runtime + :ag-core
     ├─ Agent Runtime   current Session 选择 · 一次一 Run · host 门面
     └─ AG Core         Agent Loop · Session/Run 语义 · 契约 · 事件 · 结果

   侧向 Integration（实现 Core 契约，非纵向链）：
     OpenAiCompatibleProvider ─实现→ ModelProvider
     JdkOpenAiHttpTransport   ─实现→ OpenAiHttpTransport（平台集成）
     AndroidOpenAiHttpTransport ─实现→ OpenAiHttpTransport（平台集成）
     （未来）AndroidShellTool ─实现→ Tool ─▶ AT Runtime ─▶ AT Core

   Host Services（概念归属，当前未抽 module）：
     Configuration / Credential / Persistence / Logging / Composition
     当前全部位于 :app（Host）内部 + JVM 开发侧（provider 模块内的 dev 配置读取）
```

**结论：CURRENT 物理依赖图与 TARGET 逻辑边界一致。**
`NO PHYSICAL RESTRUCTURE REQUIRED`。

---

## 4. AG Core Audit（逐 symbol，源码为准）

| Symbol | 位置 | 职责（源码实测） | 目标归属 | Verdict | Move? | 理由 |
|---|---|---|---|---|---|---|
| `AgentCore` + Agent Loop | :ag-core | 驱动 Model→Tool→ToolResult→History→Final；step 预算；事件发射；防御性终结事件 | AG Core | KEEP_IN_CORE | NO | 纯 Agent 语义内核。检查泄漏：无 Host lifecycle / UI / configuration / platform / HTTP / Android（imports 仅 `java.util.concurrent.*`） |
| `RunOptions` | :ag-core | instructions / maxSteps / approvalPolicy / eventSink | AG Core | KEEP_IN_CORE | NO | maxSteps 定义 loop 模型轮数预算（AgentCore.kt:124），是 Run 语义而非运行管理；policy/sink 是 loop 的语义参数 |
| `Session`（含 session map） | :ag-core | history 存储、单 active Run 独占（tryBeginRun/endRun）、消息追加 | AG Core | KEEP_IN_CORE | NO | 「Session 是什么 + 哪些 Session 存在 + 其状态」= 定义层。独占标志放 Session 自身 monitor，避免全局锁（Session.kt:16-18 注释） |
| `ModelProvider` / `ModelRequest` / `ModelTurn` | :ag-core | 模型边界契约（provider-neutral） | AG Core | KEEP_IN_CORE | NO | 契约归 Core，实现全部在外部（依赖倒置） |
| `Message`（User/Model/ToolResult） | :ag-core | 历史条目语义 | AG Core | KEEP_IN_CORE | NO | 有意不镜像任何 vendor schema |
| `Tool` / `ToolSpec` / `ToolCall` / `ToolResult` | :ag-core | 工具契约 + 结果语义（success=机制标志） | AG Core | KEEP_IN_CORE | NO | 契约归 Core；具体 Tool 实现（未来 ShellTool）在 Integration |
| `ToolRegistry` | :ag-core | 注册（组合期）+ dispatch：policy→lookup→execute，全路径返回结构化 ToolResult | AG Core | KEEP_IN_CORE | NO | 「模型请求如何变成 ToolResult」是 loop 的组成部分；无工具执行协议、无具体工具 |
| `ToolApproval` / `ToolApprovalPolicy` | :ag-core | 允许/拒绝语义 + 未来人工审批接缝 | AG Core | KEEP_IN_CORE | NO | 策略语义归 Core；policy 由宿主在组合期注入 |
| `AgentEvent` / `AgentEventSink` | :ag-core | Run 可观察生命周期（同步回调） | AG Core | KEEP_IN_CORE | NO | 观察契约归 Core；Core 不知道 UI，sink 实现全在宿主侧 |
| `RunOutcome` / `RunFailure`（4 种） | :ag-core | Run 结局语义 | AG Core | KEEP_IN_CORE | NO | 只有「结束 Run 本身」的条件才在此；工具失败不进这里 |
| `AgCoreMarker` | :ag-core | 包名/基线回归标记 | AG Core | KEEP_IN_CORE | NO | 无行为，纯标记 |

**Q1 证据**：Core 全部 production imports 为 `java.util.concurrent.*`（Core）与零 import（其余）。
对 22 个文件的 `android|adb|com.androidterminal` 扫描：命中全部是包名 `com.androidagent.*` 与 KDoc 文字，**零平台引用**。

## 5. Agent Runtime Audit（§12 逐问）

```text
Runtime 是否只是调用 Core？        是。send() 唯一实质动作 = agentCore.run(...)（AgentRuntime.kt:63）
有没有第二套 Agent Loop？           无（全文件 83 行，无任何 while/turn/tool 逻辑）
有没有第二套 History？              无（History SSOT = Core Session，KDoc 明示 L15）
有没有 Tool dispatch？              无（不引用 ToolRegistry）
有没有 Model parsing？              无（不引用 ModelProvider/ModelRequest/JSON）
有没有重复 Run semantics？          无（RunOptions 只是持有并透传；RunOutcome 原样返回 L54 注释）
```

| Symbol | 位置 | 职责 | Verdict | Move? |
|---|---|---|---|---|
| `AgentRuntime` 构造（agentCore, runOptions, sessionIdFactory） | :ag-runtime | 持有 Core 实例 + 宿主默认 Run 配置 + 会话 ID 工厂 | KEEP_IN_RUNTIME | NO |
| `currentSessionId` | :ag-runtime | 宿主当前使用哪个 Session | KEEP_IN_RUNTIME | NO |
| `isBusy`（AtomicBoolean） | :ag-runtime | 宿主级「一次一 send」守卫 | KEEP_IN_RUNTIME | NO |
| `send()` | :ag-runtime | 宿主门面 → Core.run；busy 拒绝；finally 释放 | KEEP_IN_RUNTIME | NO |
| `newSession()` | :ag-runtime | 切换当前 Session（busy 时拒绝）；旧 Session 保留在 Core | KEEP_IN_RUNTIME | NO |

**`RUNTIME IS THIN BY DESIGN`** —— 这是优点，不是缺陷。不扩功能。

## 6. Session / Run 边界（§9/§10 重点判定）

**当前实现已经体现「定义 Session vs 管理当前 Session」的分离：**

```text
Session 语义（定义层）                → :ag-core
  history / active-Run exclusivity /
  message append / Run 状态规则

Session 选择/生命周期（管理层）        → :ag-runtime
  currentSessionId / newSession() 时机 /
  当前 session id / 宿主串行守卫
```

- `AgentCore.session(id)` 按需创建并保有 session 集合（Core 的「哪些 session 存在」）；
- `AgentRuntime` 只持有一个 current session id 字符串（宿主的「现在用哪个」）。
- 两层无重叠、无第二套状态。

**判定：NO CHANGE。该边界已正确实现。**

Run/RunOptions 同理：`maxSteps` 留在 Core（定义 loop 预算，AgentCore.kt:124），
Runtime 不重定义任何 Run 语义。未落入「名字含 Run → 全归 Runtime」的错误规则。

## 7. Busy 双重语义（§11）

```text
Core 层:  Session.tryBeginRun 失败 → RunFailure.SessionBusy(sessionId)
          语义 = 该 Session 已有 active Run（per-Session 独占，最终保护）
          位置 = AgentCore.kt:94-97

Runtime 层: runActive.compareAndSet 失败 → 返回 RunOutcome.Failure(RunFailure.SessionBusy(sessionId))
          语义 = 该 runtime 实例正有一次 send 在途（per-Host 便利守卫）
          位置 = AgentRuntime.kt:59-61
```

两者是**不同层次**的守卫：Core 的是语义独占（即使绕过 Runtime 直接调 Core 也成立），
Runtime 的是宿主便利（AGA/AGC 免费获得「一次一 send」）。
Runtime 复用 `RunFailure.SessionBusy` 类型而非自造结果类型，是务实选择且 KDoc 已注明
（AgentRuntime.kt:54）——当前 Runtime 只持有一个 current session，两个守卫语义重合、方向一致。

```text
判定：BOTH ARE VALID. NO DUPLICATION. 不去重。
```

## 8. `:app → :ag-core` 直接依赖判定（§13）

调用链实测：

```text
ChatActivity.onSend()
  → runtime.send(text)            [只调 AgentRuntime]
  → outcome: RunOutcome（Core 值类型，用于渲染解释）
ChatCompositionRoot（组合根）
  → ToolRegistry()                [构造，Core 契约对象]
  → OpenAiCompatibleProvider(config, AndroidOpenAiHttpTransport())   [Integration]
  → AgentCore(provider, toolRegistry)   [构造]
  → AgentRuntime(agentCore)              [移交]
  → val runtime 暴露给 UI
ChatActivity 其余引用 Core 处：仅 RunOutcome / RunFailure 类型（结果渲染，ChatActivity.kt:109,143）
```

```text
判定：Case A —— VALID COMPOSITION DEPENDENCY. NO ACTION.
AgentCore 类只在组合根被构造并移交；业务运行全部经 AgentRuntime。
RunOutcome/RunFailure 作为结果值类型进入 Host 是必要的（宿主必须知道结果形状才能渲染），
不构成绕过 Runtime 的行为依赖。
不加 Factory / :ag-engine / DI 框架 —— 依赖图已正确，不为「更漂亮」加层。
```

## 9. Provider / Integration 边界（§14，重新源码定位）

重新搜索后的事实（不复制旧报告）：

| 项 | 真实位置 | 调用方 |
|---|---|---|
| `ModelProvider` 契约 | :ag-core（Model.kt:57） | — |
| `OpenAiCompatibleProvider` | :provider-openai-compatible | 仅组合根（:app）与 JVM 测试 |
| `ChatCompletionWire`（internal） | 同上 | 仅 Provider 内部 |
| `OpenAiHttpTransport` 契约 | 同上（平台中立，仅 POST） | :app（Android 实现）、:provider-jdk（JDK 实现） |
| `JdkOpenAiHttpTransport` | :provider-jdk | JVM 宿主/测试 |
| `AndroidOpenAiHttpTransport` | :app（com.androidagent.app.provider） | 仅 AGA 组合根 |
| `OpenAiCompatibleConfig`（数据模型） | :provider-openai-compatible | 同上 + :app 组合/构建 |
| `OpenAiCompatibleConfigLoader`（文件读取+解析） | :provider-openai-compatible | **仅 JVM**：ProviderConfigTest、ProviderConfigResolver；:app 零引用 |
| `openAiConfigFromEnvironment`（env 读取） | 同上（OpenAiCompatibleConfig.kt:74） | **仅 JVM**：LiveApiTest |
| `ProviderConfigResolver`（local.json→env 优先级） | 同上 | **仅 JVM**：LiveApiTest（真实 EMG 验证）+ 自身测试 |
| `config/provider.local.json` | 仓库根，**gitignored 未跟踪**（含真实 key） | 仅上述 JVM 路径读取 |

```text
分类：
  ModelProvider 契约          → AG Core            ✓ 已在
  OpenAI wire / Provider      → Integration        ✓ 已在
  HTTP transport 契约         → Integration        ✓ 已在
  JDK HTTP / Android HTTP     → Platform Integration ✓ 已在
  provider 配置数据模型        → Integration        ✓ 已在
  从文件/环境读配置            → 概念上 Host Services（开发/宿主侧）
```

**观察项（非 MISPLACED）**：`OpenAiCompatibleConfigLoader` / `ProviderConfigResolver` /
`openAiConfigFromEnvironment` 是「JVM 开发宿主侧配置来源」，概念上属于 Host Services
（配置读取），但物理上位于 Integration 模块。事实标记：

- 它们**解析 provider 专属 schema**（`provider.type=openai-compatible`），与 provider 强耦合；
- 唯一真实 consumer 是 JVM 侧（live 验证/测试）；**Android 宿主完全不使用**（:app 从 UI 字段直接构造 `OpenAiCompatibleConfig`）；
- 无重复、无反向依赖、无平台污染（该模块本就是纯 JVM）；
- 路径与环境读取均**可注入**（`resolve(localConfigPath, env)`），宿主不受默认值绑架。

按 §23 判定标准（需「明显违反冻结语义边界 + 真实重复/反向依赖/平台污染/Host 泄漏」四者兼有）：
**不成立**。标记 `SHARED_BUT_NOT_YET_EXTRACT`：未来若抽出 Host Services，
「配置来源解析」是自然迁移对象；当前**不迁移**（见 §13 Complexity Gate）。

## 10. Host Services 审计（§15）

| 概念 | 当前状态 | 位置 | 判定 |
|---|---|---|---|
| Configuration | AGA：UI 字段 + 常量默认值（ChatActivity.kt:158-162）；JVM：local.json/env resolver | :app + provider 模块（dev 侧） | 属 Host/Host Services；单宿主 → 留在 :app，`SHARED_BUT_NOT_YET_EXTRACT` |
| Credential | Key 运行时手输，仅驻留进程内存；Apply 后清空输入框（ChatActivity.kt:85） | :app | Host（AGA）。**API Key -X-> Core，-X-> Runtime：已满足** |
| Persistence | 无任何持久化（零 SharedPreferences/DataStore/文件） | — | 已知限制，非本轮目标；概念归属 Host Services |
| Logging / Trace | 无日志框架；AgentEventSink 是观察接缝，宿主可选消费 | Core 契约 + 宿主实现 | 边界正确：Core 发事件，宿主决定观测 |
| Composition | `ChatCompositionRoot`（:app）：选择实现并装配，仅此而已 | :app | 属 Composition；单宿主 → 留在 :app |

```text
第二个真实 consumer 是否存在？否（只有 AGA；AGCLI 尚不存在）。
→ 不创建 :host-services module。SHARED_BUT_NOT_YET_EXTRACT。
```

## 11. API Key 边界冻结（§16）

```text
API Key 存储/输入
    -X-> AG Core          ✓ 实测：Core 0 处引用任何 key/config
    -X-> Agent Runtime    ✓ 实测：Runtime 0 处引用
    归属 = Host Services / Credential（当前由 AGA Host 承担）
当前未持久化 = KNOWN LIMITATION（ag-stable-1 基线已记录），非本轮修复目标。
未实现任何 SharedPreferences/DataStore/Keystore/CredentialStore。✓
```

## 12. Android Lifecycle 分类（§17）

```text
Activity 重建 / ViewModel / process death / lifecycleScope
    → 全部属于 AGA Host（:app），Android UI lifecycle。
    AgentRuntime 的 "lifecycle" = Agent execution lifecycle（current Session + 单 active Run），
    二者概念不同、物理分离。
    未发现任何 UI lifecycle 逻辑迁入 :ag-runtime。✓
```

## 13. Complexity Gate（§24）—— 对每个「可能的新东西」

| 候选 | 当前真实 consumer 数 | 不加它当前哪里已重复 | 解决哪个当前问题 | 结论 |
|---|---|---|---|---|
| `:ag-engine` module / facade | 1（宿主只经 Runtime 用） | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |
| Factory / DI 框架 | 0（组合根 47 行手工装配） | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |
| `:host-services` module | 1（仅 AGA；JVM dev 侧另计） | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |
| `:provider-openai-compatible-android` | 1（仅 AGA；AndroidOpenAiHttpTransport KDoc 已自证此判断） | 无重复 | 无当前问题 | **DO NOT CREATE NOW**（保留该注释作为未来触发条件） |
| 新 Provider 抽象层 | 1（仅 OpenAI-compatible 一种） | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |
| 新 Session manager | 0（Core+Runtime 已分工清晰） | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |
| 新 Runtime 层级 | 0 | 无重复 | 无当前问题 | **DO NOT CREATE NOW** |

## 14. AGCLI / 多宿主兼容性（§18）

```text
:ag-core   可直接复用？  是 —— 纯 JVM，0 平台依赖，契约齐全（provider/tool/policy/events）
:ag-runtime 可直接复用？ 是 —— 纯 JVM、host-neutral（KDoc 明示为 AGA 和 AGC 两者设计）
AGA-specific 限制在哪？  全部在 :app：ChatActivity（UI）、ChatCompositionRoot（装配）、
                         AndroidOpenAiHttpTransport（平台 transport）
未来 AGCLI 需要什么？     自己的组合根 + 自己的 transport（:provider-jdk 的
                         JdkOpenAiHttpTransport 现成可用）+ 自己的配置来源
                         （ProviderConfigResolver 现成可用）+ 自己的 UI 循环
                         —— 0 行 Core/Runtime 改动
```

```text
结论：架构已支持多宿主。未发现阻碍 AGCLI 的 Core/Runtime 设计。
```

## 15. AG Engine 边界（§19）

```text
AG Engine = Agent Runtime + AG Core  是否成立？  是（逻辑能力边界，不建 module）

AG Engine 是否含 Provider concrete 实现？   否 —— 在 :provider-openai-compatible（Integration）
AG Engine 是否含 API Key storage？          否 —— 在 AGA Host（内存）
AG Engine 是否含 Android UI？               否 —— 在 :app
AG Engine 是否含 ShellTool concrete 实现？  否 —— 不存在（未来归 Integration → AT Runtime）
```

## 16. Integration 侧向性（§20）

实测依赖方向证实 Integration 实现 Core 契约，而非纵向主链：

```text
ModelProvider 契约 ← 实现 ← OpenAiCompatibleProvider（:provider-openai-compatible）
Tool 契约        ← 实现 ← （未来 AndroidShellToolImpl，:app 或独立集成模块）
OpenAiHttpTransport ← 实现 ← Jdk…（:provider-jdk）/ Android…（:app）
```

无 `Host → Integration → Runtime → Core` 纵向链依赖。✓

## 17. AndroidShellTool（§21/§22）

```text
当前 ToolRegistry = 0 concrete tools（ag-stable-1 基线已冻结）。
未来归属判定：Integration；执行链 AG Core Tool 契约 → AndroidShellToolImpl
→ AT Runtime → AT Core（见 AG.SHELL-0 设计文档，未实施）。
本轮未读取后修改 AndroidTerminal、未接 AT、未复制 AAR、未改 Gradle。✓
```

---

## 18. Responsibility Matrix（总表）

| Symbol / 职责 | 当前位置 | 目标归属 | Verdict | Move? | 理由 |
|---|---|---|---|---|---|
| Agent Loop | :ag-core | AG Core | KEEP_IN_CORE | NO | 纯 Agent 语义 |
| RunOptions / maxSteps | :ag-core | AG Core | KEEP_IN_CORE | NO | 定义 loop 预算（Run 语义） |
| Session（history+独占） | :ag-core | AG Core | KEEP_IN_CORE | NO | Session 定义层 |
| currentSessionId | :ag-runtime | Agent Runtime | KEEP_IN_RUNTIME | NO | 当前 Session 选择 |
| newSession() | :ag-runtime | Agent Runtime | KEEP_IN_RUNTIME | NO | Session 生命周期管理 |
| send() | :ag-runtime | Agent Runtime | KEEP_IN_RUNTIME | NO | Host 门面，透传 Core.run |
| isBusy（宿主守卫） | :ag-runtime | Agent Runtime | KEEP_IN_RUNTIME | NO | 与 Core SessionBusy 双层、非重复 |
| ModelProvider/Request/Turn | :ag-core | AG Core | KEEP_IN_CORE | NO | 契约 |
| Tool/Spec/Call/Result/Registry/Policy | :ag-core | AG Core | KEEP_IN_CORE | NO | 契约 + loop 组成部分 |
| AgentEvent/Sink、RunOutcome/RunFailure | :ag-core | AG Core | KEEP_IN_CORE | NO | 语义/结果 |
| OpenAiCompatibleProvider + Wire | :provider-openai-compatible | Integration | KEEP_AS_INTEGRATION | NO | 具体 provider |
| OpenAiHttpTransport 契约 | :provider-openai-compatible | Integration | KEEP_AS_INTEGRATION | NO | provider 集成边界 |
| JdkOpenAiHttpTransport | :provider-jdk | Platform Integration | KEEP_AS_INTEGRATION | NO | JVM 平台 |
| AndroidOpenAiHttpTransport | :app | Platform Integration | KEEP_IN_HOST（暂） | NO | 单 Android consumer（KDoc 已自证） |
| OpenAiCompatibleConfig | :provider-openai-compatible | Integration | KEEP_AS_INTEGRATION | NO | provider 配置数据模型 |
| ConfigLoader/Resolver/fromEnvironment | :provider-openai-compatible | Host Services（概念） | SHARED_BUT_NOT_YET_EXTRACT | NO | 仅 JVM dev 侧消费；provider schema 强耦合；可注入 |
| provider.local.json | 仓库根（gitignored） | Host Services（dev） | — | NO | 真实 key，不入库 |
| 配置 UI + 默认值 | :app | Host | KEEP_IN_HOST | NO | AGA 产品交互 |
| API Key 处理（内存） | :app | Host（Credential） | KEEP_IN_HOST | NO | 不落盘 = 已知限制 |
| ChatActivity / 生命周期 | :app | Host | KEEP_IN_HOST | NO | Android UI lifecycle |
| ChatCompositionRoot | :app | Host（Composition） | KEEP_IN_HOST | NO | 组合根属宿主 |
| 未来 AndroidShellTool | （不存在） | Integration → AT Runtime | （未来） | — | 本轮不实现 |

**MISPLACED 数量 = 0。**

## 19. 12 问题逐项回答（§28）

```text
Q1  AG Core 当前是否已经是纯 Agent Semantic Kernel？
    是。22 文件中 Core 部分 imports 仅 java.util.concurrent.*；
    无 HTTP/Android/ADB/AT/配置/凭据/平台引用；Agent Loop 纯语义。

Q2  Agent Runtime 当前是否已经与 Core 物理分离？
    是。独立 Gradle module，仅实现依赖 :ag-core，无反向。

Q3  Runtime 是否存在重复 Agent Loop / History / Tool dispatch？
    否。83 行全部为 current-session 选择 + 串行守卫 + 透传。无第二套任何东西。

Q4  Session 当前归属是否合理？
    合理。定义层（history/独占/追加）在 Core；选择层（current id/newSession）在 Runtime。
    已实现「定义 Session vs 管理当前 Session」分离。NO CHANGE。

Q5  Run / RunOptions 当前归属是否合理？
    合理。maxSteps 在 Core（loop 预算）；Runtime 仅持有并透传。
    未犯「Run 字样 → 全归 Runtime」错误。

Q6  Core SessionBusy 与 Runtime isBusy 是否语义重复？
    否。per-Session 语义独占（最终保护） vs per-Host 便利守卫（一次一 send）。
    BOTH ARE VALID. NO DUPLICATION。复用同一 RunFailure 类型是务实且已注明的选择。

Q7  :app 直接依赖 :ag-core 是否是问题？
    否（Case A）。AgentCore 仅在组合根构造并移交 Runtime；业务流全经 Runtime；
    进入 UI 的只是 RunOutcome/RunFailure 值类型（渲染必需）。VALID COMPOSITION DEPENDENCY。

Q8  Provider / Resolver / config loading 当前边界是否清晰？
    基本清晰。契约在 Core、实现与 wire 在 Integration、双平台 transport 分离正确。
    一个灰色项：local.json/env 配置读取（Host Services 概念）位于 provider 模块，
    仅 JVM dev 侧消费、可注入、schema 强耦合 → SHARED_BUT_NOT_YET_EXTRACT，不迁移。

Q9  Host Services 是否需要现在物理抽 module？
    否。唯一真实 consumer 是 AGA；无第二 consumer；无当前重复。SHARED_BUT_NOT_YET_EXTRACT。

Q10 AGA-specific code 是否泄漏进 AG Engine？
    否。EMG 默认 URL/model/timeout、Key 处理、UI 全部在 :app；
    Core/Runtime 0 处 AGA/EMG/Android 引用（KDoc 提及除外）。

Q11 当前 :ag-core + :ag-runtime 是否可视为正式 AG Engine 主体？
    是（逻辑能力边界）。不含具体 Provider/Key/UI/ShellTool；
    契约齐备，双宿主（JVM/Android）已验证。

Q12 下一步是否真的需要 production normalization？
    否。PRODUCTION_NORMALIZATION_REQUIRED = NO。
```

## 20. Misplaced Responsibilities（§23 判定结果）

```text
按「明显违反冻结语义边界 + 真实重复/反向依赖/平台污染/Host 泄漏」标准逐条核对：

  反向依赖？          无（依赖图单向：app→runtime/core/provider→core）
  平台污染 Core？     无（Core/Runtime 纯 JVM）
  Runtime 重复 Loop？ 无
  Host 泄漏 Engine？  无（宿主代码全在 :app）
  真实重复？          无（busy 双层为设计；配置读取仅一份）
  多宿主受阻？        无（Q11/Q18 通过）

结论：MISPLACED = 0。无最小迁移项。
```

## 21. Files Changed

```text
新增  docs/audit/AG.CORE-RUNTIME.BOUNDARY-AUDIT.md（本文件，task-owned）
production code / Gradle / 测试 / tag / push   = 0
```

## 22. Final Verdict

```text
AG_CORE_RUNTIME_BOUNDARY_AUDIT_PASS

BOUNDARY_ALREADY_ACCEPTABLE

AG_CORE
= READY_TO_FREEZE_AS_AGENT_SEMANTIC_KERNEL

AG_RUNTIME
= READY_TO_FREEZE_AS_AGENT_EXECUTION_LIFECYCLE_MANAGER

PRODUCTION_NORMALIZATION_REQUIRED
= NO
```

观察项（记录不迁移）：

```text
OBS-1  Provider 模块内的 dev 配置读取（Loader/Resolver/fromEnvironment）
       概念归属 Host Services；未来抽 Host Services 时的自然迁移对象。
OBS-2  AndroidOpenAiHttpTransport 位于 :app；第二个真实 Android consumer
       出现时再抽 :provider-openai-compatible-android（其 KDoc 已自证此判据）。
```
