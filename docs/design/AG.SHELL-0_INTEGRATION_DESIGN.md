# AG.SHELL-0 — AndroidShellTool × AT Runtime / AT Core Integration Design

> 状态：**DESIGN ONLY**。未实现 Tool、未添加 AT 依赖、未修改任何仓库代码。
> 本文冻结 AndroidAgent 作为**第三个独立 Android consumer** 接入已验证
> Runtime + AT Core 的方式。
> 依赖方向：**AG → AT，单向。AT 不知道 AG 存在。**

---

## 1. 两个仓库基线（实测）

| | AndroidAgent | AndroidTerminal-V2 |
|---|---|---|
| branch | `main` | `main` |
| HEAD | `2c8306c` | `3ffc30e` |
| tag | `ag-aga-0` → `2c8306c` | `at-v2-baseline-0` |
| remote | none | none |
| status | `?? docs/design/` | `M` ×2 文档 + `?? uploda/` |

> **用户资产**：`AndroidAgent/upload/`、`AndroidTerminal-V2/uploda/`
> 以及 AT 仓库中 2 个已被用户修改的文档 —— 均为 **USER OWNED**，
> 本轮未触碰、未暂存、未删除。
> AT 仓库的 tracked diff **在本轮开始前即已存在**，非本轮产生。

AT 关键提交链：`a96664e` 抽取 AT Core → `7f76045` 新增 TestHost → `3ffc30e` 4B 真机验证。

---

## 2. AT 已验证的架构（源码实测）

```text
:at-core   (com.android.library, namespace com.androidterminal)
   ├─ TerminalRuntime.kt   61 行   object（进程单例）
   ├─ TerminalResult.kt   100 行
   ├─ AdbManager.kt       120 行   JNI 桥
   ├─ cpp/runtime/MinimalRuntime.{h,cpp}
   ├─ cpp/at_core/、cpp/jni/、cpp/adb/
   └─ cpp/third_party/boringssl/  (含 libboringssl.a 4.3 MB)

:app        implementation(project(":at-core"))   AT.apk
:at-test-host implementation(project(":at-core"))   TestHost.apk
             注释明确：NO AT Core impl / NO ADB / NO TLS / NO JNI
             注释明确：Never project(":app")
```

`at-core/build.gradle.kts:4-12` 明确：
`APP-INDEPENDENT` · 每个 consumer 内嵌自己的副本并拥有自己的 Runtime/Core/TCP/TLS/ADB 状态 · `NOT required to leave Android`。

### 2.1 TerminalRuntime 真实公开面（`TerminalRuntime.kt`）

```kotlin
object TerminalRuntime {                                    // ← object，非可注入实例
    fun connect(host: String, port: Int, keyDir: String): Boolean   // 阻塞
    fun disconnect()                                                  // 阻塞，幂等
    fun connectionState(): ConnectionState                            // 阻塞，不因未连接抛异常
    fun execute(command: String, timeoutMs: Int = 0): TerminalResult  // 阻塞
}
```

**关键有利事实**：`keyDir` 是**普通路径字符串**，注释明写
*"so the Runtime never depends on an Android Context"*。
→ AG 只需传 `context.filesDir.absolutePath`，**无需向 AT 传 Context**。

### 2.2 TerminalResult 真实字段（`TerminalResult.kt:61-67`）

```kotlin
data class TerminalResult(
    outcome: ShellOutcome,   // COMPLETED / TIMED_OUT / CANCELLED /
                             // TRANSPORT_ERROR / PROTOCOL_ERROR / UNSUPPORTED
    stdout: String,
    stderr: String,
    exitCode: Int,
    hasError: Boolean,
    error: String?,
)
// 便捷属性：coreFailed = outcome != COMPLETED ; succeeded = COMPLETED && exitCode == 0
```

类注释（`:6-9`）明确：
*"a device command that exits non-zero is COMPLETED, not a failure"*
*"lets an Agent Tool trust stderr as genuine device stderr"*。

### 2.3 native 串行保证（`MinimalRuntime.h`）

```cpp
static MinimalRuntime& instance();      // 进程单例，native 侧唯一持有 Core
mutable std::mutex gate_;              // 覆盖 connect / disconnect / execute / connection_state
```

`RUNTIME_INSTANCE_MODEL = PROCESS_SCOPED_SINGLE` · `ACTIVITY_OWNS_RUNTIME = NO` ·
`SERIAL_EXECUTION_ONLY = TRUE`。
理由（`:21-27`）：AT Core 非线程安全，`allocateLocalId()` 是非原子读改写，
`TcpTransport` 是单阻塞 fd；一个 `std::mutex` 足够且可证明正确。

### 2.4 4B 已验证事实

```text
keyDir 模式        = <app>/files（adbkey / adbkey.pub / adb-home 存于此）
AT.apk  keyDir     = /data/user/0/com.androidterminal/files        (u0_a291)
TestHost keyDir    = /data/user/0/com.androidterminal.testhost/files (u0_a298)
endpoint           = 127.0.0.1:<adbd 动态 TLS 端口>，由 UI 显式注入
TestHost 未使用 mDNS
adb tcpip 5555     = 另一条明文 AUTH 端口，不等价于 AT TLS Golden Chain
pairing            = AT Core 未实现；TestHost 通过 run-as 从 AT.apk sandbox
                     临时复制已被设备信任的 adbkey —— 这是 TEST FIXTURE
```

---

## 3. 冻结拓扑

```text
AGA / AG.apk
    ↓
Agent Runtime (:ag-runtime)
    ↓
AG Core (:ag-core)
    ↓  Tool Registry / Tool contract
AndroidShellToolImpl                ← 唯一 AG→Android execution integration point
    ↓
TerminalRuntime   (object, 进程单例)
    ↓
Native MinimalRuntime (mutex 串行)
    ↓
AT Core → ADB/TLS → adbd → Android
```

禁止方向（必须保持）：

```text
AG Core        -X-> Android / AT Core / TerminalRuntime / ADB
Agent Runtime  -X-> TerminalRuntime / AT Core / Android
AG.apk         -X-> AT.apk（不启动、不 bind、不抓 logcat、不抓 UI）
AT Core        -X-> Agent logic
ShellTool      -X-> 业务流程逻辑（不 planning、不解析 package、不选下一命令）
```

---

## 4. D1 — 跨 repo artifact delivery

### 实测：AAR 是自包含的

```text
at-core\build\outputs\aar\at-core-debug.aar      1,247 KB
   R.txt                                          0
   AndroidManifest.xml                          880
   classes.jar                              12,398   ← 3 个 Kotlin 类
   aar-metadata.properties                     156
   jni/arm64-v8a/libandroidterminaladb.so 2,156,320   ← 完整 native runtime
   jni/arm64-v8a/libc++_shared.so         1,292,896
   jni/arm64-v8a/libnetwork_probe.so         11,200
```

**结论**：AAR 同时携带 Kotlin facade 与 native runtime。AG 引入 AAR 即可获得
完整能力，无需源码、无需 AT 的 CMake/NDK 配置。

### 方案比较

| 维度 | **A. versioned local AAR** | B. local Maven | C. composite / includeBuild |
|---|---|---|---|
| source coupling | **无**（二进制） | 无 | **高**（AG 可直接改 AT 源码） |
| build reproducibility | 高（文件固定） | 高 | 中（随 AT 源码漂移） |
| version pinning | **强**（SHA256 + commit） | 强 | 弱 |
| developer workflow | 简单 | 需 publish 步骤 | 简单但危险 |
| CI feasibility | 中（需先取 artifact） | 好 | 中 |
| JNI/.so packaging | **AAR 已含** | 同 | 由 AT build 决定 |
| consumer simplicity | **最高**（一个文件） | 需仓库配置 | 需 settings 改造 |
| 误改 AT 源码风险 | **无** | 无 | **有** |

### 决策：**Option A — versioned local AAR**

排除 §7.1 全部禁止项：未复制源码、未相对路径 import AT source tree、未重写 JNI/ADB/TLS、未依赖 AT.apk、未复制 `terminal_jni.cpp`。

**版本身份（D1 附属）**：AG 侧记录三元组

```text
AT Git commit          3ffc30e
AAR SHA256             构建后计算，写入 AG 设计/实现文档
AAR file name          at-core-debug.aar
```

**不引入 artifact registry。** 三元组足以回答"AG.apk 内嵌的是哪一个 AT Core"。

**注意（本轮不实施）**：AT 侧尚未配置 `maven-publish`。若未来需要更平滑的
交付，应在 **AT 侧**新增 publish 任务 + 本地 maven 目录（属独立 AT 步骤）。
本轮 AG 只消费 AAR 文件，不要求 AT 改动。

---

## 5. D2 — AndroidShellToolImpl 放置位置

### 决策：**`:app`**，package `com.androidagent.app.tool`

```text
app/src/main/java/com/androidagent/app/tool/AndroidShellToolImpl.kt
```

理由：

| 候选评估 | 结论 |
|---|---|
| UI coupling | **无** —— Tool 不引用 Activity/View/Context（仅用 Application context 取 filesDir，属 bootstrap 而非 UI） |
| Composition Root coupling | **无** —— Tool 由 CR 构造并注册，Tool 不反向引用 CR |
| testability | L2 靠 tool-local seam 解决（见 D9），不需要独立 module |
| dependency cycle | **无** —— `:app → :at-core` 单向 |
| 现有先例 | `AndroidOpenAiHttpTransport` 已位于 `:app`（AGA-0.R1 决策） |

**不新建** `:android-tools` / `:tool-shell` / `:at-integration` / `:execution-runtime`
—— 当前只有一个真实 consumer，无真实需求支撑新 module（§46 Complexity Gate）。

---

## 6. D3 / D4 — Runtime 与连接 ownership

### D3：Runtime ownership

`TerminalRuntime` 是 Kotlin `object`，**Kotlin 侧不持有任何 native handle**
（源码注释：*"there is no native handle for callers to hold or release"*。
真正的 owner 是 native `MinimalRuntime::instance()`，进程级。

因此 AG **不"拥有"** `TerminalRuntime`，而是**在进程内 bootstrap 它**：

```text
Owner（bootstrap）  : AGA Composition Root
被 bootstrap 的对象 : TerminalRuntime（进程单例，AG 不构造、不释放）
禁止                : Activity owns Core / Tool owns Core / AG Core owns Core
```

### D4：连接 ownership

| 动作 | 负责人 |
|---|---|
| `connect(host, port, keyDir)` | **AGA host / Composition Root**（bootstrap 阶段） |
| `connectionState()` | AGA host（状态显示）；ShellTool **不**调用 |
| `disconnect()` | AGA host（显式 / 进程退出） |
| `execute()` | **仅 ShellTool** |

ShellTool v0 **只做 execute**。不得自动 discover / pair / connect / retry / reconnect（§18 Option A）。

---

## 7. D5 — Endpoint 策略（禁止 hard-code）

| 层 | 策略 | 理由 |
|---|---|---|
| **L3** | **显式注入**（测试 fixture 提供） | 目标只是证明 AG→ShellTool→Runtime→AT Core→Android 链路 |
| **AGENT-0 E2E** | **显式注入**（同上） | 自动 discovery **不是**证明 Agent 行为的必要前置条件 → **DEFER** |
| **Production** | **DEFERRED** | 属 terminal bootstrap 产品问题，不属 Agentness |

**禁止**：hard-code `41729` / `5555`；把 endpoint 放进 tool arguments。

关键区分：`adb tcpip 5555` 是**另一条明文 AUTH 路径**，不等价于 AT TLS Golden Chain，
不能作为替代。

---

## 8. D6 — Auth / keyDir 策略

### keyDir（D6 主体）

```text
keyDir = context.filesDir.absolutePath      （AG 自己的 sandbox）
AT 侧约定 = <app>/files，adbkey 存于此 → 形状天然吻合
```

**禁止** AG 访问 `/data/user/0/com.androidterminal/...`（正常路径必须 sandbox 独立）。

### 分层

| 层 | 策略 | 标注 |
|---|---|---|
| **L3** | 允许 controlled trusted-key fixture（沿用 4B 手法：临时复制已被信任的 adbkey 到 AG sandbox） | test only · 不进 repo / docs / APK resource · 不写死 · 测试后 cleanup |
| **AGENT-0** | 同上 | **AGENT-0 PASS ≠ production pairing/bootstrap 已解决** |
| **Product** | **DEFERRED PRODUCT BOOTSTRAP ISSUE** —— 当前 AT 无正式 pairing 能力 | 本轮不实现 pairing |

**不得**因缺 production pairing UX 就阻塞 ShellTool 实现（§38）。

---

## 9. D7 — Shell arguments

```json
{ "command": "..." }
```

**足够。** 已确认无需：`cwd` / `env` / `user` / `shell` / `timeout` / `retry` /
`requestId` / `sessionId` / `stdin` / `pty` / `stream` / `background` /
`encoding` / `workingDir` / 文件 API / `push` / `pull` / `forward` / `reverse`。

**timeout 明确不暴露**：AT `execute(command, timeoutMs = 0)` 中
`timeoutMs > 0` 会返回 `UNSUPPORTED`（源码注释明写 *"never silently ignored"*）。
AG 调用时**必须传 0**（默认值）。不得伪造 timeout，不得静默忽略模型 timeout。

---

## 10. D8 — Result mapping 精确表

基于 `TerminalResult` 真实字段与 `ShellOutcome` 真实枚举：

| AT `outcome` | exitCode | hasError | AG `ToolResult.success` | AG output / error |
|---|---:|---|---:|---|
| `COMPLETED` | 0 | false | **true** | output: exitCode + stdout + stderr |
| `COMPLETED` | ≠0（如 7） | false | **true** | output: exitCode=7 + stdout + stderr |
| `COMPLETED` | any | false，stderr 非空 | **true** | stderr 作为**设备进程输出**原样保留 |
| `TRANSPORT_ERROR` | — | true | **false** | error: `error` |
| `PROTOCOL_ERROR` | — | true | **false** | error: `error` |
| `UNSUPPORTED` | — | true | **false** | error: `error` |
| `TIMED_OUT` | — | true | **false** | error: `error` |
| `CANCELLED` | — | true | **false** | error: `error` |

判定规则（可直接编码）：

```text
success = (outcome == COMPLETED)
```

依据：`TerminalResult.coreFailed = outcome != COMPLETED`（源码既有属性）。

**该映射与 AG 冻结语义完全一致**：`ToolResult.success` = invocation 机制成功；
命令 non-zero 退出**不**降级为机制失败（`Tool.kt:44-56` + `TerminalResult.kt:6-9`
两侧独立声明了同一语义）。

**未连接时执行**：Tool 应先读 `connectionState()`；非 ONLINE 则返回
`success=false`（mechanism failure），由模型决定下一步（§18 Option A）。

---

## 11. D8b — Tool output shape

**选择：plain structured text（非 JSON）。**

```text
exitCode: 7
stdout:
<stdout>
stderr:
<stderr>
```

| 候选 | 评价 |
|---|---|
| **plain structured text** | **选用**。模型已擅长解析；无需 DTO；stdout/stderr 天然分栏不混淆；exitCode 不丢；空 stdout / 空 stderr / non-zero 三者可区分 |
| JSON | 更机器可解析，但需模型做一次 parse；且引入"AG 侧 JSON 契约"这一层无 consumer 的结构 |

约束遵守：不修改 AG `ToolResult` contract、不创建 Core DTO、stdout/stderr 不混淆、exitCode 不丢失。

空值可区分性：空 stdout 输出空的 `stdout:` 段；无 stderr 输出空的 `stderr:` 段；
non-zero 通过 `exitCode:` 行体现。

---

## 12. D8c — Invalid arguments 映射

属 **Tool invocation / argument failure**，不是 AT execution failure。

| 输入 | 处理 | AG ToolResult |
|---|---|---|
| `arguments = ""` | 解析失败 | **success=false**，error: 明确说明期望 JSON |
| `arguments = "{}"` | 缺 `command` | **success=false** |
| `arguments` 非 JSON | 解析失败 | **success=false** |
| `command` 缺失 | 同上 | **success=false** |
| `command` 非字符串 | 类型错误 | **success=false** |
| `command = ""` / 空白 | 空命令 | **success=false** |

统一走 AG 既有冻结语义：`ToolResult(success=false, output="", error=...)`。
**不新增 AG 类型**（§23 要求以当前 AG Tool contract 为准）。

---

## 13. D9 — L2 测试策略

### 真实障碍

`TerminalRuntime` 是 Kotlin **`object`**（静态方法，**不可注入**），且
`AdbManager` 走 `System.loadLibrary` → **无法在 JVM unit test 中调用**。

因此若 `AndroidShellToolImpl` 直接静态调用 `TerminalRuntime.execute`，
**L2 deterministic mapping 测试根本无法进行**。

### 方案比较

| 方案 | 评价 |
|---|---|
| A. Android instrumentation L2 | 可行但**无法注入结果**，无法覆盖 mapping 分支 |
| **B. minimal package-private / tool-local seam** | **选用** |
| C. tiny tool-local executor interface | 与 B 等价，interface 形式更明确 |
| D. 只靠 L3 覆盖 | mapping 分支（非零退出、mechanism failure、invalid args）将**完全无测试** → 不可接受 |

### 决策：**C —— tool-local `TerminalExecutor` seam**

概念形态（**本轮只设计，不实现**）：

```kotlin
// tool-local，仅服务 AndroidShellToolImpl 的可测性
internal fun interface TerminalExecutor {
    fun execute(command: String, timeoutMs: Int): TerminalResult
}
```

约束（§29）：

```text
不是 universal backend abstraction
不是 AG Core contract
不进入 :ag-core
只服务 AndroidShellToolImpl testability
默认实现 = TerminalRuntime::execute
```

`AndroidShellToolImpl(executor: TerminalExecutor = TerminalExecutor { c, t -> TerminalRuntime.execute(c, t) })`
—— **一个默认构造参数**，L2 注入 lambda 即可覆盖全部分支。

---

## 14. D10 — L2 必测矩阵

| # | 用例 | 期望 |
|---|---|---|
| L2-1 | 合法 command + exit 0 + COMPLETED | success=true；output 含 `exitCode: 0` |
| L2-2 | 仅 stdout | success=true；`stderr:` 段为空 |
| L2-3 | 仅 stderr | success=true；**stderr 内容保留** |
| L2-4 | stdout + stderr 同时存在 | success=true；两者分栏不混淆 |
| L2-5 | `exitCode=7` + COMPLETED | **success=true**（机制成功 / 命令失败） |
| L2-6 | TRANSPORT_ERROR | success=false；error 取 `error` |
| L2-7 | PROTOCOL_ERROR | success=false |
| L2-8 | `arguments` 非法 JSON | success=false |
| L2-9 | 缺 `command` | success=false |
| L2-10 | `command` 为空白 | success=false |
| L2-11 | 非 ONLINE 状态执行 | success=false（mechanism） |
| L2-12 | timeoutMs 恒为 0 | 断言 executor 收到 `timeoutMs == 0` |

---

## 15. D10b — L3 集成测试设计

### 目标（§32）

```text
command = id   →  uid=2000(shell)
stdout 通道 / stderr 通道 / exit nonzero / mechanism failure
```

不要求 real LLM。**不重复 4B 的 31/31 + 32/32 全量**。

### 载体决策

| 候选 | 评价 |
|---|---|
| **`:app` 的 androidTest** | **选用**。无需新 module、无需新 app entry point；只需加 `androidx.test` 依赖（test-only） |
| 新建 `:ag-test-host` module | 需新 module + 新 APK（§46 Complexity Gate 计一次）；只有在需要 UI 驱动 harness 时才必要 —— L3 不需要 |

### Fixture

```text
endpoint  : 显式注入 127.0.0.1:<adbd TLS 端口>   （test only）
keyDir    : AG 自己的 context.filesDir
key       : controlled trusted-key fixture（沿用 4B 手法）
cleanup   : 测试后清除 fixture key
```

明确标注：**test only，不是 production contract。**

### 最小 smoke（保留 4B 结论不被破坏）

```text
command = id                → uid=2000(shell)
libandroidterminaladb.so    存在于 AG.apk
libadb.so                   不存在
```

数量按实际实现确定，本轮不写死。

### 顺序强制（§35）

```text
L2 PASS  →  L3 PASS  →  AGENT-0
```

失败时即可区分：Tool bug / AT integration bug / Provider bug / Agent reasoning bug。

---

## 16. AGENT-0 兼容性

```text
ShellTool 是否足够支撑 AGENT-0        : YES（唯一需要的能力）
需要新增 AG Core feature             : NONE
需要新增 Agent Runtime feature       : NONE
阻塞问题                             : NONE
```

AGENT-0 的因果要求（Observation A 含 package P → Action B 参数含 P）
由已冻结的 Agent Loop 直接满足；ShellTool 只是把 `P` 变成真实 stdout。

**明确不做**：不新增 `GetForegroundAppTool` / `PackageTool` / `ProcessTool` /
`FileTool`（§37）。

---

## 17. Complexity Gate 自检

| 项 | 数量 |
|---|---|
| 新 Gradle module | 0 |
| 新 backend hierarchy | 0 |
| 新 runtime abstraction | 0（复用 AT `TerminalRuntime`） |
| 新 service / IPC | 0 |
| 新 lifecycle manager | 0 |
| 新 request/result DTO hierarchy | 0（复用 `TerminalResult`） |
| 新 retry manager | 0 |

**唯一新增抽象**：tool-local `TerminalExecutor`（1 个 fun interface，仅为 L2 可测性）。

预期实现形态（§47 校验）：

```text
Composition Root
   ├─ configure TerminalRuntime bootstrap（connect + keyDir + endpoint）
   └─ register AndroidShellToolImpl
                 │
                 ▼
        TerminalExecutor → TerminalRuntime.execute(...)
                 │
                 ▼
          TerminalResult
                 │
                 ▼
           AG ToolResult
```

与预期一致，未过度设计。

---

## 18. D11 — Deferred

```text
PTY / streaming / interactive stdin / background process / file transfer
timeout 实现（AT 当前 timeout>0 → UNSUPPORTED）
production pairing / 首次 ADB 信任引导
automatic endpoint discovery（mDNS）
reconnect policy（v0 采用 Option A：失败即报，不自动重连）
Service / Binder / AIDL / IPC 机制
per-session Runtime / connection pool / multi-Core manager
Tool schema typed arguments
failure-path E2E 验收        → 候选 AGENT-0.R1
```

---

## 19. D12 — Implementation readiness

```text
READY_FOR_AG_SHELL_0_IMPLEMENTATION
```

依据：AT 公开面（4 个方法 + 6 字段结果）已足够；AAR 自包含已实测；
串行保证已在 native 侧；AG 侧无需改 Core / Runtime / Provider。

---

## 20. 未在本轮冻结（明确后置）

```text
AT 用 AAR 还是 maven / IPC   → 属后续 AT integration design
ShellTool 如何被 AT 调用     → 本设计已定为"AG 进程内 embed"
AT Runtime 如何部署          → 本设计已定为"每 APK 进程内 embed"
```
