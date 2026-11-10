# AG.AT-V044-CONSUMER-UPDATE-0

**Date:** 2026-11-10
**Type:** AT v0.4.4 Consumer Migration
**Status:** CLOSED
**VERDICT:** PASS

---

## Summary

Upgraded AndroidAgent's AT consumer from v0.4.3 to v0.4.4. The migration is a
pure artifact swap: the v0.4.4 public API is byte-identical to v0.4.3 (only
KDoc documentation changed in `ATAndroid.kt`). No AG production code was modified.

---

## Change

**Single code file:** `settings.gradle.kts`
- Maven repo path: `third_party/at-v043-consumer-freeze-v1/m2` → `third_party/at-v044-consumer-freeze-v1/m2`

**New vendor bundle:** `third_party/at-v044-consumer-freeze-v1/`
- 8 AARs/JARs built from AT source commit `e2f2d7b` (tag `v0.4.4`)
- Repo-local Maven metadata (POMs)

**SSOT updated:** `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`

---

## Verification

| Gate | Result |
|------|--------|
| Build (all modules) | PASS |
| Unit tests (integration-at, tool-android-shell, app) | PASS (0 failures) |
| Debug APK | PASS |
| API delta (git diff v0.4.3..v0.4.4) | KDoc only, zero signature change |
| Ownership audit | PASS (no AG duplication of AT) |
| DebugBridge dependency | NONE |
| Shared Session boundary | AG does not consume |
| Local one-shot | PASS (uid=10418, exitCode=0) |
| ADB one-shot | PASS (uid=2000, exitCode=0) |
| ToolResult semantics | PASS (exitCode=7/127/1 preserved, success=true) |
| Real-LLM local smoke | PASS (model: `id` → uid=10418) |
| Real-LLM ADB smoke | PASS (model: `adb shell id` → uid=2000) |
| Router regression | None (no backend code change) |
| Pairing semantics | Unchanged |

---

## Device Validation

Device: HONOR ALI-AN00 (APLMUT4312005935), Android 15
AG PID: 31149, adbState: READY

**Local one-shot:**
- `id` → uid=10418(u0_a418), exitCode=0
- `pwd` → /data/user/0/com.androidagent.app/files, exitCode=0
- `sh -c 'exit 7'` → success=true, exitCode=7
- cmd-not-found → success=true, exitCode=127

**ADB one-shot:**
- `adb shell id` → uid=2000(shell), exitCode=0, stderr="[adb] connected"
- `adb shell getprop ro.product.model` → ALI-AN00, exitCode=0
- `adb shell ls /nonexistent` → exitCode=1, stderr="No such file or directory"

**Real-LLM smoke (OpenRouter / deepseek-v4-flash-0731):**
- R1: model executed `id`, correctly reported uid=10418 (app identity)
- R2: model executed `adb shell id`, correctly reported uid=2000 (shell identity)

---

## Scope

```
AT_CONSUMER_VERSION = v0.4.4
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
PRODUCTION_CODE_CHANGED = settings.gradle.kts only (repo path)
SUBAGENTS_USED = NO
```

---

## Evidence

`docs/evidence/ag_at_v044_consumer_update_0/` (18 files)
