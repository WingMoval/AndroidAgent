# 步骤 AGA-0 — Minimal Android Chat Host Vertical Slice

## 1. Metadata

| 项 | 值 |
|---|---|
| 步骤 | `AGA-0`（Android Host 首个垂直切片）+ `AGA-0.R1`（transport 边界修正） |
| 工程 | AndroidAgent |
| 根目录 | `D:\codebank\AndCode\AndroidAgent` |
| 基线 | `2b30212`（tag `ag-provider-0`） |
| 结果 | 见 §19 阶段状态演进；AGA 阶段冻结点 = tag `ag-aga-0` |
| Commit / Push / Tag | 冻结 commit 见 §19；**未 push**；`ag-provider-0` 未移动 |

## 2. Baseline

进入时工作树**不是 CLEAN**，包含上一阶段已报告的 AGA-0 工作：

```text
M build.gradle.kts      （+ kotlin("android") / com.android.application）
M gradle.properties    （+ android.useAndroidX / nonTransitiveRClass）
M settings.gradle.kts  （+ google()、+ include(":app")）
?? app/                （AGA 模块 UI / Host）
```

均已确认保留，无未知改动。

## 3. AGA-0 初次 Android 不兼容证据（决定性）

创建 `:app` 后首次构建失败：

```text
> Task :app:compileDebugKotlin FAILED
e: app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt:34:28
   Cannot access class 'java.net.http.HttpClient'.
   Check your module classpath for missing or conflicting dependencies.
```

独立佐证 —— 直接检查 `C:\android\platforms\android-34\android.jar`：

```text
java/net/http/* 条目总数            0
java/net/http/HttpClient.class     ABSENT
对照（证明 jar 有效）：
  java/net/URL.class               present
  java/net/HttpURLConnection.class present
  java/util/concurrent/CompletableFuture.class present
```

**结论**：Provider 冻结时使用的 JDK 11 HTTP 客户端在 Android 上不存在
（AOSP 于 API 35 才引入），compileSdk 34 完全不可用。desugaring 不提供该包。

据此触发 §8 停止规则，**未**改用 OkHttp / HttpURLConnection、未复制 Provider、
未创建 AndroidProvider / EMGProvider、未改 ChatCompletionWire、未抽象
UniversalTransport。输出 `AGA-0_BLOCKED_PROVIDER_ANDROID_COMPAT`。

## 4. 批准的 transport 边界修正

真实需求驱动：JVM consumer（测试 / live / 未来 CLI）+ Android consumer（AGA）。

```text
OpenAiCompatibleProvider
        │  owns OpenAI wire / config / error semantics
        ▼
OpenAiHttpTransport          ← Provider integration boundary（非 AG Core contract）
      /                \
JDK transport            Android transport
JdkOpenAiHttpTransport    AndroidOpenAiHttpTransport
(java.net.http)           (HttpsURLConnection)
```

### 4.1 契约（最小）

```kotlin
interface OpenAiHttpTransport {
    suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse
}
data class OpenAiHttpRequest(endpoint, headers, body, timeout)
data class OpenAiHttpResponse(statusCode, body)
class OpenAiTransportNetworkException / OpenAiTransportTimeoutException
```

无 GET/PUT/DELETE、无 multipart、无 streaming/SSE、无 cookie、无 interceptor、
无 retry、无 proxy 配置 —— 均无 consumer。

### 4.2 职责边界

```text
Transport 只区分：network failure / timeout / cancellation
Transport 不解释：401 / 429 / 500 / choices / tool_calls
Provider 保持：HttpStatus / Network / Timeout / Parse / Protocol / InvalidToolCall
```

非 2xx 由 transport 原样返回 status + body（含 error body），Provider 继续映射。

### 4.3 错误与取消语义保持

```text
TransportTimeoutException  -> OpenAiProviderException.Timeout   （冻结语义不变）
TransportNetworkException -> OpenAiProviderException.Network  （冻结语义不变）
CancellationException     -> 原样传播，绝不包装为 provider failure
```

## 5. Module 结构

```text
:ag-core                        纯 JVM，stdlib only，未变
:provider-openai-compatible      Provider + wire + config + transport contract（平台中立）
:provider-openai-compatible-jdk  JdkOpenAiHttpTransport（java.net.http）
:app                             AGA Host + AndroidOpenAiHttpTransport
```

依赖方向（已核验）：

```text
:app -> :provider-openai-compatible -> :ag-core
:provider-openai-compatible-jdk   -> :provider-openai-compatible -> :ag-core
:ag-core -X-> 任何 provider 实现 / Android / :app
:app  -X->  :provider-openai-compatible-jdk   ← 已核验：app/build.gradle.kts 无此依赖
```

Android transport 暂放 `:app`（`com.androidagent.app.provider`），因为当前只有
一个 Android consumer；出现第二个真实 consumer 时再抽 module。

## 6. Provider 变更（仅 transport 边界）

```text
wire JSON 语义     UNCHANGED
ModelTurn 语义     UNCHANGED
config 字段        UNCHANGED（baseUrl/apiKey/model/requestTimeout）
错误语义           PRESERVED（映射关系不变）
取消语义           PRESERVED

java.net.http.*  : 从 provider production 完全移出（精确扫描 = 0）
java.net.URI     : 保留（仅用于 safeEndpoint 脱敏，API 1 可用）
java.time.Duration : 保留（API 26 可用）
```

`OpenAiCompatibleProvider` **不再有默认 transport**，必须由 composition root 显式注入
—— 否则 Android 会被 JDK 实现再次污染。

## 7. Composition Root（AGA）

`ChatCompositionRoot`：

```text
OpenAiCompatibleConfig(UI 输入)
  -> AndroidOpenAiHttpTransport()
  -> OpenAiCompatibleProvider(config, transport)
  -> ToolRegistry()        空 registry，tools = empty（零 concrete tool）
  -> AgentCore(provider, registry)
  -> Session 经 core.session(id) 获取（Session 构造为 internal）
```

per-Session 单 Run：host `AtomicBoolean` + AG Core `SessionBusy` 双重守卫。
host **绝不**通过偷偷新建 Session 绕过 busy。

## 8. Provider 配置与凭据策略

```text
Base URL  预填 https://api.emg.dpdns.org/v1
Model     预填 qwen3.8-local
Timeout   预填 120
API Key   运行期手工输入（password），仅存于 app process memory
```

Apply 后清空输入框。**未**使用 SharedPreferences / DataStore / SQLite / 文件 /
BuildConfig / resource / Keystore。Android APK **不**读取 repository 的
`provider.local.json`（后者仍为 JVM/development bootstrap）。

## 9. UI

Android Views（无 Compose / Navigation / DI / Room / 第三方 HTTP 库）：
Provider Status、transcript、message 输入、Send、New Session。
Send / New Session 在 Run 期间禁用。错误渲染只输出安全文本
（failure 类型名 / step 数 / busy），**不含** API Key、Authorization 或 raw config。

## 10. Tests

```text
AG Core                       41 / 41   未变
Provider core (config)        22 / 22
JDK transport (protocol + V1-V4) 24 / 24
Provider 场景合计             46        ← 与 transport 拆分前完全一致，无复制、无丢失
```

测试按 §19 重新分布：纯 HTTP fixture 场景（status / parse / timeout /
cancellation / header secret / vertical slice / live）迁至 JDK module 并使用
真实 `JdkOpenAiHttpTransport`；纯配置场景留在 provider core。**无重复、无删除。**

`:app` 未新增 automated test（instrumented test 需设备与额外依赖，属后续范围）。

## 11. JDK Live（经提取后的 transport 真实回归）

```text
L1  PASS       FinalAnswer, contentLength=15
L2  PASS       outcome=Answer, toolExecutions=1, argumentsLength=2
L3  PASS       outcome=Answer, toolExecutions=1, arguments={}
L4  PASS       DIRECTLY OBSERVED AND ASSERTED   alpha=1 beta=1
L5  PASS       TRUE SAME-TURN MIXED OBSERVED   contentLength=61 calls=1
L6  PASS       PARTIAL_HISTORY_ACCEPTED
```

证明 transport 提取未改变真实行为（请求直接走 EMG / qwen3.8-local）。

## 12. Android Build

```text
:app:compileDebugKotlin   BUILD SUCCESSFUL      ← 原阻断点已解除
:app:assembleDebug        BUILD SUCCESSFUL
APK  app\build\outputs\apk\debug\app-debug.apk   3,566,255 B
minSdk 26 / compileSdk 34 / targetSdk 34（未提高到 35）
```

## 13. Device（设备在本轮出现）

```text
adb devices : APLMUT4312005935  ALI-AN00  authorized
install     : Success
launch      : com.androidagent.app/.ChatActivity  启动成功
process     : com.androidagent.app 存活（无 FATAL / AndroidRuntime）
UI 实测渲染 : "AndroidAgent"
               "Enter the API key, tap Apply, then send a message."
               baseUrl = https://api.emg.dpdns.org/v1
               model   = qwen3.8-local
               "API Key (memory only)"
               timeout = 120
               APPLY / SEND / NEW SESSION / Message 输入框 全部存在
```

## 14. Real Conversation — REAL_ANDROID_CHAT = PASS / STRICT_REAL_MULTI_TURN_MEMORY = PENDING

### 14.1 状态（依据已取得的真实证据）

`	ext
REAL_ANDROID_CHAT               = PASS
STRICT_REAL_MULTI_TURN_MEMORY  = PENDING
`

实际完成的真实对话（设备 uiautomator 取证）：

```text
You: 你好
Agent: 你好！很高兴见到你。请问有什么我可以帮你的吗？
You: 你是？
Agent: 我是通义千问（Qwen），由阿里巴巴集团通义实验室…支持多语言，并且还能进行
      多模态理解…
status: Ready.
```

模型自述**通义千问（Qwen）**，与配置的 `qwen3.8-local` 一致 -> 证明真实到达该模型。

但这两轮**不构成记忆证明**（第二轮不依赖第一轮内容）。
建议的 `AGAZERO42` 跨轮记忆测试因 EMG 正在执行其他任务、为避免互相干扰而**主动跳过**，未伪造结果。


## 15. Deferred

```text
设备端真实两轮对话（待用户输入 Key）
instrumented / UI automated tests
持久化 Settings、secure credential storage
ShellTool / AT Core 集成
Screenshot / UI hierarchy / Tap / Swipe / Input 等工具
Skills / MCP / RAG / persistence / multi-agent
:provider-openai-compatible-android 独立 module（等第二个 Android consumer）
```

## 16. Verdict

```text
AGA-0.R1_DEVICE_CHAT_PASS_MEMORY_PENDING
```

（Android build / install / launch / 真实对话已 PASS；严格跨轮记忆待 EMG 空闲后补做。）

Android build 与 device 启动已真实通过；真实多轮对话待用户在设备输入 Key。

---

# AGA-0.ARCH — Agent Runtime / AGA UI Boundary Physical Separation

## 17.1 问题来源

AGA-0 把可脱离 UI 独立运行/测试的 Host 逻辑放进了 `:app`：

```text
ChatCompositionRoot（位于 :app）当时持有：
  currentSessionId        Session 身份
  AtomicBoolean runActive  busy guard
  send()                  驱动 AgentCore.run
  newSession()            切换 Session
  newSessionId()          Session id 生成
```

后果：**测试 Host / Agent 行为必须依赖 `:app` → 依赖 Android 编译 → 依赖 UI**。
与 AndroidTerminal 早期「执行核心与 APK/UI 未充分分离」同类。

这与 AndroidTerminal 早期问题的相似性，正是本步骤存在的理由。

## 17.2 责任分类（基于抽离前真实源码）

```text
UI 拥有    : 输入、渲染、platform lifecycle、config 输入、transcript 展示
Host 拥有  : Session 身份、busy guard、send()、newSession()   ← 应移出 UI
Core 拥有  : Agent Loop、Session/Run 语义、history、Tool Registry、Events、RunOutcome
Provider 拥有 : wire mapping、config、transport 构造、HTTP
```

## 17.3 四层边界（冻结）

| 层 | 模块 | 职责 |
|---|---|---|
| AG Core | `:ag-core` | Agent Loop、Session/Run 语义、History、ModelProvider/Tool contract、Registry、Policy、Events、RunOutcome |
| Agent Runtime | `:ag-runtime` | Host-neutral 的可运行 Agent 生命周期：当前 Session 选择 + 单 Run 串行化 |
| Composition Root | `:app`（Android）/ 未来 JVM host | 选择并装配具体实现 |
| Frontend | `:app`（AGA）；未来 AGC | 输入、显示、平台 lifecycle、配置输入 |

**AgentRuntime 不是第二个智能核心**：它不持有 history、不实现 Agent Loop、
不实现 Tool Registry、不重定义 ModelTurn / RunFailure、不自己执行 provider loop。
history 的 SSOT 永远是 AG Core Session。

## 17.4 Module

```text
:ag-runtime        package com.androidagent.runtime
依赖（production）  project(":ag-core") + kotlin-stdlib   仅此两项
```

production import 面（穷举 6 行）：

```text
com.androidagent.core.AgentCore / RunFailure / RunOptions / RunOutcome
java.util.UUID
java.util.concurrent.atomic.AtomicBoolean
```

依赖审计：Android/AndroidX/HttpURLConnection/java.net.http/JSON/serialization/
OpenAI/EMG/Lifecycle/Compose/KeyStore/SharedPreferences/DataStore **全部 0**。

`runtimeClasspath` = `kotlin-stdlib` + `project :ag-core`，无 coroutines 生产依赖
（`suspend` 是 Kotlin 语言特性）。

## 17.5 AgentRuntime Contract

```kotlin
val currentSessionId: String
val isBusy: Boolean
suspend fun send(userInput: String): RunOutcome     // 直接返回 AG Core 的 RunOutcome
fun newSession(): Boolean                            // busy 时返回 false
```

**未新造结果类型**：`send` 直接返回冻结的 `RunOutcome`；busy 复用
`RunFailure.SessionBusy`。没有 `RuntimeAnswer` / `RuntimeOutcome`。

## 17.6 Session Ownership（首次冻结）

```text
AG Core       owns Session 语义
AgentRuntime  owns「当前前端正在使用哪个 Session」
AGA           不再自己管理 currentSessionId
```

`newSession()`：生成新 id、后续 send 用新 Session、旧 Session **不被删除**（只在
AG Core 内留存），**不做** persistence / list / rename / delete。

## 17.7 Busy 语义

- Runtime 保留 host-level `isBusy` guard，使 AGA 与未来 AGC 都无需各自复制 busy 逻辑
- **AG Core 的 SessionBusy 仍是最终语义保护**，Runtime guard 不是替代
- 并发第二次 send：直接返回 `SessionBusy`，**不新建 Session、不并发进入同一 Session**
- `newSession()` 在 busy 时被**拒绝**（返回 false，Session id 不变）——不允许用换 Session 绕过 active Run

## 17.8 Cancellation / Failure

- `send()` 用 `try/finally` 释放 busy：**取消、失败、成功三条路径都释放**
- `CancellationException` 原样传播，不包装
- `RunOutcome.Failure` 原样返回给前端，由前端决定渲染文案

## 17.9 代码迁移

| 从 | 移出到 |
|---|---|
| `ChatCompositionRoot.currentSessionId` | `AgentRuntime.currentSessionId` |
| `ChatCompositionRoot.isRunActive` / `AtomicBoolean` | `AgentRuntime.isBusy` |
| `ChatCompositionRoot.send()` | `AgentRuntime.send()` |
| `ChatCompositionRoot.newSession()` / `newSessionId()` | `AgentRuntime.newSession()` / `sessionIdFactory` |
| `ChatActivity` 直接操作 root 的调用 | 改为 `runtime.send() / newSession() / isBusy` |

留在 `:app`（有意）：

```text
ChatCompositionRoot   仅做装配（provider + registry + core + runtime）
AndroidOpenAiHttpTransport   Android 平台实现（§22 不抽独立 module）
ChatActivity                输入 / 渲染 / platform lifecycle
```

`ChatActivity` 现已不再触碰 `AgentCore.session(...)`、`AgentCore.run(...)`、
Session id 生成或 busy guard。

## 17.10 Runtime JVM Tests（本步骤核心意义）

`:ag-runtime:test` **完全脱离** Android SDK / Activity / View / EMG / HTTP /
真实 Provider，全部使用 test source set 内的 fake provider。

```text
R1  basic send -> RunOutcome.Answer                          PASS
R2  同一 Session 连续两次 send，第二次 history 含第一轮       PASS
R3  newSession 换 id 且不继承旧 history                        PASS
R4  并发第二次 send 被拒，不新建 Session、不触达 provider      PASS
R5  busy 时 newSession 被拒，Session id 不变                   PASS
R6  失败后 busy 释放，runtime 仍可用                          PASS
R7  取消传播且 busy 释放，runtime 仍可用                       PASS
R8  两个 runtime 实例 session / busy / history 互不干扰        PASS

executed total : 8 / 8
```

全部为 block-body `Unit` 测试，遵守已冻结的 test hygiene 规则。

> R2 断言修正记录：初版期望值写成 `[U:first, M:...:0]`，实测为
> `[U:first, M:...:0, U:second]`。这是 **AG Core 的正确行为**——它在调用
> provider **之前**就追加了本轮 UserMessage。断言已按真实语义更正，未放宽。

## 17.11 回归与构建

```text
:ag-core                       41 / 41   ZERO production + test change
:ag-runtime                     8 / 8    新增
:provider-openai-compatible     22 / 22   （上一轮起未变）
:provider-openai-compatible-jdk 24 / 24   （上一轮起未变）
Provider 保留场景               46       语义覆盖不变

:app:compileDebugKotlin   BUILD SUCCESSFUL
:app:assembleDebug        BUILD SUCCESSFUL
APK  app-debug.apk  3,569,487 B
```

`:ag-core/**` 与 provider production 在本步骤 **ZERO CHANGE**。

## 17.12 本步骤未执行 EMG

按 §1，**未执行** `:provider-openai-compatible-jdk:liveTest`，**未**从 APK 发任何
真实请求，**未**点击 Send。全部验证为 JVM deterministic + offline + Android compile/build。

```text
JVM deterministic Runtime/Core multi-turn plumbing = PASS
REAL EMG MULTI-TURN MEMORY                         = PENDING
```

设备在本轮离线，§40 的可选 launch 验证跳过（不阻塞）。

## 17.13 Verdict

```text
AGA-0.ARCH_RUNTIME_SEPARATION_PASS
```
