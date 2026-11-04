# AG.AT-V042-LOCAL-ONLINE-STATE-ORIGIN-AUDIT-0

## Summary

**TASK**: Find the FIRST_INVALID_ONLINE_TRANSITION — when and how AG's Local transport first becomes "ONLINE / reusable" without completing the ADB handshake.

**RESULT**: VERDICT = **PASS_FIRST_DIVERGENCE_FOUND**

The native code CORRECTLY completes the handshake and sets `online_ = true` only after `RX_CNXN`. No `FIRST_BAD_STATE_WRITE` exists in the native code. The first divergence is **not in AG's native state machine** — it is in the **adbd session state**: adbd creates a host session for AG, logs "host-N: already offline", authorizes the key, but **does not send any data**. AG's HUB08 enters an EAGAIN loop (treated as "alive"), `online_` stays true, and the command execution path falls back to Wireless (which has no connection) → `TRANSPORT_ERROR`.

## Key Findings

### 1. Handshake Completes Correctly (ALL 3 RUNS)

```
TCP_CONNECTED → TX_CNXN → RX_AUTH → TX_AUTH → RX_CNXN → ONLINE → HUB START
```

- `online_` set at `plain_tcp_connection.cpp:90` — ONLY after `RX_CNXN`
- HUB starts AFTER `isOnline() == true` — correct ordering
- No concurrent socket readers during handshake
- No reentrant `connectLocalhost` calls

### 2. adbd Session is Unresponsive

```
adbd: host-23: read thread spawning
adbd: host-23: write thread spawning
adbd: host-23: already offline        ← KEY
adbd: authPass 0
adbd: Calling send_auth_request...
adbd: adb client authorized            ← key trusted
(no further adbd logs for this session)
```

- Key IS authorized (same key as AT: `6b1949b4...`)
- TCP IS ESTABLISHED
- But adbd sends NO data (no prompt, no response to OPEN)

### 3. AT Works with Identical Behavior

AT (same native code, same key) produces IDENTICAL logs:
- Same handshake sequence
- Same "already offline" adbd log
- Same HUB08 EAGAIN loop (between commands)
- BUT: AT receives `RX_WRTE` data and sends `TX WRTE` commands

**The difference is in adbd's session state, not the client.**

### 4. Command Execution Uses Wireless, Not Local

```
AdbNative.executeShell: "'id'" timeout_ms=0    ← WIRELESS path
AdbRuntime: local transport loss; activeTransport -> WIRELESS
AdbCliBridge: outcome=TRANSPORT_ERROR exit=-1
```

`executeShellLocalhost` is NEVER called. The `activeTransport` was switched from LOCAL to WIRELESS before command execution.

### 5. EAGAIN Survival Logic

`rxLoop()` treats EAGAIN as "transport still alive" — `online_` is never cleared. The transport observer polls `isOnline()` every 1500ms and sees `true` forever. No `ONLINE→!ONLINE` edge is ever detected.

## State Matrix

| Aspect | AG (FAILING) | AT (WORKING) |
|--------|-------------|-------------|
| TX_CNXN | YES | YES |
| RX_AUTH | YES | YES |
| TX_AUTH | YES | YES |
| RX_CNXN | YES | YES |
| ONLINE | YES | YES |
| HUB START | YES | YES |
| adbd "already offline" | YES | YES |
| adbd "authorized" | YES | YES |
| HUB08 EAGAIN | YES | YES (between cmds) |
| RX_WRTE received | **NO** | **YES** |
| TX WRTE sent | **NO** | **YES** |
| Command output | **NO** | **YES** |

## Excluded Hypotheses

- ❌ Key trust (key swap: same key, still fails)
- ❌ Binary variant (both debug, identical behavior)
- ❌ TCP channel (ESTABLISHED)
- ❌ Concurrent connection (AG fails alone)
- ❌ Reentrant connect race (single connect per process)
- ❌ HUB start before handshake (HUB starts after ONLINE)
- ❌ Simultaneous socket readers (no overlap)
- ❌ Build/binary divergence (identical behavior)
- ❌ Stale state leak in connectLocalhost (all paths clean)

## Root Cause Classification

**ROOT_CAUSE_CLASS = OTHER (adbd session state)**

The root cause is NOT in AG's native code. It is in the **adbd session management**: adbd creates a host session for AG's connection, marks it "already offline", authorizes the key, but does not send data. The exact mechanism by which adbd decides to send data to AT's session but not AG's session is UNKNOWN.

## Evidence

See `docs/evidence/ag_at_v042_local_online_state_origin_audit_0/` (15 files)

## Next Steps (NOT THIS TASK)

1. Investigate adbd's "already offline" log: what triggers it?
2. Add a health-check to HUB RX loop (no data after ONLINE → transport loss)
3. Ensure AG command path uses LOCAL transport (not Wireless fallback)
4. Investigate why adbd treats AG's session differently from AT's
