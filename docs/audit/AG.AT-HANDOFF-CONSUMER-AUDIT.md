# AG.AT-HANDOFF-CONSUMER-AUDIT — 从 AndroidAgent 消费者视角审查 AT Engine AG-Handoff-v0

> 类型：CROSS-REPO CONTRACT AUDIT / DESIGN / PACKAGING / DEPENDENCY AUDIT。
> 视角：**只站在 AndroidAgent 真实消费者角度**，问「已冻结的 AT Engine AG-Handoff-v0
> 是否足够、清晰、低耦合地支撑 AndroidShellToolImpl」。
> 本轮：**NO INTEGRATION IMPLEMENTATION · NO PRODUCTION CODE CHANGE**。
> AT repo 全程 **READ ONLY**（0 files changed）。
> 前置：`docs/audit/AG.CORE-RUNTIME.BOUNDARY-AUDIT.md`（AG 边界已冻结）。
> 引用 SSOT：AT.ENGINE-AG0（handoff 验证 + 契约冻结）、AT.RUNTIME-2D（recovery）、
> AT 当前 stable API / source。

---

## 1. Preflight

```text
AndroidAgent:
  branch  : main
  HEAD    : 401a365f43a38c82966acc88599b7b789ef362aa（== ag-stable-1，锚点未动）
  status  : 仅 docs/audit/（未跟踪，本轮产物）
  production tracked diff : NONE

AndroidTerminal-V2（READ ONLY）:
  branch  : main
  HEAD    : 726a441acd5d5a7e24c5015adce57e543d1897f7
  status  : 非 clean —— 含 2A/2B/2D PRE-EXISTING 未提交改动（均为已验证工作，非本轮产生）
  at-core/src/main 相对 HEAD : 无任何改动 —— AT Core 冻结边界保持
  uploda/ : 用户目录，未触碰
  本轮 AT 改动 : 0（无修改/提交/清理/reset/stash）
```

## 2. AT Stable Handoff Contract（源码实测核对）

从 `at-runtime/src/main/java/com/androidterminal/runtime/` 源码 + AT.ENGINE-AG0 SSOT 核对。
**public 方法签名只使用 Runtime-owned 类型**（Core 类型仅在 `internal` mapper 内出现）。

### STABLE（AG normal route 依赖，源码确认）

```kotlin
object AtRuntime {
  fun initialize(context: Context)                       // platform bootstrap，接 WD+NSD backend
  fun readinessState(): AtReadinessState                 // Engine-level readiness
  fun connectAuto(keyDir: String): AtRuntimeDiagnostic   // readiness->discover->select->connect->READY
  fun execute(command: String, timeoutMs: Int = 0): AtExecutionResult  // 一条 shell 命令
  fun disconnect(): AtRuntimeDiagnostic                  // teardown（idempotent）
}

data class AtExecutionResult(outcome, stdout, stderr, exitCode, hasError, error)
  + computed: coreFailed, succeeded
enum   AtExecutionOutcome : COMPLETED / TIMED_OUT / CANCELLED / TRANSPORT_ERROR / PROTOCOL_ERROR / UNSUPPORTED
enum   AtReadinessState   : UNKNOWN / DISCONNECTED / CONNECTING / READY / USER_ACTION_REQUIRED /
                            PAIRING_REQUIRED(reserved) / ENDPOINT_NOT_FOUND / AUTH_FAILURE(reserved) /
                            CONNECT_FAILED / TRANSPORT_LOST / RECOVERING / RECOVERY_FAILED
data class AtRuntimeDiagnostic(code, message, readiness, transport) + computed: isReady
data class AtConnectionRequest(host, port, keyDir)       // explicit path 输入
```

**源码确认的关键语义（非文档假设）：**

```text
execute() 不自动连接（AtRuntime.kt:31,433）：非 READY 时忠实转发 Core -> TRANSPORT_ERROR
timeoutMs 原样转发 Core（AtRuntime.kt:435-437）：Core 拒绝非零 -> UNSUPPORTED（绝不静默忽略）
非零退出 = COMPLETED（AtExecutionOutcome.kt:13-16）：只有执行路径失败才非 COMPLETED
recovery 不重放（AtRuntime.kt:457-463）：detect loss -> 同步有界恢复 -> 返回原失败结果
public API 不暴露 NsdManager/ContentResolver/NsdServiceInfo/Context（除 initialize）
```

### SUPPORTED but not normal AG path

```kotlin
fun connect(request: AtConnectionRequest): AtRuntimeDiagnostic   // explicit endpoint，AG normal 不依赖
```

### INTERNAL / TEST-ONLY / 非 AG 契约（AG 禁止依赖，源码确认存在但标记）

```text
discover() / ensureEndpoint() / rediscover() / invalidateEndpoint() / currentEndpoint()
checkReadiness()
RecoveryCoordinator / RecoveryPolicy / ConnectionIntent / EndpointStore / LossClassifier
recoverForTest() / setRecoveryPolicyForTest() / setDiscoveryBackendForTest()
connectAutoNoEndpointForTest()
DiscoveryBackend / WirelessDebuggingReadinessBackend（backend 接口）
```

**结论：稳定面与 SSOT 完全一致。AG normal route 只依赖 5 个 lifecycle 方法 + 5 个
Runtime-owned 类型。`execute` 不自动连接、`timeout>0=UNSUPPORTED`、`非零退出=COMPLETED`
均为源码事实。**

## 3. Lifecycle Ownership（§12）

```text
AtRuntime.initialize(applicationContext)
  谁调用？  -> AGA Host / Composition（Application 级 bootstrap，组合期一次）
  理由     -> 接线 Android backend（WD readiness + NSD discovery）是平台装配，属 Host。
              AG Core / Agent Runtime 不接触 Context（§23 见下）。

AtRuntime.connectAuto(keyDir)
  谁调用？  -> AGA Host / Composition（bootstrap 阶段，非 Tool 调用时）
  理由     -> 连接生命周期是「宿主拥有 AT Engine」的一部分；Tool 不拥有连接。

AtRuntime.readinessState()
  谁读取？  -> AGA Host（展示连接状态）；AndroidShellToolImpl 可在 execute 前快速判
              是否 READY（可选，非必需 —— 非 READY 时 execute 自返回 TRANSPORT_ERROR）。

AtRuntime.execute(command)
  谁调用？  -> AndroidShellToolImpl（每次 ToolCall 执行时）

AtRuntime.disconnect()
  谁调用？  -> AGA Host / Composition（宿主 teardown，如进程退出 / 用户断开）
```

**关键判定：AndroidShellToolImpl 不负责连接生命周期。**

```text
预期原则（被源码支持）：
  Host / Composition owns AT Engine bootstrap / lifecycle（initialize / connectAuto / disconnect）
  AndroidShellToolImpl owns ToolCall -> execute -> ToolResult mapping

禁止 Tool 每次调用都 initialize/connectAuto/execute/disconnect —— 源码与生命周期事实证明
这并非必要：AtRuntime 是 process-scoped singleton（AtRuntime.kt:25），连接在 Host bootstrap
时建立并保持 READY，Tool 只需 execute()。AT.ENGINE-AG0 H1 真机验证的正是这条路径
（bootstrap 一次 -> 多次 execute）。
```

## 4. AndroidShellTool 职责冻结（§13）

```text
AndroidShellToolImpl 最小职责（冻结）：
  ToolCall.arguments（JSON，v0 仅 {"command":"..."}）
    ↓ parse command
  AtRuntime.execute(command)          // 唯一 AT 调用
    ↓
  AtExecutionResult
    ↓ 映射（§5）
  ToolResult

AndroidShellToolImpl 不得拥有（冻结）：
  mDNS / endpoint selection / port / Wireless Debugging 检测
  connection recovery policy / Core / ADB protocol / TLS
  command retry（§6 recovery 所有权）
  initialize / connectAuto / disconnect（Host/Composition 拥有）
```

## 5. Result Contract Mapping（§14，逐项）

AG `ToolResult.success` 冻结语义 = **tool invocation mechanism success**（非 exitCode==0）。
AT `AtExecutionResult.coreFailed` = `outcome != COMPLETED`。两者语义对齐。

| AT outcome | exitCode | hasError | → ToolResult.success | → ToolResult.output | → ToolResult.error |
|---|---|---|---|---|---|
| COMPLETED | 0 | false | **true** | stdout（+stderr 若需） | null |
| COMPLETED | !=0（如 7） | false | **true** | stdout（+stderr） | null（非零退出非机制失败） |
| TIMED_OUT | -1 | true | **false** | "" | "timed out"（v0 不可达，见下） |
| CANCELLED | -1 | true | **false** | "" | "cancelled"（v0 不可达，见下） |
| TRANSPORT_ERROR | -1 | true | **false** | "" | error（如 "read error: failed to read 24-byte header"） |
| PROTOCOL_ERROR | -1 | true | **false** | "" | error |
| UNSUPPORTED | -1 | true | **false** | "" | error（如 timeout>0 被拒） |

**v0 可达性（按真实 contract，不虚构）：**

```text
TIMED_OUT / CANCELLED : v0 实际不可达 —— timeoutMs 必须为 0（>0 -> UNSUPPORTED），
                         且无 cancel 能力（DEFERRED）。映射表保留以对齐 6 值 outcome，
                         但 v0 真实只产生 COMPLETED / TRANSPORT_ERROR / PROTOCOL_ERROR / UNSUPPORTED。
COMPLETED + exit!=0   : 可达（H2 真机验证 exit 7 -> COMPLETED / exitCode=7 / hasError=false）
TRANSPORT_ERROR       : 可达（H3 真机验证 adbd 重启 -> TRANSPORT_ERROR，原结果保留）
```

## 6. Recovery / No-Replay 所有权（§16）

```text
automatic connection recovery = AT Runtime（RecoveryCoordinator，内部）
automatic command replay      = NO（禁止）

源码证据（AtRuntime.kt:457-463）：
  execute() -> 返回原失败 result -> maybeRecover(result)（只改 readiness，不改 result，不重跑命令）

AndroidShellToolImpl 禁止：TRANSPORT_ERROR -> 自动再次执行原 command
原因：Runtime 无法知道原命令是否已到达设备（可能已部分执行）。

正确流（冻结）：
  failed ToolCall -> failed ToolResult(success=false) -> 进入 AG history（ToolResultMessage）
  -> Model/Agent 观察并决定下一步（可能换命令/报告/放弃）
  -> 期间 Runtime 内部已恢复连接（若可），保证「下一条命令」可继续
  -> Recovery 成功只保证下一条命令可执行，不重放失败命令
```

**AT.ENGINE-AG0 H3 真机验证**：命令 A -> TRANSPORT_ERROR（保留）-> Runtime 内部
rediscover+reconnect -> READY -> 命令 B -> PASS。automatic command replay = 0。

## 7. USER_ACTION_REQUIRED Mapping（§17）

```text
触发：Wireless Debugging OFF
AT 行为（源码 AtRuntime.kt:237-246，H4 真机验证）：
  connectAuto -> readiness=USER_ACTION_REQUIRED
             -> code=USER_ACTION_REQUIRED_WD_DISABLED
             -> message="Wireless Debugging is disabled; enable it in Developer Options"
```

**AG 暴露方式（判定）：**

```text
这是 Host state，不是 ToolResult failure —— 因为 USER_ACTION_REQUIRED 在 Host bootstrap
阶段（connectAuto）就被发现，早于任何 ToolCall。Host 应直接展示 diagnostic.message
（"请开启无线调试"），此时 Tool 尚未被调用。

若 Tool 调用时连接已非 READY（如运行中 WD 被关闭、恢复失败到 USER_ACTION_REQUIRED）：
  execute() 返回 TRANSPORT_ERROR -> ToolResult(success=false)
  -> 同时 Host 可读 readinessState()==USER_ACTION_REQUIRED 以给出更具体的提示
  -> v0 用现有 ToolResult.success=false + 可读 error 文本即可，无需 AG Core 新增
     structured action type（§17：优先用现有 contract 表达）

结论：
  Host bootstrap 阶段发现 -> Host 展示（Host state）
  Tool 调用时发现非 READY  -> ToolResult(success=false) + Host 可补充展示 readiness
  两者都可能需要，但 v0 不新增 AG Core 结构。
```

## 8. Identity / keyDir / Trust Precondition（§18）

```text
AT Handoff v0 前提（AG0 明确，H1 真机验证）：
  Wireless Debugging already enabled
  identity already trusted（adbkey 已配对/受信）

AGA 的 keyDir 从哪里来？
  keyDir = AGA 自己 sandbox 下的 identity 目录（如 context.filesDir 或专用子目录），
  由 Host/Composition 在 connectAuto(keyDir) 时提供（AtConnectionRequest/AtRuntime.connectAuto
  的 keyDir 参数）。AT Runtime 不生成 identity，只使用该目录里的 adbkey。

是否是 AGA 自己 sandbox 下的 identity？
  是。每个 consumer 进程嵌入自己的 AT Engine 副本并拥有自己的 identity（at-core build
  注释：APP-INDEPENDENT，每个 consumer 拥有自己的 Runtime/Core 实例）。

是否需要预先 provisioning？
  是 —— 需要一个已受信的身份（adbkey 对）存在于 keyDir。

是否允许正式产品复制 AT.apk adbkey？
  否。AG must not depend on AT.apk sandbox/private key（§18 生产假设）。

v0 真机验收若需 test fixture：
  明确为 TEST-ONLY PROVISIONING（如 AG0 H1 用的 temporary handoff-host adbkey，
  AT.ENGINE-AG0 §17 已要求测试后删除）。production assumption 与 test provisioning 分离：
  - production: AGA 拥有自己受信 identity（来源 = 后续 pairing/identity 生命周期，Slice C，DEFERRED）
  - test: 临时 fixture identity，不入库、测试后清理
  不冻结「复制 AT.apk trusted adbkey」为长期架构。
```

## 9. Cross-Repo Artifact Handoff（§19，CRITICAL，源码/产物实测）

### 9.1 `:at-runtime` 当前 artifact

```text
build.gradle.kts : com.android.library -> 产出 AAR
依赖             : implementation(project(":at-core"))   ← implementation，非 api
                   + implementation("androidx.core:core-ktx:1.13.1")
```

### 9.2 实测 AAR 内容（非假设）

**`at-runtime/build/outputs/aar/at-runtime-debug.aar`：**

```text
  顶层            : R.txt / AndroidManifest.xml / classes.jar(78,530 B) / aar-metadata.properties
  classes.jar 内  : 47 个 class，全部 com.androidterminal.runtime.*（at-runtime 自身）
  at-core 类      : 0 个（com.androidterminal.* 非 runtime = 0）
  jni/.so         : 无
  dependencies.txt: 无
```

**`at-core/build/outputs/aar/at-core-debug.aar`：**

```text
  classes.jar 内  : 7 个 class（TerminalRuntime / AdbManager / TerminalResult / ShellOutcome 等）
  jni/arm64-v8a   : libandroidterminaladb.so(2.06 MB) + libc++_shared.so(1.23 MB) + libnetwork_probe.so
  ABI             : arm64-v8a only
```

### 9.3 判定

```text
implementation(project(":at-core")) 是 same-repo project 依赖，不是 Maven 坐标。
AAR 不携带可解析的传递依赖元数据（无 dependencies.txt，无 POM）。

=> 单拷 at-runtime-debug.aar 到 AndroidAgent：
     得不到 at-core 的 7 个 class
     得不到 libandroidterminaladb.so / libc++_shared.so
     编译期缺 com.androidterminal.TerminalRuntime（at-runtime 代码引用）
     运行期 System.loadLibrary("androidterminaladb") 失败

=> 结论：单拷 runtime AAR MAY NOT BE SUFFICIENT —— 实测确认确实不足。
```

### 9.4 同 repo 为何成功（对照）

```text
:at-ag-handoff-host -> implementation(project(":at-runtime"))
  Gradle 在同一 build 内解析 :at-runtime 的 implementation(project(":at-core"))
  -> handoff 主 APK 实测含：
       lib/arm64-v8a/libandroidterminaladb.so (4.13 MB)
       lib/arm64-v8a/libc++_shared.so (1.71 MB)
       lib/arm64-v8a/libnetwork_probe.so
  H1-H4 真机 PASS 证明该路径成立。
  即：same-repo 的 project 依赖能让 Gradle 自动带齐 at-core 类 + .so；
  cross-repo 拷 AAR 则丢失这一解析。
```

### 9.5 Publication 机制现状

```text
AT 仓无 maven-publish / mavenLocal / publishing 配置（实测扫描 0 命中）。
=> 选项 A（local Maven publication）当前不存在，需在 AT 侧新增 build 机制。
```

## 10. Cross-Repo Delivery Options（§20，基于当前 build system 比较）

| 选项 | 说明 | 需 AT 侧改动？ | 判定 |
|---|---|---|---|
| **A. local Maven publication** | at-runtime 发 artifact + POM 声明传递 at-core | **是**（加 maven-publish） | 最干净（传递解析自动），但本轮 AT READ ONLY，无法实施 |
| **B. versioned paired artifacts** | at-runtime.aar + at-core.aar 配对，consumer build 依赖两者，source 只 import Runtime | **否**（用现有 AAR） | **RECOMMENDED V0**：零 AT 改动，满足「AG source -X-> Core；AG build 获得全部 transitive」 |
| **C. fat AAR** | 把 at-core 打进 at-runtime.aar | 是（引入 fat-AAR 工具） | 不引入（当前项目不支持，DO NOT introduce casually） |
| **D. includeBuild/composite** | AG build includeBuild AT 仓 | 否（AT 仓不变） | 可行但耦合两仓 build 系统；本地开发可用，非最小交付 |

**RECOMMENDED V0 HANDOFF FORM = B（versioned paired artifacts）：**

```text
AndroidAgent build：
  同时依赖 at-runtime.aar + at-core.aar（配对版本，来自同一 AT commit）
  （file AAR / flatDir / 或后续 AT 侧 publication 后的坐标）
AndroidAgent source：
  AndroidShellToolImpl 只 import com.androidterminal.runtime.*（AtRuntime 等）
  不 import com.androidterminal.*（Core）
at-core.aar 提供：transitive Core 类 + native .so（arm64-v8a）

满足目标：
  AG source -X-> AT Core API   ✓（source 只 import runtime）
  AG build 获得 AT Runtime 所需全部 transitive implementation  ✓（at-core.aar 配对）
```

## 11. Direct Dependency Nuance（§21）

```text
允许：AndroidAgent 最终 APK 包含 at-core code + native library
      （因为 AT Runtime 本来依赖它；build/runtime transitive 是正当的）
禁止：AndroidShellToolImpl import com.androidterminal.core.* / com.androidterminal.TerminalRuntime
      （source/API 依赖 Core 是越界）

理想：
  AG build -> at-runtime coordinate -> transitive at-core coordinate（build 层传递）
  AG source -> AtRuntime only（source 层仅 Runtime）

当前 artifact 体系能否做到？
  通过选项 B（paired AAR）可以：build 层 at-core.aar 提供传递类+so；source 层只 import runtime。
  => 不是 ARTIFACT_HANDOFF_GAP 的「架构失败」，而是「交付机制尚未闭环」——
     需要最小 artifact 交付修补（AT.ARTIFACT-HANDOFF-v0）。
```

## 12. Native Packaging Audit（§22）

```text
libandroidterminaladb.so 进入 consumer APK 的路径（同 repo 已证明）：
  at-core（ndk 27.0.12077973，abiFilters=arm64-v8a，CMake 构建）
    -> at-core.aar jni/arm64-v8a/libandroidterminaladb.so
    -> at-runtime implementation 传递
    -> consumer APK lib/arm64-v8a/libandroidterminaladb.so（实测 4.13 MB）

ABI       : arm64-v8a only（BoringSSL 静态库仅 arm64 构建）
AAR owner : at-core（.so 在 at-core.aar，不在 at-runtime.aar）
Gradle merge: consumer APK 从传递的 at-core.aar 合并 .so（useLegacyPackaging=true 强制提取）
duplicate risk: 单 consumer 单 at-core 实例 -> 无重复；若未来多 at-core 来源需去重
JNI class/package ABI: Java_com_androidterminal_AdbManager_nativeXxx（at-core 包名 com.androidterminal
                        固定，重命名会破坏已验证 6/6 ABI —— at-core build 注释明确）

cross-repo 前必须明确的事实（已明确）：
  .so 来自 at-core.aar（不是 at-runtime.aar）；consumer 必须配对引入 at-core.aar 才能拿到 .so。
  arm64-v8a only 意味着 consumer APK 也只能 arm64（与 AGA 现状一致，AGA 当前无 native）。
```

## 13. Android Context Boundary（§23）

```text
AT stable contract 中 Context 只出现在 AtRuntime.initialize(context)。
initialize 内部用 context.applicationContext 构建 Android backend，不长期持有、
不通过 public contract 暴露（AtRuntime.kt:129-140 KDoc 明确）。

AGA 侧可安全调用点：
  ChatCompositionRoot / Application 级 composition（持有 applicationContext）
  -> 在组合期调用 AtRuntime.initialize(applicationContext)

确认 Context 不污染：
  AG Core        : 0 处 Context（纯 JVM，已冻结）
  Agent Runtime  : 0 处 Context（纯 JVM，已冻结）
  AndroidShellTool semantic contract : Tool/ToolCall/ToolResult 均无 Context（AG Core 契约）
  -> Context 只在 platform composition boundary（AtRuntime.initialize + AGA 组合根）

结论：Context only at platform composition boundary ✓
```

## 14. Multi-Process / Ownership Assumption（§24）

```text
AGA 当前模型（确认）：
  one APK process
  one embedded AT Runtime（AtRuntime 是 process-scoped object）
  one embedded AT Core / native runtime（每个 consumer 嵌入自己的副本，at-core build 注释）

不存在：
  Binder / shared AT.apk backend / cross-process daemon

AndroidAgent 不应调用 AT.apk（确认）：
  AGA 嵌入自己的 AT Engine，直接经 AtRuntime -> AT Core -> adbd（loopback 127.0.0.1）。
  不调用 AT.apk 进程。三个 APK（AT.apk / TestHost / AGA）互零依赖、可共存（AG0 §4）。
```

## 15. Timeout（§25）

```text
AT stable v0 : timeoutMs = 0（无超时，阻塞执行）
timeoutMs > 0 : Core 拒绝 -> UNSUPPORTED（AtRuntime.kt:435-437，源码事实）

AndroidShellTool v0 : DO NOT expose timeout to model
  （v0 tool schema 仅 {"command":"..."}，无 timeout 字段）
与旧 AG.SHELL 设计兼容记录：timeout model-facing = NO
```

## 16. Tool Schema（§26）

```text
v0 最小 schema（冻结）：
  { "command": "..." }

不提前加入：cwd / env / timeout / stdin / PTY / stream / background
（AT v0 contract 不真实需要这些；PTY/timeout/stream 均 DEFERRED）
```

## 17. No Universal Backend Abstraction（§27）

```text
当前只有一个 Android execution backend（AT Engine）。
不创建 AndroidExecutionBackend / ShellBackend / ExecutionProvider /
UniversalCommandExecutor（仅为「方便 mock」而建的抽象层 = 过度设计）。

测试 seam（最小）：
  AndroidShellToolImpl 在 JVM 单测中无法加载 Android/native runtime（System.loadLibrary
  失败）。因此需要一个 tool-local minimal seam：
    - 作用范围：仅 AndroidShellToolImpl 内部（构造注入一个「execute 函数」抽象）
    - 为什么需要：JVM 单测要验证 ToolCall->ToolResult 映射逻辑而不依赖真机
    - 为什么不是公共架构层：它只服务 ShellTool 自身测试，不暴露给 Core/Runtime/Host，
      不代表「多后端」假设（当前仅 AT 一个后端）
  该 seam 在 AG.SHELL-1 实现时引入，本轮不实现。
```

## 18. Consumer Audit Matrix（§28，真实源码补全）

| AG concern | AT capability | Ownership | Mapping | Blocker? |
|---|---|---|---|---|
| platform bootstrap | `initialize(context)` | Host/Composition | 组合期一次，applicationContext | NO |
| connection readiness | `connectAuto(keyDir)` / `readinessState()` | Host/Composition | bootstrap 建立；Tool 可选读 readiness | NO |
| shell invocation | `execute(command)` | ShellTool | ToolCall -> execute | NO |
| stdout/stderr | `AtExecutionResult.stdout/stderr` | ToolResult | 原样入 output | NO |
| exitCode != 0 | `COMPLETED` + exitCode | ToolResult.success=true | 非零退出非机制失败（H2 验证） | NO |
| transport failure | `TRANSPORT_ERROR` | Tool failure | success=false + error | NO |
| recovery | Runtime 内部（RecoveryCoordinator） | 不在 Tool | 无 replay；保下一条命令 | NO |
| WD disabled | `USER_ACTION_REQUIRED` + `USER_ACTION_REQUIRED_WD_DISABLED` | Host（bootstrap 展示）/ Tool（success=false） | 见 §7 | NO |
| identity | `keyDir` / trust | Host prerequisite | keyDir=AGA sandbox identity；test-only provisioning | NO（前提记录） |
| endpoint/mDNS | Runtime 内部（NSD backend） | forbidden（Tool 不依赖） | 隐藏（consumer 不知端口） | NO |
| Core API | 内部实现（TerminalRuntime 等） | forbidden（source 不 import） | 隐藏（经 AtRuntime 包装） | NO |
| artifact delivery | at-runtime.aar + at-core.aar | build system | 配对 AAR（选项 B） | **ARTIFACT_HANDOFF_GAP** |
| native library | at-core.aar 内 .so（arm64-v8a） | build system | consumer 配对 at-core.aar 获得 | **ARTIFACT_HANDOFF_GAP** |

## 19. Q1–Q15 逐项回答（§32）

```text
Q1  AT Handoff v0 stable API 是否足够实现 AndroidShellTool v0？
    是。5 个 lifecycle 方法 + 5 个 Runtime-owned 类型足够；H1-H4 真机 PASS 证明
    独立 consumer 仅经此边界可靠执行 shell。

Q2  AG Core 是否需要修改？
    否。ToolResult（success/output/error）足以表达 AT result（§5 映射）；
    ToolResult.success 语义（机制成功）与 AT coreFailed 对齐。AG_CORE_CONTRACT_GAP = 无。

Q3  Agent Runtime 是否需要修改？
    否。AgentRuntime.send()/newSession()/isBusy 不变；ShellTool 经 ToolRegistry 注册，
    不触碰 Runtime。

Q4  AndroidShellTool 的职责边界是什么？
    ToolCall.arguments -> parse {"command"} -> AtRuntime.execute -> AtExecutionResult
    -> ToolResult。不拥有 mDNS/endpoint/port/WD 检测/recovery/Core/ADB/TLS/retry/连接生命周期。

Q5  AT initialize/connect/disconnect 谁拥有？
    Host/Composition（AGA 组合根 / Application 级 bootstrap）。ShellTool 不拥有连接生命周期。

Q6  ToolResult 与 AtExecutionResult 如何精确映射？
    见 §5 表。coreFailed(=outcome!=COMPLETED) -> success=!coreFailed；
    stdout/stderr/error 入 output/error；exitCode 随 output 呈现（非零退出仍 success=true）。

Q7  exitCode != 0 是否仍为 tool success？
    是。COMPLETED + exitCode=7 -> success=true（H2 真机验证）。ToolResult.success 是
    机制成功，不是 shell 退出码。

Q8  TRANSPORT_ERROR 后谁决定是否重试命令？
    Model/Agent（经 AG history 观察 failed ToolResult 后决定）。Runtime 只恢复连接，
    不重放命令；ShellTool 不自动重试。

Q9  USER_ACTION_REQUIRED 如何暴露给 AG/Host？
    Host bootstrap 阶段（connectAuto）发现 -> Host 展示 diagnostic.message（Host state）。
    Tool 调用时发现非 READY -> ToolResult(success=false) + Host 可读 readinessState 补充提示。
    v0 不新增 AG Core structured action type。

Q10 trusted identity/keyDir 的 v0 前提如何满足？
    前提：WD enabled + identity trusted。keyDir = AGA sandbox 内 AGA 自己的 identity 目录。
    不复制 AT.apk adbkey（生产）。真机验收若需 fixture = TEST-ONLY PROVISIONING（测试后清理）。
    正式 identity 生命周期（pairing/SPAKE2）= Slice C，DEFERRED，非 v0 blocker。

Q11 AndroidAgent 是否可以只在 source 层依赖 AT Runtime？
    可以（理想态）。AndroidShellToolImpl 只 import com.androidterminal.runtime.*；
    不 import com.androidterminal.*（Core）。build 层经 at-core.aar 获得传递类+so。

Q12 跨 repo 的 at-runtime artifact 是否能带齐 at-core/native dependency？
    否。实测 at-runtime.aar 只含自身 47 类，0 个 at-core 类，无 .so，无 dependencies.txt。
    单拷不足。需配对 at-core.aar（或 publication）。

Q13 是否需要 AT repo 先做 artifact handoff 修补？
    是（最小交付修补）。推荐选项 B：配对发布 at-runtime.aar + at-core.aar（版本绑定，
    同一 AT commit）。本轮 AT READ ONLY，不实施；作为 NEXT = AT.ARTIFACT-HANDOFF-v0。

Q14 是否需要任何新 universal abstraction？
    否。当前仅 AT 一个 backend，不建 ExecutionBackend/ShellBackend 等抽象层。
    唯一新增 = ShellTool 内部 tool-local 测试 seam（AG.SHELL-1 实现时，非公共架构）。

Q15 是否已经 READY_FOR_AG_SHELL_1？
    语义上 READY，但被 artifact 交付阻塞。
    SEMANTIC_CONTRACT = READY；ARTIFACT_HANDOFF_GAP = YES。
    => READY_FOR_AG_SHELL_1 = BLOCKED ONLY BY ARTIFACT DELIVERY。
    下一步仅做 AT.ARTIFACT-HANDOFF-v0（最小 artifact 交付），不重开 Runtime/Core。
```

## 20. Blocker 分类（§29）

```text
NO_BLOCKER（语义层）：
  AT stable contract 足够；AG Core/Runtime 不需改；ShellTool 边界清晰；
  result/recovery/USER_ACTION/identity/timeout/schema/Context/multi-process 全部可表达。

ARTIFACT_HANDOFF_GAP（唯一 blocker）：
  AT API 已足够，但跨 repo artifact / transitive dependency / native packaging 未闭环：
    - at-runtime.aar 不含 at-core 类 / .so / 依赖元数据
    - AT 仓无 publication 机制
  解决：最小 artifact 交付修补（选项 B 配对 AAR）。不重开 AT 架构。

AT_CONTRACT_GAP：无（AG 真实需求均被 frozen stable contract 表达）
AG_CORE_CONTRACT_GAP：无（ToolResult 足以表达 shell execution）
```

## 21. Pairing 不作为 v0 Blocker（§30）

```text
AT Handoff v0 明确前提：WD enabled + trusted identity available。
Pairing / SPAKE2 / full identity bootstrap 不因本轮重新进入主线。
只记录为 AG-Handoff-v0 precondition。
若 AG.SHELL-1 真机测试需人工/test provisioning：允许作为 TEST PRECONDITION
（test-only fixture，不入库，测试后清理）。
```

## 22. Files Changed

```text
AndroidAgent:
  新增  docs/audit/AG.AT-HANDOFF-CONSUMER-AUDIT.md（本文件，task-owned，未提交等 Review）
  production code / Gradle / tag : 0
AndroidTerminal-V2:
  0 files changed（READ ONLY，无修改/提交/清理/reset/stash）
```

## 23. Final Verdict（§33）

```text
AG_AT_HANDOFF_CONSUMER_AUDIT_PASS

AT_HANDOFF_V0
= SUFFICIENT_FOR_ANDROID_SHELL_TOOL_V0（语义层）

AG_CORE_CHANGE_REQUIRED            = NO
AG_RUNTIME_CHANGE_REQUIRED         = NO
AT_RUNTIME_CONTRACT_CHANGE_REQUIRED = NO
AT_CORE_CHANGE_REQUIRED            = NO

SEMANTIC_CONTRACT                  = READY
ARTIFACT_HANDOFF_GAP               = YES（跨 repo artifact 交付未闭环）
READY_FOR_AG_SHELL_1               = BLOCKED ONLY BY ARTIFACT DELIVERY

NEXT
= AT.ARTIFACT-HANDOFF-v0（最小 artifact 交付：配对 AAR / 或 publication）
不得重开 AT Runtime / Core。
```
