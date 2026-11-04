# AG.AT-V042-WORKING-AT-CONTROL-DIFFERENTIAL-0

## Summary

**TASK**: Find FIRST DIVERGENCE between working AT (debug variant, same as AG) and failing AG consumer (`client not initialized`), distinguishing key trust vs native build vs TCP channel.

**RESULT**: ROOT_CAUSE = **AG_NATIVE_STATE_MACHINE_STALE_ONLINE**

AG's native ADB engine has a stale "ONLINE" state. The `connectLocalhost()` function sees the transport marked "ONLINE" and skips the CNXN/AUTH handshake, reusing a dead TCP transport that never completed the ADB protocol handshake. The HUB08 reader thread is in an infinite EAGAIN loop (never sent CNXN, never received AUTH), creating a deadlock with adbd.

**SUPERSEDES**: AG.AT-V042-CONSUMER-RUNTIME-DIFFERENTIAL-0 (commit 38cea94, 4096-byte banner root cause = RETRACTED)

## Key Findings

### 1. Binary Identity (Three Builds)

| Build | Native SHA256 | Variant | Status |
|-------|--------------|---------|--------|
| AT release | F381F3D6... | release (-O2, stripped) | WORKING (pre-reinstall) |
| AT debug | AE55F417... | debug (-O0, stripped) | WORKING (current) |
| AG candidate | 804E547F... | debug (per MANIFEST) | FAILING |

All three from same source (v0.4.2). SHA differences are build-environment artifacts, not code differences.

### 2. State Matrix

| Test | AT | AG | Result |
|------|-----|-----|--------|
| Concurrent (AT+AG) | READY | FAIL | AG fails with AT present |
| Sole (AT stopped) | — | FAIL | AG fails without AT |
| AT reconnect | READY | — | AT can reconnect |
| AG retry | READY | FAIL | AG still fails |
| **Key swap** | — | **FAIL** | **AG fails even with AT's trusted key** |

### 3. Key Swap Experiment (Decisive)

- Copied AT debug's identity (adbkey/adbkey.pub/adb_cert.pem/pairing_meta) to AG's identity dir
- AG now has AT's adbd-trusted key (fingerprint 6b1949b4...)
- **AG still FAILS** with identical error
- **CONCLUSION: Key trust EXCLUDED**

### 4. First Divergence (Root Cause)

AG native log sequence:
```
connectLocalhost port=38800
connectLocalhost: ALREADY ONLINE, reusing shared transport   ← STALE STATE
local-first probe: LOCAL active on :38800                    ← WRONG
AdbNative.executeShell: "'id'" timeout_ms=0                  ← IMMEDIATE
local transport loss; activeTransport -> WIRELESS            ← DEAD
outcome=TRANSPORT_ERROR exit=-1                              ← FINAL
```

HUB08 thread (continuous):
```
TCP recv() failed after 0/24 bytes: 11: Try again
ReadPacket: failed to read 24-byte header: 11: Try again
RX_IDLE_TIMEOUT: continuing (no data, transport still alive)
```

**Deadlock**: AG's HUB08 waits for adbd's AUTH response, but never sent CNXN. adbd waits for CNXN before sending AUTH. Neither speaks first.

### 5. Why AT Debug Works

AT debug correctly performs: TCP → TX_CNXN → RX_AUTH → TX_AUTH_RESPONSE → RX_CNXN → ONLINE. The state machine only marks ONLINE after the full handshake completes.

### 6. Root Cause Classification

- **NOT** key trust (excluded by key swap experiment)
- **NOT** binary variant (AT debug works, AG candidate fails, both debug)
- **NOT** TCP channel (TCP ESTABLISHED, adbd accepts connection)
- **IS** AG native state machine bug: stale ONLINE state causes handshake skip

## Constraints Compliance

- ✅ No production source changes
- ✅ No AAR/JAR/Gradle changes
- ✅ No tag/push/freeze
- ✅ G-b1 not re-run, not blocking
- ✅ Root cause explains BOTH working AT and failing AG
- ✅ Previous root cause (38cea94) formally retracted

## Evidence

See `docs/evidence/ag_at_v042_working_at_control_differential_0/` (12 files)

## Next Steps (NOT THIS TASK)

The fix (not applied, forensics only) would be in AG's native ADB engine:
1. `connectLocalhost()` should verify transport functionality before reusing
2. If transport is dead (no CNXN received), close and re-establish
3. Only mark ONLINE after complete handshake
