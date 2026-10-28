# AG.UI-SHELL-1 — Shell UI + Callback Debug Automation

## Status

IN_PROGRESS (static remediation PASS; visual alignment PASS; product controls alignment PASS; functional controls closure PASS; device Gate A/B/G PASS; C/D/E/F/I DEFERRED due to network)

## Scope

Light theme + three-zone fixed layout (header / viewport / composer) + Sidebar +
ADB indicator + model label + Send/Stop same-slot + Callback Debug Automation
(Shared Host Action Debug Channel).

## Callback Debug Automation

### Design

UI callbacks and DebugBridge commands share a single thin seam: `HostActions`.

```text
UI callback  ─┐
               ├→ HostActions → existing Runtime / Host capability
Debug command ─┘
```

HostActions is NOT a second AgentRuntime / SessionHolder / Domain Service.
It only checks parameters, calls the EXISTING capability, performs minimal
Host presentation side effects, and returns a result the automation can assert on.

### API

```kotlin
object HostActions {
    suspend fun send(text: String): SendResult
    fun stop(): StopResult
    suspend fun newSession(): Boolean
    suspend fun adbReconnect(): Boolean
    suspend fun switchProvider(node: ProviderNode): ProviderSwitchResult
    fun snapshot(): HostSnapshot
}
```

- `send` is suspend (underlying `AgentRuntime.send` is suspend)
- `stop` is sync (underlying `AgentRuntime.interruptActiveRun` is sync)
- `newSession` is suspend (underlying `SessionHolder.newSession` is suspend)
- `adbReconnect` is suspend (underlying `ShellConnection.connect` is suspend)
- `switchProvider` is suspend (underlying `SessionHolder.initialize` is suspend)
- `snapshot` is sync (read-only state reads)

### runBlocking = 0

HostActions.kt contains ZERO `runBlocking` calls. All suspend APIs are called
naturally from coroutine contexts (UI `lifecycleScope.launch`, Debug
`bridgeScope.launch`).

### Send Shared Entry

```text
UI onSend ─────┐
               ├→ HostActions.send(text) → AgentRuntime.send(text)
Debug handleSend┘
```

Common semantics owned by `HostActions.send` (single copy):
- blank check
- busy check
- User transcript block insertion (exactly once)
- busy=true before, busy=false after
- exactly one `AgentRuntime.send`
- Interrupted terminal ("Stopped.")

UI-only side effects (stay in `ChatActivity.onSend`):
- clear input
- auto-follow
- render transcript / status
- log trace

Debug-only side effects (stay in `DebugBridge.handleSend`):
- accepted / busy / error wire response
- Log.i

### Provider Switch Shared Entry

```text
UI switchNode ─────┐
                   ├→ HostActions.switchProvider(node)
Debug provider_switch┘
                          ↓
              HostActions.applyConfigCanonical (SINGLE implementation)
```

`CANONICAL_APPLY_CONFIG_IMPLEMENTATION_COUNT = 1`

The old `ChatActivity.applyConfig` was removed (dead code after delegation).
`HostActions.applyConfigCanonical` is the ONLY place where createRoot +
SessionHolder state update + ADB reconnect + initialize happen for a
provider switch.

Activity `root` / `runtime` are mirrors, not authority. After a switch, they
are rebound from `SessionHolder`. The `onUiUpdate` callback also syncs them.

DebugBridge provider lambdas read dynamically from `SessionHolder`:
```kotlin
runtimeProvider    = { sharedRuntime }                      // → SessionHolder.runtime
toolRegistryProvider = { SessionHolder.root?.toolRegistryView }
adapterProvider     = { SessionHolder.root?.shell?.adapter }
```

### DebugProtocol Additive Extension

Old messages (ping / send / tool_invoke / session_* / bootstrap_*) UNCHANGED.
New messages: Stop / NewSession / AdbReconnect / ProviderSwitch / Snapshot.
Wire compatible. No protocol id change.

### DebugBridge Handlers (Pure Proxies)

```text
handleStop          → HostActions.stop()          → stopResult
handleNewSession    → HostActions.newSession()    → newSessionResult
handleAdbReconnect  → HostActions.adbReconnect()  → adbReconnectResult
handleProviderSwitch→ HostActions.switchProvider()→ providerSwitchResult
handleSnapshot      → HostActions.snapshot()      → snapshot JSON
```

The bridge owns NO run / session / provider / ADB semantics of its own.

### Python Harness

`tools/ui_automation/debug_automation.py` — PC-side Debug automation harness.
Connects via `adb forward`, drives/observes the real product through the
shared HostActions seam. NOT a test framework or device manager.

### Tests

- `HostActionsTest` (8 tests): send guards, stop guard, newSession guard,
  snapshot SECRET-FREE, JSON encoding, null fields, Interrupted round-trip.
- `DebugProtocolTest` (26 tests, 14 new): old messages still parse, new
  messages parse, new response encodings, safe endpoint in provider_switch.

### Evidence

- `callback_baseline_before.txt` — before-state code paths
- `callback_debug_static_audit_before.txt` — before-state runBlocking + paths
- `callback_debug_remediation_diff.txt` — git diff of remediation
- `callback_debug_runblocking_audit.txt` — runBlocking = 0 verification
- `callback_debug_send_shared_entry.txt` — Send shared entry proof
- `callback_debug_provider_shared_entry.txt` — Provider shared entry proof
- `callback_debug_tests_after.txt` — test results after remediation
- `callback_debug_build_after.txt` — build results after remediation

### DebugBridge Startup Independence

`ensureDebugBridge()` is called in `onCreate` BEFORE the provider switch.
The bridge's provider lambdas are dynamic (read SessionHolder), so it can
start before runtime/root are ready. Commands that need a runtime honestly
report "not ready" until one exists.

```text
onCreate
  → ensureDebugBridge()          # always starts in debug build
  → tryDeviceLocalConfig
    → switchNode
      → HostActions.switchProvider
```

This means:
- Provider config failure does NOT prevent the debug channel from existing.
- `ping` works regardless of provider state.
- `snapshot` reports `runtimeReady=false` honestly.
- `provider_switch` can attempt to establish a composition via the real
  `HostActions.switchProvider` path.

`RELEASE_DEBUG_BOUNDARY = PASS` (BuildConfig.DEBUG guard unchanged).

### Device Gate Results (2026-10-27)

```
GATE_A_PING = PASS
GATE_B_SNAPSHOT = PASS (SNAPSHOT_SECRET_SAFE = YES)
GATE_C_DEBUG_TO_UI = DEFERRED_WITH_REASON (adb reverse not functional on device)
GATE_D_UI_TO_DEBUG = DEFERRED_WITH_REASON (same network issue)
GATE_E_UI_STOP_TO_DEBUG = DEFERRED_WITH_REASON (same)
GATE_F_DEBUG_STOP_TO_UI = DEFERRED_WITH_REASON (same)
GATE_G_NEW_SESSION = PASS
GATE_H_ADB_RECONNECT = DEFERRED_WITH_REASON (ADB not connected)
GATE_I_PROVIDER_SWITCH = DEFERRED_WITH_REASON (only 1 provider provisioned)
DEBUG_SURVIVES_PROVIDER_SWITCH = YES
DEBUG_AVAILABLE_BEFORE_PROVIDER_READY = YES
```

Network constraint: device on mobile data (10.5.64.95), NOT on Wi-Fi.
`adb reverse tcp:18080 tcp:18080` configured but device cannot reach PC fixture.
This is an environmental limitation, not a product bug.

### Visual Alignment (VNext Design SSOT)

`SHELL_VISUAL_ALIGNMENT = PASS` (2026-10-27, real device HONOR ALI-AN00)

Pure visual changes only. No behavior, no architecture, no callback/debug change.

Changes:
- **Header**: height 56→44dp, title 16sp→14sp normal (non-bold), no elevation,
  tighter padding. ADB indicator = "ADB" label + 8dp dot + status text (compact).
- **Workspace bg**: #F4F5F7 (light neutral, unchanged from prior shell work).
- **Status**: compact (12sp, ws_text_hint color, small padding above composer).
- **Composer**: unified surface (white + 1dp border + 12dp corner). Input
  background=@null (no own pill). Model label inside composer (11sp hint).
  Send/Stop = 36dp rounded square (not giant blue circle FAB).
- **Send**: #3B82F6 (restrained blue), 8dp corner radius, inside 48dp touch target.
- **+ slot**: hidden (attachment deferred).
- **Empty viewport**: clean whitespace, no welcome cards / fake messages.

Files changed (visual only):
- `res/values/dimens.xml` — header 44dp, title 14sp, caption 12sp, label 11sp,
  composer corner 12dp, composer padding 10dp
- `res/values/colors.xml` — ws_primary #3B82F6, ws_adb_disconnected #B0B4B8
- `res/drawable/ws_composer_surface.xml` — NEW (unified composer bg)
- `res/drawable/ws_send_btn.xml` — NEW (36dp rounded square)
- `res/drawable/ws_stop_btn.xml` — NEW (36dp rounded square soft red)
- `res/layout/activity_chat.xml` — rewritten (header/composer/status structure)
- `res/values/strings.xml` — added `ws_adb_short` ("ADB")

Evidence: `visual_alignment_evidence.txt` + `visual_alignment_screenshot.png`
Verification: V1-V9 all PASS (uiautomator dump + bounds analysis)

Build: `:app:test` 406/0 + `:app:assembleDebug` PASS (post-visual)

### Product Controls Alignment

`PRODUCT_CONTROLS_ALIGNMENT = PASS` (2026-10-27, real device HONOR ALI-AN00)

Separates daily LLM selection (Composer) from Provider configuration (Dialog).

**Composer LLM Selector:**
- Static `model_label` replaced with clickable `composer_model_selector`
  (model name + dropdown arrow, inside the unified composer surface)
- Tapping opens a model list dialog built from `buildModelCatalog()`:
  saved provider configs (each contributes its configured model) + optional
  `/v1/models` discovery results
- Selecting a model calls `HostActions.switchModel(node, modelId)` which
  reuses the SINGLE canonical apply path (no second createRoot/initialize)
- `COMPOSER_LLM_SELECTOR = YES`, `COMPOSER_PROVIDER_SELECTOR = NO`

**Model Discovery (`ModelDiscovery.kt`):**
- Host-level `GET {baseUrl}/models` via `HttpsURLConnection` (app module only)
- Uses SAVED provider credentials (never prompts for API key)
- Failure modes (401, 404, timeout, empty list) → safe reason, active model
  is never disturbed
- Does NOT modify AG Core, ModelProvider, or the transport layer

**Provider Settings Dialog (`dialog_provider_settings.xml`):**
- Entry: Sidebar "Models & Provider" row (replaces the old spinner + endpoint URL)
- Fields: Provider (Spinner), Endpoint (EditText), API Key (masked, toggle),
  Model (EditText), Cancel/Save buttons
- Secret semantics: empty API Key on Save = keep existing (never deletes)
- Save writes to the SAME device-local config file via
  `DeviceLocalProviderNodeLoader.save()` (reuses existing storage, no new DB)
- After save: re-loads config → `HostActions.switchProvider` → canonical apply
- `API_KEY_LOCATION = DIALOG_ONLY`, `API_KEY_MASKED = YES`, `API_KEY_SECRET_SAFE = YES`

**Config Save Path (`DeviceLocalProviderNodeLoader.save`):**
- Minimal Host-side write to the existing `filesDir/provider.<node>.json`
- Same JSON format that the loader reads (round-trip verified in tests)
- The core `DeviceLocalProviderConfigLoader` remains read-only

**Sidebar Reorder:**
- Old: New Session (top, big blue) → ADB → Provider (spinner + URL)
- New: ADB → Models & Provider (compact entry) → Session (current + New)
- New Session is now a compact borderless button in the Session section
- Provider status shows "EMG · Configured" (not the full endpoint URL)
- `SESSION_SECTION_POSITION = BELOW_PROVIDER`

**Header Compact ADB:**
- Removed duplicate `adb_label` text from header
- Header now shows only: "ADB" label + 8dp dot
- Full status text remains in Sidebar ADB section (`adb_status`)
- `contentDescription` preserved on `adb_dot` for accessibility

**+ Button (`composer_add`):**
- Visible in composer (left of input), 48dp touch target
- Click shows honest Toast: "Attachment context is not yet available."
- Does NOT fake attachment functionality

**Tests (3 new, 410 total / 0 failures):**
- T10: Core loader is read-only (updated: checks core loader section only)
- T10b: Save → load round-trip integrity
- T10c: Save JSON secret-safe structure

Evidence: `product_controls_alignment.txt` + `product_controls_screenshot.png`

### Functional Controls Closure

`FUNCTIONAL_CONTROLS_CLOSURE = PASS` (2026-10-27, real device HONOR ALI-AN00)

Closes three "UI exists but capability not wired" gaps.

**Track A: Open File / Open Folder (SAF)**
- `composer_add` (+) now opens a menu: "Open File" / "Open Folder"
- Open File → `ACTION_OPEN_DOCUMENT` → system Documents picker
- Open Folder → `ACTION_OPEN_DOCUMENT_TREE` → system Tree picker
- On grant: `takePersistableUriPermission` + `SafResourceStore.add`
- `SafFileOps`: read (1MiB bounded, binary sniff), list (500 entries), stat
  via `ContentResolver` + `DocumentsContract` (no androidx.documentfile)
- READ-ONLY. Does NOT modify FileTool contract / AG Core / Runtime.
- `OPEN_FILE_PICKER = PASS`, `OPEN_FILE_E2E = DEFERRED` (requires user selection)
- `SAF_BACKEND = SafResource.kt` (app module, Host-level)

**Track B: Add API (Custom Provider Profiles)**
- `ProviderProfileStore`: loads built-in (EMG/OpenRouter files) + custom
  (`filesDir/provider-profiles/<id>.json`)
- `buildModelCatalog()` now aggregates ALL profiles
- `HostActions.switchProfile(profileId, modelId)`: canonical apply for both
  built-in (via node) and custom (direct config)
- Provider Settings UI: list profiles → Edit / "+ Add API"
- Add API: Name / Base URL / API Key (masked) / Default Model → save
- `LEGACY_PROVIDER_CONFIG_COMPATIBLE = YES` (existing files unchanged)
- `CUSTOM_API_SAVE = PASS`, `CUSTOM_API_IN_MODEL_CATALOG = PASS`

**Track C: New Session E2E**
- `sidebar_new_session` tap → `HostActions.newSession()` → real session change
- Device verified: session A ≠ session B via debug snapshot
- `NEW_SESSION_UI_E2E = PASS`

Evidence: `functional_controls_closure.txt`

### Deferred (DO NOT REFACTOR this round)

- `DebugBridgeHolder` / `ChatActivity.sharedDebugBridge` dual reference
  (technical debt, DEFER)
- Diagnostic `Log.i("AG.UI", ...)` logs (keep until Gate A-I + device
  validation complete, then clean)
- SessionHolder.kt `runBlocking` import (legacy, unused, not in scope)
