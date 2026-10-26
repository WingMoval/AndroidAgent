# AG.AT-V4-MODEL-CAPABILITY-UPDATE-0 — AT V4 模型可见执行环境语义更新与真实 LLM 验证

## 1. 任务目标

让 LLM 对 AG 当前已存在的 AT V4 执行环境形成正确认知，并证明模型能够自主区分 Local Shell 与 ADB Shell。

## 2. 基线

| 项 | 值 |
|---|---|
| START_HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4` |
| FINAL_HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4`（未提交） |
| WORKTREE_BEFORE | DIRTY (28 files: V4 migration + 4 bug fixes + docs) |
| WORKTREE_AFTER | DIRTY (28 files + 1 new doc = 本文件) |
| AT freeze | `AT.V4-CONSUMER-FREEZE-V1` → `7216d0e` |
| 设备 | Honor ALI-AN00, API 35, serial `APLMUT4312005935` |
| AG UID | 10370 (u0_a370) |

## 3. Before / After 语义

### 3.1 Before (旧 ToolSpec description)

```
WHAT: Execute one non-interactive shell command on the Android device
(via the app's own local shell) and return its result.
NOTE: Commands run as the app UID (not shell/uid=2000).
```

**问题：** "on the Android device" 暗示 ADB shell；未提及 adb CLI 可用性；principal distinction 不够明确。

### 3.2 After (新 ToolSpec description)

```
WHAT: Execute one non-interactive command in AndroidAgent's local shell
environment and return its result.
CONTEXT: Commands run locally under AndroidAgent's application execution
identity by default. File paths are relative to the app's private storage.
ADB: An `adb` CLI provided by AndroidTerminal V4 is available inside this
shell. Use `adb ...` for ADB-specific operations. Use `adb shell ...`
to run commands under the Android shell principal (a different execution
identity from the local app shell). Inspect with `adb shell id` when
identity matters.
WHEN: Use the local shell for operations that belong to AndroidAgent's own
execution environment (files, local state). Use the `adb` CLI inside this
shell when the task requires ADB-specific operations, ADB shell privileges,
pairing, or device-side shell access.
```

### 3.3 变更原因

AT V4 集成后，`android_shell` 的执行语义已从"ADB shell on connected device"变为：
- 默认 = Local one-shot（app UID）
- `adb ...` = AT V4 CLI facade → ADB engine → adbd
- `adb shell ...` = 不同 execution principal (uid=2000)

旧描述来自 AG.SHELL-1 时代（当时 android_shell 直接走 ADB），现在存在歧义。

## 4. 精确模型可见契约

### ToolSpec
- name: `android_shell`（不变）
- arguments: `{"command": "..."}` (不变)
- description: 重写（local shell / app identity / adb CLI / principal distinction）
- result: `outcome/exitCode/stdout/stderr/hasError/error`（不变）
- COMPLETED → success=true even if exitCode≠0（不变）

### AgentInstructions
- version: `AGENT_INSTRUCTIONS_V0` → `AGENT_INSTRUCTIONS_V1`
- 新增 4 行环境语义（末尾追加）：
```
Execution environment: android_shell runs in AndroidAgent's local app shell.
An adb CLI supplied by the AndroidTerminal environment is available there.
Local shell identity and adb shell identity are different execution principals;
do not assume they are the same.
```
- 原有 5 条原则全部保留

## 5. 单元测试 / 静态验证

| 测试 | 结果 |
|---|---|
| U15_spec_describes_local_shell_and_app_identity | PASS |
| U16_spec_describes_adb_cli_availability | PASS |
| U17_spec_distinguishes_local_and_adb_shell_principals | PASS |
| U18_spec_preserves_result_semantics | PASS |
| U19_tool_name_unchanged | PASS |
| T8_same_instructions_same_hash (version→V1) | PASS |
| T9_redaction_never_emits_fake_sentinels (version→V1) | PASS |
| 全量回归 (324 tests) | PASS |
| assembleDebug | PASS |

## 6. Direct Execution Ground Truth (Phase E)

| Gate | 结果 | 证据 |
|---|---|---|
| DIRECT_LOCAL_ID | PASS | `uid=10370(u0_a370) ... context=u:r:untrusted_app:s0` |
| DIRECT_ADB_CLI | PASS | exit 0, "AndroidTerminal adb CLI (app_process entrypoint, bridge protocol v1)" |
| DIRECT_ADB_ID | PASS | `uid=2000(shell) ... context=u:r:shell:s0` |
| DIRECT_ADB_DEVICE_QUERY | PASS | `ALI-AN00` |

全部通过 AG 生产 Tool 路径：`tool_invoke → ToolRegistry.dispatch → AndroidShellToolImpl → AtExecutionAdapter.executeLocal → Local PTY`

## 7. Real LLM Trace (Phase F)

### Test 1: Principal Distinction

**Goal:** "Find out what identity your normal command execution uses and what identity ADB shell execution uses on this device. Explain the difference based on what you actually observe."

**Model behavior (2 ToolCalls):**
1. `android_shell({"command":"id"})` → `uid=10370(u0_a370) ... untrusted_app`
2. `android_shell({"command":"adb shell id"})` → `uid=2000(shell) ... shell`

**Final Answer (excerpt):**
> These are two completely different Android security principals:
> 1. Local commands run as an ordinary installed app — uid 10370 (u0_a370)... SELinux context is untrusted_app
> 2. ADB shell commands run as uid 2000 (shell)... SELinux context is shell

**Verdict:** PASS (模型仅 2 步即正确区分两种 principal)

### Test 2: Correct ADB Selection

**Goal:** "Tell me the model name reported by the Android device through the ADB execution path."

**Model behavior (1 ToolCall):**
1. `android_shell({"command":"adb shell getprop ro.product.model"})` → `ALI-AN00`

**Final Answer:**
> The model name reported by the Android device through the ADB execution path (`adb shell getprop ro.product.model`) is **ALI-AN00**.

**Verdict:** PASS (模型自主选择 ADB 路径，无需任何命令提示)

### 判定

| Gate | 结果 |
|---|---|
| MODEL_AUTONOMOUS_LOCAL_SELECTION | PASS |
| MODEL_AUTONOMOUS_ADB_SELECTION | PASS |
| LOCAL_PRINCIPAL_OBSERVED | PASS |
| ADB_PRINCIPAL_OBSERVED | PASS |
| PRINCIPAL_DISTINCTION_CORRECT | PASS |
| OBSERVATION_DEPENDENCY | PASS |
| DEVICE_FACT_MATCH | PASS |

## 8. ADB 连接恢复记录

本轮遇到 ADB 连接中断（AG 重启后 NSD 发现 0 endpoints）。根因：
- `adb install -r` 后 APK 路径变更
- 旧 `adb_bootstrap_*.sh` 引用已不存在的 APK 路径
- `app_process` 加载失败 → SIGABRT (exit 134)

修复：删除旧 bootstrap 脚本 + 通过 `session_open backend=local_cli` 触发 `writeCliBootstrapScript` 重新生成（新 APK 路径）。

AG 内部 ADB 自动重连机制正常：`connectAuto READY via 127.0.0.1:37549`（MDNS_LOOPBACK）。

## 9. 架构 Gate 确认

```
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
TOOL_CONTRACT_CHANGED = NO
TOOL_NAME_CHANGED = NO
TOOL_ARGUMENT_SCHEMA_CHANGED = NO
TOOL_RESULT_SEMANTICS_CHANGED = NO
NEW_MODEL_VISIBLE_TOOL = NO
ADB_SKILL_IMPLEMENTED = NO
SKILLS_LOADER_IMPLEMENTED = NO
PERSISTENT_TERMINAL_MODEL_VISIBLE = NO
SESSION_PERSISTENCE_CHANGED = NO
AT_SOURCE_CHANGED = NO
```

## 10. 本轮变更文件

| 文件 | 变更类型 |
|---|---|
| `tool-android-shell/.../AndroidShellToolImpl.kt` | ToolSpec description 重写 |
| `app/.../AgentInstructions.kt` | V0→V1 + 环境语义 |
| `app/.../AgentPreflightTest.kt` | 版本 pin V0→V1 (2处) |
| `tool-android-shell/.../AndroidShellToolImplTest.kt` | 新增 U15-U19 |
| `docs/implementation/AG.AT-V4-MODEL-CAPABILITY-UPDATE-0.md` | 本文件（新增） |

## 11. 最终报告

```text
AG.AT-V4-MODEL-CAPABILITY-UPDATE-0 FINAL REPORT

START_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

FINAL_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

WORKTREE_BEFORE =
DIRTY (28 files: V4 migration + 4 bug fixes + docs)

WORKTREE_AFTER =
DIRTY (28 files + 1 new doc)

PRE_EXISTING_DIRTY_FILES_PRESERVED =
YES

OLD_ANDROID_SHELL_SEMANTICS =
"Execute one non-interactive shell command on the Android device (via the app's own local shell). NOTE: Commands run as the app UID (not shell/uid=2000)."

NEW_ANDROID_SHELL_SEMANTICS =
"Execute one non-interactive command in AndroidAgent's local shell environment. Commands run locally under AndroidAgent's application execution identity by default. An adb CLI provided by AndroidTerminal V4 is available. adb shell = different execution principal."

LOCAL_EXECUTION_SEMANTICS =
PASS (uid=10370 u0_a370, untrusted_app)

ADB_CLI_SEMANTICS =
PASS (AT V4 CLI facade, app_process entrypoint, bridge protocol v1)

ADB_SHELL_SEMANTICS =
PASS (uid=2000 shell, shell SELinux context)

INSTRUCTIONS_VERSION_BEFORE =
AGENT_INSTRUCTIONS_V0

INSTRUCTIONS_VERSION_AFTER =
AGENT_INSTRUCTIONS_V1

INSTRUCTIONS_HASH_AFTER =
(computed at class load; pinned by T8 test)

DIRECT_LOCAL_ID_GATE =
PASS

DIRECT_ADB_CLI_GATE =
PASS

DIRECT_ADB_ID_GATE =
PASS

DIRECT_ADB_DEVICE_QUERY_GATE =
PASS

REAL_LLM_USED =
YES

MODEL_PROVIDER =
OpenRouter

MODEL =
deepseek/deepseek-v4-flash-0731

REAL_LLM_PRINCIPAL_TEST =
PASS

MODEL_AUTONOMOUS_LOCAL_SELECTION =
PASS

MODEL_AUTONOMOUS_ADB_SELECTION =
PASS

LOCAL_PRINCIPAL_OBSERVED =
PASS

ADB_PRINCIPAL_OBSERVED =
PASS

PRINCIPAL_DISTINCTION_CORRECT =
PASS

OBSERVATION_DEPENDENCY =
PASS

REAL_LLM_ADB_QUERY_TEST =
PASS

DEVICE_FACT_MATCH =
PASS (ALI-AN00)

AG_CORE_CHANGED =
NO

AG_RUNTIME_CHANGED =
NO

TOOL_CONTRACT_CHANGED =
NO

TOOL_NAME_CHANGED =
NO

TOOL_ARGUMENT_SCHEMA_CHANGED =
NO

TOOL_RESULT_SEMANTICS_CHANGED =
NO

NEW_MODEL_VISIBLE_TOOL =
NO

ADB_SKILL_IMPLEMENTED =
NO

SKILLS_LOADER_IMPLEMENTED =
NO

PERSISTENT_TERMINAL_MODEL_VISIBLE =
NO

SESSION_PERSISTENCE_CHANGED =
NO

AT_SOURCE_CHANGED =
NO

TESTS =
PASS (324/324 + 5 new U15-U19)

BUILD =
PASS

DEVICE_TEST =
PASS

TASK_OWNED_FILES =
AndroidShellToolImpl.kt (description)
AgentInstructions.kt (V0->V1)
AgentPreflightTest.kt (version pin)
AndroidShellToolImplTest.kt (U15-U19)
AG.AT-V4-MODEL-CAPABILITY-UPDATE-0.md (this doc)

UNEXPECTED_FILES =
NONE

FINAL_VERDICT =
PASS — MODEL_AT_V4_SEMANTICS = VERIFIED
```
