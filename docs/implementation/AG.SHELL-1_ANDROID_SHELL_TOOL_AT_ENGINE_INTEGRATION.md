# AG.SHELL-1 — AndroidShellToolImpl × AT Engine Handoff Bundle v0 正式集成

> 类型：PRODUCTION IMPLEMENTATION · CROSS-REPO BINARY INTEGRATION · ANDROID TOOL ·
> REAL-DEVICE TOOL ACCEPTANCE。
> 前置：`AG.SHELL-1.PREFLIGHT` = PASS（bundle 接收验收）。
> 三个不变量：① AG Core / Agent Runtime / AGA Host 永远不直接依赖 AT implementation；
> ② `:tool-android-shell` 是唯一 AT Engine consumer；一次 ToolCall ≤ 一次 execute；
> ③ AT Engine Bundle 是外部已验证 binary dependency（不复制 AT source、不改 frozen contract）。

---

## 1. Baseline

```text
AndroidAgent : main @ 780fc5b（Commit P 之后）+ 本 commit
AT Bundle    : at-engine-handoff-v0（sourceTreeSha256 ff0a6432…，dirty HEAD 726a441）
设备         : APLMUT4312005935 / HONOR ALI-AN00 / Android 15 / arm64-v8a / user build
```

## 2. Bundle Identity（vendored）

```text
location : third_party/at-engine-handoff-v0/（byte-for-byte 复制自 AT dist）
verifier : PASS=18 FAIL=0 exit=0（vendoring 后复跑）
secret   : 强私钥标记全 bundle 0 命中（vendoring 后再扫）
runtime SHA : 1affba2e…c22    core SHA : 67015cfa…e55（m2 内 AAR 与 SHA256SUMS 一致）
```

## 3. Artifact Wiring（probe → 最终方案）

```text
probe 结论：com.android.library 中本地 AAR 依赖可行。
最终方案：repo-local Maven metadata wrapper
  third_party/at-engine-handoff-v0/m2/
    com/androidterminal/at-runtime/0.1.0/at-runtime-0.1.0.aar  （byte-identical）
    com/androidterminal/at-runtime/0.1.0/at-runtime-0.1.0.pom
    com/androidterminal/at-core/0.1.0/at-core-0.1.0.aar       （byte-identical）
    com/androidterminal/at-core/0.1.0/at-core-0.1.0.pom
  settings.gradle.kts：dependencyResolutionManagement 增加
    maven { url = uri("third_party/at-engine-handoff-v0/m2") }
  AAR bytes：从未重打包（hash == SHA256SUMS）
  绝对 AT 路径：0（无 D:\...\AndroidTerminal-V2）
  includeBuild / source vendoring / project 依赖：无
```

## 4. Module Graph

```text
:app ──▶ :tool-android-shell ──▶ :ag-core（Tool contract）
                              └─▶ com.androidterminal:at-runtime（stable contract）
                                 └─▶ com.androidterminal:at-core（transitive，POM compile）

:app ──▶ :ag-core / :ag-runtime / :provider-openai-compatible（不变）
:app / :ag-core / :ag-runtime ─X─ AT（:app 构建文件仅注释提及，无真实依赖）
:tool-android-shell -X- :app / :ag-runtime / provider 模块
```

## 5. `:tool-android-shell`

```text
namespace : com.androidagent.tool.androidshell
类型      : com.android.library，minSdk 26 / compileSdk 34 / JVM 17
依赖      : :ag-core · com.androidterminal:at-runtime:0.1.0 · kotlinx-serialization-json ·
            kotlinx-coroutines-core（+ JUnit5 / coroutines test）
```

## 6. AndroidShellToolImpl Contract

```text
tool name : android_shell
implements: com.androidagent.core.Tool
schema v0 : {"command":"..."}（无 cwd/env/timeout/stdin/PTY/stream/background）
职责      : ToolCall.arguments -> parse -> AtRuntime.execute(command, 0) -> AtExecutionResult -> ToolResult
禁止      : connectAuto / initialize / disconnect / discover / recover / retry / endpoint/port 检查
线程      : withContext(Dispatchers.IO)（Tool.execute 为 suspend，避免阻塞调用线程）
seam      : Executor fun interface（tool-local，test-only fake；生产 = AtRuntime.execute）
invalid   : invalid JSON / missing command / command 非 string / blank  → success=false + 0 次 execute
```

## 7. ShellConnection Contract

```text
public API : initialize() · suspend connect(): Status · readiness(): Status · suspend disconnect(): Status
Status     : data class Status(ready: Boolean, code: String, message: String)  ← integration-owned
AT-owned 泄漏 : 0（不暴露 AtRuntime / AtRuntimeDiagnostic / AtReadinessState）
keyDir     : appContext.filesDir/at-identity（AGA sandbox 私有目录）
ownership  : Host 决定 policy；ShellConnection 执行 AtRuntime.initialize/connectAuto/readinessState/disconnect
```

## 8. Identity Precondition（v0）

```text
生产前提：Wireless Debugging enabled + trusted identity already provisioned（pairing = Slice C，DEFERRED）
AGA keyDir：filesDir/at-identity（不复制 AT.apk adbkey；不提交私钥）
真机验收：TEST-ONLY provisioning —— PC 已授权 adbkey 经 run-as 管道注入 sandbox，
         测试后删除（本任务 §11）。
```

## 9. Result Mapping（冻结，与 AT.ENGINE-AG0 一致）

```text
AT COMPLETED                 -> ToolResult.success = true（含 exitCode != 0，如 exit 7）
AT TRANSPORT_ERROR           -> success = false
AT PROTOCOL_ERROR            -> success = false
AT UNSUPPORTED               -> success = false
AT TIMED_OUT / CANCELLED     -> success = false（v0 不可达，mapper 保留完整）

payload（deterministic）：
  {"outcome":"COMPLETED","exitCode":7,"stdout":"...","stderr":"...","hasError":false[, "error":"..."]}
stderr 永远不提升为 ToolResult.error；只有 AT mechanism failure 才进 error。
```

## 10. No-Replay（HARD）

```text
一次 ToolCall <= 一次 AtRuntime.execute()。
TRANSPORT_ERROR -> 返回原失败 ToolResult -> 进 AG history -> Model 决定下一步。
AT Runtime 自行恢复连接（不重放命令）。U12/U13 单测验证 call count == 1。
```

## 11. Host Integration（:app 最小接入）

```text
ChatCompositionRoot(config, applicationContext)：
  toolRegistry.register(AndroidShellToolImpl())   ← 0 concrete tools -> 1（android_shell）
  val shell: ShellConnection = ShellConnection(applicationContext)   ← 暴露给 Host
  AgentCore / AgentRuntime / Provider 语义不变
ChatActivity.applyConfig()：
  root = ChatCompositionRoot(cfg, applicationContext)
  lifecycleScope.launch { val s = root.shell.connect(); 展示 status }  ← 最小状态提示（§27）
:app 无 com.androidterminal.* import（静态扫描 0）
```

## 12. Unit Tests（:tool-android-shell，JVM）

```text
U1  valid {"command":"id"} 解析+执行一次        PASS
U2  invalid JSON -> failure / 0 calls           PASS
U3  missing command -> failure / 0 calls        PASS
U4  blank command -> failure / 0 calls          PASS
U4b 非对象参数 -> failure / 0 calls             PASS
U4c command 非 string（数字）-> failure / 0     PASS
U5  COMPLETED exit0 -> success=true             PASS
U6  COMPLETED exit7 -> success=true             PASS
U7  stdout 保留                                 PASS
U8  stderr 保留且不进 ToolResult.error          PASS
U9  TRANSPORT_ERROR -> success=false            PASS
U10 PROTOCOL_ERROR -> success=false             PASS
U11 UNSUPPORTED -> success=false                PASS
U12 transport failure -> executor 恰好 1 次     PASS
U13 无自动重放                                  PASS
U14 payload 含 outcome/exitCode/stdout/stderr   PASS
总计 16/16（JUnit XML 实测，非源码计数）
```

## 13. Existing Regression

```text
ag-core 41/41 · ag-runtime 8/8 · provider 22/22 · provider-jdk 24/24
OVERALL 95/95 + 16 新 = 111/111，clean build 后 0 regression
```

## 14. Build

```text
:tool-android-shell:assembleDebug   PASS
:app:assembleDebug                  PASS（clean build）
:app:assembleDebugAndroidTest       PASS
gradlew clean 全量                  PASS（111/111）
```

## 15. APK Audit（:app debug）

```text
size   : 4,870.8 KB
native : lib/arm64-v8a/libandroidterminaladb.so · libc++_shared.so · libnetwork_probe.so（3/3）
ABI    : 仅 arm64-v8a（unexpected ABI = 0）
runtime类 : AtRuntime PRESENT（dex 71 refs）
core类   : TerminalRuntime / AdbManager PRESENT（经 at-core.aar 传递）
tool类   : AndroidShellToolImpl PRESENT
duplicate native : 0（AG 原零 native，唯一来源 at-core.aar）
```

## 16. Static Boundary Audit

```text
:ag-core  production com.androidterminal import = 0
:ag-runtime production com.androidterminal import = 0
:app      production com.androidterminal import = 0
:tool-android-shell import com.androidterminal.runtime.* = 6（allowed）
:tool-android-shell 非 runtime com.androidterminal.* import = 0
绝对 AT 路径（D:\...\AndroidTerminal-V2）命中 = 0
:app 直接 AT Gradle 依赖 = 无（build 文件仅注释提及字符串）
```

## 17. Real-Device Acceptance（APLMUT4312005935，S1–S5）

```text
TEST-ONLY provisioning：PC 已授权 adbkey 经 run-as 管道注入 filesDir/at-identity（不提交、测后删）
S1  ShellConnection -> connect -> READY           PASS
S2  {"command":"id"} -> success / COMPLETED / uid=2000(shell)  PASS
S3  sh -c 'echo OUT; echo ERR >&2; exit 7' -> success / exitCode=7 / OUT / ERR / hasError=false  PASS
S4  无效参数 -> success=false                    PASS
S5  ToolRegistry -> android_shell -> Tool -> AT Engine -> shell（echo AG0_S5_OK）  PASS
测试运行：am instrument -w …ShellToolRealDeviceTest  → OK (6 tests)（含早期一次瞬时 WD 读值 0 的排障，probe 确认 app 可读 1 后全过）
```

## 18. Cleanup

```text
files/at-identity/adbkey* : 已删（run-as rm -rf，验证 GONE）
/data/local/tmp/agkey*     : 已删
WD 状态                    : 恢复为开始值 1（未改动）
test APK                   : 已卸载
AT.apk / TestHost / 用户资产：未触碰
```

## 19. Known Limitations（必须记录）

```text
1. arm64-v8a only（AT at-core 唯一 ABI；:app 已设 abiFilters）
2. Wireless Debugging 必须已启用（Engine 只检测，OFF -> USER_ACTION_REQUIRED）
3. trusted identity 必须已存在（pairing 未实现）
4. pairing / SPAKE2 未实现（Slice C，DEFERRED）
5. timeout > 0 unsupported（v0 execute 恒 timeoutMs=0）
6. 无 PTY
7. 无 streaming
8. 无 cancel
9. 无自动命令重放（HARD rule）
10. model 自主选工具未验证（属 AGENT-0）
11. WD readiness 在 HONOR/MagicOS 上存在 app 侧瞬时读值异常（AT.ENGINE-AG0 §9 已文档化；
    本任务真机验证中观察到一次，probe 确认可读后 S1–S5 全过；属 AT 侧已记录 OEM 行为，
    非 AG 集成缺陷）
```

## 20. Next Stage（不在本轮）

```text
AGENT-0（model 自主选工具 / Two-Environment Contrast）
pairing · AGCLI · persistence · MCP · multi-agent · AIController
```

## 21. Final Verdict

```text
AG_SHELL_1_PASS

ANDROID_SHELL_TOOL       = IMPLEMENTED
TOOL_REGISTRY_INTEGRATION = VERIFIED
AT_ENGINE_BUNDLE          = INTEGRATED
AG_CORE                   = UNCHANGED
AG_RUNTIME                = UNCHANGED
AT_ENGINE                 = UNCHANGED
AG_ARCHITECTURE_BOUNDARY  = PRESERVED
REAL_DEVICE_TOOL_PATH     = VERIFIED
READY_FOR_AGENT_0         = YES
```