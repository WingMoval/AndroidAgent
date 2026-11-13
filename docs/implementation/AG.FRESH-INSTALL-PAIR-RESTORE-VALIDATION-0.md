# AG.FRESH-INSTALL-PAIR-RESTORE-VALIDATION-0 — Implementation Report

## Summary

Validated the full cycle: **backup user data → uninstall → reinstall → restore user data only (no AT identity) → fresh pair → verify session/provider/ADB all work**.

## Experiment Flow

```
1. Backup (Session DB + Provider config, NO AT identity)
   ↓
2. Full uninstall (pm uninstall — destroys everything including AT identity)
   ↓
3. Fresh reinstall (clean APK install)
   ↓
4. Restore user data only (Session DB + Provider config, NO AT identity)
   ↓
5. First launch → system pairing authorization (HONOR Android 15)
   ↓
6. Verify:
   - Session restored (sentinel recalled)
   - Provider restored (API key works)
   - Fresh AT identity created
   - ADB uid=2000 execution works
   - Session still intact after pair
```

## Key Results

| Gate | Result |
|------|--------|
| Backup archive (no AT identity) | PASS |
| Full uninstall | PASS |
| Fresh reinstall | PASS |
| Restore before first launch | PASS |
| AT identity absent post-restore | PASS |
| Session restore (sentinel) | PASS |
| Provider restore (API key) | PASS |
| Fresh pair (system auth) | PASS |
| ADB uid=2000 execution | PASS |
| Session intact after pair | PASS |
| Provider intact after pair | PASS |

## Finding: HONOR Android 15 Pairing Method

This device uses **system-level Wireless Debugging authorization** rather than the in-app 6-digit pairing code flow. When AG first attempted ADB connection after fresh install, the Android system displayed a pairing authorization dialog. User approved → AT created fresh identity → ADB READY.

This is device/OS-specific behavior, not an AG bug. The in-app Pair UI (6-digit code) remains available for devices that use the pairing-code flow.

## What This Proves

- **PC backup** of Session + Provider is valid
- **Full uninstall** destroys AT identity (as intended)
- **Selective user data restore** works (Session + Provider survive)
- **AT identity does NOT survive** uninstall/restore (correct)
- **Fresh pair** creates new identity and establishes ADB
- **Session + Provider** remain fully functional after fresh pair
- **API key** works without re-entry after reinstall

## Security Non-Goals

- API key at-rest encryption: OUT_OF_SCOPE
- Backup encryption: OUT_OF_SCOPE
- SAF grant restore: OUT_OF_SCOPE
- Workspace restore: OUT_OF_SCOPE
- AT identity backup: INTENTIONALLY FORBIDDEN

## No Production Code Changed

Pure validation task. Only docs/evidence created.

## Backup Location

```
D:\AG-Backup\
  ag-user-state.tar          (SECRET_BEARING — do not commit)
  ag-user-state-manifest.txt (safe metadata)
```
