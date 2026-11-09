# AG.AT-PAIRING-READINESS-SEMANTICS-AUDIT-0

**Date:** 2026-11-09
**Type:** Read-only Cross-Layer Semantics Audit
**Status:** CLOSED
**VERDICT:** PASS_WITH_FINDINGS

---

## Purpose

Audit whether AT's pairing/connection/readiness states survive the
`AT → integration-at → android_shell → ToolResult` chain and are
understandable by the LLM. No production change.

---

## Key Findings

### AT has a RICH state model

- **AdbRuntimeState** (9 values): DISCONNECTED, CONNECTING, READY, CONNECT_FAILED, ENDPOINT_NOT_FOUND, USER_ACTION_REQUIRED, TRANSPORT_LOST, RECOVERING, RECOVERY_FAILED
- **AdbBootstrapState** (6 values): READY, PAIRING_REQUIRED, USER_ACTION_REQUIRED_WD, CONNECT_FAILED, ENDPOINT_NOT_FOUND, NOT_READY
- **PairingResult.Outcome** (5 values): SUCCESS, REJECTED, SERVICE_NOT_FOUND, MULTIPLE_ENDPOINTS, FAILED

### The LLM sees specific, actionable text

When ADB is unpaired and the model runs `adb shell id`:
```
success=true, exitCode=1, stdout=""
stderr="[adb] pairing required: no ADB identity found
[adb] open Settings -> Wireless Debugging -> Pair device with pairing code,
then run: adb pair <host>:<port> <code>"
```

The LLM can clearly understand: command didn't execute, reason is pairing,
and what to do about it.

### Local shell is fully independent of ADB pairing

```
id  → uid=10418(u0_a418)  [app UID, always works]
adb shell id  → uid=2000(shell)  [requires pairing]
```

`ADB_UNPAIRED != AG_UNAVAILABLE`

### AT PairActivity is reusable for AG

Self-contained, uses the same AdbConsumerBootstrap, handles all endpoint cases.
AG sidebar "ADB Connection" can adapt this pattern without protocol changes.

---

## Decision

```
PAIRING_ERROR_SEMANTICS = SUFFICIENT
AG_SEMANTICS_CHANGE_REQUIRED = NO
SHOULD_AG_REQUIRE_PAIRING_AT_FIRST_RUN = NO
ADB_PAIRING_IS_OPTIONAL_CAPABILITY_SETUP = YES
NEXT_PRODUCT_WORK = ADB connection/sidebar UI only
```

---

## Non-blocking Findings

1. USER_ACTION_REQUIRED (WD off) collapsed into generic "not connected" at CLI level
2. TRANSPORT_LOST / RECOVERING collapsed (auto-recovery handles)
3. AG AtAdbReadiness.PAIRING_REQUIRED value never set by current mapping (cosmetic)

---

## Scope

```
DOCS_EVIDENCE_ONLY = YES
PRODUCTION_CODE_CHANGED = NO
AT_CHANGED = NO
PAIRING_STATE_CHANGED_ON_DEVICE = NO
AT_CONSUMER_VERSION = v0.4.3
```

---

## Evidence

`docs/evidence/ag_at_pairing_readiness_semantics_audit_0/` (11 files)
