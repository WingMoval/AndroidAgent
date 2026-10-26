# AG.FRESH-INSTALL-LLM-PAIRING-E2E-0 — Fresh Install / Fresh Identity / LLM 自主配对全链路真机验收

## 1. 任务目标

从全新安装状态开始，清除既有 AG ADB identity，重新建立 fresh identity，让真实 LLM 在无命令提示下自主完成 ADB pairing、建立 READY、执行 adb shell，验证 App 重启后无需重新配对。

## 2. 基线

| 项 | 值 |
|---|---|
| START_HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4` |
| FINAL_HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4`（未提交） |
| WORKTREE | DIRTY (40 files) |
| 设备 | Honor ALI-AN00 (X50), API 35, serial `APLMUT4312005935` |
| 设备 IP | 192.168.137.3 |

## 3. Build

| 项 | 值 |
|---|---|
| BUILD | PASS |
| APK_SHA256 | `D87E4CF2058A6617E2BF36D97E62E66D9E17486638111B4F7E82D55970A28D97` |
| APK_SIZE | 7,973,551 bytes |

## 4. Fresh Install 证据

| 项 | 值 |
|---|---|
| OLD_APK_PATH | `/data/app/~~MrmvjJF0Pntlj8bEWpSYXQ==/...` |
| OLD_AG_IDENTITY_FINGERPRINT | `QAAAAOuafSk9wgQGgYUrtfASVy/opnibComOjZoTt5Z5H7lDZ/T2lxF7MP9vSj...` |
| APP_UNINSTALLED | YES |
| OLD_APP_DATA_REMOVED | YES (`/data/user/0/com.androidagent.app` → No such file) |
| NEW_APK_PATH | `/data/app/~~IKDGkO-CNgYwMao796vnRw==/...` |
| NEW_APP_UID | 10418 (u0_a418) |

## 5. Fresh Identity

| 项 | 值 |
|---|---|
| NEW_AG_IDENTITY_PRESENT | YES |
| NEW_AG_IDENTITY_FINGERPRINT | `QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefMEsa4Hdja1r8eJwxs9gpbwgdj...` |
| NEW_IDENTITY_DIFFERS_FROM_OLD | YES |
| IDENTITY_COPIED_FROM_PC | NO |
| IDENTITY_COPIED_FROM_AT_PRODUCT | NO |
| IDENTITY_RESTORED_FROM_BACKUP | NO |

## 6. Provider Config

| 项 | 值 |
|---|---|
| PROVIDER_CONFIG_REPROVISIONED | YES (user-provided OpenRouter key) |
| PROVIDER_SECRET_LEAKED | NO |
| MODEL | deepseek/deepseek-v4-flash-0731 (StreamLake) |

## 7. Bootstrap

| 项 | 值 |
|---|---|
| BOOTSTRAP_AUTO_GENERATED | YES (first `adb` command triggered ensureCliBootstrapCurrent) |
| STALE_APK_PATH_PRESENT | NO |

## 8. LLM Pairing Trace

### Phase 1: Initial Diagnosis

**User Goal:** "这是一次全新安装。请建立你自己的 Android ADB 执行连接，并验证你通过 ADB shell 使用的执行身份。如果过程中需要我提供 Android 系统要求的人类输入，请告诉我。"

**Model behavior (6 ToolCalls):**
1. `id && which adb && adb version` → local uid=10418, adb CLI available
2. `adb devices` → unsupported (only shell + pair supported)
3. `which adb; adb` → CLI help
4. `adb shell id` → "wireless debugging: off, user action required"
5. `adb --help` → confirmed `adb pair <host>:<port> <code>` syntax
6. `adb shell echo hello` → same "not connected" error

**Final Answer:** 模型正确诊断出 Wireless Debugging 关闭，请求用户提供配对信息。

**LLM_PAIRING_REQUIREMENT_DETECTED = PASS**

### Phase 2: Pairing

**User Input:** "端口 192.168.137.3:42881，配对码 [REDACTED]"（仅提供安全输入，未提供命令）

**Model behavior:**
1. `adb pair 192.168.137.3:42881 [REDACTED]` → **"pairing succeeded (guid=adb-APLMUT4312005935-J4nb5z)"**
2. `adb shell id` → **uid=2000(shell) ... context=u:r:shell:s0**
3-8. 进一步验证 shell 身份（whoami, id -u, pwd, uname -a 等）

**PAIRING_EXECUTED_BY_LLM = YES**
**PAIR_RESULT = SUCCESS**
**ADB_SHELL_AFTER_PAIR = PASS**

### Phase 22: Device Fact

**User Goal:** "再告诉我这台设备报告的型号。"

**Model behavior:**
1. `adb shell cat /proc/device-info/model` → not found
2. `adb shell getprop | head -40` → partial
3. `adb shell getprop | grep model|product|...` → filtered
4. `adb shell getprop ro.product.model` → **ALI-AN00**

**Final Answer:** 荣耀 X50 (HONOR), model ALI-AN00, SoC SM6450

**DEVICE_FACT_VIA_LLM_ADB = PASS**
**DEVICE_FACT_MATCH = PASS**

### Phase 25: Post-Restart

**User Goal:** "验证当前 ADB shell 执行身份。"

**Model behavior (1 ToolCall):**
1. `adb shell id` → **uid=2000(shell) ... context=u:r:shell:s0**

**ADB_SHELL_AFTER_RESTART = PASS**
**REPAIRING_AFTER_RESTART = NO**

## 9. Restart Persistence

| 项 | 值 |
|---|---|
| IDENTITY_FP_BEFORE_RESTART | `QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefM...` |
| IDENTITY_FP_AFTER_RESTART | `QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefM...` |
| IDENTITY_PERSISTED | YES |

## 10. 安全 Gate

| 项 | 值 |
|---|---|
| DEBUGBRIDGE_PAIR_USED | NO |
| PC_ADB_PAIR_USED | NO |
| PC_ADB_USED_AS_EXECUTION_SUBSTITUTE | NO |
| PAIRING_CODE_RECORDED | NO (redacted in this doc) |
| TRACE_REDACTED | YES |

## 11. 架构 Gate

```
PRODUCTION_CODE_CHANGED = NO
TEST_CODE_CHANGED = NO
AT_CHANGED = NO
GRADLE_CHANGED = NO
```

## 12. 最终报告

```text
AG.FRESH-INSTALL-LLM-PAIRING-E2E-0 FINAL REPORT

START_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

FINAL_HEAD =
a6eea61dc209898a01cd0e7f2e82304fff7ed9d4

WORKTREE_BEFORE =
DIRTY (40 files)

WORKTREE_AFTER =
DIRTY (40 files + this doc)

PRE_EXISTING_DIRTY_FILES_PRESERVED =
YES

PRODUCTION_CODE_CHANGED =
NO

TEST_CODE_CHANGED =
NO

AT_CHANGED =
NO

BUILD =
PASS

APK_SHA256 =
D87E4CF2058A6617E2BF36D97E62E66D9E17486638111B4F7E82D55970A28D97

OLD_PACKAGE_INSTALLED =
YES

OLD_APP_UID =
10370 (u0_a370)

OLD_APK_PATH =
/data/app/~~MrmvjJF0Pntlj8bEWpSYXQ==/...

OLD_AG_IDENTITY_PRESENT =
YES

OLD_AG_IDENTITY_FINGERPRINT =
QAAAAOuafSk9wgQGgYUrtfASVy/opnibComOjZoTt5Z5H7lDZ/T2lxF7MP9vSj...

APP_UNINSTALLED =
YES

OLD_APP_DATA_REMOVED =
YES

OLD_IDENTITY_REMOVED =
YES

FRESH_INSTALL =
PASS

NEW_APP_UID =
10418 (u0_a418)

NEW_APK_PATH =
/data/app/~~IKDGkO-CNgYwMao796vnRw==/...

PROVIDER_CONFIG_REPROVISIONED =
YES

PROVIDER_SECRET_LEAKED =
NO

NEW_AG_IDENTITY_PRESENT =
YES

NEW_AG_IDENTITY_FINGERPRINT =
QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefMEsa4Hdja1r8eJwxs9gpbwgdj...

NEW_IDENTITY_DIFFERS_FROM_OLD =
YES

IDENTITY_COPIED_FROM_PC =
NO

IDENTITY_COPIED_FROM_AT_PRODUCT =
NO

IDENTITY_RESTORED_FROM_BACKUP =
NO

BOOTSTRAP_AUTO_GENERATED =
PASS

BOOTSTRAP_CURRENT_APK_PATH =
/data/app/~~IKDGkO-CNgYwMao796vnRw==/...

BOOTSTRAP_STALE_PATH =
NO

REAL_LLM_USED =
YES

MODEL_PROVIDER =
OpenRouter

MODEL =
deepseek/deepseek-v4-flash-0731 (StreamLake)

INITIAL_USER_PROMPT_INCLUDED_COMMAND =
NO

LLM_PAIRING_REQUIREMENT_DETECTED =
PASS

USER_PROVIDED_SECURITY_INPUT =
YES

USER_PROVIDED_COMMAND =
NO

PAIRING_EXECUTED_BY_LLM =
YES

PAIR_TOOL_NAME =
android_shell

PAIR_RESULT =
SUCCESS

PAIR_IDENTITY_MATCH =
YES (new identity used for pairing)

DEBUGBRIDGE_PAIR_USED =
NO

PC_ADB_PAIR_USED =
NO

ADB_READY_AFTER_PAIR =
YES

ADB_SHELL_AFTER_PAIR =
PASS

REMOTE_PRINCIPAL =
shell (uid=2000)

DEVICE_FACT_VIA_LLM_ADB =
PASS

DEVICE_FACT =
ALI-AN00 (Honor X50)

DEVICE_FACT_MATCH =
PASS

PC_ADB_USED_AS_EXECUTION_SUBSTITUTE =
NO

IDENTITY_FP_BEFORE_RESTART =
QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefM...

IDENTITY_FP_AFTER_RESTART =
QAAAAEuj5rmdsZX045Y4TOAL8u8/yitk8wWbsefM...

IDENTITY_PERSISTED =
YES

REPAIRING_AFTER_RESTART =
NO

ADB_READY_AFTER_RESTART =
YES

ADB_SHELL_AFTER_RESTART =
PASS

NSD_ZERO_ENDPOINT_OBSERVED =
NO (MDNS_LOOPBACK connected directly)

LOOPBACK_RECOVERY_OBSERVED =
NO (not needed)

RECOVERY_GATE =
PASS

TRACE_REDACTED =
YES

PAIRING_CODE_RECORDED =
NO

UNEXPECTED_FILES =
NONE

FINAL_VERDICT =
PASS — FRESH_INSTALL_LLM_PAIRING_E2E_VERIFIED

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

*Evidence sanitization note: The original test trace contained the transient 6-digit Wireless Debugging pairing code. This value has been redacted from the retained evidence. No production/test behavior or acceptance verdict was changed.*
