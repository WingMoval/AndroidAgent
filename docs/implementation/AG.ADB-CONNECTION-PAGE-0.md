# AG.ADB-CONNECTION-PAGE-0 — Implementation Report

## Summary

Added a dedicated ADB Connection / Pairing page to AndroidAgent, accessible from the sidebar. The page reuses the existing AT v0.4.4 composition (single runtime) and provides full state visibility, pairing UI, and reconnect controls.

## What Was Built

### New Files
| File | Purpose |
|------|---------|
| `app/src/main/java/.../AdbConnectionActivity.kt` | Dedicated ADB Connection page |
| `app/src/main/res/layout/activity_adb_connection.xml` | Page layout |
| `app/src/main/res/drawable/adb_dot_ready.xml` | Green status dot |
| `app/src/main/res/drawable/adb_dot_pairing.xml` | Orange status dot |
| `app/src/main/res/drawable/adb_dot_disconnected.xml` | Grey status dot |
| `app/src/main/res/drawable/bg_status_card.xml` | Card background |

### Modified Files
| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | Registered AdbConnectionActivity |
| `app/src/main/java/.../ChatActivity.kt` | Sidebar entry + Intent navigation |
| `app/src/main/res/layout/activity_chat.xml` | `sidebar_adb_page_entry` TextView |
| `app/src/main/res/values/strings.xml` | 30+ `adb_*` strings |

## Architecture

```
ChatActivity (sidebar)
    └── "ADB Connection" entry
            └── AdbConnectionActivity (AG-owned UI)
                    ├── State: SessionHolder.root?.shell?.readiness()
                    ├── Pairing: SessionHolder.root?.shell?.adapter?.pairDiscovered(code)
                    └── Reconnect: SessionHolder.root?.shell?.connect()
                            └── Existing ATAndroidComposition (single instance)
                                    └── AT v0.4.4 adbControl
```

**Single AT runtime guaranteed**: `ATAndroid.create()` exists only in `ChatCompositionRoot.kt`. The ADB page accesses the composition through the process-scoped `SessionHolder.root`.

## State Handling

| State | Title | Dot | Actions |
|-------|-------|-----|---------|
| READY | Connected | Green | Refresh |
| PAIRING_REQUIRED | Not yet paired | Orange | Open Settings, Refresh, **Pairing section** |
| USER_ACTION_REQUIRED_WD | Wireless Debugging off | Grey | Open Settings, Refresh |
| CONNECT_FAILED | Not connected | Grey | Reconnect, Open Settings, Refresh |
| ENDPOINT_NOT_FOUND | No endpoint found | Grey | Reconnect, Open Settings, Refresh |
| NOT_READY | Initializing | Grey | Reconnect, Refresh |

## Pairing Flow

1. User sees 3-step instructions (Settings → Developer Options → Wireless Debugging → Pair with code)
2. Enters 6-digit code (numeric input, button disabled until valid)
3. `pairDiscovered(code)` → AT discovers endpoint + pairs
4. On SUCCESS: auto `ensureReady()` → state refreshes to READY
5. On failure: specific message per outcome, retry allowed

## Product Semantics Preserved

- `ADB_UNPAIRED != AG_UNAVAILABLE`: Page info explicitly states local execution works without ADB
- No first-run pairing gate
- No mandatory pairing popup
- No auto-unpair or identity deletion
- Reconnect ≠ Re-pair (CONNECT_FAILED shows "Reconnect", not "Pair")

## Validation

- Build: PASS (all 4 Gradle tasks)
- Unit tests: PASS (app, integration-at, tool-android-shell)
- Device smoke: PASS (sidebar → page → READY state → back → re-entry)
- Settings roundtrip: PASS (page survives lifecycle, no crash)
- Pairing: NOT_RUN_SAFE_STATE_PRESERVED (device already paired)

## Commit

```
feat(app): add ADB connection and pairing page
```
