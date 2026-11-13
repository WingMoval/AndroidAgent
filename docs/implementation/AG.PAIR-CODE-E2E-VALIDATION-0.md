# AG.PAIR-CODE-E2E-VALIDATION-0 — Implementation Report

## Summary

Validated the **AG in-app 6-digit pairing-code E2E path**: delete AT identity → PAIRING_REQUIRED → AG Pair UI → enter real 6-digit code → pairDiscovered() → SUCCESS → READY → uid=2000.

## Causal Chain

```
1. Pre-reset: ADB READY, uid=2000 confirmed
2. Force-stop app
3. Delete files/at-consumer/ (AT identity only)
4. Verify: Session DB + Provider config intact, identity GONE
5. Launch app → AT generates NEW self-signed keys (16:37)
6. State: PAIRING_REQUIRED (keys exist, device hasn't authorized)
7. HONOR system auth dialog appears → DISMISSED (KEYCODE_BACK)
8. User opens system pair-code screen (6-digit code displayed)
9. User enters 6-digit code in AG Pair UI → taps Pair
10. pairDiscovered() → SUCCESS → pairing_meta created (16:49)
11. State: READY
12. adb shell id → uid=2000(shell) ✓
13. Session recall: PAIR_RESTORE_SENTINEL_7F29C1 ✓
14. Provider call: PAIR_CODE_E2E_PROVIDER_OK ✓
```

## Key Finding: AT Identity Generation

When `files/at-consumer/` is absent, AT **generates new self-signed keys** on app startup:
- `adbkey` (private)
- `adbkey.pub` (public)
- `adb_cert.pem` (certificate)

These keys are self-generated and have NOT been authorized by the device. The state is `PAIRING_REQUIRED` until the device accepts the pairing (via 6-digit code or system authorization).

Upon successful pairing, a `pairing_meta` file (152 bytes) is created, confirming the device has authorized this identity.

## HONOR Android 15 Behavior

HONOR shows `HwUsbDebuggingActivity` (USB/Wireless debugging authorization dialog) on first ADB connection attempt. This is a **system-level** authorization, separate from the in-app pair-code flow.

For this test, the system dialog was **dismissed** and the in-app 6-digit pair-code flow was used instead, proving the AG Pair UI → pairDiscovered() path works independently of system auto-authorization.

## What This Proves

- AG in-app 6-digit pair-code path is **functional E2E**
- AT identity can be reset without affecting Session/Provider
- Fresh identity → pair → READY → uid=2000 works
- Session history survives identity reset
- API key survives identity reset
- No production code changes needed

## Security Notes

- 6-digit pairing code: NOT recorded in evidence
- API key: NOT printed
- Private key: NOT printed
- pairing_meta: 152 bytes (not inspected for contents)

## No Production Code Changed

Pure validation task. Only docs/evidence created.
