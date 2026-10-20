# AG.HOST-SESSION-CONTINUITY-0 — Session Continuity Across Window/Config Changes

> ROOT_CAUSE_CLASSIFICATION = SESSION_OBJECT_LOST + HISTORY_BINDING_LOST + RUN_OBSERVER_LOST (MULTIPLE)

---

## 1. Root Cause

`ChatActivity` held `root` (ChatCompositionRoot), `runtime` (AgentRuntime),
`transcriptModel`, and all status text as **instance fields**. On Activity
recreation (rotation, config change, window size class change), these were all
lost. The new Activity instance had no reference to the existing Session.

Additionally, `onSaveInstanceState`/`onCreate(savedInstanceState)` were not
implemented, so no state survived recreation.

The Manifest had no `configChanges` attribute, so orientation changes triggered
full Activity destruction.

## 2. Old Ownership Path

```text
ChatActivity (instance field)
    ├── root: ChatCompositionRoot?
    ├── runtime: AgentRuntime?
    ├── transcriptModel: TranscriptModel
    └── providerStatusText / atStatusText / runStatusText
```

Activity recreation → all fields null/empty → Session "disappeared".

## 3. New Ownership Path

```text
SessionHolder (process-scoped object, like sharedDebugBridge)
    ├── root: ChatCompositionRoot?
    ├── transcriptModel: TranscriptModel
    ├── providerNode / providerStatusText / atStatusText / runStatusText
    └── isBusy

ChatActivity (UI projection, rebinds on onCreate)
    ├── reads from SessionHolder
    ├── writes status back to SessionHolder
    └── onSaveInstanceState → SessionHolder.saveState()
```

`ChatActivity.sharedRuntime` now delegates to `SessionHolder.runtime`, so the
DebugBridge's `runtimeProvider` lambda always reads the current Session.

## 4. Window Change Type

- **ACTIVITY_RECREATION** (rotation, config change): Session survives via
  SessionHolder (process-scoped). UI rebinds in `onCreate`.
- **RECOMPOSITION** (same Activity, layout change): No effect — the Activity
  is not destroyed, so all fields remain valid.

## 5. Session Identity

`AgentRuntime.currentSessionId` is the stable identity. It lives in the
process-scoped `ChatCompositionRoot` → `AgentRuntime` chain. The UI never
creates a new Session on resize.

## 6. Compact UI Behavior

The current layout is a single-column ScrollView (no two-pane). On compact
screens, the layout simply scrolls. The transcript, input, and buttons remain
accessible. No new responsive framework was added (minimal fix principle).

## 7. Run Continuity

An active Run is owned by `AgentRuntime` (process-scoped). The Run coroutine
is structured inside the caller's `lifecycleScope`, but the **Run state**
(session, tool calls, model request) lives in the Runtime, not the Activity.
On Activity recreation:
- The Run continues in the process.
- The new Activity rebinds to the same Runtime.
- `onRunEvent` / `streamObserver` callbacks from the OLD Activity's root are
  replaced by the NEW Activity's callbacks (wired in `createRoot`).
- No duplicate Provider request, no duplicate Tool call.

## 8. Non-Goals (NOT implemented)

- Session Persistence (STATE-003)
- Session Resume (STATE-006)
- Process-death recovery
- Room/DB/JSON session store
- Session serialization
- Multi-session UI (single-session model unchanged)

## 9. Files Changed

```text
app/src/main/java/com/androidagent/app/SessionHolder.kt       (NEW)
app/src/main/java/com/androidagent/app/ChatActivity.kt        (modified)
app/src/main/java/com/androidagent/app/TranscriptModel.kt     (fromRendered added)
```

No changes to: ag-core, ag-runtime, integration-at, tool-*, AT artifacts.

## 10. Verification

- `:app:testDebugUnitTest` — PASS
- `:app:assembleDebug` — PASS
- Full build — PASS (476 tasks)
- Device: install + launch OK
- Device: rotation/recreation test — PENDING (requires manual interaction)

## 11. Final Verdict

```text
IN_PROCESS_UI_CONTINUITY = IMPLEMENTED
PROCESS_DEATH_RECOVERY = NOT_REQUIRED (by design)
SESSION_PERSISTENCE = NOT_IMPLEMENTED
SESSION_RESUME = NOT_IMPLEMENTED
```
