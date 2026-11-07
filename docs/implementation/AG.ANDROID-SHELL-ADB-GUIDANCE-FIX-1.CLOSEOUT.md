# AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1.CLOSEOUT

**Date:** 2026-11-07
**Type:** Validation Closeout Only (no production change)
**Status:** CLOSED
**VERDICT:** PASS

---

## Purpose

Close the one gap in `AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1`: prove the real LLM
correctly interprets **`ToolResult.success=true` + `exitCode!=0`** — i.e. that
the model reads exitCode (not the success flag) to judge command outcome.

F1/F2 already accepted. The previous F3 attempt used `test -e ... || echo
MISSING` which made exitCode=0, so the nonzero-exitCode interpretation was not
directly proven. This closeout targets exactly that.

---

## Method

- Fresh TopicSession.
- Prompt **explicitly gives** the command `adb shell ls <nonexistent-path>` and
  forbids rewriting it (tests result interpretation, not command planning).
- Required observation: `success=true` + `outcome=COMPLETED` + `exitCode=1`.

---

## Result

**Tool call (unchanged):**
```
adb shell ls /data/local/tmp/ag_nonzero_exit_probe_91C7
```

**ToolResult:**
```
success=true, outcome=COMPLETED, exitCode=1
stderr: "No such file or directory"
```

**Model answer (key parts):**
> 命令执行机制成功完成（outcome: COMPLETED, hasError: false），但**命令本身的
> 退出码为 exitCode: 1（非零）**，说明远端 ls 命令执行失败。
>
> 该路径**不存在**。ls 因此以非零退出码（1）失败。

The model correctly separated the two layers:
- mechanism: COMPLETED / success=true
- command: exitCode=1 → **failed**
- path: **absent** (from stderr)

No forbidden conflation ("success=true so command succeeded" / "path exists").

**Path remained absent** before and after (no device side effect).

---

## Acceptance

```
REQUIRED_COMMAND_EXECUTED_UNCHANGED = YES
TOOLRESULT_SUCCESS = true
COMMAND_EXITCODE_NONZERO = YES (1)
MODEL_NOT_FOOLED_BY_SUCCESS_TRUE = YES
MODEL_READS_EXITCODE_CORRECTLY = YES
MODEL_CLASSIFIES_COMMAND_AS_FAILED = YES
MODEL_CLASSIFIES_PATH_AS_ABSENT = YES
DEVICE_STATE_CHANGED = NO
```

---

## Scope

```
PRODUCTION_CODE_CHANGED = NO
ANDROID_SHELL_GUIDANCE_CHANGED = NO
AT_CHANGED = NO
TOOLRESULT_CONTRACT_CHANGED = NO
DEVICE_STATE_CHANGED = NO
```

Docs/evidence + closeout report only.

---

## Final

```
AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1 = PASS / CLOSED
ADB_GUIDANCE_GAP = CLOSED
EXITCODE_RESULT_GUIDANCE = CLOSED
CURRENT_CAPABILITY_RELEASE_BASELINE = READY_WITH_GUIDANCE_FIX
RELEASE_BLOCKER = NONE
NEXT = RELEASE_BASELINE_FREEZE
STOP = YES
```
