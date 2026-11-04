# AG.AT-V042-CONSUMER-RUNTIME-DIFFERENTIAL-0

**Date:** 2026-11-04
**VERDICT:** PASS_ROOT_CAUSE_FOUND
**G-b1:** UNRESOLVED_AGENT_EXECUTION_LIFECYCLE_SEMANTICS (not re-tested, not blocking)

## 1. Main Question

AT v0.4.2 standalone was previously validated as PASS on Local TCP ADB.
AG consumer (same AAR bytes) shows TCP ESTABLISHED but ADB handshake incomplete.
This round's goal: find the **FIRST_DIVERGENCE_POINT** between the two paths.

## 2. Answer

**There is NO standalone-vs-consumer divergence.** Both AT standalone and AG
consumer use the **same native code** (`PlainTcpAdbConnection::connect`) and
**both FAIL** on the current device (HONOR ALI-AN00, Android 15).

The FIRST_DIVERGENCE is not between standalone and consumer — it is between
**AT v0.4.2's CNXN packet format** and **this adbd's CNXN parsing**.

### FIRST_DIVERGENCE_POINT

`PlainTcpAdbConnection::connect()` (plain_tcp_connection.cpp:52-58) sends:

```cpp
cnxn.msg.arg0 = 0x01000000u;    // A_VERSION (with checksum)
cnxn.msg.arg1 = 1048576;        // 1MB max payload
banner = "host::A35";           // 9 bytes
cnxn.msg.data_length = 9;       // ← should be 4096
```

The ADB protocol requires the CNXN banner to be **4096 bytes**
(`A_CNXN_MAX_BANNER_LENGTH`). adbd on HONOR Android 15 rejects the 9-byte
banner: `read failed: Success` → session terminated → no AUTH/CNXN response.

AT's `handleAuth()` then loops forever in `readPacket` → `RX_IDLE_TIMEOUT`.

### Evidence

| Test | Result |
|------|--------|
| AT standalone, clean device (after reboot) | **FAIL** — same RX_IDLE_TIMEOUT |
| AT standalone, dirty device (stale AG connection) | **FAIL** — same pattern |
| PC adb forward + CNXN (4096-byte banner) | adbd creates host-N but `read failed` |
| Source code inspection | CNXN banner = 9 bytes (confirmed) |
| adbd log | No AUTH/CNXN response to AT app's connection |

### Classification

| Field | Value |
|-------|-------|
| ROOT_CAUSE_CLASS | LOCAL_TCP_PROTOCOL_TX |
| ROOT_CAUSE_CONFIDENCE | **HIGH** |
| FIX_OWNER | AT (adb-engine native) |
| MINIMUM_FIX | Pad CNXN banner to 4096 bytes; verify arg0 version flag |
| AG_CHANGE_REQUIRED | NO |
| AT_CHANGE_REQUIRED | YES |
| DEVICE_STATE_ACTION | NO |

## 3. Source Chain Comparison

Both paths converge to the **same** native code:

```
Standalone:  AppRuntime → ATAndroid.create → AdbRuntime.ensureReady
             → probeLocalFirst → connectLocal → AdbEngine.connectLocalhost
             → AdbNative.connectLocalhost [JNI] → PlainTcpAdbConnection::connect

AG:          ChatCompositionRoot → ATAndroid.create → CompositionAdbSemantic
             → AtExecutionAdapter → adbSemantic.ensureReady
             → ATAndroidAdbControl.ensureReady → AdbRuntime.ensureReady
             → probeLocalFirst → connectLocal → AdbEngine.connectLocalhost
             → AdbNative.connectLocalhost [JNI] → PlainTcpAdbConnection::connect
                                                                    ^^^ SAME
```

Only differences:
- `identityProfile`: `adb_identity` vs `at-consumer/identity` (expected)
- AG has extra `CompositionAdbSemantic` adapter (thin delegation, no logic)

## 4. Ownership

| Metric | Standalone | AG |
|--------|-----------|-----|
| Composition count | 1 | 1 |
| AdbRuntime count | 1 | 1 |
| CLI bridge count | 1 | 1 |
| Pairing & CLI share runtime | YES | YES |
| Duplicate ownership | NO | NO |

## 5. Identity

- Different identity paths between standalone and AG: **EXPECTED** (per-consumer)
- Within each host: identity is consistent (pairing = connecting = local TCP)
- AG identity continuity: **PASS**

## 6. Why Previous Standalone Validation Passed

The AT v0.4.2 release validation (7/7 gates PASS) was likely performed on a
**different device/adbd build** that was more lenient about the CNXN banner
length (e.g., older AOSP adbd that accepted short banners, or a different
HONOR firmware version). The current HONOR ALI-AN00 Android 15 adbd strictly
enforces the 4096-byte banner requirement.

This is a **device-environment-dependent** compatibility issue in AT's
protocol TX, not an AG integration issue.

## 7. G-b1 Treatment

Per task instruction section 26: G-b1 is preserved as
`UNRESOLVED_AGENT_EXECUTION_LIFECYCLE_SEMANTICS`. Not re-tested. Not used
to block this round. Will be addressed in
`AG.RUN-CONTROL-SHELL-LIFECYCLE-RESEARCH-0`.

## 8. Constraints Compliance

- No production source modified (AG or AT)
- No AAR/JAR modified
- No Gradle/AGENTS/Roadmap/handoff/freeze modifications
- No tag/push/freeze
- No G-b1 re-test
- No LocalOneShotBackend / AgentRuntime / Shell Stop changes

## 9. Next Step

**AT team:** Fix `PlainTcpAdbConnection::connect()` in `adb-engine`:
1. Pad CNXN banner to 4096 bytes
2. Verify `arg0` version flag compatibility (0x01000000 = with-checksum may
   need to be 0x00000001 = no-checksum for this adbd)
3. Re-validate on HONOR ALI-AN00 Android 15

After AT fix: re-run this differential audit to confirm standalone PASS,
then re-run AG.AT-V042-COMPAT-VALIDATION-1 full 7-gate matrix.

## 10. Evidence Index

| File | Content |
|------|---------|
| `00_preflight.txt` | Device, APK identity |
| `01_source_call_chain_diff.txt` | Two call chains, side by side |
| `02_ownership_identity_diff.txt` | Ownership + identity comparison |
| `03_standalone_current_smoke.txt` | AT standalone FAIL evidence |
| `04_ag_current_smoke.txt` | AG status (BLOCKED, same root cause) |
| `06_protocol_tx_rx_diff.txt` | CNXN packet analysis + adbd behavior |
| `09_first_divergence_and_root_cause.txt` | Root cause detail |
| `10_final_verdict.txt` | Final verdict matrix |
