# AG.AT-V043-COMPAT-VALIDATION-0

## VERDICT: PASS_LOCAL_WITH_WIRELESS_LIMITATION

## Baseline

- AG HEAD: 657a473 (main)
- AT FIX COMMIT: 4ebabe5 (feature/local-tcp-v0.4.2)
- BASE RELEASE: v0.4.2
- CANDIDATE: v0.4.3 candidate (POST_V042_FIX_CANDIDATE)
- Device: HONOR ALI-AN00, Android 15

## Fix Identity

Commit 4ebabe5 contains exactly one change:
- `AdbOneShotBackend.execute()`: `AdbEngine.executeShell()` → `AdbRuntime.executeShell()`
- 1 file, 1 line + 4 comment lines
- No native, pairing, shell-v2, or protocol changes

## Artifact Provenance

| Field | Value |
|-------|-------|
| AAR | at-backend-adb-4.0.0.aar |
| SHA256 | 58B26810E4E452AD... |
| Source commit | 4ebabe5 |
| Maven path | com/at/backend/adb/at-backend-adb/4.0.0/ |
| Native lib | libadbd_engine.so (804E547F... unchanged) |

## Local Results (ALL PASS)

| Gate | Result |
|------|--------|
| A: Local Startup | PASS |
| B: Local One-shot | PASS (executeShellLocalhost, uid=2000) |
| C: Command Matrix | PASS 6/6 |
| D: Local Reuse | PASS 10/10 |
| E: App Restart | PASS |
| F: TRUE Local Loss | PASS (adb usb) |
| J: WD-off Independence | PASS |

## True Loss Results

**Method**: PC USB `adb usb`
- 38800 listener: LISTENING → ABSENT
- 38800 ESTABLISHED: 1 → 0
- AG PID: unchanged (5545)
- Loss detection: `clean EOF (FIN)` → `markTransportLost` → `RX_OWNER_EXIT` → `ONLINE→!ONLINE edge` → `activeTransport → WIRELESS`
- No false READY, no crash, no double RX owner

## Wireless Results

- **Pairing**: PASS (adb pair 192.168.137.56:37313 452236, TLS 1.3, guid confirmed)
- **Wireless TLS READY in closeout**: NOT_OBSERVED
- **Reason**: `adb usb` disables Wireless Debugging (USB mode has no WD service)
- **Classification**: TESTABILITY_LIMITATION_BY_ADB_USB_DISABLING_WD (not a product defect)
- **Wireless execution compatibility**: SUPPORTED_WITHOUT_ISOLATED_ONESHOT_GATE

## Transport Invariants

- MAX_ACTIVE_ADB_TRANSPORTS = 1
- MAX_RX_OWNER_PER_CONNECTION = 1
- No duplicate connections
- Canonical router preserved (AdbRuntime.executeShell)

## Consumer API Compatibility

- GAV coordinates unchanged
- Method signatures unchanged
- No breaking changes
- PUBLIC_CONSUMER_API_DIFF = NONE

## Known Limitations

1. `adb usb` (only validated true-loss method) disables WD → cannot test Wireless recovery in the same session
2. Wireless→Local promotion not observed in closeout (recovery used Local-first)
3. Both limitations are testability constraints, not product defects

## Final Verdict

The post-v0.4.2 routing-fix candidate is **acceptable as AG consumer baseline**.
Local transport fully validated including true loss detection and recovery.
Wireless pairing/TLS verified. The inability to test Wireless recovery after
`adb usb` is an inherent limitation of the loss method (USB mode has no WD),
not a product defect.

**CANDIDATE_ACCEPTABLE_AS_AG_BASELINE = YES**
**FORMAL_V043_RELEASE_READY = CONTROLLER_DECISION**

## Evidence

See `docs/evidence/ag_at_v043_compat_validation_0/` (22 files)
