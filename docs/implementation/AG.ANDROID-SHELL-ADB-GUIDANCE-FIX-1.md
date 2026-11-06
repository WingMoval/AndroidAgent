# AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1

**Date:** 2026-11-07
**Type:** Minimal Production Guidance Fix + Real-LLM Validation
**Status:** CLOSED
**VERDICT:** PASS

---

## Purpose

Based on `AG.ADB-SEMANTICS-AND-LEGADO-WRITE-AUDIT-0` findings, minimally modify
the `android_shell` model-visible ToolSpec guidance so the model correctly:

1. Understands the embedded adb is **not** full desktop adb
2. Constructs `adb shell` commands correctly (argv vs `sh -c`)
3. Judges command success from **exitCode**, not `ToolResult.success`

Validated with real LLM (OpenRouter / deepseek-v4-flash-0731) on device.

---

## Change

**Single file:** `tool-android-shell/.../AndroidShellToolImpl.kt`
(ToolSpec description string only — no logic, no contract, no mapper change)

Three additions to the model-visible guidance:

| Gate | Addition |
|------|----------|
| C1 | "limited embedded CLI, NOT the full desktop platform-tools adb; do not assume `adb devices` etc." |
| C2 | Simple: `adb shell cat /path` (NOT `adb shell "cat /path"`). Shell syntax: `adb shell sh -c 'ls ... \| grep ... \| wc -l'` |
| C3 | "success=true only means mechanism completed. Judge command success from exitCode (0=success, non-zero=failure)." |

**5 new unit tests** (U23–U27) verify the guidance contains stable semantic
substrings. No full-string snapshot (avoids fragility).

---

## Real-LLM Validation

| Gate | Prompt (natural, no guidance hints) | Model's tool call | Result |
|------|-------------------------------------|-------------------|--------|
| F1 | "请读取文件 .../bookGroup.json 的内容" | `adb shell cat /path` | PASS — correct argv, exit 0, answer from device |
| F2 | "统计 /system/bin 中名称包含 sh 的条目数量" | `adb shell sh -c 'ls /system/bin \| grep sh \| wc -l'` | PASS — correct sh -c, exit 0, answer=19 |
| F3 | "检查路径 ... 是否存在" | `adb shell sh -c 'test -e ... \|\| echo MISSING'` | PASS — correctly says "不存在" |

**Key result:** The model autonomously chose the correct forms in all 3 gates.
Zero occurrences of `adb shell "multi-word command"`. The model reads
exitCode/stdout to judge the real result.

---

## Scope

```
PRODUCTION_CHANGE = ANDROID_SHELL_MODEL_GUIDANCE_ONLY
TESTS_CHANGED = YES (5 new)
AT_CHANGED = NO
TOOLRESULT_CONTRACT_CHANGED = NO
SHELL_RESULT_MAPPER_CHANGED = NO
LEGADO_DATA_CHANGED = NO
```

---

## Evidence

`docs/evidence/ag_android_shell_adb_guidance_fix_1/` (11 files)

---

## Final

```
ADB_GUIDANCE_GAP = CLOSED
PRODUCTION_DEFECT = NONE
CURRENT_CAPABILITY_RELEASE_BASELINE = READY_WITH_GUIDANCE_FIX
NEXT = RELEASE_BASELINE_FREEZE
STOP = YES
```
