# AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX — CLI Bootstrap 失效路径自动修复

## 1. 症状

`adb install -r` 后，AG 内部 `adb` CLI 函数崩溃：
```
adb version → exit 134, stderr: "Aborted"
adb shell id → exit 134, stderr: "Aborted"
```

## 2. 根因

```
adb install -r
→ APK 安装路径变化 (~~OLD/ → ~~NEW/)
→ files/at-cli/adb_bootstrap_N.sh 仍引用旧路径
→ executeLocal() source 旧 bootstrap
→ adb() → app_process -Djava.class.path=<OLD> → APK 不存在 → SIGABRT
```

历史关联：AG.DEBUG-LLM-ADB-DIAGNOSIS-0 修了 bootstrap 写入路径（/data/app/ → appFilesDir/）和 executeLocal source 缺失。本轮修复的是**生命周期同步**问题。

## 3. 旧生命周期

```
writeCliBootstrapScript(sessionId) → files/at-cli/adb_bootstrap_{N}.sh (per-session)
executeLocal() → listFiles → sortedByDescending(name) → source latest
openBootstrappedLocalSession() → writeCliBootstrapScript(sessionId)
```

问题：
- 多文件 latest-wins，无有效性检查
- APK 路径变化后旧文件仍然 "latest"
- executeLocal 不检查 bootstrap 是否匹配当前安装实例

## 4. 新生命周期

```
ensureCliBootstrapCurrent()
  → 检查 files/at-cli/adb_bootstrap.sh (canonical)
  → 如果不存在或引用旧 APK path / 旧 socket path → 原子刷新 (.tmp + rename)
  → 返回 source line

executeLocal() → ensureCliBootstrapCurrent() → source → command
openBootstrappedLocalSession() → ensureCliBootstrapCurrent() → PTY inject
```

## 5. 设计决策

| 决策 | 理由 |
|---|---|
| Canonical 单文件 `adb_bootstrap.sh` | 消除 latest-wins 歧义；语义清晰："永远代表当前安装实例" |
| 有效性 = 内容包含当前 APK path + socket path | 不依赖文件名、时间戳、版本号 |
| 原子写入 (.tmp + rename) | 避免另一条执行路径 source 半写入脚本 |
| executeLocal 独立 ensure | 不依赖先开 local_cli session |
| 保留 writeCliBootstrapScript(sessionId) | 向后兼容（标记 deprecated） |

## 6. 变更文件

| 文件 | 变更 |
|---|---|
| `integration-at/.../AtExecutionAdapter.kt` | 新增 `ensureCliBootstrapCurrent()`；重写 `executeLocal()`；更新 `openBootstrappedLocalSession()` |
| `integration-at/.../CliBootstrapLifecycleTest.kt` | 新增 8 个单元测试 (B1-B8) |

## 7. 单元测试

| 测试 | 覆盖 | 结果 |
|---|---|---|
| B1_missing_bootstrap_generates_with_current_apk | 首次生成 | PASS |
| B2_valid_bootstrap_is_not_regenerated | 有效不重写 | PASS |
| B3_stale_apk_path_triggers_atomic_refresh | **核心回归**：旧路径→自动刷新 | PASS |
| B4_legacy_per_session_files_do_not_interfere | 旧 per-session 文件不干扰 | PASS |
| B5_socket_path_change_triggers_refresh | 动态配置变化 | PASS |
| B6_no_session_needed_first_call_generates | 无 session 前置 | PASS |
| B7_empty_apk_path_returns_null | 未初始化保护 | PASS |
| B8_no_tmp_file_remains_after_successful_write | 原子写入无残留 | PASS |

## 8. 真机 Reinstall 回归

| Step | 结果 |
|---|---|
| R1: Install A, 记录路径 `~~yGJDqgp9A9ocl4NSCvJ6cg==` | PASS |
| R2: install -r B, 新路径 `~~MrmvjJF0Pntlj8bEWpSYXQ==` (≠ A) | PASS |
| R3: 不删除 bootstrap，不手动 session_open | 遵守 |
| R4: 第一个 `adb version` → exit 0, 无 SIGABRT | PASS |
| R5: `adb shell id` → uid=2000(shell) | PASS |
| R6: Bootstrap 内容 = 新路径（旧路径不存在） | PASS |

```
MANUAL_BOOTSTRAP_DELETE_REQUIRED = NO
LOCAL_CLI_SESSION_REQUIRED_FOR_REPAIR = NO
```

## 9. LLM Smoke (Phase 15)

Goal: "Check the identity used by the ADB shell on this device."
→ 模型 1 次 ToolCall: `adb shell id` → `uid=2000(shell)` → 正确回答

```
MODEL_AT_V4_SEMANTICS_REGRESSION = PASS
```

## 10. 回归

| 模块 | 结果 |
|---|---|
| :ag-core:test | PASS |
| :ag-runtime:test | PASS |
| :integration-at:test (8 new) | PASS |
| :tool-android-shell:test | PASS |
| :tool-network:test | PASS |
| :tool-file:test | PASS |
| :app:testDebugUnitTest | PASS (1 known flaky re-run) |
| :app:assembleDebug | PASS |

## 11. 架构 Gate

```
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
TOOL_CONTRACT_CHANGED = NO
TOOL_SPEC_CHANGED = NO
AGENT_INSTRUCTIONS_CHANGED = NO
AT_SOURCE_CHANGED = NO
AT_ARTIFACT_CHANGED = NO
ADB_PROTOCOL_CHANGED = NO
ADB_RUNTIME_CHANGED = NO
```

## 12. 最终报告

```text
AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX FINAL REPORT

START_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

FINAL_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

WORKTREE_BEFORE =
DIRTY (28 files + model capability update docs)

WORKTREE_AFTER =
DIRTY (28 files + model capability update + this fix: AtExecutionAdapter.kt modified, CliBootstrapLifecycleTest.kt new, this doc new)

PRE_EXISTING_DIRTY_FILES_PRESERVED =
YES

ROOT_CAUSE =
Bootstrap script lifecycle not synchronized with APK install path changes after install -r

BUG_OWNER =
integration-at (AtExecutionAdapter)

OLD_BOOTSTRAP_POLICY =
per-session files (adb_bootstrap_N.sh), latest-wins by filename, no validity check

NEW_BOOTSTRAP_POLICY =
canonical file (adb_bootstrap.sh), validity = content contains current APK + socket paths, atomic refresh on staleness

CANONICAL_BOOTSTRAP_PATH =
files/at-cli/adb_bootstrap.sh

CURRENT_APK_PATH_SOURCE =
applicationInfo.sourceDir (injected by Host as appApkPath)

STALE_PATH_DETECTION =
content.contains(appApkPath) && content.contains(sockPath) → valid; otherwise regenerate

ATOMIC_REFRESH =
write .tmp → renameTo canonical (fallback: delete + rename)

BOOTSTRAP_GENERATED_WITHOUT_LOCAL_CLI_SESSION =
PASS

STALE_BOOTSTRAP_AUTO_REFRESH =
PASS

LEGACY_LATEST_WINS_REMOVED =
PASS (executeLocal no longer uses sortedByDescending)

UNIT_TESTS =
PASS (8/8: B1-B8)

DIRECT_LOCAL_GATE =
PASS (uid=10370 u0_a370)

DIRECT_ADB_CLI_GATE =
PASS (exit 0, "AndroidTerminal adb CLI")

DIRECT_ADB_SHELL_GATE =
PASS (uid=2000(shell))

APK_PATH_BEFORE_REINSTALL =
/data/app/~~yGJDqgp9A9ocl4NSCvJ6cg==/com.androidagent.app-7l9_MrqwYMXWOlw6ci7QDQ==/base.apk

APK_PATH_AFTER_REINSTALL =
/data/app/~~MrmvjJF0Pntlj8bEWpSYXQ==/com.androidagent.app-JkBcIoELQ-apcyOJ6GTMDw==/base.apk

APK_PATH_CHANGED =
YES

MANUAL_BOOTSTRAP_DELETE_AFTER_REINSTALL =
NO

MANUAL_LOCAL_CLI_REGEN_AFTER_REINSTALL =
NO

ADB_VERSION_AFTER_REINSTALL =
PASS

ADB_SHELL_AFTER_REINSTALL =
PASS

EXIT_134_AFTER_REINSTALL =
NO

SIGABRT_AFTER_REINSTALL =
NO

OLD_APK_PATH_PRESENT_IN_ACTIVE_BOOTSTRAP =
NO

CURRENT_APK_PATH_PRESENT_IN_ACTIVE_BOOTSTRAP =
YES

MODEL_AT_V4_SEMANTICS_REGRESSION =
PASS

AG_CORE_CHANGED =
NO

AG_RUNTIME_CHANGED =
NO

TOOL_CONTRACT_CHANGED =
NO

TOOL_SPEC_CHANGED =
NO

AGENT_INSTRUCTIONS_CHANGED =
NO

AT_SOURCE_CHANGED =
NO

AT_ARTIFACT_CHANGED =
NO

ADB_PROTOCOL_CHANGED =
NO

ADB_RUNTIME_CHANGED =
NO

TESTS =
PASS

BUILD =
PASS

DEVICE_TEST =
PASS

TASK_OWNED_FILES =
AtExecutionAdapter.kt (ensureCliBootstrapCurrent + executeLocal + openBootstrappedLocalSession)
CliBootstrapLifecycleTest.kt (new, 8 tests)
AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX.md (this doc)

UNEXPECTED_FILES =
NONE

FINAL_VERDICT =
PASS — CLI_BOOTSTRAP_LIFECYCLE_FIXED
```
