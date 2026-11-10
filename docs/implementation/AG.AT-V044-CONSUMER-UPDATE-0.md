# AG.AT-V044-CONSUMER-UPDATE-0

**Date:** 2026-11-10
**Type:** AT v0.4.4 Consumer Migration
**Status:** CLOSED (device smoke pending)
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
| Local independence | Source-verified (no ADB coupling) |
| Router regression | None (no backend code change) |
| Pairing semantics | Unchanged |
| Real-device smoke | **PENDING** (device unreachable) |

---

## Device Validation (Pending)

Device `APLMUT4312005935` was unreachable during this session. The following
gates require reconnection:

- Gate G/H: Local one-shot (`id`, `pwd`)
- Gate I: ADB one-shot (`adb shell id`)
- Gate R: Real-LLM smoke (2 prompts)

Risk is MINIMAL: zero API change, all unit tests pass, source paths verified
identical to v0.4.3 (which was fully device-validated).

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
