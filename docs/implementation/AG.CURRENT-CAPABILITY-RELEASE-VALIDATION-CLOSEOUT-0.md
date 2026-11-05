# AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-CLOSEOUT-0

**Date:** 2026-11-06
**Status:** CLOSED
**VERDICT:** PASS

---

## Purpose

Close 3 gaps found by Controller Review in AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-0:

| Gap | Description | Resolution |
|-----|-------------|------------|
| GAP-1 | Full module test baseline not truly green | Test fakes repaired, 601/0 fail |
| GAP-2 | Historical session switch-back not verified | UI session browser B→A verified |
| GAP-3 | ToolResult durable restore not verified | Force-stop → restore → 0-toolcall recall PASS |

---

## GAP 1: Test Harness Repair

**Root cause:** AG.UI-TRANSCRIPT-CARDS-1 added `listSessions()` to `TopicSessionStore`
interface. 4 test fakes were not updated (test-only compilation error).

**Fix:** Added minimal `listSessions()` override to 4 test fake classes.
No production code changed.

**Result:** 601 tests / 0 fail / 3 skip / assembleDebug + assembleRelease PASS.

---

## GAP 2: Session Switch-Back

**Method:** UI Session Browser (sidebar → Current Session → session list → tap).

- Created Session A ("SESSION_SWITCH_A_7F3A") and Session B ("SESSION_SWITCH_B_91C2")
- Switched B → A via UI dialog
- Verified: A transcript contains SESSION_SWITCH_A_7F3A, does NOT contain SESSION_SWITCH_B_91C2
- currentSessionId changed correctly (B's ID → A's ID)

**Result:** HISTORICAL_SESSION_SWITCH_BACK = PASS

---

## GAP 3: ToolResult Durable Restore

**Complete chain verified:**

```
real LLM → file.read ToolCall → real ToolResult (RESTORE_227DD7513F1B)
→ durable persistence → force-stop (PID 25046→30238) → restart
→ DB restore → Runtime/Core hydrate → next ModelRequest history
→ 0 new ToolCall → correct token recall
```

- New session C with real file.read (token RESTORE_227DD7513F1B)
- Pre-stop: 6 transcript blocks, ToolCall + ToolResult present
- Force-stop → restart: exact session C restored (5 blocks)
- Post-restore: 0 new toolcalls, model correctly recalls token from history
- NO model replay, NO tool replay on restore

**Result:** TOOLRESULT_DURABLE_RESTORE = PASS

---

## Previous Validation Reconciliation

AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-0 (85e1254) remains as
point-in-time functional smoke record. Its gates B/I/J-K had the gaps
closed by this document.

---

## Scope

```
PRODUCTION_CODE_CHANGED = NO
TEST_HARNESS_ONLY_CHANGE = YES (4 test fakes)
NEW_CAPABILITY_ADDED = NO
AT_CHANGED = NO
```

---

## Evidence

`docs/evidence/ag_current_capability_release_validation_closeout_0/` (14 files)

---

## Final

```
CURRENT_CAPABILITY_RELEASE_BASELINE = READY
RELEASE_BLOCKER = NONE
G_B1 = KNOWN_DEFERRED_LIMITATION
NEXT = RELEASE_BASELINE_FREEZE
STOP
```
