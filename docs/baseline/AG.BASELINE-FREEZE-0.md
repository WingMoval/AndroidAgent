# AG.BASELINE-FREEZE-0 — 当前稳定基线冻结

> 目的：冻结**当前已经能工作的** AndroidAgent，使其可验证 / 可回退 / 可追踪。
> 原则：**FREEZE WHAT EXISTS —— 冻结已验证工作的实现，而不是先改成理想架构再冻结。**
> 本轮 **production code change = 0**。
> 本轮**不做** Core / Runtime 职责边界审计（见 §12）。

---

## 1. Git Baseline

| 项 | 值 |
|---|---|
| branch | `main` |
| HEAD（冻结前） | `2c8306c312017b9a310ac7fa69df78c611712aa2` |
| remote | **none**（从未 push） |
| tracked production diff | **NONE** |
| 冻结前工作树 | 仅 2 个未跟踪设计文档（见 §13） |
| 既有 tag | `ag-0` / `ag-aga-0` / `ag-baseline-0` / `ag-provider-0` |

既有 tag 指向（**均未移动**）：

```text
ag-0            9d43598  feat: implement AG Core v0 runtime
ag-aga-0        2c8306c  docs: finalize AGA-0 freeze SSOT
ag-baseline-0   231b319  baseline: initialize AndroidAgent project
ag-provider-0   2b30212  docs: close provider freeze SSOT
```

> ⚠ **tag 名冲突**：建议名 `ag-baseline-0` **已被占用**（指向首个工程初始化 commit）。
> 按冻结规则 **不移动、不删除**。本轮改用 **`ag-stable-0`**。

---

## 2. Module Inventory（`gradlew projects` 实测）

```text
Root project 'AndroidAgent'
+--- Project ':ag-core'
+--- Project ':ag-runtime'
+--- Project ':app'
+--- Project ':provider-openai-compatible'
\--- Project ':provider-openai-compatible-jdk'
```

共 **5** 个 module，无其他。

---

## 3. Dependency Facts（真实 Gradle 声明）

```text
:ag-core                      （无 project 依赖）生产依赖 = 无
                              test: coroutines-core, JUnit5
        ▲
        │
:ag-runtime                   implementation(project(":ag-core"))
        ▲                    生产额外依赖 = 无
        │                    test: coroutines-core, JUnit5
        │
:provider-openai-compatible   implementation(project(":ag-core"))
                              + kotlinx-serialization-json
                              + kotlinx-coroutines-core
        ▲
        │
:provider-openai-compatible-jdk
                              implementation(project(":provider-openai-compatible"))
                              + kotlinx-coroutines-core
                              test 额外: kotlinx-serialization-json,
                                          project(":ag-core"), JUnit5

:app                          implementation(project(":ag-core"))
                              implementation(project(":ag-runtime"))
                              implementation(project(":provider-openai-compatible"))
                              + androidx.core-ktx / appcompat /
                                activity-ktx / lifecycle-runtime-ktx
                              插件: com.android.application
```

**已验证事实**：

```text
:app -X-> :provider-openai-compatible-jdk      （未依赖）
:ag-runtime -X-> :app / provider / Android
:ag-core -X-> :ag-runtime / :app
```

---

## 4. AG Core Current Contract

public surface（`ag-core/src/main`）：

```text
AgentCore        class  + RunOptions(data class)
Session          class（constructor internal）
ModelProvider    fun interface   complete(ModelRequest): ModelTurn
ModelRequest     data class      sessionId / instructions / history / tools
ModelTurn        sealed          FinalAnswer(content) | ToolCalls(calls, content)
Message          sealed          UserMessage / ModelMessage / ToolResultMessage
Tool             interface       spec + suspend execute(ToolCall): ToolResult
ToolSpec / ToolCall / ToolResult   data class
ToolRegistry     class
ToolApproval     sealed          Allow | Deny(reason)
ToolApprovalPolicy  fun interface
AgentEvent       sealed          8 个事件类型
AgentEventSink   fun interface
RunOutcome       sealed          Answer | Failure
RunFailure       sealed          ProviderFailure / MaxStepsReached /
                                   SessionBusy / RuntimeFailure
AgCoreMarker     object
```

### Agent Loop 仍然存在（实测行号）

```text
AgentCore.kt:123   while (true)
AgentCore.kt:122   var completedTurns = 0
AgentCore.kt:124   if (completedTurns >= options.maxSteps)
AgentCore.kt:136   history = session.history()          ← 喂回模型
AgentCore.kt:147   completedTurns++
AgentCore.kt:150   is ModelTurn.FinalAnswer ->          ← 模型终止
AgentCore.kt:158   is ModelTurn.ToolCalls ->            ← 无 return，继续循环
AgentCore.kt:172   session.append(ToolResultMessage(...))
```

`Model → Tool → ToolResult → Model → Final` **仍然成立**，未改动。

---

## 5. Agent Runtime Current Contract

```kotlin
class AgentRuntime(agentCore, runOptions, sessionIdFactory)
  val currentSessionId: String
  val isBusy: Boolean
  suspend fun send(userInput: String): RunOutcome     // 内部 agentCore.run(...)
  fun newSession(): Boolean                          // busy 时返回 false
```

依赖方向：**`:ag-runtime → :ag-core`**，无反向。

---

## 6. Provider Current State

```text
:provider-openai-compatible
  OpenAiCompatibleProvider   实现冻结的 ModelProvider
  OpenAiCompatibleConfig     baseUrl / apiKey / model / requestTimeout
  OpenAiCompatibleConfigLoader / ProviderConfigResolver
  OpenAiHttpTransport       平台中立 contract（仅 POST）
  ChatCompletionWire        OpenAI wire mapping
  OpenAiProviderException   Network / HttpStatus / Timeout / Protocol /
                            Parse / InvalidToolCall

:provider-openai-compatible-jdk
  JdkOpenAiHttpTransport    基于 java.net.http
```

Provider core **不使用** `java.net.http`（0 引用），该实现只在 jdk module。

---

## 7. AGA Current State

```text
package / applicationId : com.androidagent.app
versionCode / versionName : 1 / 0.1.0
minSdk / targetSdk / compileSdk : 26 / 34 / 34
label                    : AndroidAgent
Composition Root         : ChatCompositionRoot
Android HTTP transport   : AndroidOpenAiHttpTransport (HttpsURLConnection)
ToolRegistry             : 空 —— 零个 concrete tool 注册
配置 UI                  : Base URL / Model / API Key(password) / Timeout / Apply
聊天 UI                  : transcript + 输入 + Send + New Session
native library           : 无（不为了对称 AT 而制造）
```

### 真实限制（**KNOWN LIMITATION，不是基线失败**）

```text
1. API Key 仅驻留进程内存
   ChatActivity.kt:85  applyConfig() 之后立即 apiKeyField.setText("")
   全仓库无 SharedPreferences / DataStore / 文件写入

2. 配置完全不持久化
   baseUrl / model / timeout / apiKey 全部不保存
   每次冷启动需重新输入并点 Apply

3. Activity 重建即丢失全部状态
   runtime 是普通 Activity 字段 (ChatActivity.kt:40)
   无 ViewModel / 无 onSaveInstanceState
   Manifest 无 android:configChanges
   → 旋转屏幕 / 切换深色模式 / 改字体大小 / 进程被回收，都会重置

4. 无 durable Session
   Session 仅存活于 AgentCore 进程内存

5. ToolRegistry 为空
   零个 Android Tool；无 ShellTool / 无 AT 集成
   → AG.apk 不会执行任何设备命令

6. 跨轮语义记忆未做严格验证
   真实 Android chat 已 PASS（你好 / 你是？ -> 通义千问 Qwen）
   但 AGENT-0 S1–S11 完整验收（含 Two-Environment Contrast）从未执行

7. 无 AGCLI / AGC
```

---

## 8. Test Matrix（真实 JUnit 执行结果）

```text
ag-core                        41 / 41   PASS
ag-runtime                      8 / 8    PASS
provider-openai-compatible      22 / 22   PASS
provider-openai-compatible-jdk  24 / 24   PASS
--------------------------------------------------------
OVERALL                        95 / 95   failed=0 errors=0 skipped=0
```

覆盖确认（来自现有测试，未新增）：

```text
Agent Loop               T1 / T2 / T7（跨 turn 重入）
Session semantics        T10 / T11 / T12 / T12b
ToolResult feedback      T2 / T4 / T13 / T13b
unknown tool             T4
denied tool              T6 / T6b
tool exception           T5
maxSteps                 T7
mixed text + tool calls  T15 / T15b
EventSink defensive      EventSinkContractTest 全 6 项
Session busy / 独占       T11 / T11b
Runtime contract         R1–R8
Provider protocol        P1–P5b / P6–P14b（含 real transport）
```

**未重新执行**：`liveTest`（真实 EMG）—— 按本轮规则 **DO NOT CALL EMG**。

```text
REAL_MODEL_E2E = PREVIOUSLY VERIFIED / NOT RE-RUN IN THIS FREEZE
```

---

## 9. Build Evidence

```text
gradlew projects                 5 modules 可见
gradlew clean                    BUILD SUCCESSFUL
:ag-core:test                    BUILD SUCCESSFUL
:ag-runtime:test                 BUILD SUCCESSFUL
:provider-openai-compatible:test  BUILD SUCCESSFUL
:provider-openai-compatible-jdk:test  BUILD SUCCESSFUL
:app:assembleDebug               BUILD SUCCESSFUL
```

### Android Artifact

```text
path        : app\build\outputs\apk\debug\app-debug.apk
size        : 3,568,441 bytes
SHA256      : 9F803B97B07672AE3F73269BA49F8675A0D0E2F5571FF71443987C04AFAC0C07
package     : com.androidagent.app
version     : versionCode=1  versionName=0.1.0
compileSdk  : 34
label       : AndroidAgent
native libs : 无
```

### 重做复验（REDO RE-VERIFICATION）

```text
2026-10-06 全量重跑（--rerun-tasks 强制，绕过 org.gradle.caching=true 缓存）：
  4 模块测试 + :app:assembleDebug 全部 BUILD SUCCESSFUL
  测试数不变：41 / 8 / 22 / 24 = 95/95 PASS
APK 重建后：
  size  : 3,568,437 bytes
  SHA256: A1DD32D0BAF36FD0C575BC73B0B17D644E0525B971DFF410C46D38D4AC7ACA67
  package 身份不变（com.androidagent.app v0.1.0(1)，minSdk 26 / target 34）
  内部结构不变（7 个 dex，无 native lib）

已验证事实：
  APK 非字节可复现 —— 同源码两次构建 SHA256 / size 不同（差 4 字节）。
  因此上方 SHA 是「冻结时刻的证据快照」，不是可复现性承诺。
  回退/比对 APK 时以 package 身份 + 构建 provenance 为准，不要求 SHA 一致。

---

## 10. Dependency Audit（无反向依赖）

```text
:app                -> :ag-core, :ag-runtime, :provider-openai-compatible
:ag-runtime         -> :ag-core
:provider-*-jdk     -> :provider-openai-compatible
:provider-openai-compatible -> :ag-core
:ag-core            -> （无）
无环。未新增 module。
```

---

## 11. Deferred Architecture Questions

以下**不冻结为结论**，作为 `BOUNDARY-AUDIT CANDIDATE` 上报，
留给 `AG.CORE-RUNTIME.BOUNDARY-AUDIT`：

```text
1. 长期方向为 Host / AG Engine(Runtime + Core) / Integration / Host Services
   四层；当前 5 个 flat module 未按该分层组织。

2. Host Services（Config / Credential / Persistence / Logging / Composition）
   目前散在 :app 的 ChatActivity 内，未抽为独立归属。

3. Session 的宿主可见性：Session 在 :ag-core，currentSessionId 选择在
   :ag-runtime —— 是否应统一，属边界问题。

4. provider.local.json 的加载位于 :app 内的 ProviderConfigResolver；
   未来 Host Services 抽取后归属未定。

5. :app 仍直接引用 AgentCore（ChatCompositionRoot.kt:43），
   是否应只经 AgentRuntime 暴露，待审。

6. AGCLI / AGC 未来如何共享 :ag-runtime，尚未验证。

本轮仅记录，以上均未改代码。
```

---

## 12. 本轮明确不做

```text
AG Core / Agent Runtime production code 修改      0
类迁移 / 重命名 / 重构                          0
新增 Runtime 抽象 / Host Services / Engine module  0
新增 ShellTool / AT Core / AT Runtime 接入        0
API Key 持久化 / Keystore / Settings            0
新增测试                                          0
真实 EMG 调用                                     0
```

---

## 13. Files Changed

```text
首次冻结 commit（2fa5f2d）:
新增  docs/baseline/AG.BASELINE-FREEZE-0.md              （本文件）
随冻结提交纳入此前两步已完成但未提交的设计文档：
      docs/design/AGENT-0_First_Real_Android_Agent_Vertical_Slice.md
      docs/design/AG.SHELL-0_INTEGRATION_DESIGN.md

重做 commit（本轮回退锚点）:
修改  docs/baseline/AG.BASELINE-FREEZE-0.md
      —— 追加 redo 复验证据 + APK 非字节可复现事实
```

> 两份 design 文档是 `AGENT-0.DESIGN`(PASS) 与 `AG.SHELL-0.INTEGRATION-DESIGN`(PASS)
> 的产物，此前未提交。按 **FREEZE WHAT EXISTS**，一并纳入本基线，
> 使 tag 代表当前完整状态而非留下游离文件。

**production code change = 0。**

---

## 14. Final Verdict

```text
AG_BASELINE_FREEZE_0_PASS

BASELINE_FROZEN
READY_FOR_AG_CORE_RUNTIME_BOUNDARY_AUDIT
```

本基线表示：

```text
CURRENT WORKING IMPLEMENTATION
BEFORE CORE/RUNTIME BOUNDARY AUDIT
```

**不表示**：Core/Runtime 架构已审计完成 · 生产级 AndroidAgent · AT 集成完成 ·
真实跨轮记忆已验证。
