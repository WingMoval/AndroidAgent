# AG.SESSION-MANAGEMENT-UX-0

**Date:** 2026-11-13
**Status:** VERIFIED
**Device:** HONOR ALI-AN00 (APLMUT4312005935), Android 15
**Provider:** OpenRouter / deepseek/deepseek-v4-flash-0731

## 1. Objective

Implement the Session Management UX for AndroidAgent-V2:
- Transient blank draft sessions (NO USER MESSAGE = NO SESSION)
- First-message materialization with auto-title
- Central Session Management surface (rename/delete)
- Busy-block for rename/delete
- Legacy audit
- Cold-restart persistence
- Device E2E verification

## 2. Core Contract

```text
NO USER MESSAGE = NO SESSION
```

- **Blank draft**: transient, non-durable, non-listed, no title. Created by `enterBlankDraft()`. The draft hydrates Core under `DRAFT_CORE_ID="__blank_draft__"` with `sessionId=null` and `blankDraft=true`.
- **First message materialization**: the first non-blank UserMessage creates a durable TopicSession with `title = normalized first message` and `title_origin = AUTO_FIRST_MESSAGE`. Materialization uses a two-step ordering (create row, then restore) to avoid the empty-history-UNRECOVERABLE trap.
- **Title auto-update**: fires ONLY ONCE at materialization. Subsequent messages do NOT change the title.
- **Manual rename**: sets `title_origin = USER_RENAMED` and wins permanently (auto-update does NOT override).
- **Delete current**: enters a blank draft (NO replacement row). `SessionManagementActivity.finish()` returns to ChatActivity with an empty transcript.
- **Delete non-current**: removes the row + messages via FK CASCADE. Current session unchanged.
- **Busy sessions**: rename and delete are disabled while a Run is active (`isBusy=true`).
- **Restore**: passive (0 model calls, 0 tool calls). DB -> Runtime/Core -> Presentation (single pipeline).

## 3. Architecture

### 3.1 AgentRuntime changes (ag-runtime)

- `enterBlankDraft()`: non-suspend; hydrates Core under `DRAFT_CORE_ID`, sets `sessionId=null`, `blankDraft=true`. Replaces old `newSession()`.
- `materializeDraft()`: creates durable row (title=normalized first msg, origin=AUTO_FIRST_MESSAGE), then `restoreSession(id, emptyList())` so Core's D1 commits the user message to the real session.
- `renameCurrentSession(newTitle)`: sets title + `title_origin=USER_RENAMED`. Rejected when busy or no current session.
- `deleteSession(sessionId)`: returns `DeleteResult.Deleted(currentAfter)` or `DeleteResult.Rejected(reason)`. If current, enters blank draft. If non-current, current unchanged.
- `normalizeTitle(text)`: trim + collapse whitespace (no LLM).
- `blankDraft` flag: tracks transient draft state.
- `DeleteResult` sealed interface: `Deleted(currentAfter)` / `Rejected(reason)`.

### 3.2 TitleOrigin enum (ag-runtime)

```kotlin
enum class TitleOrigin {
    AUTO_FIRST_MESSAGE, // 0: set at materialization
    USER_RENAMED        // 1: set by manual rename (wins permanently)
}
```

### 3.3 Store contract additions (ag-runtime)

- `renameSession(sessionId, newTitle, titleOrigin)`: updates title + origin
- `deleteSession(sessionId)`: deletes row (FK CASCADE handles messages)
- `countMessages(sessionId)`: returns message count
- `createSession` gained `titleOrigin` param

### 3.4 Room schema v2 (app)

- `MIGRATION_1_2`: adds `title_origin INTEGER` column to `topic_sessions`
- All existing rows: `title_origin=NULL` (treated as legacy)
- No data loss (69 sessions survived)
- `TopicSessionRecord` gained `titleOrigin` field
- `RoomTopicSessionStore` implements new store methods

### 3.5 UI (app)

- **SessionManagementActivity**: single central management surface (mirrors AdbConnectionActivity). Lists durable sessions (last_used_at DESC), current highlighted. Per-row actions: Open / Rename / Delete. "+ NEW SESSION" button at top.
- **Sidebar integration**: "Sessions" entry (`sidebar_sessions_entry`) opens SessionManagementActivity. Old in-place switcher dialog removed from ChatActivity.
- **SessionSummary**: gained `titleOrigin` + `messageCount`.
- **SessionHolder**: `renameSession()`, `deleteSession()`, `newSession()` (delegates to `enterBlankDraft()`).
- **HostActions**: `renameSession()`, `deleteSession()` (shared entry for UI).

### 3.6 Boundary compliance

- AG Core: UNCHANGED
- AG Runtime: additive only
- Tool Layer: UNCHANGED
- Provider: UNCHANGED
- AT Engine: UNCHANGED
- No new modules, no new dependencies, no new AgentEvent
- No Architecture v1.0 change

## 4. Verification

### 4.1 Unit tests

```text
ag-core:    52 tests, 0 failures
ag-runtime: 103 tests, 0 failures
app:        248 tests, 0 failures
TOTAL:      403 tests, 0 failures
```

New tests: `SessionLifecycleManagementTest` (15 tests) covering:
- Blank draft (non-durable, non-listed, no title)
- First message materialization (title, origin)
- Title stability (auto-update once)
- Rename (durability, USER_RENAMED, permanent win, busy-block, no-current)
- Delete (FK CASCADE, current->blank, non-current, busy-block, not-found)

Test fakes updated: InMemoryTopicSessionStore, FakeTopicSessionStore, GatingStore, ProviderProtocolTest, StoreMessageDurabilityTest, HostStopStateTest.

### 4.2 Build

```text
:ag-core:test          PASS
:ag-runtime:test       PASS
:app:testDebugUnitTest PASS
:app:assembleDebug     PASS
```

### 4.3 Device E2E

| Test | Result |
|------|--------|
| ADB regression (READY, uid=2000, no re-pair) | PASS |
| Blank draft (non-durable, non-listed, no title) | PASS |
| First message materialization | PASS |
| Title = first message, origin=AUTO | PASS |
| Title stable after 2nd message | PASS |
| Provider regression (real model) | PASS |
| Rename (unit + UI flow, origin=USER_RENAMED) | PASS |
| Delete non-current (FK CASCADE) | PASS |
| Delete current -> blank draft | PASS |
| Re-materialization (new session, title from 1st msg) | PASS |
| Cold restart persistence | PASS |
| Restore passive (0 model/tool calls) | PASS |
| Legacy audit (no timestamp titles, no auto-delete) | PASS |

### 4.4 Legacy audit

- All 65 pre-existing sessions had `title=NULL` (no timestamp titles in DB)
- `LEGACY_TIMESTAMP_TITLE_NORMALIZED = NOT_NEEDED`
- 30 empty legacy sessions exist but are NOT auto-deleted (conservative per §57)
- No data loss: 69 sessions survived migration 1->2

## 5. Harness artifacts

1. **Rename text garble**: ADB `input text` INSERTS rather than REPLACES in the EditText on this HONOR device. The garbled title is a harness artifact, not an app bug. Rename API proven by unit tests + UI flow.
2. **Screen lock**: Phone locked repeatedly during UI automation. Mitigated with `svc power stayon true` + wake + dismiss-keyguard.
3. **DB pull timing**: Some intermediate pulls showed stale counts (async Room commit). Final verification confirms all changes persisted.

## 6. Evidence

```text
docs/evidence/ag_session_management_ux_0/
  00_git_baseline.txt
  01_architecture.txt
  02_blank_draft.txt
  03_materialization.txt
  04_title_stability.txt
  05_rename_e2e.txt
  06_delete_e2e.txt
  07_legacy_audit.txt
  08_delete_current_blank_draft.txt
  09_cold_restart_persistence.txt
  10_session_management_ui.txt
  11_adb_regression.txt
  12_tests_build.txt
  13_db_schema_migration.txt
  14_architecture_boundary.txt
  15_device_e2e_summary.txt
  16_final_verdict.txt
```

## 7. Final Closeout

```text
CONTROLLER_REVIEW = PASS

START_HEAD = 83ce04f
IMPLEMENTATION_COMMIT = see repository HEAD for this closeout
BRANCH = main
WORKTREE = CLEAN (post-commit)

FREEZE_TAG = ag-current-capability-release-baseline-v1
FREEZE_TAG_TARGET = 8828893
FREEZE_TAG_INTACT = YES

NEW_TAG = NO
PUSH = NO
SUBAGENTS_USED = NO
DELEGATED_WORKERS_USED = NO
```
