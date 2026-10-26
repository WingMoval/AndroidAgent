# AG.DEBUG-LLM-ADB-DIAGNOSIS-0 — DebugBridge 驱动 AG LLM 诊断并修复 ADB 连接

## 1. 任务目标

通过 PC 端 OpenCode，使用 AndroidAgent 已有的 DebugBridge 与 AG APK 内配置的真实 LLM 交互，让 AG 自己调查 ADB 连接失败的原因。同时验证：

1. DebugBridge 是否真正连接到 AG 的聊天、Run 和工具调用链路。
2. AG 能否通过 AT V4 Consumer API 使用 Local Shell / PTY，并将执行结果反馈给 LLM。
3. AG 的真实 LLM 能否获取充分的 ADB 诊断证据，分析 post-TLS C09 失败的实际原因。

## 2. 仓库与版本基准

| 项 | 值 |
|---|---|
| AG HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4` |
| AG worktree | DIRTY（V4 migration + 本轮 4 个 bug fix，未提交） |
| AT freeze tag | `AT.V4-CONSUMER-FREEZE-V1` |
| AT freeze commit | `7216d0e8131e92e8914ce7c8443e1668b1d53aca` |
| 设备 | Honor ALI-AN00, API 35, serial `APLMUT4312005935` |
| AG UID | 10370 (u0_a370) |

## 3. 第一阶段：DebugBridge 链路审计

### 3.1 协议与入口

DebugBridge 是 TCP JSONL 服务器（debug build only），绑定 `0.0.0.0:45678`。

支持的客户端消息：

| 消息 | 用途 |
|---|---|
| `ping` | 连通性检查 |
| `send` | 提交用户输入到 `AgentRuntime.send()`（与 UI 同一路径） |
| `tool_invoke` | 直接调用生产 Tool（绕过 LLM，debug 验证用） |
| `session_open/write/read/resize/close/state` | AT V4 持久会话操作 |
| `bootstrap_state/discover/pair` | ADB 配对与状态 |

### 3.2 链路验证

```
PC (OpenCode / Python)
  → TCP JSONL (192.168.137.44:45678)
  → DebugBridge.handleSend()          [DebugBridge.kt:269]
  → AgentRuntime.send(text)            [DebugBridge.kt:292] — 与 UI 同一 runtime
  → ModelProvider (OpenRouter)
  → ToolCall → ToolDispatcher → AtExecutionAdapter (AT V4)
  → ToolResult → AgentEvent stream → DebugBridge broadcast → PC
```

**验证结果：**

| Gate | 结果 | 证据 |
|---|---|---|
| DEBUGBRIDGE_CONNECT | PASS | ping/pong; session_open/write/read/close |
| REAL_AG_CHAT | PASS | send → accepted → run_start → model_turn → tool_call → tool_result → final_answer → run_finished |
| AG_PROVIDER | PASS | OpenRouter deepseek-v4-flash-0731; logcat 确认 endpoint |
| TOOLCALL_DISPATCH | PASS | 多次真实 ToolCall 经 ToolRegistry.dispatch 执行 |
| TOOLRESULT_FEEDBACK | PASS | ToolResult 进入模型下一轮上下文；模型据此调整行动 |
| STREAM_EVENT | PASS | reasoning_delta, content_delta, tool_call, tool_result, final_answer, run_finished 全部观察到 |

**能力缺口：** 模型可见工具只有 `android_shell`（one-shot）。没有 persistent PTY 工具。DebugBridge 的 `session_open` 是独立于 Agent Run 的调试通道，LLM 无法调用。

## 4. 第二阶段：Local Shell 验证

通过 DebugBridge 向 AG LLM 发送任务，让 LLM 用 `android_shell` 工具执行 Local 命令。

### 4.1 测试结果

| 命令 | 结果 |
|---|---|
| `id` | `uid=10370(u0_a370) gid=10370(u0_a370) ...` ✓ |
| `pwd` | `/data/user/0/com.androidagent.app/files` ✓ |
| `cat agent/diag_marker.txt` | 读到随机标记 ✓ |
| `echo 'RESULT_<marker>' > agent/diag_result.txt` | 写入成功 ✓ |
| `cat agent/diag_result.txt` | 读回验证 ✓ |

5/5 工具调用成功。独立 ground truth 验证：`RESULT_DIAG_1792672891` 与期望一致。

### 4.2 执行链路

```
LLM → android_shell → AtExecutionAdapter.executeLocal()
    → localEngine.execute(ATExecutionRequest(wrapped, timeoutMs))
    → AT V4 Local Backend → /system/bin/sh (app UID)
```

`executeLocal` 包装命令：`cd <appFilesDir> && <command>`，使相对路径正确解析。

## 5. 第三阶段：ADB 诊断

### 5.1 诊断证据采集

通过 PC 侧 `adb.exe`（仅用于 logcat 采集，非 ADB 执行替代）收集：

- Identity 文件：`files/at-consumer/identity/`（adbkey, adbkey.pub, adb_cert.pem）
- Bootstrap 状态：`CONNECT_FAILED, hasIdentity=true`
- NSD 发现：`adb-APLMUT4312005935-J4nb5z:37255(192.168.137.44)`
- 完整连接日志 C01-C09

### 5.2 LLM 自主诊断

将诊断数据通过 DebugBridge 发送给 AG LLM。LLM 输出 8676 字符分析，并**自主执行 2 次工具调用**检查设备 identity 文件。

**LLM 关键发现：**

1. **C09 失败点**：TLS 握手成功（C07 TLS_OK）后 ~1ms，设备主动关闭连接（`SSL_read err=1` = SSL_ERROR_SSL = fatal TLS alert）。这是 adbd 在授权边界拒绝未配对密钥的典型特征。

2. **两套 identity 并存**：
   - `files/at-identity/`（V3 遗留，2026-10-18）
   - `files/at-consumer/identity/`（V4 当前，2026-10-22）
   - 两套公钥**完全不同**

3. **根因判断（高置信度）**：未配对。`hasIdentity=true` 只代表本地有 key，不代表设备认可。

4. **建议实验**：清理 identity → 重新配对 → 重试连接。若配对后 C09 通过 → 状态问题；若仍失败 → AT V4 identity 选择或协议顺序 bug。

### 5.3 配对验证

**第一次配对（DebugBridge bootstrap_pair 旁路）：**

```
Python → DebugBridge.bootstrap_pair(code)
       → AtExecutionAdapter.pairDiscovered(code)
       → AT V4 AdbEngineJNI.pair(ip, port, code, keyDir)
       → TLS 1.3 → SPAKE2 → AES-128-GCM → SUCCESS
```

配对成功后重启 app，ADB 连接完整通过：

```
C07 TLS_OK
C09 RX_CNXN_POST_TLS arg0=0x01000001 banner=368 bytes  ← 之前失败点
C10 NEGOTIATED_VERSION=0x01000001
C11 NEGOTIATED_MAX_PAYLOAD=1048576
C12 REMOTE_FEATURES (count=21)
C13 ONLINE
HUB01 RX_OWNER_START
AdbClient::connect: ADB session established
```

**C09 根因确认：未配对。不是 Honor 设备 bug，不是 AT V4 协议 bug。**

### 5.4 LLM 驱动配对（核心验证）

用户要求：LLM 通过 `android_shell`（Local one-shot）执行 `adb pair`，不走 DebugBridge 旁路。

**AT V4 的 `adb` 命令实现：**

AT V4 的 `adb` 不是二进制文件，是 Local PTY session 里的 **shell 函数**，由 `LocalCliBootstrap.writeScript()` 生成 bootstrap 脚本，session 启动时 source 进去：

```sh
adb() {
  /system/bin/app_process -Djava.class.path="$AT_APP_APK" /system/bin \
    com.androidterminal.app.cli.AtCliMain "$@"
}
```

**问题 1：** `local_cli` session 打开失败 — bootstrap 脚本写到 APK 安装目录（`/data/app/...`，只读）→ `ENOENT`。

**修复：** `writeCliBootstrapScript` 改为写到 `appFilesDir/at-cli/`（app 私有可写目录）。

**问题 2：** LLM 的 `android_shell` 走 one-shot（`executeLocal`），不走 `local_cli` session，所以 `adb` 函数不可用。

**修复：** `executeLocal` 在命令前 source 最新的 `adb_bootstrap_*.sh`。

**最终验证（LLM 驱动）：**

| 命令 | 结果 |
|---|---|
| `adb version` | exit=0, `AndroidTerminal adb CLI (app_process entrypoint, bridge protocol v1)` |
| `adb pair 192.168.137.44:38855 397755` | exit=0, `pairing succeeded (guid=adb-APLMUT4312005935-J4nb5z)` |
| `adb shell id` | exit=0, `uid=2000(shell) gid=2000(shell) ...` |
| `adb shell getprop ro.product.model` | exit=0, `ALI-AN00` |

完整链路：

```
LLM → android_shell → executeLocal()
    → source adb_bootstrap.sh → adb() shell function
    → app_process → AtCliMain → CLI bridge socket
    → AT V4 ADB engine → AdbNative.pair() / AdbNative.connect()
    → adbd (uid=2000)
```

## 6. 第四阶段：对照验证

| 对比项 | 结果 |
|---|---|
| AT V4 reference consumer 历史证据 | 上轮已验证 Local one-shot + persistent PASS |
| AG 当前冻结 AT V4 连接记录 | 本轮 C01-C13 全通过（配对后） |
| Identity 来源 | AG 自生成，未复制 AT Product 私钥 |
| PC adb.exe | 仅用于 install/logcat/run-as 诊断，未替代 AG 内部 ADB 执行 |

## 7. Bug 修复清单

### 7.1 TranscriptModel 线程安全（ConcurrentModificationException）

**文件：** `app/src/main/java/com/androidagent/app/TranscriptModel.kt`

**根因：** `blocks` 是普通 `ArrayList`，被 IO dispatcher（`SessionHolder.onStreamEvent` / `onRunEvent`）和 UI 线程（`renderTranscript`）并发修改。UI 线程迭代 `blocks` 时 IO 线程正在添加元素 → `ConcurrentModificationException` → app 崩溃。

**修复：** 引入 `synchronized(lock)` + `withBlocks{}` 内联函数，所有 `blocks` 访问通过锁保护。`render()` 在锁内取快照后渲染。

### 7.2 userInputSink 操作错误对象（User 块丢失）

**文件：** `app/src/main/java/com/androidagent/app/ChatActivity.kt`

**根因：** `userInputSink` 修改 Activity 本地 `transcriptModel` 副本（hash=48123094），但 `SessionHolder.onRunEvent` 操作 `SessionHolder.transcriptModel`（hash=259137710）。每次 `notifyUiUpdate()` 触发，`onUiUpdate` 回调用 `SessionHolder.transcriptModel` 覆盖 Activity 本地副本 → User 块丢失。

**修复：** `userInputSink` 直接操作 `SessionHolder.transcriptModel`（进程级权威模型），在 UI 线程执行。

### 7.3 CLI bootstrap 脚本路径错误（ENOENT）

**文件：** `integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt`

**根因：** `writeCliBootstrapScript` 用 `File(File(appApkPath).parentFile, "at-cli/...")` 写脚本 → 路径是 `/data/app/~~.../com.androidagent.app-.../at-cli/`（APK 安装目录，app 进程只读）→ `ENOENT`。

**修复：** 改为写到 `appFilesDir/at-cli/`（`files/at-cli/`，app 私有可写目录）。

### 7.4 executeLocal 不 source bootstrap 脚本（adb 函数不可用）

**文件：** `integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt`

**根因：** `executeLocal`（one-shot）不 source `adb_bootstrap_*.sh`，所以 `adb` shell 函数在 one-shot 命令里不存在 → LLM 执行 `adb pair` 返回 `exit 127: adb: not found`。

**修复：** `executeLocal` 在命令前 source 最新的 `adb_bootstrap_*.sh`（按文件名排序取最新）。

## 8. 测试与构建

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| ag-core | 41 tests, 0 failures |
| ag-runtime | 26 tests, 0 failures（1 次 flaky 超时，重跑通过） |
| tool-android-shell | 15 tests, 0 failures |
| tool-network | 78 tests, 0 failures |
| tool-file | 55 tests, 0 failures |
| app | 109 tests, 0 failures |
| **总计** | **324 tests, 0 failures** |

## 9. 最终报告

```
AG.DEBUG-LLM-ADB-DIAGNOSIS-0

AG_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

AG_WORKTREE =
DIRTY (20 files: 11 modified + 9 untracked)

AT_FREEZE_TAG =
AT.V4-CONSUMER-FREEZE-V1

AT_FREEZE_COMMIT =
7216d0e8131e92e8914ce7c8443e1668b1d53aca

AT_ARTIFACT_HASH_GATE =
PASS

DEBUGBRIDGE_CONNECT_GATE =
PASS

REAL_AG_CHAT_GATE =
PASS

AG_PROVIDER_CONFIRMED =
PASS (OpenRouter deepseek-v4-flash-0731)

TOOLCALL_DISPATCH_GATE =
PASS

TOOLRESULT_FEEDBACK_GATE =
PASS

STREAM_EVENT_GATE =
PASS

LOCAL_ONESHOT_GATE =
PASS (id, pwd, marker read/write, 5/5 tool calls)

LOCAL_PTY_GATE =
PASS (DebugBridge session: open/write/read/close/resize/ctrl-c)

LOCAL_SESSION_CONTINUITY_GATE =
PASS (env vars + CWD persist)

LOCAL_OUTPUT_RETURN_GATE =
PASS

LOCAL_TOOL_MODEL_VISIBILITY =
ONE-SHOT (android_shell; LLM can now run adb via bootstrap script sourcing)

ADB_IDENTITY_STATUS =
PRESENT + PAIRED (at-consumer/identity/; two-identity split documented)

ADB_PAIRING_STATUS =
PAIRED (LLM-driven adb pair via android_shell; SPAKE2 verified)

ADB_DISCOVERY_STATUS =
PASS (NSD _adb-tls-connect + _adb-tls-pairing)

ADB_TCP_STATUS =
PASS

ADB_TLS_STATUS =
PASS (TLS 1.3 handshake success)

ADB_POST_TLS_CNXN_STATUS =
PASS (C09: 368-byte CNXN banner received; was FAIL before pairing)

ADB_ONLINE_STATUS =
PASS (C13 ONLINE; HUB01 RX_OWNER_START)

ADB_SHELL_OPEN_STATUS =
PASS (adb shell id → uid=2000(shell))

DIAGNOSTIC_EVIDENCE_COMPLETE =
PASS (C01-C13 full log; NSD; SPAKE2; identity listing)

LLM_DIAGNOSIS_RESULT =
COMPLETED (8676-char analysis; 2 autonomous tool calls; root cause identified)

ROOT_CAUSE_CONFIDENCE =
CONFIRMED (unpaired key; resolved by LLM-driven adb pair)

MISSING_EVIDENCE =
NONE

AG_PRODUCTION_CHANGED =
YES (4 bug fixes; no feature/contract changes)

AT_CHANGED = NO
IDENTITY_CLEARED = NO
REPAIRING_PERFORMED =
YES (LLM-driven adb pair; user provided code; no identity deleted)
PC_ADB_USED_AS_SUBSTITUTE =
NO

BUILD =
PASS

TESTS =
PASS (324/324)

DEVICE_TEST =
PASS (Local + ADB one-shot + ADB persistent + LLM-driven pair/shell)

RECOMMENDED_FIX =
NONE (all identified bugs fixed this round)

NEXT =
1. Commit pending changes (awaiting Controller approval)
2. ADB persistent session via LLM tool (separate capability)
3. Document bootstrap script lifecycle (per-session, latest-wins)
4. STOP

STOP =
YES
```

## 10. 本轮变更文件

### 修改（modified）

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/androidagent/app/TranscriptModel.kt` | 线程安全：synchronized + withBlocks |
| `app/src/main/java/com/androidagent/app/ChatActivity.kt` | userInputSink 操作 SessionHolder.transcriptModel |
| `integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt` | writeCliBootstrapScript 路径修复 + executeLocal source 脚本 |

### 上轮遗留（未提交，非本轮修改）

| 文件 | 来源 |
|---|---|
| `app/build.gradle.kts` | V4 migration |
| `app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt` | V4 migration |
| `app/src/main/java/com/androidagent/app/debug/DebugBridge.kt` | V4 migration |
| `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md` | V4 migration |
| `integration-at/build.gradle.kts` | V4 migration |
| `settings.gradle.kts` | V4 migration |
| `tool-android-shell/src/main/java/.../AndroidShellToolImpl.kt` | V4 migration + Local-first |
| `tool-android-shell/src/main/java/.../ShellConnection.kt` | V4 migration |

### 新增（untracked）

| 文件 | 来源 |
|---|---|
| `app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt` | V4 migration |
| `docs/audit/AG.AT-V4-Handoff-Audit.md` | 上轮审计 |
| `docs/implementation/AG.AT-V4-Integration-Plan.md` | 上轮集成计划 |
| `docs/implementation/AG.DEBUG-LLM-ADB-DIAGNOSIS-0.md` | **本轮** |
| `integration-at/src/main/java/.../AdbSemantic.kt` | V4 migration |
| `integration-at/src/main/java/.../AtAdbBootstrap.kt` | V4 migration |
| `integration-at/src/main/java/.../AtAdbReadiness.kt` | V4 migration |
| `integration-at/src/main/java/.../AtPairingDiscovery.kt` | V4 migration |
| `integration-at/src/main/java/.../AtPairingResult.kt` | V4 migration |
| `third_party/at-v4-consumer-freeze-v1/` | V4 migration |
