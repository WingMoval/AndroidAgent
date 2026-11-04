# AG.AT-V042-PAIRING-TO-EXECUTION-STATE-AUDIT-0

## Summary

**TASK**: Trace PAIRING → BOOTSTRAP → READINESS → ACTIVE TRANSPORT → EXECUTION ROUTE. Find the first state divergence. Determine if `activeTransport=LOCAL` is really written back to WIRELESS, or if the CLI execution thread reads a stale/wrong value.

**RESULT**: VERDICT = **PASS_FIRST_DIVERGENCE_FOUND** | **PAIRING_EXCLUDED**

The pairing state is correct (key trusted, metadata valid, identity present). The first divergence is at **LAYER 4→5**: `activeTransport` is correctly written as LOCAL at birth, but the command execution routes to WIRELESS. The root cause class is **ACTIVE_TRANSPORT_CROSS_THREAD_VISIBILITY**: `AdbRuntime.activeTransport` is a plain `var` (no `@Volatile`), written by the IO thread and read by the CLI bridge worker thread with no happens-before guarantee.

## Key Findings

### 1. Five-Layer State Consistency

| Layer | Status | Correct? |
|-------|--------|----------|
| PAIRING_COMPLETE | Key trusted, AUTH passes | YES |
| IDENTITY_TRUSTED | adbd "adb client authorized" | YES |
| LOCAL_READY | ONLINE + HUB STARTED + "LOCAL active" | YES |
| ACTIVE_TRANSPORT_LOCAL | Written LOCAL at birth; read as WIRELESS at command | **DIVERGENCE** |
| EXECUTION_ROUTE_LOCAL | `AdbNative.executeShell` (Wireless) called | **NO** |

### 2. activeTransport: Plain var, No Synchronization

```kotlin
// AdbRuntime.kt:84
var activeTransport: AdbTransportMode = AdbTransportMode.WIRELESS
```

- **Writer**: IO thread (probeLocalFirst, line 982) → LOCAL
- **Reader**: CLI bridge worker thread (AdbBackend.ensureConnected, line 64)
- **@Volatile**: NO
- **Synchronization**: NONE
- **HAPPENS_BEFORE_PROVEN**: NO

### 3. Execution Path Bypasses AdbRuntime Routing

```
AdbCliBridgeServer.onOneShot()
  → AdbOneShotBackend.execute()
    → AdbEngine.executeShell()  ← DIRECT WIRELESS JNI
    → AdbRuntime.onOperationResult(transportLost=true)  ← writes WIRELESS
```

`AdbBackend` calls `AdbEngine.executeShell()` directly (not `AdbRuntime.executeShell()`). The `ensureConnected()` check at line 64 is the ONLY routing gate. If it returns false, the Wireless path is taken unconditionally.

### 4. Restart and Re-pair Do Not Fix

- RESTART_AFTER_PAIR_RESULT = **FAIL**
- SAME_KEY_REPAIR_RESULT = **FAIL**
- Command immediately after ONLINE = **FAIL**

→ NOT a pairing state issue. NOT a timing issue.

### 5. AT Control: Works with Same Native Code

AT (same native code, same key) receives RX_WRTE data and executes commands. The difference is in the execution routing, not the native handshake.

## Excluded

- ❌ Pairing post-state not propagated
- ❌ Bootstrap state projection bug
- ❌ Pairing callback transport override
- ❌ Readiness routing mismatch
- ❌ Pair state restore bug
- ❌ adbd "already offline" as root cause (non-discriminating)

## Root Cause

**ROOT_CAUSE_CLASS = ACTIVE_TRANSPORT_CROSS_THREAD_VISIBILITY**

`AdbRuntime.activeTransport` is a plain `var` without `@Volatile`. The IO thread writes LOCAL at birth; the CLI bridge worker thread reads it at command time. Without a happens-before guarantee, the read may return the stale initial value (WIRELESS), causing the execution to route to the Wireless path, which fails because no Wireless connection exists.

**CONFIDENCE = MEDIUM** (the 5s gap makes a pure stale-read unlikely in practice; an alternative is `localhostConnected()` returning false at command time)

## Minimum Fix Direction

1. Make `AdbRuntime.activeTransport` `@Volatile`
2. Add diagnostic log in `AdbBackend.ensureConnected()` recording `activeTransport`, `localhostConnected()`, `connectionState().isOnline`
3. If `localhostConnected()` returns false despite HUB being alive, investigate native transport state visibility

## Evidence

See `docs/evidence/ag_at_v042_pairing_to_execution_state_audit_0/` (6 files)
