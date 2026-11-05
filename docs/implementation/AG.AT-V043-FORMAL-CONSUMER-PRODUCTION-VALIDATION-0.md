# AG.AT-V043-FORMAL-CONSUMER-PRODUCTION-VALIDATION-0

**Date:** 2026-11-05
**Status:** CLOSED
**VERDICT:** PASS

---

## Purpose

Formal AT v0.4.3 consumer sync + production execution validation + direct one-shot network closure.

This step establishes **AT v0.4.3** as AG's formal consumer baseline, replacing the v0.4.2 candidate. It directly validates the Local/ADB one-shot network capability that was previously only "equivalence inferred" in AT.NETWORK-ROOT-CAUSE-0.

---

## What Changed

| Area | Change |
|------|--------|
| `settings.gradle.kts` | v0.4.3 repo ACTIVE, v0.4.2 candidate + v4 freeze → HISTORICAL (commented) |
| `third_party/at-v043-consumer-freeze-v1/` | NEW: formal v0.4.3 bundle (6 artifacts, 6/6 SHA256 verified) |
| `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md` | Updated: CURRENT = AT v0.4.3 |
| Production source | **UNCHANGED** (no integration-at, tool-android-shell, AG Core, Runtime modifications) |

---

## Artifact Provenance

```
AT v0.4.3 (tag 2e1cdd5, source 5517fc0, fix 4ebabe5)
  → third_party/at-v043-consumer-freeze-v1/ (AG repo)
  → settings.gradle.kts active maven repo
  → Gradle resolves com.at.*:4.0.0 from v0.4.3 bytes
  → SHA256 6/6 verified
```

---

## Production Validation Summary

All gates via **DebugBridge tool_invoke** (production ToolRegistry, NO LLM):

| Gate | Result |
|------|--------|
| L1-L6 Local one-shot | PASS (uid=10418, stdout/stderr/exit7/cmd-not-found) |
| LN1-LN3 Local network | PASS (TCP + HTTPS 200 + content) — **DIRECT VERIFIED** |
| A CLI bootstrap | PASS |
| ADB1-4 ADB one-shot | PASS (uid=2000, stdout/stderr/exit7) |
| AR Router trace | PASS (executeShellLocalhost, 0 bypass) |
| AN1-AN3 ADB network | PASS (TCP + HTTPS 200) — **DIRECT VERIFIED** |

---

## Network Evidence Update

| Path | Before | After |
|------|--------|-------|
| LOCAL_PTY_NETWORK | historically direct verified | (unchanged) |
| ADB_PTY_NETWORK | historically direct verified | (unchanged) |
| LOCAL_ONESHOT_NETWORK | equivalence only | **DIRECTLY VERIFIED VIA AG PRODUCTION CONSUMER** |
| ADB_ONESHOT_NETWORK | equivalence only | **DIRECTLY VERIFIED VIA AG PRODUCTION CONSUMER** |

---

## Architecture Invariants (preserved)

- AG Core / Runtime: NO AT dependency
- AdbRuntime: sole ADB transport router
- Local one-shot: AG primary local execution (app UID)
- ADB one-shot: secondary shell-UID path via canonical router
- PTY: special interactive path (not tested this round)

---

## Known Limitations

- Timeout: NOT_EXPOSED in current ToolSpec (no change made)
- G-b1: NOT REOPENED
- Wireless direct one-shot: NOT isolated (carried forward from v0.4.3 freeze)
- APK path: unchanged during reinstall (stale-path gate not triggered)

---

## Evidence

`docs/evidence/ag_at_v043_formal_consumer_production_validation_0/` (17 files)

## Next

AG.TOOL-SELECTION-SESSION-DURABILITY-0
