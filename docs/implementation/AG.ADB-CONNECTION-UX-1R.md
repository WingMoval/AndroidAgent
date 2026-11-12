# AG.ADB-CONNECTION-UX-1R — Implementation Report

## Summary

Consolidated the ADB UI from a complex 3-part sidebar (status + reconnect + navigation) into a clean two-layer structure:

```
Sidebar: ADB + status square (single row, clickable)
Management: Unified ADB management surface (Activity)
```

## What Changed

### New Files
| File | Purpose |
|------|---------|
| `app/src/main/java/.../AdbUiPresentation.kt` | Shared state→UI presentation mapping |
| `app/src/test/.../AdbUiPresentationTest.kt` | 28 dedicated unit tests |

### Modified Files
| File | Change |
|------|--------|
| `ChatActivity.kt` | Sidebar: removed old ADB section, added single row; uses `adbIndicator()` |
| `AdbConnectionActivity.kt` | Rewritten as full management surface |
| `activity_chat.xml` | Sidebar ADB section → single clickable row with dot |
| `activity_adb_connection.xml` | Management layout: status + actions + expandable pairing |
| `strings.xml` | Added UX-1R strings |

## Architecture

```
ChatActivity
├── Header: "ADB ■" (adbIndicator → color)
└── Sidebar: "ADB ■" (adbIndicator → color, clickable)
        └── AdbConnectionActivity (management surface)
                ├── Status: adbManagementPresentation(code)
                ├── Reconnect: shell.connect() (state-dependent)
                ├── Pair/Re-pair: adapter.pairDiscovered(code) (always visible)
                ├── Open Settings: Intent (always visible)
                └── Refresh: renderState() (always visible)
```

## Key Design Decisions

1. **No Disconnect**: AT v0.4.4 has no formal disconnect API. `ShellConnection.disconnect()` is a no-op. Not shown in UI.

2. **Pair/Re-pair always visible**: Previously hidden in READY state. Now always present, click to expand/collapse.

3. **Settings always visible**: Previously hidden in READY state. Now always available.

4. **Shared presentation**: `AdbUiPresentation.kt` is the single source of truth for state→UI mapping, consumed by header, sidebar, and management surface.

5. **Activity (not Dialog)**: Project uses XML + AppCompat. Activity provides proper lifecycle (onResume refresh) without new dependencies.

## Button Matrix

| State | Reconnect | Pair/Re-pair | Settings | Refresh |
|-------|-----------|--------------|----------|---------|
| READY | hidden | visible | visible | visible |
| PAIRING_REQUIRED | hidden | visible | visible | visible |
| USER_ACTION_REQUIRED_WD | hidden | visible | visible | visible |
| CONNECT_FAILED | visible | visible | visible | visible |
| ENDPOINT_NOT_FOUND | visible | visible | visible | visible |
| NOT_READY | visible | visible | visible | visible |

## Validation

- Unit tests: 28 PASS (presentation mapping + pairing results)
- Build: all 4 Gradle targets PASS
- Device: sidebar, management, pair/repair, settings roundtrip all PASS
- Indicator consistency: header + sidebar use same mapping

## Commit

```
feat(app): simplify ADB connection management UX
```
