# AG.UI-TRANSCRIPT-CARDS-1 — Transcript Cards + Session Browser

## Status

CLOSED / PASS

C1-C9 = PASS (全部真机验证, OpenRouter deepseek-v4-flash-0731)
Session Browser E2E = PASS (S1-S10)
TESTS = 410 / 0
ASSEMBLE_DEBUG = PASS
DEVICE_VALIDATION = PASS

Retry semantics = Retry as New Turn
Rewind = NOT IMPLEMENTED
Redo = NOT IMPLEMENTED
Display-only fake rewind = NOT IMPLEMENTED

## Scope

Replace single-TextView transcript renderer with semantic card renderer (RecyclerView)
supporting per-block Copy, Retry, Edit & Resend, collapse/expand, and native text selection.
Add TopicSession history browser (list + switch) to the Sidebar.

## Track A: Transcript Cards

### Renderer

- `RecyclerView` (`transcript_recycler`) replaces `ScrollView` + `TextView`
- `TranscriptCardAdapter` maps `TranscriptModel.Block` → card views
- `item_transcript_card.xml`: label + action buttons + body + collapse toggle
- PRESENTATION ONLY — SSOT remains `TranscriptModel.Block`

### Card Types

| Block | Card | Label | Actions |
|-------|------|-------|---------|
| User | UserCard | "You" | Copy, Retry, More (Copy/Edit&Resend) |
| Thinking (THINKING) | ThoughtCard | "Thinking..." | Copy |
| Thinking (THOUGHT) | ThoughtCard | "Thought" | Copy, collapse (>200) |
| Assistant | AssistantCard | "Assistant" | Copy, Retry (origin User) |
| Shell | ToolExecution | "Shell" | Copy |
| Result | ToolExecution | "Result" | Copy, collapse (>300) |
| Failure | FailureCard | "Failed" | Copy, Retry |
| Interrupted | InterruptedCard | "Stopped" | Copy, Retry |

### Copy

- `ClipboardManager.setPrimaryClip` per block
- Only copies current block text (never entire transcript)

### Retry

- Finds originating User text (backward scan to nearest User block)
- Calls `HostActions.send(originText)` — creates a NEW User Turn
- Old history is NOT deleted/modified
- Hidden when `isBusy == true`
- `RETRY_MUTATES_HISTORY = NO`, `RETRY_CREATES_NEW_TURN = YES`

### Edit & Resend

- UserCard "More" → "Edit & Resend"
- Restores old User text to Composer `input`
- User edits and presses Send → new User Turn
- Does NOT modify old durable message

### Thought Collapse

- Same card lifecycle (Thinking → Thought phase flip)
- >200 chars: truncated + "Show more" / "Show less"

### Result Collapse

- >300 chars: truncated + "Show more" / "Show less"
- Does NOT fake "expand" for upstream-truncated content

### Dependency

- `androidx.recyclerview:recyclerview:1.3.2` (WHY: per-block interaction impossible with single TextView)

## Track B: Session Browser

### Store (Additive)

- `TopicSessionStore.listSessions(): List<TopicSessionRecord>` — new interface method
- `TopicSessionDao.listAll()` — `SELECT * FROM topic_sessions ORDER BY last_used_at DESC`
- `RoomTopicSessionStore.listSessions()` — implementation
- `DB_SCHEMA_CHANGE = NO`, `MIGRATION = NO`

### Runtime (Additive)

- `AgentRuntime.switchSession(targetSessionId): Boolean`
- Reuses `restoreExistingSession` (load + repair dangling + hydrate Core)
- Rejects if busy or session not found
- `SESSION_ROW_EXISTS_BEFORE_CURRENT` invariant preserved

### HostActions (Shared Entry)

- `HostActions.listSessions(): List<SessionSummary>`
- `HostActions.switchSession(sessionId): SessionSwitchResult`
- UI and (future) Debug share the same entry

### UI

- Sidebar "Current Session" label is clickable → opens session switcher dialog
- Dialog shows all sessions (last_used_at DESC), current highlighted with "●"
- Click → `HostActions.switchSession` → `projectCurrentHistory` → re-render cards

### SSOT

```
DB → Runtime/Core → Transcript projection → Cards
```

No second pipeline. UI never reads DB directly.

## Files Changed/Added

### Production
- `app/build.gradle.kts` — +recyclerview
- `app/src/main/res/layout/activity_chat.xml` — RecyclerView replaces TextView
- `app/src/main/res/layout/item_transcript_card.xml` — NEW card layout
- `app/src/main/java/com/androidagent/app/TranscriptCardAdapter.kt` — NEW
- `app/src/main/java/com/androidagent/app/ChatActivity.kt` — adapter wiring, retry/edit, session switcher
- `app/src/main/java/com/androidagent/app/HostActions.kt` — listSessions, switchSession
- `app/src/main/java/com/androidagent/app/SessionHolder.kt` — topicSessionStoreRef accessor
- `app/src/main/java/com/androidagent/app/persistence/Daos.kt` — listAll query
- `app/src/main/java/com/androidagent/app/persistence/RoomTopicSessionStore.kt` — listSessions
- `ag-runtime/.../persistence/TopicSessionStore.kt` — listSessions interface
- `ag-runtime/.../AgentRuntime.kt` — switchSession

### Tests (updated)
- `HostStopStateTest.kt` — NoopStore implements listSessions
- `StoreMessageDurabilityTest.kt` — ScriptedStore implements listSessions

## Deferred

- Session rename/delete/fork: AG.UI-CONTROLS-1
- Debug topic_session_list/switch commands: future (not added this round to avoid protocol scope creep)
- Turn grouping visual dividers: v1 uses card labels only

## Verdict

```
TRANSCRIPT_SINGLE_TEXTVIEW_REPLACED = YES
USER_CARD = PASS (C1 device verified)
THOUGHT_CARD = PASS (C5: OpenRouter reasoning 真机验证)
ASSISTANT_CARD = PASS (C6: streaming + copy)
TOOL_EXECUTION_CARD = PASS (C7: OpenRouter Shell+Result 真机验证)
FAILURE_CARD = PASS (device: "Failed" + "Provider failure (Network)")
INTERRUPTED_CARD = PASS (C8 device verified: "Stopped" ≠ "Failed")
PER_BLOCK_COPY = PASS (C2 + C6 device verified: logcat clipboard confirmation)
NATIVE_SELECTION = PASS (C9 device verified: selection handles + system menu)
RETRY = PASS (C3 device verified: new Run, old history preserved)
EDIT_RESEND = PASS (C4 device verified: Composer restored with old text)
THOUGHT_COLLAPSE = PASS (C5: 真机验证)
RESULT_COLLAPSE = PASS (C7: 真机验证)
RETRY_CREATES_NEW_TURN = YES
RETRY_MUTATES_HISTORY = NO
SESSION_LIST = PASS (device: 14 sessions from durable DB)
SESSION_CURRENT_HIGHLIGHT = PASS (device: ● on current)
SESSION_SWITCH = PASS (device: 3 successful switches)
SESSION_SWITCH_E2E = PASS (device: session ID changed, 3x verified)
DB_SCHEMA_CHANGE = NO
SECOND_TRANSCRIPT_PIPELINE = NO
CALLBACK_DEBUG_REGRESSION = NONE
TESTS = 410 / 0
ASSEMBLE_DEBUG = PASS
DEVICE_VALIDATION = PASS (C1-C9 全部真机验证, OpenRouter deepseek-v4-flash-0731)
```
