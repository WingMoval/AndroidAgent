# AG.AT-V042-ADB-BACKEND-ROUTING-FIX-0

## Summary

**TASK**: Fix `AdbOneShotBackend` bypassing the canonical `AdbRuntime.executeShell()` transport router, causing AG Local ADB one-shot commands to use the Wireless JNI path instead of `executeShellLocalhost`.

**RESULT**: VERDICT = **PASS**

All real-device gates A-F PASS. Command matrix 6/6. Repeated commands 10/10. App restart PASS. Local loss semantics PASS. Wireless regression PASS (by code inspection).

## Root Cause

**ROOT_CAUSE_CLASS = ADB_BACKEND_BYPASSES_RUNTIME_TRANSPORT_ROUTER**

`AdbOneShotBackend.execute()` directly called `AdbEngine.executeShell()` (Wireless-only JNI primitive) instead of `AdbRuntime.executeShell()` (canonical transport router).

Even when `activeTransport = LOCAL` and Local transport was ONLINE, the one-shot execution still used the Wireless JNI path, which failed with "client not initialized" because no Wireless connection existed.

## Before Fix

```
AdbCliBridgeServer.onOneShot()
  → DefaultATEngine.execute()
    → AdbOneShotBackend.execute()
      → AdbEngine.executeShell()          ← DIRECT WIRELESS JNI (BUG)
      → AdbRuntime.onOperationResult()
```

## After Fix

```
AdbCliBridgeServer.onOneShot()
  → DefaultATEngine.execute()
    → AdbOneShotBackend.execute()
      → AdbRuntime.executeShell()         ← CANONICAL ROUTER
        → if (activeTransport == LOCAL)
          → AdbEngine.executeShellLocalhost()   ← LOCAL PATH
        → else
          → AdbEngine.executeShell()            ← WIRELESS PATH
      → AdbRuntime.onOperationResult()
```

## Why This Is Root Cause

1. `AdbRuntime.executeShell()` (AdbRuntime.kt:860-865) is the canonical router that decides LOCAL vs WIRELESS based on `activeTransport`.
2. `AdbOneShotBackend.execute()` bypassed this router by calling `AdbEngine.executeShell()` directly.
3. `AdbEngine.executeShell()` is a Wireless-only JNI primitive with no routing logic.
4. Result: even with `activeTransport = LOCAL`, the command went through Wireless JNI → "client not initialized" → TRANSPORT_ERROR.

## Why Pairing Is Not Involved

- Pairing state is correct (key trusted, metadata valid, AUTH passes)
- Restart does NOT fix the issue (confirmed by previous audit)
- Same-key re-pair does NOT fix the issue
- AT standalone works with the same native code
- The divergence is in execution routing, not pairing

## Why Native Is Not Changed

- The native `executeShellLocalhost()` primitive already works correctly
- AT standalone uses the same native code successfully
- The fix is purely in the Kotlin routing layer
- No native protocol / framing / AUTH / CNXN changes

## Why AdbRuntime Remains Routing Owner

- `AdbRuntime` is the unique ADB transport lifecycle and routing owner
- The fix delegates routing to `AdbRuntime.executeShell()` (canonical router)
- `AdbOneShotBackend` no longer makes routing decisions
- No duplicate routing state in the backend
- `DUPLICATE_ROUTING_OWNER = NO`

## Key Finding: @Volatile Already Present

The previous audit (AG.AT-V042-PAIRING-TO-EXECUTION-STATE-AUDIT-0) incorrectly identified `activeTransport` as a plain `var` without `@Volatile`.

Actual source (AdbRuntime.kt:83-85):
```kotlin
@Volatile
var activeTransport: AdbTransportMode = AdbTransportMode.WIRELESS
    private set
```

`@Volatile` is ALREADY present. No change needed. The primary fix is the routing correction, not the visibility fix.

## Artifact

- **Source**: AT repo, `feature/local-tcp-v0.4.2` branch
- **AAR**: `at-backend-adb-4.0.0.aar`
- **Old SHA**: `15895A5B6FE4B699` (9196 bytes, pre-fix)
- **New SHA**: `58B26810E4E452AD` (9197 bytes, post-fix)
- **Status**: POST_V042_FIX_CANDIDATE
- **Base version**: 0.4.2
- **Patch**: ADB backend transport routing fix

## Evidence

See `docs/evidence/ag_at_v042_adb_backend_routing_fix_0/` (14 files)

## Next

- AG.AT-V043-COMPAT-VALIDATION
- AG.V1-RELEASE-FULL-CAPABILITY-VALIDATION-0
- CONTROLLER_REVIEW
