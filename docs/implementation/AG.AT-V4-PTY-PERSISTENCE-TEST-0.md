# AG.AT-V4-PTY-PERSISTENCE-TEST-0 — AG 经 AT V4 的 PTY 持久会话验证

## 1. 任务目标

在 AndroidAgent V2 当前开发基线上，通过现有 DebugBridge，使用 AG 已接入的 AT V4 Consumer Composition 和 Execution Adapter，完成 PTY 持久会话的确定性测试。

本轮只验证执行层，不调用 LLM。

## 2. 仓库与版本基准

| 项 | 值 |
|---|---|
| AG HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4` |
| AG worktree | DIRTY（28 files，V4 migration + 4 bug fixes，未提交） |
| AT freeze tag | `AT.V4-CONSUMER-FREEZE-V1` |
| AT freeze commit | `7216d0e8131e92e8914ce7c8443e1668b1d53aca` |
| 设备 | Honor ALI-AN00, API 35, serial `APLMUT4312005935` |
| AG UID | 10370 (u0_a370) |
| ADB 状态 | READY（上轮 LLM 驱动配对完成） |

## 3. 约束遵守

| 约束 | 遵守情况 |
|---|---|
| 禁止调用 LLM | ✓ 无 `send` 命令，无 Agent Run，无模型请求 |
| 必须走正式消费者路径 | ✓ DebugBridge → AtExecutionAdapter → ATEngine → ATTerminalSession |
| 不修改 AT 源码 | ✓ |
| 不新增 Agent Tool | ✓ |
| 不修改生产代码 | ✓ 本轮零代码修改 |
| 不使用 PC 侧 adb.exe 替代执行 | ✓ PC adb 仅用于 preflight 检查 |
| 不实现 Session Resume | ✓ |

## 4. 调用链核验

### 4.1 DebugBridge 入口

DebugBridge（`app/.../debug/DebugBridge.kt`）支持以下 PTY 相关命令：

| 客户端消息 | 处理方法 | 调用目标 |
|---|---|---|
| `session_open` | `handleSessionOpen()` | `adapter.openLocalSession()` / `adapter.openAdbSession()` / `adapter.openBootstrappedLocalSession()` |
| `session_write` | `handleSessionWrite()` | `adapter.writeCommand()` / `adapter.writeCtrlC()` |
| `session_read` | `handleSessionRead()` | `adapter.readOutput()` |
| `session_resize` | `handleSessionResize()` | `adapter.resize()` |
| `session_close` | `handleSessionClose()` | `adapter.closeSession()` |
| `session_state` | `handleSessionState()` | `adapter.sessionState()` |
| `bootstrap_state` | `handleBootstrapState()` | `adapter.bootstrapState()` |

### 4.2 Adapter 层

`AtExecutionAdapter`（`integration-at/.../AtExecutionAdapter.kt`）：

| 方法 | 实际调用 |
|---|---|
| `openLocalSession()` | `localEngine.openSession(ATSessionLaunch(cmd, rows, cols))` |
| `openAdbSession()` | `adbEngine.openSession(ATSessionLaunch(cmd, rows, cols))` |
| `writeCommand(session, cmd)` | `session.write((cmd + "\n").toByteArray())` |
| `writeCtrlC(session)` | `session.write(byteArrayOf(0x03))` |
| `readOutput(session, timeoutMs)` | `session.read(timeoutMs)` |
| `resize(session, rows, cols)` | `session.resize(rows, cols)` |
| `closeSession(session)` | `session.close(true)` |

所有方法都是 `ATTerminalSession` 的 thin wrapper，无额外逻辑。

### 4.3 Session 管理

- Session ID：`AtomicInteger` 自增，`ConcurrentHashMap<Int, ATTerminalSession>` 存储
- 输出模式：同步读取（`session_read` 带 `timeoutMs`），非事件推送
- Local 和 ADB 共用相同的 DebugBridge session 管理层，仅 backend 参数不同

## 5. Local PTY 测试结果

### L1: 创建与初始化 — PASS

```
请求: session_open backend=local
响应: session_opened id=3 backend=local
初始输出: ":/ $ "（shell prompt，CWD=/）
```

### L2: 连续读写与状态保持 — PASS

同一 Session（id=3）内依次执行：

| 步骤 | 命令 | 输出 | 验证 |
|---|---|---|---|
| 1 | `printf 'AT_PTY_TEST_01\n'` | `AT_PTY_TEST_01` | ✓ |
| 2 | `export AT_PTY_MARKER=at_pty_persistence_7f31` | （无输出） | — |
| 3 | `cd /` | （无输出） | — |
| 4 | `printf 'AT_PTY_MARKER=%s\n' "$AT_PTY_MARKER"` | `AT_PTY_MARKER=at_pty_persistence_7f31` | ✓ 环境变量保持 |
| 5 | `pwd` | `/` | ✓ CWD 保持 |
| 6 | `printf 'AT_PTY_TEST_02\n'` | `AT_PTY_TEST_02` | ✓ |

Session ID 前后一致（id=3），无隐式重建。

### L3: stdin 与原始输出 — PASS

| 测试 | 结果 |
|---|---|
| 唯一标记 `RAW_STDIN_abc456` | ✓ 完整接收 |
| 顺序写入 SEQ_A → SEQ_B | ✓ 顺序正确 |

### L4: 异步输出与持续读取 — PASS

```sh
for i in 1 2 3 4 5; do printf 'SEG_%s\n' $i; done
```

输出：`SEG_1` `SEG_2` `SEG_3` `SEG_4` `SEG_5`，5 段全部收到，顺序正确，无丢失。

### L5: Ctrl-C — PASS

```
写入: sleep 30
写入: ctrlc=true
输出: "sleep 30\r\r\n^C\r\n130|:/ $ "（exit code 130 = SIGINT）
写入: printf 'AFTER_INTERRUPT\n'
输出: "AFTER_INTERRUPT"（Session 仍可用）
```

### L6: Resize — PASS

```
请求: session_resize rows=40 cols=120
响应: session_resized rows=40 cols=120 ok=true
后续命令: printf 'AFTER_RESIZE\n' → "AFTER_RESIZE"（Session 未重建）
```

### L7: 自然退出 — PASS

新 Session（id=4）：

```
写入: echo DONE && exit 0
输出: "DONE\n"
第二次读取: <EOF>
写入: echo should_fail
响应: error "no session with id=4"（Session 已终止）
```

### L8: 显式关闭 — PASS

新 Session（id=5）：

```
写入: echo CLOSE_TEST → "CLOSE_TEST"
关闭: session_close → session_closed id=5
写入: echo after_close
响应: error "no session with id=5"（Session 已关闭）
```

## 6. 多 Session 隔离 — PASS

| 项 | 结果 |
|---|---|
| Session A id | 6 |
| Session B id | 7 |
| A 设置 `AT_SESSION_MARKER=session_A_71c9` | A 读到 `session_A_71c9`，无 `session_B_42d6` ✓ |
| B 设置 `AT_SESSION_MARKER=session_B_42d6` | B 读到 `session_B_42d6`，无 `session_A_71c9` ✓ |
| 关闭 A 后 B 仍可用 | B 读到 `session_B_42d6` ✓ |
| 输出无交叉 | ✓ |

## 7. ADB PTY 测试结果

### 前置条件

- ADB identity：已配对（上轮 LLM 驱动 `adb pair`）
- `bootstrap_state`：`READY`

### A1: 创建 — PASS

```
请求: session_open backend=adb
响应: session_opened id=11 backend=adb
初始输出: "HNALI-Q:/ $ "（ADB shell prompt，设备型号前缀）
```

### A2: 执行身份 — PASS

```
命令: id
输出: uid=2000(shell) gid=2000(shell) groups=2000(shell),... context=u:r:shell:s0
```

确认为 ADB shell 身份（uid=2000），非 app UID。

### A3: 状态保持 — PASS

| 步骤 | 结果 |
|---|---|
| `export ADB_M=adb_persist_9x2k` → `echo $ADB_M` | `adb_persist_9x2k` ✓ |
| `cd /data/local/tmp` → `pwd` | `/data/local/tmp` ✓ |

### A4: Ctrl-C — PASS

```
写入: sleep 30
写入: ctrlc=true
输出: "sleep 30\r\r\n^C\r\n130|HNALI-Q:/ $ "
写入: echo CC_DONE
输出: "CC_DONE"（Session 仍可用）
```

### A5: Resize — PASS

```
请求: session_resize rows=50 cols=100
响应: session_resized rows=50 cols=100 ok=true
后续命令: echo POST_RS → "POST_RS"
（输出中可见屏幕重排的 backspace 序列，确认 PTY 尺寸变更生效）
```

### A6: 显式关闭 — PASS

```
关闭: session_closed id=11
写入: echo dead → error "no session with id=11"
```

### A7: 多 ADB Session 隔离 — PASS

| 项 | 结果 |
|---|---|
| Session A id / Session B id | 12 / 13（不同） |
| A 设 `ADB_ISO=A_iso_11aa`，B 设 `ADB_ISO=B_iso_22bb` | A 只读到 A，B 只读到 B ✓ |
| 无交叉污染 | ✓ |

## 8. DebugBridge 会话生命周期核验

| 检查项 | 结果 |
|---|---|
| Session ID 正确关联 | ✓ 每个请求携带正确 ID，输出路由到正确 Session |
| 同一 Session 多请求顺序 | ✓ L2 六步命令顺序正确 |
| 关闭后拒绝操作 | ✓ write/read 返回 error |
| 自然退出后 Session 失效 | ✓ 第二次 read = EOF，write = error |
| 无重复创建 | ✓ 同一 Session ID 贯穿 L1-L6 |
| 无资源泄漏 | ✓ 所有 Session 显式关闭 |
| 失败区分 | ✓ 命令失败（exit code）≠ 读取超时（空数据）≠ Session 关闭（error）≠ EOF |

## 9. ADB PTY 输出延迟说明

ADB PTY 的输出比 Local PTY 有约 1-2 秒延迟（网络往返 + ADB 协议开销）。测试脚本使用 `read_all()` 累积读取（多次 `session_read` 直到超时），确保不遗漏延迟输出。这不是 bug，是 ADB 远程 PTY 的正常特征。

## 10. 构建与回归

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| ag-core | 41 tests, 0 failures |
| ag-runtime | 26 tests, 0 failures |
| tool-android-shell | 15 tests, 0 failures |
| tool-network | 78 tests, 0 failures |
| tool-file | 55 tests, 0 failures |
| app | 109 tests, 0 failures |
| **总计** | **324 tests, 0 failures** |

## 11. 最终报告

```
AG.AT-V4-PTY-PERSISTENCE-TEST-0

AG_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

AG_WORKTREE =
DIRTY (28 files, uncommitted)

AT_FREEZE_TAG =
AT.V4-CONSUMER-FREEZE-V1

AT_FREEZE_COMMIT =
7216d0e8131e92e8914ce7c8443e1668b1d53aca

AT_ARTIFACT_SHA256_GATE =
PASS

LLM_INVOKED = NO
PROVIDER_REQUESTS = 0

DEBUGBRIDGE_ENTRY_GATE =
PASS

ADAPTER_CALL_PATH_GATE =
PASS

LOCAL_PTY_CREATE_GATE =
PASS

LOCAL_PTY_CONTINUITY_GATE =
PASS

LOCAL_PTY_STDIN_GATE =
PASS

LOCAL_PTY_OUTPUT_GATE =
PASS

LOCAL_PTY_CTRL_C_GATE =
PASS

LOCAL_PTY_RESIZE_GATE =
PASS

LOCAL_PTY_NATURAL_EXIT_GATE =
PASS

LOCAL_PTY_CLOSE_GATE =
PASS

MULTI_SESSION_ISOLATION_GATE =
PASS

ADB_IDENTITY_STATUS =
PAIRED

ADB_READINESS_STATUS =
PASS (READY)

ADB_PTY_CREATE_GATE =
PASS

ADB_PTY_CONTINUITY_GATE =
PASS

ADB_PTY_IO_GATE =
PASS (uid=2000(shell))

ADB_PTY_CTRL_C_GATE =
PASS

ADB_PTY_RESIZE_GATE =
PASS

ADB_PTY_CLOSE_GATE =
PASS

ADB_MULTI_SESSION_ISOLATION_GATE =
PASS

DEBUGBRIDGE_SESSION_CORRELATION =
PASS

RESOURCE_CLEANUP =
PASS

UNEXPECTED_SESSION_RECREATION =
NONE

AUTOMATED_TESTS =
PASS (324/324)

DEVICE_TEST =
PASS

BUILD =
PASS

PRODUCTION_CODE_CHANGED = NO
AT_CHANGED = NO
MODEL_TOOL_CHANGED = NO
SESSION_RESUME_IMPLEMENTED = NO

FAILURES =
NONE

EVIDENCE_PATHS =
C:\temp\test_pty_persistence.py
C:\temp\test_pty_adb_v2.py
C:\temp\test_pty_adb_final.py

RECOMMENDED_NEXT =
1. Commit all pending changes (awaiting Controller)
2. Document ADB PTY 1-2s output latency for test harness design
3. STOP

NEXT =
STOP

STOP =
YES
```

## 12. 本轮变更文件

**无。** 本轮为纯测试验证任务，零代码修改。
