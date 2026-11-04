# AG.AT-V042-COMPAT-VALIDATION-1

**Date:** 2026-11-04
**Status:** COMPLETED (partial — P0 fail triggered early stop)
**VERDICT:** **FAIL_P0** — Local one-shot Stop does not terminate child processes. ADB transport blocked.

## 1. Scope

Validate the AT v0.4.2 consumer candidate AAR/JAR integrated into AG app on a real device,
covering the 7-gate matrix (G-a through G-g). This is a **validation task**, not a fix task.
Per section 35 (P0 Early Decision Gate), a deterministic P0 failure stops all subsequent
destructive transport tests and routes to CONTROLLER_REVIEW.

## 2. Device & APK Identity

| Item | Value |
|---|---|
| Device | HONOR ALI-AN00 (ALI_AN00), Android 15 (SDK 35) |
| Serial | APLMUT4312005935 |
| AG APK | app/build/outputs/apk/debug/app-debug.apk |
| APK size | 6306722 bytes |
| APK SHA256[:16] | 51f6e5a3d3b3317a |
| Native lib | lib/arm64-v8a/libadbd_engine.so |
| Native SHA256 | 804e547fa57a5972f263df9052d3d55fefffec744cdb41e6fda79dee0b0b67d7 |
| v0.4.2 identity confirmed | YES |
| Install mode | `adb install -r` (upgrade, data preserved) |

Evidence: `docs/evidence/ag_at_v042_compat_validation_1/00_preflight_and_apk_identity.txt`

## 3. Gate Matrix Results

| Gate | Path | Status | Detail |
|------|------|--------|--------|
| G-a1 | Local one-shot stdout | **PASS** | `echo` → COMPLETED, exit 0 |
| G-a2 | Local one-shot stdout+stderr | **PASS** | dual-stream preserved |
| G-a3 | Local one-shot non-zero exit | **PASS** | exit 7 preserved, hasError=false |
| G-b1 | Local one-shot Stop | **FAIL_NATIVE_TERMINATION** | Stop accepted (`requested`) but child processes survive 90s+; Run never reaches terminal |
| G-b2 | ADB one-shot Stop | BLOCKED_ADB_TRANSPORT | See §5 |
| G-c1 | Local one-shot timeout | NOT_EXECUTED | Stopped by P0 rule (§35) |
| G-c2 | ADB one-shot timeout | BLOCKED_ADB_TRANSPORT | See §5 |
| G-d | ADB recovery | BLOCKED_ADB_TRANSPORT | See §5 |
| G-e | ADB pairing | BLOCKED_ADB_TRANSPORT | Pairing succeeded but ADB client not initialized |
| G-f | Local TCP ADB | BLOCKED_ADB_TRANSPORT | See §5 |
| G-g | ADB CLI normal | **BLOCKED_ADB_TRANSPORT** | `client not initialized` — see §5 |

**G-a (Local one-shot smoke): PASS** (3/3 sub-gates)
**G-b1 (Local one-shot Stop): FAIL** (P0)
**All ADB-dependent gates: BLOCKED** (AT Local TCP ADB transport issue)

## 4. G-b1 FAIL Detail (P0)

### Test setup
- Real AG Run via `AgentRuntime.send` (OpenRouter deepseek-v4-flash-0731)
- Prompt: "Use the android_shell tool to run: sleep 120"
- Model correctly generated ToolCall: `{"command":"sleep 120"}`
- Execution path: `AndroidShellToolImpl → executeLocal → LocalOneShotBackend (ProcessBuilder)`

### Timeline
| Time | Event |
|------|-------|
| 0.0s | `send` dispatched |
| ~3s | Model turn complete, ToolCall issued |
| ~4s | Tool execution starts (ProcessBuilder) |
| 12.2s | PS check: PID 10210 `[sh]` + PID 10214 `[sleep]` (UID 10418), ETIME 00:10 — **ALIVE** |
| 12.2s | **STOP sent** → `stop_result: "requested"` |
| 27.5s | PS check 15s after stop: PID 10210 + 10214 **STILL ALIVE** (ETIME 00:25) |
| ~90s | No `run_finished`, no `Interrupted` event. Run stuck without terminal state. |
| Post | Residual processes required external `run-as kill` cleanup |

### Verdict
- Stop was **accepted** by AG (`stop_result=requested`)
- `AgentRuntime.interruptActiveRun()` was invoked (AG-side coroutine cancellation)
- `LocalOneShotBackend` **did not terminate** the ProcessBuilder child process tree
- No orphan cleanup mechanism observed
- Run never reached terminal state (no RunFinished / Interrupted)

### Classification
- **FAILURE_ORIGIN_CANDIDATE:** AT
- **LIKELY_LAYER:** AT native Local one-shot backend — `ProcessBuilder.destroy()` not called on cancellation, or child not in same process group
- **REPRODUCIBLE:** Yes (deterministic)
- **V040_HAS_PROOF_OF_WORKING:** No — same gap existed in v0.4.0; historical G1 was TRULY_UNVERIFIED

This is exactly the G1 gap predicted by the historical evidence reconciliation audit
(AG.AT-V042-PREFLIGHT-AUDIT-CLOSEOUT-0).

Evidence: `docs/evidence/ag_at_v042_compat_validation_1/03_g_b1_local_stop_FAIL.txt`

## 5. ADB Transport Blockage (G-g and all ADB-dependent gates)

### Symptoms
- AT Local TCP ADB transport: TCP connection **ESTABLISHED** (127.0.0.1:38182 → 127.0.0.1:38800)
- ADB protocol handshake **incomplete**: continuous logcat
  - `TCP recv() failed after 0/24 bytes: 11: Try again`
  - `ReadPacket: failed to read 24-byte header: 11: Try again`
  - `[HUB08] RX_IDLE_TIMEOUT: continuing (no data, transport still alive)`
- In-shell `adb` CLI: `client not initialized` (exit 1)
- Pairing succeeded: `bootstrap_pair_result success=true` (192.168.137.56:43099, code 422531)
- `adb_reconnect` returned `READY` but ADB client remained uninitialized

### Likely cause
AT v0.4.2 Local TCP ADB engine's key authorization with device adbd does not complete
over loopback TCP. The TCP connection is alive but no ADB protocol data flows.
Possible causes: adbkey not present/malformed, key format change in v0.4.2, or
adbd rejecting the client key over local TCP.

### Impact
All gates requiring ADB transport (G-b2, G-c2, G-d, G-e, G-f, G-g) are **BLOCKED**.

Evidence: `docs/evidence/ag_at_v042_compat_validation_1/02_g_g_adb_cli_blocked.txt`

## 6. Failure Summary

| # | Failure | Origin | Severity |
|---|---------|--------|----------|
| 1 | Local one-shot Stop does not terminate child processes | AT (LocalOneShotBackend) | **P0** |
| 2 | Local TCP ADB transport handshake incomplete (client not initialized) | AT (ADB engine / key auth) | P1 (blocks validation) |

## 7. P0 Early Decision (Section 35)

G-b1 is a **deterministic P0 failure** (native termination not working). Per task
instruction section 35, all subsequent destructive transport tests (G-b2, G-c1, G-c2,
G-d, G-e, G-f) are **STOPPED**. G-c1 (Local timeout) was also not executed.

## 8. Constraints Compliance

- No production source modified (AG or AT)
- No candidate AAR/JAR modified
- No Gradle/AGENTS/Roadmap/handoff/freeze modifications
- No tag/push/freeze
- Temporary fixture (ToolCall generator) placed repo-external (`C:\Users\Wing\AppData\Local\Temp\opencode\`)
- `upload/` not committed
- Provider config temporarily changed (project dir `config/provider.local.json`), backup preserved

## 9. Next Steps (Controller Review)

1. **AT team:** Fix `LocalOneShotBackend` to call `ProcessBuilder.destroy()` (or
   equivalent process-group kill) on cancellation. Add orphan cleanup for child processes.
2. **AT team:** Investigate Local TCP ADB key authorization failure (adbkey
   generation/storage vs adbd expectations over loopback TCP).
3. **Re-validation:** After AT fixes, re-run full 7-gate matrix on same device.
4. **Historical evidence:** Update v0.4.0 G1 evidence with this confirmation that
   the gap is real (not just unverified).

## 10. Evidence Index

| File | Content |
|------|---------|
| `docs/evidence/ag_at_v042_compat_validation_1/00_preflight_and_apk_identity.txt` | Device, APK, native lib identity |
| `docs/evidence/ag_at_v042_compat_validation_1/01_g_a_local_smoke.txt` | G-a PASS evidence |
| `docs/evidence/ag_at_v042_compat_validation_1/02_g_g_adb_cli_blocked.txt` | ADB transport blockage |
| `docs/evidence/ag_at_v042_compat_validation_1/03_g_b1_local_stop_FAIL.txt` | G-b1 FAIL detail |
| `docs/evidence/ag_at_v042_compat_validation_1/10_final_gate_matrix.txt` | Final gate matrix |
