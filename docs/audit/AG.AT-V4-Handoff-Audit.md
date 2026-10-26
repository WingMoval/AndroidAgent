# AG.AT-V4-Handoff-Audit — AndroidAgent × AndroidTerminal V4 跨仓库集成前审计

> 类型：CROSS-REPOSITORY ARCHITECTURE AUDIT / CONSUMER MIGRATION PLANNING
> 视角：从 AndroidAgent-V2 实际工程出发，重新审计 AT → AG 消费边界
> 本轮：**NO PRODUCTION CODE CHANGE · NO BUILD CHANGE · NO DEVICE TEST · NO GIT COMMIT**
> AT V4 repo 全程 **READ ONLY**（0 files changed）
> 前置：`docs/audit/AG.AT-HANDOFF-CONSUMER-AUDIT.md`（V3 handoff-v0 审计）

---

## 0. Preflight（实际 Git 状态核验）

### AndroidAgent-V2（开发源）
```
branch  : main
HEAD    : a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
          (AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: close complete AT V3 consumer subsystem)
status  : CLEAN（无未提交修改）
最近提交:
  a6eea61 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0
  4d8aec7 AG.HOST-SESSION-CONTINUITY-0.1 (session UI rebinding fix)
  c4a58fe AG.HOST-SESSION-CONTINUITY-0 (preserve session across config changes)
  6c4d8f1 AG.AT-FULL-CONSUMER-INTEGRATION-0
  50211c6 AG.AT-CONSUMER-RECONCILIATION-0.1
```
SESSION_CONTINUITY 工作（c4a58fe + 4d8aec7）已提交并稳定，HEAD 在其之上。本轮不触碰。

### AndroidTerminal-V4（只读参考基线）
```
HEAD          : 7f3a7dface6e504970714631ba4fb2224052f5ef (AT.V4-6.1 docs closure)
FREEZE_TAG    : AT.V4-CONSUMER-FREEZE-V1
TAG_TARGET    : 7216d0e8131e92e8914ce7c8443e1668b1d53aca (AT.V4-6 freeze commit)
status        : CLEAN
```

### 结论
```
AG_BASELINE_VERIFIED = YES
SESSION_CONTINUITY_BASELINE_PRESERVED = YES
AT_V4_FREEZE_TAG_TARGET_CORRECT = YES
```

---

## 1. 第一阶段：重建此前的决策链（带证据）

### 1.1 第一次 AT → AG 审计的结论
文件：`docs/audit/AG.AT-HANDOFF-CONSUMER-AUDIT.md`（针对 AT Handoff-v0，AT V2 时代）

结论（§23 Final Verdict）：
```
AT_HANDOFF_V0 = SUFFICIENT_FOR_ANDROID_SHELL_TOOL_V0（语义层）
SEMANTIC_CONTRACT = READY
ARTIFACT_HANDOFF_GAP = YES（跨 repo artifact 交付未闭环）
READY_FOR_AG_SHELL_1 = BLOCKED ONLY BY ARTIFACT DELIVERY
NEXT = AT.ARTIFACT-HANDOFF-v0（最小 artifact 交付：配对 AAR / publication）
```

### 1.2 当时指出的边界问题（带证据）
1. **artifact 交付未闭环**（§9）：`at-runtime.aar` 只含自身 47 类，0 个 at-core 类，无 .so，无 dependencies.txt。单拷 AAR 不足。
2. **跨 repo transitive dependency 丢失**（§9.3）：`implementation(project(":at-core"))` 是 same-repo project 依赖，跨 repo 拷 AAR 时 Gradle 无法解析传递依赖。
3. **无 publication 机制**（§9.5）：AT 仓无 maven-publish 配置。
4. **logical boundary 不足以满足 consumer isolation**：V2 时代 AT 是 `at-runtime` + `at-core` 两个模块，consumer 需配对两个 AAR；AT Core（`com.androidterminal.*`）被 AG source 禁止 import，但 build 层仍需 at-core.aar 提供传递类 + native。

### 1.3 当时的 AT 消费方式与依赖关系
V2 时代（AG.AT-HANDOFF-CONSUMER-AUDIT）：
```
AG source  -> AtRuntime (com.androidterminal.runtime.*) only
AG build   -> at-runtime.aar + at-core.aar（配对）
```
V3 时代（当前 AG V2 实际）：
```
AG source  -> AtExecutionAdapter 直接 import：
              com.adb.engine.AdbRuntime / AdbConsumerBootstrap / AdbBootstrapState
              com.at.backend.adb.AdbOneShotBackend / AdbPersistentBackend
              com.at.backend.local.LocalOneShotBackend / LocalPersistentBackend
              com.at.engine.ATEngine（concrete class）/ ATExecutionRequest / ATExecutionResult
              com.at.cli.AdbCliBridgeServer / ATCliExecution / LocalCliBootstrap
AG build   -> vendored AARs via third_party/at-consumer-freeze-v1/m2
              (com.at.engine:at-engine:1.0.0, com.at.backend.local:at-backend-local:1.0.0,
               com.at.backend.adb:at-backend-adb:1.0.0, com.adb.engine:adb-engine:1.0.0,
               com.at.cli:at-cli:1.0.0)
              source baseline c9cee21 (AT.AG-CONSUMER-FREEZE-V1), release AARs
```

### 1.4 为什么逻辑分层不足以满足真正的消费者隔离
V3 的 `:integration-at` adapter 虽然隔离了 AT 依赖（只有 adapter 直接依赖 AT），但 adapter 的 **source** 直接 import 了：
- `com.adb.engine.AdbRuntime`（ADB lifecycle owner — 内部实现）
- `com.adb.engine.AdbConsumerBootstrap`（first-use bootstrap — 内部实现）
- `com.at.backend.adb.AdbOneShotBackend` / `AdbPersistentBackend`（backend 实现）
- `com.at.backend.local.LocalOneShotBackend` / `LocalPersistentBackend`（backend 实现）
- `com.at.engine.ATEngine` 作为 **concrete class** 直接 `ATEngine(oneShot=..., persistent=...)` 构造

这意味着 AG 的 adapter **越过了 AT 的公共 consumer boundary**，直接依赖 AT 的 backend 实现和 ADB 内部类型。如果 AT 重构 backend 包名 / 改 AdbRuntime API / 改 ATEngine 为 interface，AG adapter 会编译失败。

### 1.5 AT V4 为解决这些问题实际新增、迁移和封闭了哪些模块
AT V4 的物理架构（SSOT `docs/AT.V4_MASTER_SSOT.md` + 冻结 bundle）：
```
新增模块:
  :at-engine-api         — public consumer contract（ATEngine interface + ATExecution + ATSession）
  :at-engine-spi         — backend SPI（implementation contract，consumer 禁止直接依赖）
  :at-composition-android — Android assembly root（ATAndroid.create + ATAndroidAdbControl + 语义类型）

迁移/封闭:
  :at-engine             — DefaultATEngine（implementation，consumer 禁止直接依赖）
  :at-backend-local      — LocalOneShotBackend / LocalPersistentBackend（implementation）
  :at-backend-adb        — AdbOneShotBackend / AdbPersistentBackend（implementation）
  :adb-engine            — AdbRuntime / AdbEngine / AdbConsumerBootstrap（implementation）
  :at-cli                — AdbCliBridgeServer / LocalCliBootstrap / ATCliExecution（optional frontend）

Consumer Boundary（V4-5 冻结 + machine-enforced）:
  Consumer
  ├── :at-engine-api           (REQUIRED — public execution/session contract)
  ├── :at-composition-android  (REQUIRED — Android assembly root + ADB semantic facade)
  └── :at-cli                  (OPTIONAL — CLI frontend)

  FORBIDDEN direct consumer dependencies:
  :at-engine / :at-engine-spi / :at-backend-local / :at-backend-adb / :adb-engine
```

### 1.6 V4-4、V4-5、V4-6 分别证明了什么
- **V4-4.2**（DEVICE_CLOSURE = CLOSED）：所有 REQUIRED device gates PASS（C4-C6, A5-A8, A10, CONNECTION_REUSE, RUNTIME_OWNERSHIP, HOST_RESTART, Transport-Loss, APP_RESTART）。V3→V4 单 identity parity = PASS。
- **V4-4.3**（COMPLETE）：First-use Auto-WD permission self-grant bootstrap。POST_BOOTSTRAP_AUTO_WD = PASS。
- **V4-5**（COMPLETE）：Consumer Conformance + Architecture Enforcement。新建 `:at-consumer-conformance-host`（只依赖 :at-engine-api + :at-composition-android + :at-cli）+ `:at-architecture-gate`（machine-enforced gates）。8/8 conformance gates PASS。CONSUMER_CONTRACT_GAP = NO。
- **V4-6**（COMPLETE）：Device Parity + Consumer Freeze。18/18 parity gates PASS。V3_V4_SEMANTIC_DELTA = NONE。CONSUMER_HANDOFF PUBLISHED。V4_AAR_BUNDLE PUBLISHED（8 artifacts）。AT.V4-CONSUMER-FREEZE-V1 tag。

### 1.7 哪些历史问题已被 V4 解决，哪些尚未在 AG 中完成对应迁移

**已被 V4 解决：**
1. artifact 交付闭环：V4 bundle 含 8 artifacts（6 AAR + 2 JAR）+ SHA256SUMS.txt + native libs 在各 owning AAR 内。
2. consumer isolation：`:at-composition-android` 提供 `ATAndroid.create(context, config)` 统一入口 + `ATAndroidAdbControl` semantic facade。Consumer 不需要 import 任何 backend / ADB 内部类型。
3. ATEngine 从 concrete class → interface：consumer 不再直接构造 `ATEngine(oneShot=..., persistent=...)`，改为 `ATAndroid.create().adbEngine` / `.localEngine`。
4. ADB lifecycle 封装：`ATAndroidAdbControl.ensureReady()` / `readinessState()` / `bootstrapState()` / `pairDiscovered()` / `completeFirstUseBootstrap()` 全部通过 composition facade，consumer 不直接调 `AdbRuntime` / `AdbConsumerBootstrap`。
5. identity 封装：`ATAndroidConfig(identityProfile)` — identity 位置由 composition 拥有，consumer 不管理 identityDir。
6. machine-enforced architecture gates：V4-5 的 `:at-architecture-gate` 自动检查 consumer 不直接依赖 forbidden modules。

**尚未在 AG 中完成对应迁移（= 本审计识别的 GAP）：**
1. AG `:integration-at` adapter 仍直接 import AT V3 内部类型（AdbRuntime / AdbConsumerBootstrap / backends）。
2. AG 仍消费 AT V3 vendored AARs（c9cee21），未迁移到 V4 bundle（7216d0e）。
3. AG 的 `AtExecutionAdapter` 直接构造 `ATEngine(oneShot=..., persistent=...)`（V3 concrete class），V4 中 ATEngine 是 interface。
4. AG 无 `ATAndroid.create()` 调用 — V4 的统一 composition 入口未被使用。
5. AG 的 `:tool-android-shell` 通过 `api(project(":integration-at"))` 传递 AT types（ATExecutionResult），但 V4 中 ATExecutionResult 在 `:at-engine-api`（public），不违反 boundary — 但 adapter 的 re-export 方式需调整。

---

## 2. 第二阶段：审计 AG 当前真实实现

### 2.A AG Core 与 Agent Runtime

**AG Core（`:ag-core`）：**
- source 文件：`AgCoreMarker.kt` / `AgentCore.kt` / `AgentEvent.kt` / `Message.kt` / `Model.kt` / `RunOutcome.kt` / `Session.kt` / `Tool.kt` / `ToolApproval.kt` / `ToolRegistry.kt`
- 零 AT / Android / ADB / JNI import（实测 `import com.at.` / `import com.adb.` / `import android.` / `import com.androidterminal.` = 0 命中）
- build 依赖：仅 testImplementation（coroutines + junit）。零 production 依赖。
- **结论：AG_CORE_BOUNDARY = PASS（无需修改）**

**Agent Runtime（`:ag-runtime`）：**
- 零 AT / Android / ADB import（实测 0 命中）
- build 依赖：`implementation(project(":ag-core"))` + coroutines
- **结论：AG_RUNTIME_BOUNDARY = PASS（无需修改）**

**Session / Run / Tool contract：**
- `Tool` interface：`spec: ToolSpec` + `suspend fun execute(call: ToolCall): ToolResult`
- `ToolResult(success, output, error?)` — success = tool mechanism success（非 exitCode==0）
- `ToolRegistry` — Host 注册 concrete tools
- **结论：Tool contract 无需修改。AG Core / Runtime / Session / Run 架构保持不变。**

### 2.B `:integration-at`（AtExecutionAdapter）

**当前直接 import（实测 AtExecutionAdapter.kt）：**
```kotlin
import com.adb.engine.AdbBootstrapState          // FORBIDDEN（V4 consumer boundary）
import com.adb.engine.AdbConsumerBootstrap       // FORBIDDEN
import com.adb.engine.AdbPairingDiscovery        // FORBIDDEN
import com.adb.engine.AdbPairingResultV2         // FORBIDDEN
import com.adb.engine.AdbConnectionRequest       // FORBIDDEN
import com.adb.engine.AdbRuntime                 // FORBIDDEN
import com.adb.engine.AdbRuntimeState            // FORBIDDEN
import com.at.backend.adb.AdbOneShotBackend      // FORBIDDEN
import com.at.backend.adb.AdbPersistentBackend   // FORBIDDEN
import com.at.backend.local.LocalOneShotBackend  // FORBIDDEN
import com.at.backend.local.LocalPersistentBackend // FORBIDDEN
import com.at.cli.AdbCliBridgeServer             // OK（:at-cli 是 allowed）
import com.at.cli.ATCliExecution                 // OK
import com.at.cli.LocalCliBootstrap              // OK
import com.at.engine.ATEngine                    // OK（V4: interface）
import com.at.engine.ATExecutionRequest          // OK
import com.at.engine.ATExecutionResult           // OK
import com.at.engine.ATOpenSessionResult         // OK
import com.at.engine.ATPersistentBackend         // ⚠️ V4 中此类型在 :at-engine-spi（FORBIDDEN）
import com.at.engine.ATSessionEvent              // OK
import com.at.engine.ATTerminalSession           // OK
```

**当前直接依赖（build.gradle.kts）：**
```kotlin
api("com.at.engine:at-engine:1.0.0")           // FORBIDDEN（V4 consumer boundary）
api("com.at.backend.local:at-backend-local:1.0.0") // FORBIDDEN
api("com.at.backend.adb:at-backend-adb:1.0.0") // FORBIDDEN
api("com.adb.engine:adb-engine:1.0.0")         // FORBIDDEN
api("com.at.cli:at-cli:1.0.0")                 // OK（optional frontend）
```

**当前行为：**
- `adbEngine` / `localEngine`：直接 `ATEngine(oneShot=..., persistent=...)` 构造（V3 concrete class）
- `initializeAdb()`：直接 `AdbRuntime.initialize(appContext)`
- `ensureAdbReady()`：直接 `AdbRuntime.ensureReady(identityDir())` + `AdbRuntime.readinessState()`
- `adbState()`：直接 `AdbRuntime.readinessState()`
- `disconnectAdb()`：直接 `AdbRuntime.disconnect()`
- `bootstrap`：直接 `AdbConsumerBootstrap(identityDir())`
- `bootstrapState()` / `discoverPairing()` / `pairDiscovered()` / `pair()`：直接调 `AdbConsumerBootstrap` 方法
- `openAdbSession()` / `openLocalSession()`：用 `ATPersistentBackend.Launch(...)` 构造 launch（V3 类型）

**AT_ADAPTER_BOUNDARY = FAIL**
**违规项：** 直接 import 7 个 `com.adb.engine.*` + 4 个 `com.at.backend.*` + 直接构造 `ATEngine` concrete class + 直接调 `AdbRuntime` / `AdbConsumerBootstrap`

### 2.C `:tool-android-shell`

**当前 import（AndroidShellToolImpl.kt）：**
```kotlin
import com.androidagent.core.Tool / ToolCall / ToolResult / ToolSpec   // OK（AG Core）
import com.androidagent.integration.at.AtExecutionAdapter               // OK（adapter）
import com.at.engine.ATExecutionResult                                  // OK（public contract）
```
- Tool 通过 `Executor` seam 调用 `adapter.executeAdb(command)` → `ATExecutionResult` → `ShellResultMapper.toToolResult()`
- **不直接构造 ATEngine、不直接调 AdbRuntime、不管理连接生命周期**
- `ShellConnection`（同模块）：持有 `AtExecutionAdapter` 实例，委托 `initializeAdb()` / `ensureAdbReady()` / `bootstrapState()` / `disconnect()`
- ShellConnection 的 public API 只暴露 integration-owned `Status(ready, code, message)`，不泄漏 AT 类型

**SHELL_TOOL_BOUNDARY = PASS**
- Tool 是薄消费者，通过 adapter 消费 AT
- 连接生命周期由 ShellConnection（Host-owned）管理
- 不违规 import AT 内部类型（只 import `ATExecutionResult` — public contract）

**注意：** `:tool-android-shell/build.gradle.kts` 的 header 注释提到 `includeBuild("../AndroidTerminal-V3")` 和 `dependencySubstitution`，但实际 `settings.gradle.kts` 没有 `includeBuild`。注释过期，实际依赖通过 `api(project(":integration-at"))` 传递（adapter re-exports AT types）。

### 2.D AGA Android Host Composition Root

**`ChatCompositionRoot`（app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt）：**
- 唯一 Composition Root：构造 `ShellConnection(applicationContext)` → `shell.adapter`（AtExecutionAdapter）→ 注册 `AndroidShellToolImpl(shell.adapter)`
- 调用 `shell.adapter.startCliBridge()`
- **AG Core / Agent Runtime 不接触 AT 类型**（只通过 Tool contract）
- **Host 持有 Context**（applicationContext）— 这是 platform composition boundary，正确

**AGA_COMPOSITION_BOUNDARY = PASS（但需调整）**
- 当前：Host 创建 `ShellConnection(context)` → 内部创建 `AtExecutionAdapter(context, identityDirName)`
- V4 目标：Host 应调用 `ATAndroid.create(context, ATAndroidConfig(identityProfile))` 获得 `ATAndroidComposition`，然后注入 adapter
- 当前 Host 不直接 import AT 内部类型（通过 ShellConnection 封装），但 ShellConnection 内部的 AtExecutionAdapter 违规

**唯一性：** 只有一个 Composition Root（ChatCompositionRoot）。AT Engine 创建只发生在 AtExecutionAdapter 内部（lazy）。无多处创建风险。

### 2.E 旧交接制品与依赖

**`third_party/at-consumer-freeze-v1/`（当前活跃）：**
- 5 个 V3 AAR（at-engine / at-backend-local / at-backend-adb / adb-engine / at-cli）
- m2 wrapper（POM + AAR，`com.at.engine:at-engine:1.0.0` 等）
- MANIFEST.md：source baseline c9cee21，release AARs，SHA256 hashes
- **状态：PRODUCTION DEPENDENCY（当前 build 实际解析）**

**`third_party/at-engine-handoff-v0/`（历史残留）：**
- V2 时代 handoff bundle（at-runtime.aar + at-core.aar）
- **状态：SUPERSEDED / INACTIVE（AGENTS.md 明确"no longer in dependency graph"）**
- 可安全删除（但本轮不删除，只分类）

**`includeBuild` / composite build：**
- `settings.gradle.kts` 无 `includeBuild`。`tool-android-shell/build.gradle.kts` header 注释提到但实际不存在。
- **状态：注释过期，无实际 composite build**

**AT V3 项目依赖：**
- 无。AG 不依赖 AT V3 源码树，通过 vendored AAR 消费。
- **状态：CLEAN（无 sibling AT source tree dependency）**

**旧 AAR（at-runtime.aar / at-core.aar）：**
- 在 `third_party/at-engine-handoff-v0/` 内
- **状态：SUPERSEDED（V2 时代），不在当前 build graph**

**分类总结：**
| 制品 | 状态 | 可安全删除？ |
|------|------|-------------|
| `third_party/at-consumer-freeze-v1/` | PRODUCTION（当前 build 解析） | NO（需先迁移到 V4） |
| `third_party/at-engine-handoff-v0/` | SUPERSEDED（V2，inactive） | YES（迁移后） |
| `includeBuild` 注释 | 过期（无实际 composite） | YES（迁移时清理注释） |
| AT V3 源码依赖 | NONE | N/A |

---

## 3. 第三阶段：对照 V4 Consumer Handoff

### 3.A Public API（at-engine-api.jar）

**V4 实际提供的 API（源码实测）：**
```kotlin
// ATEngine（interface，V4-1 从 concrete class 拆分）
interface ATEngine {
    fun execute(request: ATExecutionRequest): ATExecutionResult
    fun openSession(launch: ATSessionLaunch): ATOpenSessionResult
}

// ATExecution.kt
enum class ATExecutionOutcome { COMPLETED, FAILED, TIMEOUT, UNSUPPORTED }
data class ATExecutionRequest(command: String, timeoutMs: Long = 30_000)
data class ATExecutionResult(outcome, stdout, stderr, exitCode, tookMs, hasError, error)

// ATSession.kt
enum class ATSessionState { OPENING, ACTIVE, EXITED, FAILED, CLOSED }
data class ATSessionLaunch(command, initialRows=0, initialCols=0, env=emptyMap())
sealed class ATOpenSessionResult { Success(session) / Failure(reason) }
interface ATTerminalSession {
    val state: ATSessionState
    fun write(bytes: ByteArray): Int
    fun read(timeoutMs: Int): ByteArray?
    fun setListener(listener: (ATSessionEvent) -> Unit)
    fun resize(rows: Int, cols: Int): Boolean
    fun close(terminateProcess: Boolean = true)
}
sealed class ATSessionEvent { Data(bytes) / Resized(rows,cols) / Exited(exitCode) / Failed(error) }
```

**AG 当前使用的 V3 API → V4 映射：**
| AG 当前（V3） | V4 public API | 差异 |
|---------------|---------------|------|
| `ATEngine(oneShot=..., persistent=...)` | `ATAndroid.create().adbEngine` / `.localEngine` | V3 concrete class → V4 interface + composition factory |
| `ATEngine.execute(ATExecutionRequest)` | `ATEngine.execute(ATExecutionRequest)` | 相同 |
| `ATPersistentBackend.Launch(command, rows, cols)` | `ATSessionLaunch(command, initialRows, initialCols)` | 类型名变更（ATPersistentBackend.Launch → ATSessionLaunch） |
| `ATExecutionResult(outcome, stdout, stderr, exitCode, hasError, error)` | 相同 | 相同 |
| `ATTerminalSession` | 相同（write/read/resize/close/setListener/state） | 相同 |

**V4 新增 AG 未使用的能力：**
- `ATSessionLaunch.env: Map<String,String>` — V3 无 env 参数
- `ATSessionEvent.Resized` / `Exited` / `Failed` — V3 事件类型可能不同
- `ATExecutionResult.tookMs` — 执行耗时

**能力缺口：** NONE。V4 public API 完整覆盖 AG 当前使用的 V3 能力（one-shot + persistent + raw I/O + resize + Ctrl-C + lifecycle + multi-session）。

**V4_PUBLIC_API_CONSUMABLE = YES**

### 3.B Android Composition（at-composition-android-debug.aar）

**V4 实际提供的 composition（源码实测）：**
```kotlin
// ATAndroid.kt
fun ATAndroid.create(context: Context, config: ATAndroidConfig = ATAndroidConfig()): ATAndroidComposition
data class ATAndroidConfig(identityProfile: String = "adb_identity")
class ATAndroidComposition internal constructor(
    val localEngine: ATEngine,
    val adbEngine: ATEngine,
    val adbControl: ATAndroidAdbControl
)

// ATAndroidAdbControl.kt（consumer semantic facade）
fun readinessState(): ATAdbReadinessState
fun bootstrapState(): ATAdbBootstrapState
fun hasIdentity(): Boolean
fun discoverPairing(timeoutMs: Long): ATAdbPairingDiscovery
fun pair(host: String, port: Int, code: String): ATAdbPairingResult
fun pairDiscovered(code: String, timeoutMs: Long): ATAdbPairingResult
fun ensureReady(onStatusLine: (String) -> Unit): Boolean
fun completeFirstUseBootstrap(): FirstUseBootstrapResult
fun addLifecycleListener(onTransition: (old, new) -> Unit): ATAdbLifecycleListenerHandle
fun removeLifecycleListener(handle)
fun canWriteSecureSettings(): Boolean
fun isRuntimeInitialized(): Boolean
```

**Context 边界：** `ATAndroid.create(context, ...)` 是唯一接受 Context 的入口。Context 不传播进 ATEngine / ATTerminalSession / 任何运行期业务 API。

**identity 位置：** `ATAndroidConfig(identityProfile)` — composition 拥有 identity 位置（`files/<identityProfile>/`）。Consumer 不管理 identityDir。

**V4_ANDROID_COMPOSITION_CONSUMABLE = YES**

### 3.C Internal Implementation（consumer 禁止直接依赖）

```
:at-engine-spi        — backend SPI（implementation contract）
:at-engine            — DefaultATEngine（implementation）
:at-backend-local     — LocalOneShotBackend / LocalPersistentBackend
:at-backend-adb       — AdbOneShotBackend / AdbPersistentBackend
:adb-engine           — AdbRuntime / AdbEngine / AdbConsumerBootstrap
```

**AG 当前违规：** `:integration-at` 直接依赖 `:at-engine` / `:at-backend-local` / `:at-backend-adb` / `:adb-engine`（全部 FORBIDDEN）。

**V4 目标：** `:integration-at` 只依赖 `:at-engine-api` + `:at-composition-android`（+ `:at-cli` optional）。

### 3.D Artifact Readiness

**V4 bundle（`dist/AT.V4-CONSUMER-FREEZE-V1/`）：**
- 8 artifacts：6 AAR + 2 JAR
- SHA256SUMS.txt：8 个哈希
- native libs：libat_local_backend.so（at-backend-local AAR）/ libadbd_engine.so（adb-engine AAR）/ libatcli_env.so（at-cli AAR）
- **无 POM 文件**（V4 bundle 是 raw AAR/JAR，非 Maven publication）

**AG 解析方式：**
- 当前：V3 m2 wrapper（`com.at.engine:at-engine:1.0.0` 等，有 POM）
- V4：需要新的 m2 wrapper 或直接 file 依赖（V4 bundle 无 POM）

**debug vs release：**
- V3 vendored：release AARs（MANIFEST.md 明确 BUILD_VARIANT = release）
- V4 bundle：debug AARs（`at-engine-debug.aar` 等）
- **GAP：V4 bundle 是 debug 制品，AG 当前用 release。debug vs release 兼容性需确认。**

**V4_ARTIFACT_CLOSURE = PASS**（8 artifacts 完整，native libs 在 owning AAR 内）
**DEBUG_VS_RELEASE_READINESS = GAP**（V4 bundle 是 debug，AG 需 release 或确认 debug 可用）

---

## 4. 第四阶段：唯一目标依赖图

### 4.1 真实 Gradle 依赖有向图（V4 目标态）

```
:ag-core
    ↑ (implementation)
:ag-runtime
    ↑ (implementation)
:app (AGA Composition Root)
    ├── implementation(:ag-core)
    ├── implementation(:ag-runtime)
    ├── implementation(:provider-openai-compatible)
    ├── implementation(:tool-android-shell)
    ├── implementation(:tool-network)
    ├── implementation(:tool-file)
    └── implementation(:integration-at)          ← Host 注入 AT composition
                    │
                    │ (api)
                    ▼
           :tool-android-shell
                    │
                    │ (implementation — 只依赖 AG Core + adapter)
                    ▼
           :ag-core (Tool contract)

:integration-at（V4 目标态）
    ├── api(:at-engine-api)                       ← REQUIRED（public contract）
    ├── api(:at-composition-android)              ← REQUIRED（assembly root）
    └── api(:at-cli)                              ← OPTIONAL（CLI frontend）
                    │
                    │ (transitive，consumer 不可见)
                    ▼
           AT internal implementation
           (:at-engine / :at-engine-spi / :at-backend-local /
            :at-backend-adb / :adb-engine)
```

### 4.2 对象注入关系（V4 目标态）

```
ChatCompositionRoot（AGA Host）
    │
    │ 1. ATAndroid.create(applicationContext, ATAndroidConfig(identityProfile))
    ▼
ATAndroidComposition
    ├── localEngine: ATEngine
    ├── adbEngine: ATEngine
    └── adbControl: ATAndroidAdbControl
    │
    │ 2. 注入 AtExecutionAdapter（adapter 持有 composition，不自行构造）
    ▼
AtExecutionAdapter（:integration-at）
    ├── executeAdb(command) → composition.adbEngine.execute(...)
    ├── openAdbSession()    → composition.adbEngine.openSession(...)
    ├── ensureAdbReady()    → composition.adbControl.ensureReady(...)
    ├── bootstrapState()    → composition.adbControl.bootstrapState()
    ├── pairDiscovered()    → composition.adbControl.pairDiscovered(...)
    └── ...（全部委托 composition，不直接调 AdbRuntime / AdbConsumerBootstrap）
    │
    │ 3. 注入 AndroidShellToolImpl（tool 是薄消费者）
    ▼
AndroidShellToolImpl（:tool-android-shell）
    └── Executor { command -> adapter.executeAdb(command) }
```

### 4.3 目标边界验证

| 边界 | 目标 | V4 满足？ |
|------|------|----------|
| AG Core 不依赖 AT | 零 import | YES（已验证） |
| Agent Runtime 不依赖 AT | 零 import | YES（已验证） |
| AT adapter 对 AT 直接依赖仅限 :at-engine-api + :at-composition-android | 不 import backend / ADB 内部 | **NO（当前违规，需迁移）** |
| AGA Host 构建 AT Engine 并注入 adapter | ATAndroid.create() | **NO（当前 adapter 自行构造，需迁移）** |
| Host 不直接依赖 AT backend/SPI/engine impl | 通过 composition facade | YES（Host 不 import AT 类型） |
| :tool-android-shell 通过 Tool contract 暴露能力 | 不实现 AT Runtime | YES（已验证） |
| AT 拥有 ADB lifecycle / identity / pairing / recovery | AdbRuntime 是 unique owner | YES（AT 内部，consumer 不可见） |
| AG consumer 拥有自己的 app-private identity | 不复制 AT Product identity | YES（identityProfile = "at-consumer/identity"） |

### 4.4 冲突及最小修复

**冲突：** AG 当前 `:integration-at` 直接依赖 4 个 forbidden modules + 直接 import 11 个 AT 内部类型。V4 consumer boundary 禁止这些。

**最小修复（不扩大依赖边界）：**
1. `:integration-at` 的 build 依赖从 5 个 AT V3 AARs 改为 `:at-engine-api` + `:at-composition-android` + `:at-cli`
2. `AtExecutionAdapter` 的 source 从直接 import `com.adb.engine.*` / `com.at.backend.*` 改为 import `com.at.composition.android.*` + `com.at.engine.*`
3. `AtExecutionAdapter` 的构造从 `ATEngine(oneShot=..., persistent=...)` 改为接收 `ATAndroidComposition`（由 Host 注入）
4. `AtExecutionAdapter` 的 ADB lifecycle 方法从直接调 `AdbRuntime` 改为委托 `composition.adbControl`
5. `ChatCompositionRoot` 从 `ShellConnection(context)` → 内部创建 adapter 改为 `ATAndroid.create(context, config)` → 注入 adapter

---

## 5. 第五阶段：AG 实施指令

### 5.1 唯一推荐任务

**`AG.AT-V4-INTEGRATION-1 — Replace Legacy AT Consumption with V4 Consumer Boundary`**

推荐条件已满足：
- AT V4 已解决所有 V3 时代的 artifact / boundary 问题
- AG Core / Runtime / Tool 无需修改
- 只需迁移 `:integration-at` adapter + `ChatCompositionRoot` 的 AT 构造方式
- V4 public API 完整覆盖 AG 当前使用的 V3 能力（无能力缺口）

### 5.2 实施步骤（可独立验证）

#### Step 1: 基线冻结
- **输入：** 当前 AG V2 HEAD（a6eea61）
- **允许修改范围：** 无（只记录基线）
- **验收条件：** `git rev-parse HEAD` = a6eea61；worktree CLEAN；Session Continuity 测试绿
- **失败停止条件：** HEAD 不符或 worktree dirty → STOP

#### Step 2: Artifact Resolution
- **输入：** AT V4 bundle（`dist/AT.V4-CONSUMER-FREEZE-V1/`）
- **允许修改范围：** `third_party/`（新增 V4 m2 wrapper 或 file 依赖配置）
- **验收条件：** AG build 能解析 V4 artifacts（`at-engine-api` / `at-composition-android` / `at-cli` + 传递的 internal AARs）；不依赖 AT 源码仓库
- **失败停止条件：** artifact 解析失败 → 检查 POM 生成 / native libs 完整性

#### Step 3: Adapter Migration
- **输入：** `:integration-at` 当前 source + V4 public API
- **允许修改范围：** `integration-at/src/main/java/.../AtExecutionAdapter.kt`
- **验收条件：** adapter 只 import `com.at.engine.*` + `com.at.composition.android.*` + `com.at.cli.*`；不 import `com.adb.engine.*` / `com.at.backend.*`；不直接构造 ATEngine；不直接调 AdbRuntime / AdbConsumerBootstrap
- **失败停止条件：** 编译失败 → 检查 V4 API 映射

#### Step 4: Host Composition
- **输入：** `ChatCompositionRoot` + V4 `ATAndroid.create()`
- **允许修改范围：** `app/src/main/java/.../ChatCompositionRoot.kt` + `ShellConnection.kt`
- **验收条件：** Host 调用 `ATAndroid.create(context, ATAndroidConfig(identityProfile))` 获得 `ATAndroidComposition`；注入 adapter；adapter 不自行构造 ATEngine
- **失败停止条件：** composition 创建失败 → 检查 identity / Context

#### Step 5: Shell Tool Integration
- **输入：** `:tool-android-shell` 当前 source
- **允许修改范围：** 无（tool 已正确通过 adapter 消费）
- **验收条件：** `AndroidShellToolImpl` 编译通过；`ShellResultMapper` 映射正确
- **失败停止条件：** 编译失败 → 检查 ATExecutionResult 类型兼容性

#### Step 6: Dependency Closure
- **输入：** 迁移后的 build graph
- **允许修改范围：** 无（只验证）
- **验收条件：** `:ag-core` / `:ag-runtime` / `:tool-android-shell` 零 AT 内部 import；`:integration-at` 只依赖 `:at-engine-api` + `:at-composition-android` + `:at-cli`
- **失败停止条件：** 发现违规 import → 回退 Step 3/4

#### Step 7: Build and Tests
- **输入：** 迁移后的完整 build
- **允许修改范围：** 无（只构建 + 测试）
- **验收条件：** `./gradlew :ag-core:test :ag-runtime:test :tool-android-shell:test :app:assembleDebug` 全绿
- **失败停止条件：** 测试失败 → 定位回归

#### Step 8: Device Validation
- **输入：** 构建好的 AG APK + V4 artifacts
- **允许修改范围：** 无（只设备测试）
- **验收条件：** AG consumer 用自己的 identity（`at-consumer/identity`）执行 `adb shell id` → uid=2000(shell)；不复制 AT Product identity
- **失败停止条件：** 设备测试失败 → 检查 identity / ADB 连接

#### Step 9: Documentation Closure
- **输入：** 迁移后的状态
- **允许修改范围：** `docs/` / `AGENTS.md` / `settings.gradle.kts` 注释
- **验收条件：** 更新 AG SSOT / Roadmap / AGENTS.md；清除过期的 AT V3 指引（`third_party/at-consumer-freeze-v1` → V4）；删除 `third_party/at-engine-handoff-v0`（如确认 inactive）
- **失败停止条件：** 文档不一致 → 修正

---

## 6. 证据矩阵

### 6.1 Dependency Matrix

| 模块 | 当前直接依赖 | 当前 source imports | 当前职责 | 目标职责 | 违反目标边界？ | 推荐修改 | 证据位置 |
|------|-------------|-------------------|---------|---------|---------------|---------|---------|
| :ag-core | 无（零 production 依赖） | 零 AT/Android | Tool contract / Session / Run | 不变 | NO | 无 | `ag-core/src/main/kotlin/` |
| :ag-runtime | :ag-core + coroutines | 零 AT/Android | Agent lifecycle | 不变 | NO | 无 | `ag-runtime/src/main/kotlin/` |
| :integration-at | 5 个 V3 AARs（at-engine / at-backend-local / at-backend-adb / adb-engine / at-cli） | 11 个 forbidden（com.adb.engine.* + com.at.backend.*）+ 4 个 allowed（com.at.engine.* + com.at.cli.*） | AT 消费点 | 只依赖 :at-engine-api + :at-composition-android + :at-cli | **YES** | 迁移到 V4 public API | `integration-at/src/main/java/.../AtExecutionAdapter.kt` |
| :tool-android-shell | :ag-core + :integration-at + coroutines + serialization | com.androidagent.core.* + com.androidagent.integration.at.* + com.at.engine.ATExecutionResult | Concrete Shell Tool | 不变 | NO | 无 | `tool-android-shell/src/main/java/.../AndroidShellToolImpl.kt` |
| :app (AGA) | :ag-core + :ag-runtime + :provider + 3 tools + :integration-at + AndroidX | com.androidagent.* + android.content.Context | Composition Root | 调用 ATAndroid.create() 并注入 adapter | NO（当前不违规，需调整构造方式） | 改用 ATAndroid.create() | `app/src/main/java/.../ChatCompositionRoot.kt` |

### 6.2 API Mapping Matrix

| AG 当前调用（V3） | V4 public API | 参数/结果语义差异 | 能力缺口？ | 需 AG adapter 映射？ | 需 AT Contract 任务？ |
|-------------------|---------------|-----------------|-----------|---------------------|---------------------|
| `ATEngine(oneShot=AdbOneShotBackend, persistent=AdbPersistentBackend)` | `ATAndroid.create(context, config).adbEngine` | V3 concrete class → V4 interface + composition factory | NO | YES（adapter 构造方式） | NO |
| `ATEngine(oneShot=LocalOneShotBackend, persistent=LocalPersistentBackend)` | `ATAndroid.create(context, config).localEngine` | 同上 | NO | YES | NO |
| `AdbRuntime.initialize(context)` | `ATAndroid.create(context, ...)`（内部自动初始化） | V3 手动 → V4 composition 自动 | NO | YES（删除手动调用） | NO |
| `AdbRuntime.ensureReady(identityDir)` | `composition.adbControl.ensureReady(onStatusLine)` | 相同语义，identityDir 由 composition 管理 | NO | YES | NO |
| `AdbRuntime.readinessState()` | `composition.adbControl.readinessState()` | 类型名变更（AdbRuntimeState → ATAdbReadinessState） | NO | YES | NO |
| `AdbConsumerBootstrap(identityDir)` | `composition.adbControl`（内部持有） | V3 直接构造 → V4 composition facade | NO | YES（删除直接构造） | NO |
| `bootstrap.bootstrapState()` | `composition.adbControl.bootstrapState()` | 类型名变更（AdbBootstrapState → ATAdbBootstrapState） | NO | YES | NO |
| `bootstrap.pairDiscovered(code)` | `composition.adbControl.pairDiscovered(code)` | 结果类型变更（AdbPairingResultV2 → ATAdbPairingResult） | NO | YES | NO |
| `ATPersistentBackend.Launch(command, rows, cols)` | `ATSessionLaunch(command, initialRows, initialCols)` | 类型名变更 | NO | YES | NO |
| `ATEngine.execute(ATExecutionRequest)` | `ATEngine.execute(ATExecutionRequest)` | 相同 | NO | NO | NO |
| `ATEngine.openSession(Launch)` | `ATEngine.openSession(ATSessionLaunch)` | Launch 类型名变更 | NO | YES | NO |
| `ATTerminalSession` | `ATTerminalSession` | 相同（write/read/resize/close/setListener/state） | NO | NO | NO |
| `ATExecutionResult` | `ATExecutionResult` | 相同（outcome/stdout/stderr/exitCode/hasError/error） | NO | NO | NO |

**结论：无能力缺口。所有 V3 API 都有 V4 对应。AG adapter 需做类型名映射（AdbRuntimeState→ATAdbReadinessState 等）和构造方式迁移。**

### 6.3 Ownership Matrix

| 职责 | 唯一责任方 | 说明 |
|------|-----------|------|
| AG Session / Run | :ag-core + :ag-runtime | 不变 |
| Tool selection / approval | :ag-core (ToolRegistry) + :app (Host) | 不变 |
| AT Runtime lifecycle | AT（AdbRuntime，内部） | Consumer 不拥有 |
| AT identity / pairing | AT（AdbConsumerBootstrap，内部） | Consumer 通过 adbControl facade 触发 |
| AT recovery | AT（AdbRecoveryCoordinator，内部） | Consumer 不拥有 |
| Android Composition | :app (AGA Composition Root) | Host 调用 ATAndroid.create() |
| UI / user-action handling | :app (AGA) | Host 展示 readiness / pairing 状态 |
| persistence backend | :ag-core (Session) | 不变 |

### 6.4 Build / Artifact Matrix

| V4 bundle 文件 | 哈希（SHA256 前 16） | 依赖闭包 | AG 解析方式 | 构建兼容性 | Native packaging 风险 |
|---------------|---------------------|---------|------------|-----------|---------------------|
| at-engine-api.jar | b6b28fb1a88eb313 | REQUIRED | m2 wrapper 或 file dep | YES（JAR） | 无 native |
| at-engine-spi.jar | 04bf7569f66a15d4 | internal（transitive） | m2 wrapper | YES（JAR） | 无 native |
| at-engine-debug.aar | bb46d47d5e04159e | internal（transitive） | m2 wrapper | YES（AAR） | 无 native |
| at-backend-local-debug.aar | 684f282423b498dd | internal（transitive） | m2 wrapper | YES（AAR） | libat_local_backend.so（arm64-v8a） |
| at-backend-adb-debug.aar | 5e60f66eb61c5126 | internal（transitive） | m2 wrapper | YES（AAR） | 无 native |
| adb-engine-debug.aar | 2a3edde073a88405 | internal（transitive） | m2 wrapper | YES（AAR） | libadbd_engine.so（arm64-v8a） |
| at-composition-android-debug.aar | d3d13476a1b94f7d | REQUIRED | m2 wrapper | YES（AAR） | 无 native |
| at-cli-debug.aar | e301389d0110d63f | OPTIONAL | m2 wrapper | YES（AAR） | libatcli_env.so（arm64-v8a） |

**风险：**
1. V4 bundle 是 **debug** 制品（`*-debug.aar`），AG 当前用 **release** V3 AARs。需确认 debug 制品可用于 AG build，或 AT 侧生成 release bundle。
2. V4 bundle 无 POM 文件。AG 需生成 m2 wrapper（POM + AAR）或直接 file 依赖。
3. Native libs 是 arm64-v8a only（与 AG 当前一致，AGA 无其他 ABI）。

---

## 7. Final Verdict

```
VERDICT = PASS_WITH_GAPS

AG_BASELINE_VERIFIED = YES
SESSION_CONTINUITY_BASELINE_PRESERVED = YES

LEGACY_AT_V3_DEPENDENCY_STATUS = ACTIVE（:integration-at 直接依赖 5 个 V3 AARs）
LEGACY_HANDOFF_BUNDLE_STATUS = SUPERSEDED（at-engine-handoff-v0 inactive）

V4_PUBLIC_API_CONSUMABLE = YES
V4_ANDROID_COMPOSITION_CONSUMABLE = YES
V4_ARTIFACT_CLOSURE = PASS
DEBUG_VS_RELEASE_READINESS = GAP（V4 bundle 是 debug，AG 当前用 release）

AG_CORE_BOUNDARY = PASS
AG_RUNTIME_BOUNDARY = PASS
AT_ADAPTER_BOUNDARY = FAIL（直接 import 11 个 forbidden 类型 + 依赖 4 个 forbidden modules）
AGA_COMPOSITION_BOUNDARY = PASS（需调整构造方式）
SHELL_TOOL_BOUNDARY = PASS

IDENTITY_OWNERSHIP = PASS（AG 有自己的 app-private identity）
LIFECYCLE_OWNERSHIP = PASS（AT 拥有 ADB lifecycle，AG 通过 facade 触发）
ERROR_SEMANTICS_MAPPING = PASS（ToolResult.success 与 ATExecutionResult.outcome 对齐）
EXECUTION_CAPABILITY_MAPPING = PASS（V4 完整覆盖 V3 能力）

SOURCE_CHANGES = 0
BUILD_CHANGES = 0
DEVICE_PAIRING = NOT_RUN
GIT_COMMIT = NO
PUSH = NO

RECOMMENDED_NEXT_TASK = AG.AT-V4-INTEGRATION-1 — Replace Legacy AT Consumption with V4 Consumer Boundary
NEXT_OPEN_AUTHORIZED = NO
STOP = YES
```

### 最终结论

**AT V4 是否已解决此前 AG 提出的物理边界问题：**
YES。V4 通过 `:at-engine-api`（public contract）+ `:at-composition-android`（assembly root）+ `:at-cli`（optional frontend）的 consumer boundary，解决了 V3 时代的 artifact 交付、transitive dependency、consumer isolation 问题。V4-5 的 machine-enforced architecture gates 确保 consumer 不直接依赖 forbidden modules。V4-6 的 18/18 parity gates 证明 V3→V4 语义 delta = NONE。

**AG 当前哪些部分可以原样保留：**
- `:ag-core`（Tool contract / Session / Run）— 零修改
- `:ag-runtime`（Agent lifecycle）— 零修改
- `:tool-android-shell`（AndroidShellToolImpl + ShellConnection）— 零修改（已通过 adapter 消费）
- `:tool-network` / `:tool-file` — 零修改（无 AT 依赖）
- `:app` ChatCompositionRoot 的 Tool 注册 / Provider 装配 — 保留（只调整 AT 构造方式）

**AG 哪些依赖和代码必须迁移：**
- `:integration-at` build.gradle.kts：5 个 V3 AARs → `:at-engine-api` + `:at-composition-android` + `:at-cli`
- `:integration-at` AtExecutionAdapter.kt：删除 11 个 forbidden imports；改用 `ATAndroidComposition` + `ATAndroidAdbControl`
- `:app` ChatCompositionRoot.kt + ShellConnection.kt：改用 `ATAndroid.create(context, config)` 构造 composition 并注入 adapter

**哪些旧文档需要更新：**
- `AGENTS.md`：`third_party/at-consumer-freeze-v1`（V3）→ V4 bundle；`AT_CONSUMER_INTEGRATION` 从 `AT.AG-CONSUMER-FREEZE-V1` → `AT.V4-CONSUMER-FREEZE-V1`
- `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`：更新 AT 版本 + artifact 信息
- `:tool-android-shell/build.gradle.kts` header 注释：删除过期的 `includeBuild("../AndroidTerminal-V3")` 注释
- `settings.gradle.kts`：更新 m2 repository URL（V3 → V4）

**具体下一步如何实施和验收：**
见 §5.2 的 9 个步骤。每步有明确的输入、允许修改范围、验收条件和失败停止条件。

**是否存在需要先由 Controller 决策的阻塞项：**
1. **DEBUG_VS_RELEASE_READINESS**：V4 bundle 是 debug 制品。需 Controller 决策：(a) AG 接受 debug 制品（开发阶段）；(b) AT 侧生成 release bundle；(c) AG 从 V4 source 构建 release AARs。
2. **Artifact Resolution 方式**：V4 bundle 无 POM。需 Controller 决策：(a) AG 生成 m2 wrapper（POM + AAR）；(b) AG 用 file 依赖；(c) AT 侧做 Maven publication（AT.ARTIFACT-BUNDLE-0，DEFERRED）。

STOP.
