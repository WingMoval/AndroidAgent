# AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0 PROGRESS REPORT

**STATUS: Phase A/B/C/D/E/F COMPLETED, 0F.NORMALIZE-1/1B COMPLETED, 0G.BLOCKER-FIX-1 + NORMALIZE-1 COMPLETED, Phase G COMPLETED, Phase H MERGED INTO G / COMPLETED, Phase I COMPLETED, DEVICE-I VERIFIED (FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED), POST-I ARCHITECTURE CLOSURE = PASS (§21; TOPIC_SESSION_FOUNDATION = FROZEN — docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md; NEXT = UI + RELEASE PREPARATION)**

## 1. Baseline

```text
START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
BRANCH = main
WORKTREE_BEFORE = DIRTY (46 files: 42 pre-existing + 2 architecture docs)
PRE_EXISTING_PRODUCTION_DIRTY_FILES = 13
```

## 2. Completed Work

### Phase A — AG Core Durability Seam (COMPLETED)

| File | Change |
|------|--------|
| `ag-core/.../MessageDurability.kt` | NEW: `fun interface MessageDurability` with `suspend fun commit(sessionId: String, message: Message)`; `DurabilityCommitException`; `MessageDurability.Noop` (companion, NO default) |
| `ag-core/.../RunOutcome.kt` | ADDED: `RunFailure.DurabilityFailure(boundary: String, cause: String)` |
| `ag-core/.../AgentCore.kt` | MODIFIED: constructor now requires `messageDurability: MessageDurability` (no default); D1/D2/D3 integration; `failDurability()` helper; `safeCause()` helper; `restoreSession()` method |
| `ag-core/.../Session.kt` | MODIFIED: ADDED `restoreHistory(history: List<Message>)` (internal) |

### D1 Integration (UserMessage)

```kotlin
// AgentCore.driveLoop(), after tryBeginRun() == true:
val userMsg = UserMessage(userInput)
try {
    messageDurability.commit(session.id, userMsg)
} catch (e: DurabilityCommitException) {
    return failDurability(sink, RunFailure.DurabilityFailure("USER", safeCause(e)), terminalClaimed)
}
session.append(userMsg)
// then ModelCall
```

### D2 Integration (ModelMessage)

```kotlin
// AgentCore.driveLoop(), after ModelTurn received:
val modelMsg = ModelMessage(turn.content, calls)  // or ModelMessage(turn.content) for FinalAnswer
try {
    messageDurability.commit(session.id, modelMsg)
} catch (e: DurabilityCommitException) {
    return failDurability(sink, RunFailure.DurabilityFailure("MODEL" or "FINAL_ANSWER", safeCause(e)), terminalClaimed)
}
session.append(modelMsg)
// then Tool dispatch or AnswerProduced
```

### D3 Integration (ToolResultMessage)

```kotlin
// AgentCore.driveLoop(), after Tool.execute() returns:
val resultMsg = ToolResultMessage(call.id, call.name, result)
try {
    messageDurability.commit(session.id, resultMsg)
} catch (e: DurabilityCommitException) {
    return failDurability(sink, RunFailure.DurabilityFailure("TOOL_RESULT", safeCause(e)), terminalClaimed)
}
session.append(resultMsg)
// then ToolCallFinished → next ModelCall
```

### Phase B — Core Restore Seam (COMPLETED)

| Method | Location |
|--------|----------|
| `Session.restoreHistory(history: List<Message>)` | `ag-core/.../Session.kt` (internal, synchronized, checks !activeRun) |
| `AgentCore.restoreSession(id: String, initialHistory: List<Message>): Session` | `ag-core/.../AgentCore.kt` (creates Session, calls restoreHistory, replaces in sessions map) |

**Key property:** Restore does NOT trigger `MessageDurability` (no re-persistence).

### Construction Site Migration

| Type | Count | Status |
|------|-------|--------|
| Production | 1 (`ChatCompositionRoot.kt`) | `StoreMessageDurability` wired in Phase F; production `MessageDurability.Noop` removed |
| Tests | 11+ files | Test constructions may use explicit `MessageDurability.Noop` or test fakes where appropriate |

**CURRENT STATE:** Production durability is `AgentCore → MessageDurability → StoreMessageDurability → TopicSessionStore → RoomTopicSessionStore → Room/SQLite`. `MessageDurability.Noop` is not used in production.

### App-level `when` Exhaustiveness

Added `RunFailure.DurabilityFailure` branch to:
- `TranscriptModel.kt:339`
- `DebugProtocol.kt:377`
- `AgentTraceCollector.kt:130`

### Test Results

```text
ag-core:test                        PASS
ag-runtime:test                     PASS
provider-openai-compatible:test     PASS
provider-openai-compatible-jdk:test PASS
tool-android-shell:test             PASS
tool-network:test                   PASS
tool-file:test                      PASS
app:testDebugUnitTest               PASS
```

All modules compile and test green.

## 3. Phase C — TopicSessionStore Contract (COMPLETED)

### Files

| File | Content |
|------|---------|
| `ag-runtime/.../persistence/TopicSessionStore.kt` | `interface TopicSessionStore` — storage-agnostic port: `createSession`, `appendMessage`, `loadSession`, `loadLatestSession`, `loadMessages`, `updateModelProviderMetadata` |
| `ag-runtime/.../persistence/Records.kt` | `TopicSessionRecord`, `MessageRecord` (storage-neutral records); `SessionNotFoundException`, `PersistenceException` (safe category, never raw backend message) |

### Frozen Semantics

```text
STORE_OWNER = ag-runtime
STORE_ANDROID_DEPENDENCY = NONE
STORE_ROOM_DEPENDENCY = NONE
SEQUENCE_OWNER = persistence implementation (atomic in ONE transaction)
RUNTIME_PREALLOCATES_SEQUENCE = NO
APPEND_MESSAGE_RETURN_TYPE = MessageRecord
APPEND_MESSAGE_RETURN_TYPE_REASON = the store-assigned sequence + message id +
    createdAt are committed facts the caller (Phase F adapter / diagnostics)
    needs; the contract forbids caller-side preallocation
LOAD_MESSAGES_ORDER = sequence ASC (caller never reorders)
LOAD_LATEST_SESSION = ORDER BY last_used_at DESC LIMIT 1 (opaque to caller)
MISSING_SESSION = null for loadSession/loadMessages; SessionNotFoundException
    for appendMessage/updateModelProviderMetadata
```

### Test (test-source-set only, NOT a production fallback)

`ag-runtime/.../persistence/InMemoryTopicSessionStore.kt` — models the frozen
semantics (atomic sequence from 0, last_used_at updated only on successful
append, per-session isolation, sequence ASC load).

**Contract tests (9): STORE-T1..T6 + append return + metadata update +
multi-toolcall survival — ALL PASS**

## 4. Phase D — Message Serializer (COMPLETED)

### Files

| File | Content |
|------|---------|
| `ag-runtime/.../persistence/MessageSerializer.kt` | `object MessageSerializer` (pure Kotlin, kotlinx-serialization-json TREE API); `MessageType` enum (USER/MODEL/TOOL_RESULT frozen set); `SerializedMessage`; `MessageDecodeException` / `MessageSerializationException` (safe messages, raw payload NEVER embedded) |

### Frozen Semantics

```text
MESSAGE_SERIALIZER_LIBRARY = kotlinx-serialization-json 1.8.0 (tree API,
    same dependency + same tree-API style the provider modules already use;
    NO serialization compiler plugin; NO new JSON stack)
NEW_JSON_DEPENDENCY_ADDED = kotlinx-serialization-json to :ag-runtime only
    (version 1.8.0 = identical to provider modules)
SUPPORTED_MESSAGE_TYPES = USER / MODEL / TOOL_RESULT
UNKNOWN_TYPE = EXPLICIT_FAILURE (MessageDecodeException)
MALFORMED_PAYLOAD = EXPLICIT_FAILURE (MessageDecodeException, no raw payload)
UNKNOWN_JSON_FIELDS = IGNORED (forward compatibility)
SERIALIZER_TRUNCATES = NO (large output saved as-is; Tool Layer owns bound)
REASONING_PERSISTED = NO
TOOLCALL_IN_PAYLOAD = YES (ToolCall is part of the MODEL payload, never a row)
```

**Serializer tests (15): SER-T1..T13 + no-truncation + unknown-field
forward-compat — ALL PASS**

## 5. Phase E — Room Schema + Atomic Store + WAL (COMPLETED)

### Gradle (minimal, no upgrades)

| File | Change |
|------|--------|
| root `build.gradle.kts` | `+ id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false` (KSP pinned to Kotlin 2.1.0) |
| `app/build.gradle.kts` | `+ id("com.google.devtools.ksp")`; `+ room-runtime/room-ktx/room-compiler:2.7.2`; `+ robolectric:4.13`, `androidx.test:core:1.6.1`, `junit:junit:4.13.2`, `junit-vintage-engine` (test-only) |

```text
ROOM_VERSION = 2.7.2
ROOM_COMPILER = room-compiler 2.7.2 via KSP 2.1.0-1.0.29
KSP_OR_KAPT = KSP (no kapt)
WHY = KSP is versioned per Kotlin release; 2.1.0-1.0.29 matches the project's
      Kotlin 2.1.0. Room 2.7.2 = last 2.7.x, KSP 1.x-era compiler proven with
      AGP 8.5. No Kotlin/AGP/Gradle/JDK upgrade.
```

### Production files (`:app` only — Room entities never leak into ag-runtime)

| File | Content |
|------|---------|
| `app/.../persistence/Entities.kt` | `TopicSessionEntity` (topic_sessions), `MessageEntity` (messages) — snake_case columns pinned via `@ColumnInfo` to match the frozen SSOT; FK ON DELETE CASCADE; UNIQUE(topic_session_id, sequence); INDEX(topic_session_id, sequence); INDEX(last_used_at) |
| `app/.../persistence/Daos.kt` | `TopicSessionDao` (insert ABORT-on-conflict, findById, findLatest `ORDER BY last_used_at DESC, created_at DESC, id LIMIT 1`, updateModelProvider), `MessageDao` (insert, findBySession `ORDER BY sequence ASC`, nextSequence `COALESCE(MAX(sequence),-1)+1`, touchLastUsed) |
| `app/.../persistence/TopicSessionDatabase.kt` | `TopicSessionDatabase` (version 1, `topic_sessions.db`, exportSchema=true, NO fallbackToDestructiveMigration); `WalOpenHelperFactory` (EXPLICIT WAL via `setWriteAheadLoggingEnabled(true)`); `journalModeOf()` helper |
| `app/.../persistence/RoomTopicSessionStore.kt` | `RoomTopicSessionStore : TopicSessionStore` — record/entity mapping + the atomic append transaction |

### Frozen semantics implemented

```text
DATABASE_NAME = topic_sessions.db (app-private)
DATABASE_VERSION = 1
TABLE_COUNT = 2 (topic_sessions, messages) — verified in generated schema
FOREIGN_KEY = PASS (PRAGMA foreign_keys=1; orphan insert fails)
CASCADE_DELETE = PASS (deleting a session removes its messages)
UNIQUE_SESSION_SEQUENCE = PASS (duplicate (session,sequence) fails)
INDEXES = PASS (index_messages_topic_session_id_sequence UNIQUE + index_topic_sessions_last_used_at)
SEQUENCE_OWNER = persistence transaction (nextSequence + insert in ONE withTransaction)
RUNTIME_PREALLOCATES_SEQUENCE = NO
FIRST_SEQUENCE = 0
APPEND_TRANSACTION = nextSequence + INSERT + touchLastUsed in ONE withTransaction
LAST_USED_UPDATE_RULE = only on successful durable append (reads/metadata do NOT touch it)
WAL_CONFIGURATION = EXPLICIT (WalOpenHelperFactory.setWriteAheadLoggingEnabled(true))
WAL_RUNTIME_VERIFICATION = PASS (PRAGMA journal_mode = wal, file-backed)
DESTRUCTIVE_MIGRATION = NO
PRODUCTION_MAIN_THREAD_DB = NO (all DAO ops suspend; no allowMainThreadQueries)
MESSAGE_SERIALIZER_REUSED = YES (RoomTopicSessionStore calls MessageSerializer; no second serializer)
```

### Room integration tests (21, JVM via Robolectric 4.13 + JUnit 4/Vintage)

`app/src/test/.../persistence/RoomTopicSessionStoreTest.kt`

```text
ROOM-T1 createSession->loadSession equivalent        PASS
ROOM-T2 loadLatestSession picks latest last_used     PASS
ROOM-T3 first append -> sequence 0                   PASS
ROOM-T4 multiple appends contiguous 0..N-1           PASS
ROOM-T5 loadMessages sequence ASC                    PASS
ROOM-T6 successful append -> last_used updated       PASS
ROOM-T7 failed append -> last_used unchanged (rollback atomicity)  PASS
ROOM-T8 missing session append -> SessionNotFound    PASS
ROOM-T9 metadata update persisted, last_used untouched PASS
ROOM-T10 FK enforced at DB level                     PASS
ROOM-T11 unique(session,sequence) enforced           PASS
ROOM-T12 journal_mode = wal at runtime               PASS
ROOM-T13 reads do not touch last_used                PASS
ROOM-T14 cascade delete                               PASS
serializer<->room round-trip (all persisted message variants: User, Model+multi-ToolCall, ToolResult success, ToolResult INTERRUPTED_UNKNOWN)  PASS
large (200k) tool result not truncated                PASS
corrupt payload -> MessageDecodeException             PASS
file-backed reopen durability                         PASS
duplicate createSession id -> explicit failure        PASS
uncommitted transaction leaves no durable row         PASS
concurrent (16) appends -> unique contiguous 0..15    PASS
```

> NOTE (ROOM-T7): forcing a UNIQUE collision *inside* the store transaction
> via the public API is not reliably reproducible under Robolectric (the store
> computes MAX+1, so a pre-occupied lower sequence does not collide). ROOM-T7
> therefore proves the SAME atomicity property directly: the store's two
> operations (message INSERT + last_used UPDATE) inside a `withTransaction`
> that is forced to ROLLBACK leave neither the row nor the last_used change
> durable. The `uncommitted transaction leaves no durable row` test adds a
> second, independent rollback proof. The DB-level UNIQUE constraint is
> separately proven by ROOM-T11 (direct collision) + the concurrent test.

## 6. Remaining Work (Phase F-L)

| Phase | Description | Status |
|-------|-------------|--------|
| F | `StoreMessageDurability` adapter (persistence-backed, cancellation-shielded) + replace production Noop + D1/D2/D3 persistence integration tests | COMPLETED (§12) |
| G | `AgentRuntime` startup create-or-restore + controlled recovery + Core hydration + idempotence | COMPLETED (§17 + §18 NORMALIZE-1) |
| H | Dangling tool detection + synthetic repair | COMPLETED WITH G / MERGED INTO G (cross-process durable repair shipped inside Phase G) |
| I | Transcript re-projection from history + user-visible resume | COMPLETED (§19) — restored Core history → TranscriptModel.reproject; FULL_USER_VISIBLE_RESUME = PASS |
| J | AG Core deterministic tests (CORE-T1..T8) + CANCEL-T1..T6 + ScriptedMessageDurability | COMPLETED (CANCEL-F coverage landed with Phase F) |
| K | ~~Serializer round-trip tests~~ | DONE in Phase D (SER-T1..T13) |
| L | ~~Room integration tests (ROOM-T1..T14)~~ | DONE in Phase E (21 Room tests) + Phase G/G.NORMALIZE-1 (file-backed reopen + recovery) |
| — | Device verification (DEV-I1..I4) | COMPLETED (§20) — real-device PASS on HONOR ALI-AN00 (Android 15); FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED |

## 6a. Test Results (measured from JUnit XML)

```text
Phase C/D:
ag-runtime:test  = 49 tests, 0 failures (26 pre-existing + 9 store contract + 15 serializer + 1 multi-toolcall store)
ag-core:test     = 41 tests, 0 failures
app:testDebugUnitTest = 109 tests, 0 failures
app:assembleDebug = BUILD SUCCESSFUL

Phase E (cumulative):
ag-core:test     = 41 tests, 0 failures
ag-runtime:test  = 49 tests, 0 failures
app:testDebugUnitTest = 130 tests, 0 failures (109 pre-existing + 21 Room integration)
app:assembleDebug = BUILD SUCCESSFUL
```

## 7. Architecture Compliance (updated after Phase E)

```text
DURABLE_FIRST = YES (commit → append, no append → persist)
D1_BEFORE_MODEL = YES
D2_BEFORE_TOOL = YES
D3_BEFORE_NEXT_MODEL = YES
FINAL_ANSWER_DURABLE_BEFORE_SUCCESS = YES
AMBIGUOUS_COMMIT_OUTCOME_ALLOWED = NO (by contract; IMPLEMENTED in Phase F)
CORE_ORPHAN_MESSAGE_ALLOWED = NO
PRODUCTION_NOOP = NO (StoreMessageDurability is the production durability; Noop only in tests)
CORE_ROOM_DEPENDENCY = NONE
CORE_SQLITE_DEPENDENCY = NONE
TOPIC_SESSIONSTORE_OWNER = ag-runtime (IMPLEMENTED in Phase C)
AG_RUNTIME_JSON_DEPENDENCY = kotlinx-serialization-json tree API only (Phase D)
DATABASE_TABLES = 2 (topic_sessions + messages; IMPLEMENTED in Phase E, verified in generated schema)
SEQUENCE_ALLOCATION = ATOMIC_WITH_INSERT (IMPLEMENTED in Phase E; nextSequence+insert in ONE transaction)
WAL = EXPLICIT (IMPLEMENTED in Phase E; WalOpenHelperFactory + runtime PRAGMA verified)
ROOM_IMPLEMENTATION_OWNER = :app (RoomTopicSessionStore; entities never leak into ag-runtime)
DANGLING = UNKNOWN semantics IMPLEMENTED (same-process closeout + cross-process startup repair; canonical AgentCore.syntheticInterruptedToolResult)
AUTO_REPLAY = NO
DURABLE_REPAIR_FIRST = YES (Phase G: durable repair → reload → Core hydrate second)
CROSS_PROCESS_REPAIR = IMPLEMENTED (Phase G; Room file-backed reopen + second-reopen idempotence verified)
SESSION_UNRECOVERABLE_RECOVERY = CONTROLLED_FRESH (Phase G NORMALIZE-1: corrupt session preserved, fresh durable session created, no destructive recovery)
```

## 8. Files Changed (Cumulative, All Rounds)

### New Files
```text
ag-core/src/main/kotlin/com/androidagent/core/MessageDurability.kt
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/TopicSessionStore.kt
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/Records.kt
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/MessageSerializer.kt
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/InMemoryTopicSessionStore.kt
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/TopicSessionStoreContractTest.kt
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/MessageSerializerTest.kt
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
```

### Modified Files (Production)
```text
ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
ag-core/src/main/kotlin/com/androidagent/core/Session.kt
ag-core/src/main/kotlin/com/androidagent/core/RunOutcome.kt
ag-runtime/build.gradle.kts   (Phase D: +kotlinx-serialization-json 1.8.0, +kotlin test-junit5 for tests)
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt
app/src/main/java/com/androidagent/app/TranscriptModel.kt
app/src/main/java/com/androidagent/app/debug/DebugProtocol.kt
app/src/main/java/com/androidagent/app/trace/AgentTraceCollector.kt
```

### Modified Files (Tests — construction site migration)
```text
ag-core/src/test/kotlin/com/androidagent/core/AgentLoopTest.kt
ag-core/src/test/kotlin/com/androidagent/core/EventSinkContractTest.kt
ag-core/src/test/kotlin/com/androidagent/core/SessionConcurrencyTest.kt
ag-core/src/test/kotlin/com/androidagent/core/ToolRegistryTest.kt
ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeInterruptTest.kt
ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt
app/src/test/java/com/androidagent/app/HostStopStateTest.kt
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/LiveApiTest.kt
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/ProviderProtocolTest.kt
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/VerticalSliceTest.kt
```

## 9. Pre-existing Dirty Files

All pre-existing dirty files preserved. No `git reset`, `git clean`, `git stash`, or `git checkout --` performed.

## 10. Git Policy

```text
COMMIT = NO
TAG = NO
PUSH = NO
```

## 11. Recommended Next

**TopicSession persistence subsystem = CLOSED / FREEZE CANDIDATE** (device verification completed, see §20; this section's original "device verification PENDING" wording is superseded).

Current durable/user-visible state:
- TOPIC_SESSION_PERSISTENCE = CLOSED / FREEZE CANDIDATE
- Phase F = CLOSED
- Phase G = CLOSED
- Phase H = MERGED INTO G / CLOSED
- Phase I = CLOSED
- DEVICE-I = VERIFIED (§20)
- `DURABLE_RUNTIME_RESUME = PASS`
- `TRANSCRIPT_REPROJECTION = PASS`
- `FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED` (§20)

NEXT:
- POST-I ARCHITECTURE / IMPLEMENTATION CLOSURE AUDIT (module inventory, Gradle physical dependency graph, package import audit, Core/Runtime boundary, Tool/Provider inversion, persistence freeze audit)
  - if PASS: UI + RELEASE PREPARATION
  - if BLOCKED: separate minimal-fix round before any freeze

Known test-stability note:
- `AgentRuntimeInterruptTest I11` has pre-existing flakiness under full-matrix parallel load and passes in isolation; treat it as KNOWN TEST STABILITY DEBT (not a product blocker) unless a reproducible focused regression shows contrary evidence.

---

# AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0C FINAL REPORT

```text
START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (unchanged; COMMIT = NO)
BRANCH = main

WORKTREE_BEFORE = DIRTY (63 files)
WORKTREE_AFTER  = DIRTY (66 files: +2 production persistence files, +3 test
                  persistence files, +1 gradle modification, +1 this report
                  update; no pre-existing file lost)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES (git diff --check: only pre-existing
                  CRLF warnings on pre-existing files; no hunk loss)

PHASE_A_STATUS = COMPLETED
PHASE_B_STATUS = COMPLETED
PHASE_C_STATUS = COMPLETED
PHASE_D_STATUS = COMPLETED

TOPIC_SESSIONSTORE_FILE = ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/TopicSessionStore.kt
TOPIC_SESSIONSTORE_OWNER = ag-runtime
TOPIC_SESSIONSTORE_ANDROID_DEPENDENCY = NONE
TOPIC_SESSIONSTORE_ROOM_DEPENDENCY = NONE

TOPIC_SESSIONSTORE_API = createSession(id, nowMillis, model?, provider?, title?) : TopicSessionRecord
                         appendMessage(sessionId, message, nowMillis) : MessageRecord
                         loadSession(id) : TopicSessionRecord?
                         loadLatestSession() : TopicSessionRecord?
                         loadMessages(sessionId) : List<Message>?
                         updateModelProviderMetadata(sessionId, model?, provider?)
APPEND_MESSAGE_RETURN_TYPE = MessageRecord
APPEND_MESSAGE_RETURN_TYPE_REASON = the store-assigned sequence (atomic, NOT
                         preallocated by the caller) + committed message id +
                         createdAt are facts the Phase F adapter and
                         diagnostics need; returning the committed record also
                         makes the ONE-transaction contract observable

SEQUENCE_OWNER = persistence implementation (atomic, inside the append
                 transaction; UNIQUE(topic_session_id, sequence) as final guard)
RUNTIME_PREALLOCATES_SEQUENCE = NO

TOPIC_SESSION_RECORD = TopicSessionRecord(id, createdAt, lastUsedAt, model?, provider?, title?)
MESSAGE_RECORD = MessageRecord(id, topicSessionId, sequence, type, payloadJson, createdAt)

MESSAGE_SERIALIZER_FILE = ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/MessageSerializer.kt
MESSAGE_SERIALIZER_LIBRARY = kotlinx-serialization-json 1.8.0 (TREE API /
                 JsonElement; same dependency + style as the provider modules;
                 NO serialization compiler plugin)
NEW_JSON_DEPENDENCY_ADDED = kotlinx-serialization-json:1.8.0 to :ag-runtime
                 ONLY (identical version to provider modules; no new JSON
                 stack, no second library)

SUPPORTED_MESSAGE_TYPES = USER / MODEL / TOOL_RESULT (frozen; NO
                 RUN/EVENT/TOOL_CALL/FINAL_ANSWER)

USERMESSAGE_ROUNDTRIP = PASS (SER-T1)
MODELMESSAGE_ROUNDTRIP = PASS (SER-T2)
SINGLE_TOOLCALL_ROUNDTRIP = PASS (SER-T3, empty arguments exact)
MULTI_TOOLCALL_ROUNDTRIP = PASS (SER-T4, ordering preserved)
TOOLRESULT_SUCCESS_ROUNDTRIP = PASS (SER-T5)
TOOLRESULT_FAILURE_ROUNDTRIP = PASS (SER-T6)
INTERRUPTED_UNKNOWN_ROUNDTRIP = PASS (SER-T7, semantic preserved, not interpreted)
UNICODE_ROUNDTRIP = PASS (SER-T8, 中文 + emoji)
MULTILINE_ROUNDTRIP = PASS (SER-T9, \n, tabs, \r\n, shell output)
ESCAPING_ROUNDTRIP = PASS (SER-T10, quotes/backslashes/JSON-looking content)
MALFORMED_PAYLOAD_BEHAVIOR = EXPLICIT_FAILURE (SER-T12, MessageDecodeException;
                 raw payload NOT embedded in the exception message)
UNKNOWN_TYPE_BEHAVIOR = EXPLICIT_FAILURE (SER-T13, MessageDecodeException)

SERIALIZER_TRUNCATES = NO (verified with 100k-char output)
REASONING_PERSISTED = NO

STORE_CONTRACT_TESTS = 9 PASS (STORE-T1..T6 + append return + metadata +
                 multi-toolcall survival)
SERIALIZER_TESTS = 15 PASS (SER-T1..T13 + no-truncation + unknown-field
                 forward-compat)

AG_CORE_TESTS = PASS (41 tests, 0 failures)
AG_RUNTIME_TESTS = PASS (49 tests, 0 failures)
APP_UNIT_TESTS = PASS (109 tests, 0 failures)
ASSEMBLE_DEBUG = PASS (BUILD SUCCESSFUL)

ROOM_CODE_ADDED = NO
SQLITE_CODE_ADDED = NO
STORE_MESSAGE_DURABILITY_ADDED = NO (Phase F)
STARTUP_RESTORE_ADDED = NO (Phase G)
DANGLING_REPAIR_ADDED = NO (Phase H)
TRANSCRIPT_REPROJECTION_ADDED = NO (Phase I)

PRODUCTION_NOOP = TEMPORARY (ChatCompositionRoot -> MessageDurability.Noop;
                 Phase F pending, as frozen)

PRODUCTION_CODE_CHANGED = YES (2 new ag-runtime persistence files +
                 ag-runtime/build.gradle.kts)
TEST_CODE_CHANGED = YES (3 new ag-runtime test files)
GRADLE_CHANGED = YES (ag-runtime only: +kotlinx-serialization-json 1.8.0,
                 +kotlin test-junit5)

TASK_OWNED_FILES = ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/{TopicSessionStore,Records,MessageSerializer}.kt
                   ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/{InMemoryTopicSessionStore,TopicSessionStoreContractTest,MessageSerializerTest}.kt
                   ag-runtime/build.gradle.kts
                   docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (git diff --check clean apart from pre-existing CRLF
                 warnings; ag-runtime/build.gradle.kts diff = only the two
                 intended dependency additions; no format churn)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0E
                   (Room schema + DAO atomic append + explicit WAL +
                    Room integration tests; then 0F StoreMessageDurability)

FINAL_VERDICT = PASS — PHASE_C_D_STORE_CONTRACT_AND_SERIALIZER_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

# AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0E FINAL REPORT

```text
START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (COMMIT = NO)
BRANCH = main

IMPLEMENTATION_0_INITIAL_BASELINE = 46 files (42 pre-existing + 2 architecture docs)
0E_START_BASELINE = 66 files (measured fresh this round; NOT copied from prior reports)
0E_END_BASELINE = 69 files (+4 production persistence files, +1 Room test file,
                 +root build.gradle.kts mod, +app build.gradle.kts mod, +report update;
                 no pre-existing file lost)

PRE_EXISTING_DIRTY_FILES_PRESERVED = YES (git diff --check: only pre-existing CRLF
                 warnings; gradle diffs = additions only, no hunk loss)

PHASE_A_STATUS = COMPLETED
PHASE_B_STATUS = COMPLETED
PHASE_C_STATUS = COMPLETED
PHASE_D_STATUS = COMPLETED
PHASE_E_STATUS = COMPLETED

ROOM_VERSION = 2.7.2
ROOM_COMPILER = room-compiler 2.7.2 via KSP 2.1.0-1.0.29
KSP_OR_KAPT = KSP
ROOM_DEPENDENCY_CHANGES = root build.gradle.kts (+ksp plugin), app/build.gradle.kts
                 (+ksp apply, +room-runtime/ktx/compiler 2.7.2, +robolectric 4.13,
                  +androidx.test:core 1.6.1, +junit4 4.13.2, +junit-vintage-engine)
                 No Kotlin/AGP/Gradle/JDK upgrade.

DATABASE_FILE = topic_sessions.db
DATABASE_NAME = topic_sessions.db
DATABASE_VERSION = 1
DATABASE_LOCATION = Android app-private database directory (Room default)

DATABASE_JOURNAL_MODE_CONFIG = EXPLICIT (WalOpenHelperFactory.setWriteAheadLoggingEnabled(true))
DATABASE_JOURNAL_MODE_RUNTIME = wal (verified via PRAGMA journal_mode, file-backed)

ROOM_SCHEMA_EXPORT = true (exportSchema=true; schema JSON emitted to build output)

TOPIC_SESSION_ENTITY = app/.../persistence/Entities.kt : TopicSessionEntity
MESSAGE_ENTITY = app/.../persistence/Entities.kt : MessageEntity
DAO_FILES = app/.../persistence/Daos.kt : TopicSessionDao + MessageDao
DATABASE_CLASS = app/.../persistence/TopicSessionDatabase.kt : TopicSessionDatabase
ROOM_STORE_FILE = app/.../persistence/RoomTopicSessionStore.kt : RoomTopicSessionStore

TABLE_COUNT = 2

TOPIC_SESSION_SCHEMA = PASS (id TEXT PK, created_at, last_used_at, model, provider, title;
                 INDEX last_used_at)
MESSAGE_SCHEMA = PASS (id TEXT PK, topic_session_id, sequence, type, payload_json, created_at)

FOREIGN_KEY = PASS (topic_session_id -> topic_sessions.id; PRAGMA foreign_keys=1)
CASCADE_DELETE = PASS (ROOM-T14)
UNIQUE_SESSION_SEQUENCE = PASS (UNIQUE INDEX; ROOM-T11)
MESSAGE_INDEX = PASS (INDEX topic_session_id, sequence — unique)
LAST_USED_INDEX = PASS (INDEX last_used_at)

TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO

CREATE_SESSION_BEHAVIOR = insert row; duplicate id -> PersistenceException (ABORT, no overwrite)
LOAD_SESSION_BEHAVIOR = id exists -> record; missing -> null
LOAD_LATEST_BEHAVIOR = ORDER BY last_used_at DESC, created_at DESC, id LIMIT 1 (DB-side)
LOAD_MESSAGES_ORDER = WHERE topic_session_id=? ORDER BY sequence ASC (DB-side)

MISSING_SESSION_LOAD_BEHAVIOR = null (loadSession/loadMessages)
MISSING_SESSION_APPEND_BEHAVIOR = SessionNotFoundException

APPEND_MESSAGE_RETURN_TYPE = MessageRecord (with store-assigned sequence)

SEQUENCE_OWNER = PERSISTENCE_TRANSACTION
RUNTIME_PREALLOCATES_SEQUENCE = NO
FIRST_SEQUENCE = 0

APPEND_TRANSACTION = nextSequence + INSERT + touchLastUsed in ONE withTransaction
SEQUENCE_ALLOCATION = ATOMIC_WITH_INSERT
LAST_USED_UPDATE_RULE = only on successful durable append (reads/metadata excluded)

UPDATE_MODEL_PROVIDER_METADATA_BEHAVIOR = UPDATE model/provider only; last_used_at untouched;
                 missing session -> SessionNotFoundException

MESSAGE_SERIALIZER_REUSED = YES
SECOND_SERIALIZER_ADDED = NO
PERSISTENCE_TRUNCATES_TOOL_OUTPUT = NO (200k round-trip verified)

ROOM_T1 = PASS
ROOM_T2 = PASS
ROOM_T3 = PASS
ROOM_T4 = PASS
ROOM_T5 = PASS
ROOM_T6 = PASS
ROOM_T7 = PASS (rollback atomicity: forced-rollback withTransaction leaves no row +
             last_used unchanged; see NOTE in progress report re: collision forcing)
ROOM_T8 = PASS
ROOM_T9 = PASS
ROOM_T10 = PASS
ROOM_T11 = PASS
ROOM_T12 = PASS
ROOM_T13 = PASS
ROOM_T14 = PASS

ATOMIC_SEQUENCE_CONCURRENCY_TEST = PASS
CONCURRENT_APPEND_COUNT = 16
CONCURRENT_SEQUENCE_RESULT = exactly 0..15, 16 rows, unique + contiguous

ROLLBACK_TEST = PASS (uncommitted transaction leaves no durable row)
WAL_RUNTIME_TEST = PASS (journal_mode = wal)
FILE_BACKED_REOPEN_TEST = PASS (close + reopen same file, data survives)
SERIALIZER_ROOM_INTEGRATION_TEST = PASS (all persisted message variants round-trip through Room:
                 User, Model+multi-ToolCall, ToolResult success, ToolResult INTERRUPTED_UNKNOWN)

AG_CORE_TESTS = PASS (41, 0 failures)
AG_RUNTIME_TESTS = PASS (49, 0 failures)
APP_TESTS = PASS (130, 0 failures = 109 pre-existing + 21 Room)
ASSEMBLE_DEBUG = PASS

PRODUCTION_NOOP = TEMPORARY / UNCHANGED (ChatCompositionRoot -> MessageDurability.Noop;
                 Phase F pending)
STORE_MESSAGE_DURABILITY_ADDED = NO
STARTUP_RESTORE_ADDED = NO
DANGLING_REPAIR_ADDED = NO
TRANSCRIPT_REPROJECTION_ADDED = NO

CORE_ROOM_DEPENDENCY = NONE
CORE_SQLITE_DEPENDENCY = NONE
AG_RUNTIME_ROOM_DEPENDENCY = NONE (ag-runtime depends only on ag-core + kotlinx;
                 Room lives in :app)

PRODUCTION_CODE_CHANGED = YES (4 new :app persistence files + 2 gradle files)
TEST_CODE_CHANGED = YES (1 new Room integration test file, 21 tests)
GRADLE_CHANGED = YES (root + app; minimal, no upgrades)

TASK_OWNED_FILES =
  app/src/main/java/com/androidagent/app/persistence/{Entities,Daos,TopicSessionDatabase,RoomTopicSessionStore}.kt
  app/src/test/java/com/androidagent/app/persistence/RoomTopicSessionStoreTest.kt
  build.gradle.kts (root)
  app/build.gradle.kts
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (git diff --check: only pre-existing CRLF warnings; gradle diffs
                 are additions-only; no format churn, no pre-existing hunk loss)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F
                   (StoreMessageDurability adapter + cancellation shielding +
                    deterministic COMMITTED/NOT_COMMITTED + replace production Noop +
                    D1/D2/D3 persistence integration tests)

FINAL_VERDICT = PASS — PHASE_E_ROOM_SCHEMA_ATOMIC_STORE_AND_WAL_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## 12. Phase F FINAL REPORT — StoreMessageDurability Production Wiring + Cancellation-Safe Deterministic Commit

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
BRANCH = main

0F_START_BASELINE = 69 dirty (32 modified + 34 untracked)
0F_END_BASELINE = 70 dirty (33 modified + 34 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES
NEW_FILES_THIS_ROUND = 2 (StoreMessageDurability.kt production,
                         StoreMessageDurabilityTest.kt test)
MODIFIED_FILES_THIS_ROUND = 2 (ChatCompositionRoot.kt, SessionHolder.kt)

PHASE_A_STATUS = COMPLETED
PHASE_B_STATUS = COMPLETED
PHASE_C_STATUS = COMPLETED
PHASE_D_STATUS = COMPLETED
PHASE_E_STATUS = COMPLETED
PHASE_F_STATUS = COMPLETED

STORE_MESSAGE_DURABILITY_FILE =
  app/src/main/java/com/androidagent/app/persistence/StoreMessageDurability.kt
STORE_MESSAGE_DURABILITY_OWNER = :app
  (RoomTopicSessionStore lives in :app; the adapter depends only on
   ag-core MessageDurability + ag-runtime TopicSessionStore, but placing
   it in :app keeps the Room-backed chain in one module. ag-core does
   NOT depend on ag-runtime; ag-runtime does NOT depend on Room.)

PRODUCTION_DURABILITY_CHAIN =
  AgentCore
    -> MessageDurability (interface, ag-core)
    -> StoreMessageDurability (:app, cancellation-shielded adapter)
    -> TopicSessionStore (interface, ag-runtime)
    -> RoomTopicSessionStore (:app, atomic append transaction)
    -> Room -> SQLite -> topic_sessions.db (WAL)

PRODUCTION_NOOP = NO

TOPIC_SESSION_CREATION_OWNER =
  ChatCompositionRoot.sessionIdFactory (Host/Composition Root)
  The factory runs createSession synchronously (blocking on a one-shot
  CountDownLatch, 15s timeout) BEFORE returning the session id to the
  Runtime. This is the minimal, explicit session-row bootstrap required
  for production persistence wiring. It is NOT Phase G startup restore.

SESSION_ROW_EXISTS_BEFORE_D1 = YES
  (by construction: the factory blocks until the row is committed or
   failed; a failed bootstrap throws at the next D1 append as a
   DurabilityCommitException — never a silent ephemeral mode)

CLOCK_OWNER = StoreMessageDurability (injected () -> Long;
  production = System.currentTimeMillis; tests = deterministic fake)

CANCELLATION_SHIELD_STRATEGY =
  ensureActive() before the transaction (Case I1: cancellation wins,
  NOT_COMMITTED); withContext(NonCancellable) around appendMessage
  (Cases I2-I5: transaction runs to deterministic COMMIT or ROLLBACK).
  The committed fact is returned to the Core — the Core and the DB agree.
  A caller cancellation during the transaction is NOT converted into a
  DurabilityCommitException (the row IS committed).

SHIELD_SCOPE =
  ONLY the persistence transaction / outcome determination. The model
  call, tool execution, and the whole Run remain fully
  cancellation-responsive.

COMMIT_OUTCOME_MODEL =
  COMMITTED (normal return) / NOT_COMMITTED (DurabilityCommitException)
  — deterministic, never MAYBE_COMMITTED

AMBIGUOUS_COMMIT_OUTCOME_ALLOWED = NO

CANCELLATION_BEFORE_COMMIT = NOT_COMMITTED (CancellationException
  propagates; nothing was written)
CANCELLATION_DURING_COMMIT = transaction completes to COMMIT or
  ROLLBACK; outcome is deterministic
CANCELLATION_AFTER_COMMIT = COMMITTED (the committed fact is returned;
  the Run's normal cancellation path applies at the next checkpoint)

DURABILITY_EXCEPTION_MAPPING =
  MessageSerializationException -> DurabilityCommitException
  MessageDecodeException       -> DurabilityCommitException
  SessionNotFoundException     -> DurabilityCommitException
  PersistenceException         -> DurabilityCommitException
  (Core-facing: ONLY DurabilityCommitException; safeCause() extracts
   the cause class name, never the raw message)

D1_USER_DURABILITY = PASS
D1_MODEL_CALL_AFTER_COMMIT = PASS
D2_MODEL_DURABILITY = PASS
D2_TOOL_DISPATCH_AFTER_COMMIT = PASS
D2_FINAL_ANSWER_BEFORE_SUCCESS = PASS
D3_TOOL_RESULT_DURABILITY = PASS
D3_NEXT_MODEL_AFTER_COMMIT = PASS
DURABILITY_EXACTLY_ONCE = PASS
  (single in-process run attempt; CANCEL-F5/F6 verify no duplicate
   append after cancellation)

SEQUENCE_OWNER = PERSISTENCE
RUNTIME_PREALLOCATES_SEQUENCE = NO
SECOND_SERIALIZER = NO
LAST_USED_RULE_PRESERVED = YES
  (only the Room store transaction touches last_used_at; the adapter
   never calls touchLastUsed)

CANCEL_F1 = PASS (cancellation before commit -> no durable row)
CANCEL_F2 = PASS (cancellation during in-flight commit -> committed
               deterministically, no DurabilityCommitException)
CANCEL_F3 = PASS (commit succeeds with pending cancellation -> committed,
               not a durability failure)
CANCEL_F4 = PASS (transaction fails with pending cancellation -> not
               committed, no row)
CANCEL_F5 = PASS (no duplicate append after cancellation)
CANCEL_F6 = PASS (committed messages appear exactly once, unique
               sequences)

AG_CORE_TESTS = 41 (0 failures)
AG_RUNTIME_TESTS = 49 (0 failures)
APP_TESTS = 147 (0 failures = 130 pre-existing + 17 StoreMessageDurability)
ASSEMBLE_DEBUG = PASS

STARTUP_RESTORE_ADDED = NO
DANGLING_REPAIR_ADDED = NO
TRANSCRIPT_REPROJECTION_ADDED = NO

ROOM_SCHEMA_CHANGED = NO
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO
KOTLIN_UPGRADED = NO
AGP_UPGRADED = NO
GRADLE_UPGRADED = NO
ROOM_UPGRADED = NO
KSP_UPGRADED = NO

PRODUCTION_FILES_CHANGED = 2 (StoreMessageDurability.kt new;
  ChatCompositionRoot.kt + SessionHolder.kt modified)
TEST_FILES_CHANGED = 1 (StoreMessageDurabilityTest.kt new, 17 tests)
DOC_FILES_CHANGED = 1 (this file)

UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (no format churn; all changes are Phase F scoped)

FINAL_VERDICT = PASS — PHASE_F_PRODUCTION_WIRING_AND_CANCELLATION_COMPLETE

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G
  (startup restore: loadLatestSession + restoreHistory into AgentCore;
   dangling tool detection + synthetic repair)

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## 13. Phase F.NORMALIZE-1 FINAL REPORT — TopicSession Creation Ownership / Blocking Bootstrap Normalization

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F.NORMALIZE-1 FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
BRANCH = main

0F_NORMALIZE_START_BASELINE = 70 dirty (33 modified + 34 untracked)
0F_NORMALIZE_END_BASELINE = 73 dirty (35 modified + 35 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES
NEW_FILES_THIS_ROUND = 1 (RuntimeSessionCreationTest.kt)
MODIFIED_FILES_THIS_ROUND = 8 (AgentRuntime.kt, TestSupport.kt,
  AgentRuntimeTest.kt, AgentRuntimeInterruptTest.kt,
  HostStopStateTest.kt, ProviderProtocolTest.kt,
  ChatCompositionRoot.kt, SessionHolder.kt, ChatActivity.kt)

OLD_SESSION_CREATION_OWNER = ChatCompositionRoot.sessionIdFactory
  (blocking CountDownLatch bootstrap, 15s timeout)
NEW_SESSION_CREATION_OWNER = AgentRuntime (initialize / newSession)

SESSION_ID_FACTORY_FILE = ChatCompositionRoot.kt (line: sessionIdFactory = { "session-" + UUID.randomUUID() })
SESSION_ID_FACTORY_SEMANTICS = PURE ID GENERATION (no DB side effect)
SESSION_ID_FACTORY_DB_SIDE_EFFECT = NO

OLD_BLOCKING_MECHANISM = CountDownLatch + 15s blocking timeout in sessionIdFactory
COUNTDOWNLATCH_BOOTSTRAP_PRESENT = NO (removed)
RUNBLOCKING_BOOTSTRAP_PRESENT = NO (in production; test-only runBlocking in SessionHolder for async bridging)

AGENT_RUNTIME_FILE = ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt
AGENT_RUNTIME_STORE_DEPENDENCY = TopicSessionStore (required runtime dependency; see 0F.NORMALIZE-1B)
AGENT_RUNTIME_ROOM_DEPENDENCY = NO

DURABLE_CREATE_API = AgentRuntime.initialize() [suspend]
DURABLE_CREATE_IS_SUSPEND = YES
DURABLE_CREATE_CLOCK_OWNER = AgentRuntime (injected nowMillis: () -> Long)

CREATE_ORDER = factory() → store.createSession() → sessionId = id
SESSION_ROW_EXISTS_BEFORE_CURRENT = YES
SESSION_ROW_EXISTS_BEFORE_D1 = YES

CREATE_FAILURE_BEHAVIOR = exception propagates; no current session; no D1
CREATE_FAILURE_MAKES_SESSION_CURRENT = NO
CREATE_FAILURE_REACHES_D1 = NO

LAZY_CREATE_ON_D1 = NO

CHAT_COMPOSITION_ROOT_ROLE = dependency assembly only
CHAT_COMPOSITION_ROOT_CALLS_CREATESESSION = NO

SESSIONHOLDER_ROLE_CHANGED = YES (added suspend initialize() + made newSession() suspend)

STORE_MESSAGE_DURABILITY_SEMANTICS_CHANGED = NO
PRODUCTION_NOOP = NO

ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO

PHASE_G_STARTUP_RESTORE_ADDED = NO
DANGLING_REPAIR_ADDED = NO
TRANSCRIPT_REPROJECTION_ADDED = NO

CREATE_T1 = PASS (initialize calls factory + store once; currentSessionId set)
CREATE_T2 = PASS (current session not usable before store.createSession completes)
CREATE_T3 = PASS (store failure → no current session, error propagated, no D1)
NO_D1_BEFORE_CREATE_TEST = PASS (send with no session → Failure, no model call)

STORE_MESSAGE_DURABILITY_TESTS = PASS (17/17)
CANCELLATION_TESTS = PASS (CANCEL-F1..F6)

AG_CORE_TESTS = 41 (0 failures)
AG_RUNTIME_TESTS = 54 (0 failures = 49 pre-existing + 5 new RuntimeSessionCreationTest)
APP_TESTS = 147 (0 failures)
ASSEMBLE_DEBUG = PASS

PRODUCTION_CODE_CHANGED = YES (AgentRuntime.kt, ChatCompositionRoot.kt,
  SessionHolder.kt, ChatActivity.kt)
TEST_CODE_CHANGED = YES (TestSupport.kt, AgentRuntimeTest.kt,
  AgentRuntimeInterruptTest.kt, HostStopStateTest.kt,
  ProviderProtocolTest.kt, RuntimeSessionCreationTest.kt new)
GRADLE_CHANGED = NO

TASK_OWNED_FILES =
  ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeTest.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeInterruptTest.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/RuntimeSessionCreationTest.kt (NEW)
  app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt
  app/src/main/java/com/androidagent/app/SessionHolder.kt
  app/src/main/java/com/androidagent/app/ChatActivity.kt
  app/src/test/java/com/androidagent/app/HostStopStateTest.kt
  provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/ProviderProtocolTest.kt
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (only pre-existing CRLF warnings; no format churn)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G
  (startup restore: loadLatestSession + restoreHistory into AgentCore;
   dangling tool detection + synthetic repair; the Runtime now owns
   durable create, so 0G can naturally do create-or-restore in the
   same lifecycle owner)

FINAL_VERDICT = PASS — PHASE_F_NORMALIZE-1_CREATION_OWNERSHIP_AND_BLOCKING_REMOVAL_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## 14. Phase F.NORMALIZE-1B FINAL REPORT — Required TopicSessionStore / Remove Nullable Runtime Persistence Path

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F.NORMALIZE-1B FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
BRANCH = main

0F_NORMALIZE_1B_START_BASELINE = 73 dirty (35 modified + 35 untracked)
0F_NORMALIZE_1B_END_BASELINE = 73 dirty (35 modified + 35 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

AGENT_RUNTIME_FILE = ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt

OLD_TOPIC_SESSION_STORE_TYPE = TopicSessionStore? (nullable, default null)
NEW_TOPIC_SESSION_STORE_TYPE = TopicSessionStore (required, non-null)

TOPIC_SESSION_STORE_REQUIRED = YES
TOPIC_SESSION_STORE_NULLABLE = NO
NULL_STORE_ALLOWED = NO

EPHEMERAL_RUNTIME_PATH = NO (removed; no null sentinel, no optional persistence)

INITIALIZE_STORE_CALL = mandatory (topicSessionStore.createSession always called)
NEWSESSION_STORE_CALL = mandatory (delegates to initialize → always durable create)

SESSION_ROW_EXISTS_BEFORE_CURRENT = YES
SESSION_ROW_EXISTS_BEFORE_D1 = YES

CREATE_FAILURE_BEHAVIOR = exception propagates; no current session; no D1
CREATE_FAILURE_MAKES_CURRENT = NO
CREATE_FAILURE_REACHES_D1 = NO

SESSION_ID_FACTORY_PURE = YES

TEST_STORE_IMPLEMENTATION = FakeTopicSessionStore (ag-runtime test) +
  NoopStore (app HostStopStateTest) + inline object (provider test)
TEST_FAKE_STORE_LOCATION = test source sets only
PRODUCTION_FAKE_STORE = NO

NULLABLE_STORE_GREP_RESULT = 0 matches in ag-runtime/app production
  (SessionHolder.kt:69 is an internal @Volatile lazy-cache field, not the
   Runtime contract; it caches the process-scoped store for reuse)
EPHEMERAL_WORDING_GREP_RESULT = 0 stale "ephemeral test mode" references in
  production code (one doc comment in AgentRuntime says "no ephemeral mode")

CHAT_COMPOSITION_ROOT_STORE = RoomTopicSessionStore (always supplied)
SESSIONHOLDER_STORE_OWNERSHIP = process-scoped lazy cache (unchanged)

STORE_MESSAGE_DURABILITY_SEMANTICS_CHANGED = NO
PRODUCTION_NOOP = NO

ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO

PHASE_G_STARTUP_RESTORE_ADDED = NO
DANGLING_REPAIR_ADDED = NO
TRANSCRIPT_REPROJECTION_ADDED = NO

STORE_REQ_T1 = PASS (compile-level: AgentRuntime constructor requires
  TopicSessionStore — no default, no nullable; source evidence: AgentRuntime.kt:62)
STORE_REQ_T2 = PASS (initialize always calls topicSessionStore.createSession;
  no if/let/null branch; source evidence: AgentRuntime.kt:172)
STORE_REQ_T3 = PASS (CREATE-T3: store failure → no session current, no D1)
STORE_REQ_T4 = PASS (all test constructions explicitly pass a fake store:
  TestSupport.runtimeOf/runtimeWithStore, AgentRuntimeInterruptTest.fixture,
  RuntimeSessionCreationTest.makeRuntime, HostStopStateTest NoopStore,
  ProviderProtocolTest inline object)
STORE_REQ_T5 = PASS (newSession delegates to initialize → always durable create;
  no in-memory-only branch)

RUNTIME_CREATION_TESTS = PASS (5/5 RuntimeSessionCreationTest)
STORE_MESSAGE_DURABILITY_TESTS = PASS (17/17)
CANCELLATION_TESTS = PASS (CANCEL-F1..F6)

AG_CORE_TESTS = 41 (0 failures)
AG_RUNTIME_TESTS = 54 (0 failures)
APP_TESTS = 147 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

PRODUCTION_CODE_CHANGED = YES (AgentRuntime.kt: nullable → required,
  removed null branch in initialize, cleaned duplicate doc comments)
TEST_CODE_CHANGED = YES (TestSupport.kt: runtimeOf now returns store;
  AgentRuntimeInterruptTest.kt: explicit FakeTopicSessionStore in fixture;
  HostStopStateTest.kt: NoopStore added + passed to constructor;
  ProviderProtocolTest.kt: inline store object;
  RuntimeSessionCreationTest.kt: makeRuntime requires store)
GRADLE_CHANGED = NO

TASK_OWNED_FILES =
  ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeInterruptTest.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/RuntimeSessionCreationTest.kt
  app/src/test/java/com/androidagent/app/HostStopStateTest.kt
  provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/ProviderProtocolTest.kt
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (no format churn; all changes Phase F.NORMALIZE-1B scoped)

PHASE_A_F_STATUS = COMPLETED
0F_NORMALIZE_STATUS = COMPLETED (NORMALIZE-1 + NORMALIZE-1B both closed)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G
  (Runtime startup lifecycle: loadLatestSession; if none → durable create;
   if found → loadMessages + validate + dangling repair + Core restore → READY)

FINAL_VERDICT = PASS — REQUIRED_TOPIC_SESSION_STORE_AND_RUNTIME_DURABILITY_CONTRACT_NORMALIZED

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## 15. Phase 0G.BLOCKER-FIX-1 FINAL REPORT — Same-Process Interrupt Durable Closeout

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G.BLOCKER-FIX-1 FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
BRANCH = main

BLOCKER_FIX_START_BASELINE = 73 dirty (35 modified + 35 untracked)
BLOCKER_FIX_END_BASELINE = 74 dirty (36 modified + 35 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

PHASE_A_F_STATUS = COMPLETED
0F_NORMALIZE_STATUS = COMPLETED
PHASE_G_STATUS = RE-OPENED (blocker fixed; Phase G proper can now proceed)

BLOCKER = SAME_PROCESS_INTERRUPT_CORE_ONLY_SYNTHETIC
  (confirmed in Phase G audit; AgentCore.kt appendSyntheticToolResults
   called session.append() without messageDurability.commit())

SYNTHETIC_HELPER_OLD =
  private fun appendSyntheticToolResults(session, pairing)
  → session.append(ToolResultMessage(...)) only (Core memory)

SYNTHETIC_HELPER_NEW =
  private suspend fun appendSyntheticToolResults(session, pairing, sink, terminalClaimed)
  → messageDurability.commit(session.id, synthetic) THEN session.append(synthetic)
  → DURABLE_FIRST → CORE_SECOND (matching D1/D2/D3)

SYNTHETIC_CALL_SITES =
  1. driveLoop provider-call CancellationException catch (AgentCore.kt ~line 340)
  2. driveLoop tool-dispatch CancellationException catch (AgentCore.kt ~line 450)
  Both now call interruptCloseout() which wraps the durable synthetic
  reconciliation in withContext(NonCancellable).

NONCANCELLABLE_SCOPE = interruptCloseout() ONLY
  (NOT the whole Run, NOT the ModelCall, NOT the tool execution,
   NOT the normal D1/D2/D3 path)

FAIL_CLOSED =
  If messageDurability.commit() fails during synthetic closeout:
  → RunFailure.DurabilityFailure(boundary="SYNTHETIC") returned
  → Run does NOT report Interrupted with inconsistent Core/DB state
  → terminalClaimed protects against double-terminal

SYNTHETIC_ORDER = DURABLE_FIRST → CORE_SECOND (frozen)

LIVE_CORE_HISTORY_EQ_DURABLE_HISTORY = YES (after fix)

AG_CORE_GRADLE_CHANGE =
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  added (was testImplementation only). Minimal: withContext + NonCancellable
  for the interrupt closeout. AG Core remains Android-free / pure JVM.

AG_CORE_TESTS = 41 (0 failures)
AG_RUNTIME_TESTS = 54 (0 failures)
APP_TESTS = 147 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

PRODUCTION_CODE_CHANGED = YES (AgentCore.kt: appendSyntheticToolResults
  made suspend + durable-first; interruptCloseout added; 2 call sites
  updated; ag-core/build.gradle.kts: +implementation coroutines)
TEST_CODE_CHANGED = NO (existing I1-I15/M1-M3 interrupt tests all pass
  unchanged — the fix is transparent to them)
GRADLE_CHANGED = YES (ag-core: +implementation kotlinx-coroutines-core)

TASK_OWNED_FILES =
  ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
  ag-core/build.gradle.kts
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS

SUCCESS_CLOSEOUT_STABLE_BOUNDARY = PASS
  (synthetic ToolResults are now durably committed before Core append;
   LIVE_CORE_HISTORY == DURABLE_HISTORY maintained after successful interrupt)

FAILURE_CLOSEOUT_STABLE_BOUNDARY = PENDING
  (synthetic durability failure may leave unresolved dangling ToolCall;
   Session reuse denial NOT yet implemented — resolved in NORMALIZE-1 below)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G.BLOCKER-FIX-1.NORMALIZE-1
  (interrupt closeout failure quarantine + dedicated invariant tests)

FINAL_VERDICT = PASS — SAME_PROCESS_INTERRUPT_DURABLE_CLOSEOUT_COMPLETE
  (success path only; failure path quarantine deferred to NORMALIZE-1)

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## §16 0G.BLOCKER-FIX-1.NORMALIZE-1 FINAL REPORT

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G.BLOCKER-FIX-1.NORMALIZE-1
FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

NORMALIZE_START_BASELINE = 74 dirty files (36 modified + 35 untracked + 3 LF warnings)
NORMALIZE_END_BASELINE = 75 dirty files (36 modified + 36 untracked + 3 LF warnings)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

OLD_FAILURE_BEHAVIOR =
  appendSyntheticToolResults commit failure → RunOutcome.Failure(DurabilityFailure)
  BUT Session remained reusable (tryBeginRun would admit the next Run)
  → dangling ToolCall in durable history with no reconciliation path

NEW_FAILURE_BEHAVIOR =
  appendSyntheticToolResults commit failure → session.markReconciliationRequired()
  → RunOutcome.Failure(DurabilityFailure("SYNTHETIC", cause))
  → subsequent tryBeginRun DENIED (SessionBusy)
  → no D1, no ModelCall, no ToolCall until restoreSession replaces the Session

SESSION_RECONCILIATION_STATE = reconciliationRequired (Boolean, default false)
STATE_OWNER = Session (private var, guarded by Session's monitor)
STATE_SYNCHRONIZATION = synchronized(monitor) — same as activeRun / messages

REPAIR_FAILURE_MARK_POINT =
  AgentCore.appendSyntheticToolResults: on DurabilityCommitException,
  session.markReconciliationRequired() is called BEFORE
  failDurability(...) returns the terminal RunOutcome.Failure.

TRY_BEGIN_RUN_WHEN_RECONCILIATION_REQUIRED = DENIED (returns false)
  (same rejection path as activeRun; caller sees SessionBusy)

NEXT_RUN_AFTER_REPAIR_FAILURE = DENIED (SessionBusy)
NEXT_RUN_D1 = NO
NEXT_RUN_MODEL_CALL = NO
NEXT_RUN_TOOL_CALL = NO

SUCCESS_CLOSEOUT_SESSION_REUSABLE = YES
  (reconciliationRequired remains false; tryBeginRun admits next Run)

PARTIAL_FAILURE_BEHAVIOR =
  A commit PASS → A appended Core; B commit FAIL → markReconciliationRequired
  Core history == durable committed ledger (both have A's synthetic only)
  B is in neither Core nor durable (consistent)

CORE_EQUALS_DURABLE_AFTER_PARTIAL_FAILURE = YES

RESTORESESSION_QUARANTINE_BEHAVIOR =
  AgentCore.restoreSession creates a NEW Session instance (fresh
  reconciliationRequired = false) → old quarantined instance is replaced
  → next Run is admitted. No new public API needed.

SYNTHETIC_ORDER = DURABLE_FIRST → CORE_SECOND (commit → append)
NONCANCELLABLE_SCOPE = interruptCloseout (withContext(NonCancellable))

INTERRUPT_N1 = PASS (success ordering: commit before Core append)
INTERRUPT_N2 = PASS (cancelled context: NonCancellable shield works)
INTERRUPT_N3 = PASS (commit failure → no Core append, DurabilityFailure)
INTERRUPT_N4 = PASS (session quarantined, next Run denied, no D1/model/tool)
INTERRUPT_N5 = PASS (partial: A committed+appended, B in neither, Core==durable)
INTERRUPT_N6 = PASS (restoreSession clears quarantine, next Run admitted)
INTERRUPT_N7 = PASS (successful closeout → session reusable)
INTERRUPT_N8 = PASS (existing ToolResult not duplicated)
INTERRUPT_N9 = PASS (A/B/C each exactly once, order preserved)
INTERRUPT_N10 = PASS (no dangling → no synthetic, no quarantine)

EXISTING_INTERRUPT_TESTS = PASS (I1-I15, M1-M3 all pass unchanged)
D1_D2_D3 = PASS
CANCEL_F1_F6 = PASS

AG_CORE_TESTS = 52 (0 failures)
AG_RUNTIME_TESTS = 54 (0 failures)
APP_TESTS = 147 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

AG_RUNTIME_PRODUCTION_CHANGED = NO
STORE_MESSAGE_DURABILITY_CHANGED = NO
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO
ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO
GRADLE_CHANGED = NO

PHASE_G_STARTUP_FEATURES_ADDED = NO

TASK_OWNED_FILES =
  ag-core/src/main/kotlin/com/androidagent/core/Session.kt
  ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
  ag-core/src/test/kotlin/com/androidagent/core/InterruptCloseoutTest.kt
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (only CRLF warnings, pre-existing)

BLOCKER_STATUS = CLOSED
  (SAME_PROCESS_INTERRUPT_CORE_ONLY_SYNTHETIC is fully resolved:
   success closeout = durable-first; failure closeout = quarantine;
   partial failure = Core==durable + quarantine; restore = recovery)

SAME_PROCESS_INTERRUPT_STABLE_BOUNDARY = PASS
  (includes: success closeout, failure closeout, partial failure,
   subsequent Run admission, restore recovery)

PHASE_G_STATUS = READY_TO_RESUME

RECOMMENDED_NEXT =
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G
  (re-opened: process-scoped startup create-or-restore +
   cross-process dangling repair + Core hydration + idempotence +
   Room integration tests. The blocker is now fully closed.)

FINAL_VERDICT = PASS — INTERRUPT_CLOSEOUT_FAILURE_QUARANTINE_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## §17 PHASE G FINAL REPORT

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

0G_START_BASELINE = 75 dirty files (36 modified + 36 untracked)
0G_END_BASELINE = 77 dirty files (38 modified + 39 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

PHASE_A_F_STATUS = CLOSED
0F_NORMALIZE_STATUS = CLOSED
INTERRUPT_BLOCKER_STATUS = CLOSED

INITIALIZE_OLD_SEMANTICS = durable session creation only (no restore)
INITIALIZE_NEW_SEMANTICS = process startup create-or-restore

NEWSESSION_SEMANTICS = always create brand-new durable session

STARTUP_OWNER = AgentRuntime
STARTUP_STATE = UNINITIALIZED / INITIALIZING / READY / FAILED
STARTUP_SYNCHRONIZATION = Mutex (kotlinx.coroutines, per-instance)

INITIALIZE_IDEMPOTENT_AFTER_SUCCESS = YES
CONCURRENT_INITIALIZE_SINGLE_FLIGHT = YES

CURRENT_SESSION_PUBLICATION_POINT = after load+validate+repair+reload+revalidate+Core restore

FRESH_STORE_PATH = loadLatestSession null → createNewDurableSession
EXISTING_SESSION_PATH = loadMessages → validate → repair → reload → revalidate → restoreSession

LOAD_LATEST_SESSION = TopicSessionStore.loadLatestSession()
LOAD_MESSAGES = TopicSessionStore.loadMessages(sessionId)

SEQUENCE_VALIDATION_OWNER = RoomTopicSessionStore.loadMessages (Room implementation)
SEQUENCE_VALIDATION_BEHAVIOR = explicit UnrecoverableSessionHistoryException("sequence_continuity_violation") → Runtime controlled fresh recovery

MALFORMED_HISTORY_BEHAVIOR = MessageDecodeException propagates → UNRECOVERABLE
UNKNOWN_TYPE_BEHAVIOR = MessageDecodeException → UNRECOVERABLE
MISSING_HISTORY_BEHAVIOR = UnrecoverableSessionHistoryException("session_history_inconsistent") → controlled fresh recovery (see §18 NORMALIZE-1)

CORRUPT_SESSION_PRESERVED = YES (no deletion, no destructive migration)
CONTROLLED_FRESH_SESSION_RECOVERY = YES (new session created, old preserved)

CANONICAL_SYNTHETIC_OWNER = AgentCore.syntheticInterruptedToolResult (companion)
CANONICAL_SYNTHETIC_SEMANTICS = IN_FLIGHT_INTERRUPTED_ERROR / NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR

DANGLING_DETECTION_OWNER = AgentRuntime.findDanglingToolCalls
DANGLING_DETECTION_ALGORITHM = ModelMessage.toolCalls minus ToolResultMessage.toolCallIds

DANGLING_REPAIR_ORDER = DURABLE_FIRST (append synthetic before Core hydrate)
DANGLING_REPAIR_COMMIT_SHIELD = ensureActive + withContext(NonCancellable) per append
DANGLING_REPAIR_RELOAD = YES (loadMessages again after repair)
DANGLING_REPAIR_REVALIDATE = YES (findDanglingToolCalls on reloaded history)

AUTO_TOOL_REPLAY = NO
STARTUP_TOOL_EXECUTION_COUNT = 0
STARTUP_MODEL_CALL_COUNT = 0

PARTIAL_REPAIR_BEHAVIOR = successfully appended synthetics remain; failure propagates
REPAIR_RETRY_BEHAVIOR = retry re-loads DB; already-repaired calls are not duplicated

CORE_RESTORE_CALL = AgentCore.restoreSession(sessionId, finalHistory)
RESTORE_TRIGGERS_MESSAGE_DURABILITY = NO

READ_RESTORE_TOUCHES_LAST_USED = NO
REPAIR_TOUCHES_LAST_USED = YES (via appendMessage normal path)

NEWSESSION_FAILURE_PRESERVES_OLD_CURRENT = YES

ACTIVITY_RECREATION_BEHAVIOR = idempotent initialize (same process, same Runtime)
SESSIONHOLDER_ROLE = process-scoped holder; delegates to Runtime.initialize()

STARTUP_G1 = PASS (fresh store → new session → Core empty → READY)
STARTUP_G2 = PASS (existing empty session → restored, no new creation)
STARTUP_G3 = PASS (existing history → restored exactly, no repersistence)
STARTUP_G4 = PASS (sequential initialize → idempotent, no duplicate I/O)
STARTUP_G5 = PASS (concurrent initialize → single-flight, same id)
STARTUP_G6 = PASS (loadLatest failure → no current, no READY)
STARTUP_G7 = PASS (loadMessages null → session-level UNRECOVERABLE; superseded by STARTUP-G7-R controlled recovery in §18)
STARTUP_G8 = PASS (recovery fresh create failure → no current / FAILED; §18)

VALIDATE_G1 = PASS (sequence gap → explicit UnrecoverableSessionHistoryException, Room integration test; Runtime controlled recovery in §18)
VALIDATE_G2 = PASS (starts-at-1 is a gap from 0 under the same continuity rule; Runtime recovery holds by the same rule — see §18)
VALIDATE_G3 = PASS (malformed payload → MessageDecodeException → Runtime controlled recovery, §18)
VALIDATE_G4 = PASS (unknown type → MessageDecodeException → Runtime controlled recovery, §18)
VALIDATE_G5 = PASS (valid empty history → restored as empty)

REPAIR_G1 = PASS (no dangling → zero synthetic writes)
REPAIR_G2 = PASS (one dangling → synthetic UNKNOWN durable → reload → restore)
REPAIR_G3 = PASS (mixed: B/C only repaired, A untouched)
REPAIR_G4 = PASS (existing synthetic not duplicated)
REPAIR_G5 = PASS (append failure → no Core restore, no current)
REPAIR_G6 = PASS (partial repair failure + retry idempotence; §18)
REPAIR_G7 = PASS (cancellation before repair append commits → no write; §18)
REPAIR_G8 = PASS (cancellation during repair append → deterministic commit; §18)
REPAIR_G9 = PASS (no tool replay, no model call during startup)

RESTORE_G1 = PASS (clean restore: N messages remain N)
RESTORE_G2 = PASS (K dangling → exactly N+K after repair)

NEW_G1 = PASS (newSession → durable B, current B, A preserved)
NEW_G2 = PASS (B create failure → current remains A)
NEW_G3 = PASS (newSession always new, never restores latest)

ROOM_RUNTIME_RESTORE_INTEGRATION = PASS
FILE_BACKED_REOPEN_RESTORE = PASS (process death → new process → same session)
FILE_BACKED_DANGLING_REPAIR = PASS (one synthetic UNKNOWN persisted)
SECOND_REOPEN_NO_DUPLICATE_REPAIR = PASS (synthetic count remains 1)
ROOM_SEQUENCE_CORRUPTION_TEST = PASS (gap 0,1,3 → explicit failure)

SAME_PROCESS_INTERRUPT_REGRESSION = PASS (InterruptCloseoutTest N1-N10)
D1_D2_D3 = PASS
CANCEL_F1_F6 = PASS

AG_CORE_TESTS = 52 (0 failures)
AG_RUNTIME_TESTS = 74 (0 failures)
APP_TESTS = 152 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

DEVICE_G_VERIFICATION = DEFERRED (JVM/Robolectric only this round)

TRANSCRIPT_REPROJECTION_ADDED = NO

ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO (version = 1)
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO

PHASE_H_DANGLING_REPAIR_SCOPE = COMPLETED_WITH_PHASE_G (MERGED_INTO_G)

PRODUCTION_CODE_CHANGED = YES
  (AgentCore.kt: canonical factory extraction;
   AgentRuntime.kt: full startup create-or-rewrite;
   RoomTopicSessionStore.kt: sequence validation)
TEST_CODE_CHANGED = YES
  (TestSupport.kt: enhanced FakeTopicSessionStore;
   StartupRestoreTest.kt: 20 new tests;
   PhaseGRoomIntegrationTest.kt: 5 new tests)
GRADLE_CHANGED = NO

TASK_OWNED_FILES =
  ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
  ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/StartupRestoreTest.kt
  app/src/main/java/com/androidagent/app/persistence/RoomTopicSessionStore.kt
  app/src/test/java/com/androidagent/app/persistence/PhaseGRoomIntegrationTest.kt
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (CRLF warnings only, pre-existing)

PHASE_G_STATUS = CLOSED

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0I
  (Transcript re-projection: Core restored history → UI visible)

FINAL_VERDICT = PASS — PROCESS_SCOPED_STARTUP_RESTORE_AND_CROSS_PROCESS_REPAIR_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## §18 0G.NORMALIZE-1 FINAL REPORT — Controlled Recovery Semantics + Missing Evidence + SSOT Normalization

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0G.NORMALIZE-1 FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

NORMALIZE_START_BASELINE = 76 dirty files (36 modified + 37 untracked)
NORMALIZE_END_BASELINE = 76 dirty files (36 modified + 37 untracked)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

PHASE_G_MAINLINE_STATUS = ACCEPTED (§17)
PHASE_G_NORMALIZE_STATUS = CLOSED (this section)

OLD_LOADMESSAGES_NULL_BEHAVIOR =
  loadMessages(sessionId) == null → PersistenceException("session_history_inconsistent")
  → initialize() FAILED, no current, NO recovery (startup stuck FAILED)

NEW_LOADMESSAGES_NULL_BEHAVIOR =
  loadMessages(sessionId) == null → UnrecoverableSessionHistoryException
  ("session_history_inconsistent") → doStartup catches → recoverWithFreshSession()
  → controlled fresh durable session → READY

SESSION_UNRECOVERABLE_CLASSIFICATION =
  NEW typed exception UnrecoverableSessionHistoryException(sessionId, category, cause)
  in ag-runtime persistence. Distinct from PersistenceException (backend/IO failure).
  - Session-level invalid history → UnrecoverableSessionHistoryException → controlled fresh recovery
  - Backend/operation failure → PersistenceException → FAIL CLOSED (no recovery)

MISSING_HISTORY_RECOVERY = PASS (UnrecoverableSessionHistoryException → fresh recovery)
SEQUENCE_INVALID_RECOVERY = PASS (Room throws UnrecoverableSessionHistoryException("sequence_continuity_violation") → Runtime fresh recovery)
MALFORMED_HISTORY_RECOVERY = PASS (Room propagates MessageDecodeException → Runtime classifies → fresh recovery)
UNKNOWN_TYPE_RECOVERY = PASS (Room propagates MessageDecodeException → Runtime classifies → fresh recovery)

CORRUPT_SESSION_PRESERVED = YES (no delete; loadSession returns the corrupt row)
CORRUPT_MESSAGES_PRESERVED = YES (message rows intact; verified via countBySession)
DESTRUCTIVE_RECOVERY = NO (no fallbackToDestructiveMigration; DATABASE_VERSION = 1)

CONTROLLED_FRESH_RECOVERY_ORDER =
  recoverWithFreshSession() = createNewDurableSession():
  createSession (durable) → Core restoreSession(empty) → publish (by initialize after return)
  Single helper, no forked recovery ordering.

RECOVERY_CREATE_FAILURE_BEHAVIOR =
  recoverWithFreshSession → createSession fails → PersistenceException propagates
  → initialize() FAILED, sessionId = null. Corrupt old session preserved.
RECOVERY_CREATE_FAILURE_CURRENT = NONE
RECOVERY_CREATE_FAILURE_READY = NO

RECOVERY_SESSION_BECOMES_LATEST = YES (recovery session has newer last_used_at)
RESTART_AFTER_RECOVERY_LOOP = NO (restart restores the recovery session; no 3rd session)

PARTIAL_REPAIR_BEHAVIOR =
  A append PASS, B append FAIL → A durable once, B absent → no Core restore → no current
PARTIAL_REPAIR_RETRY =
  retry reloads DB; A already resolved (skipped), B repaired once → Core restored → READY
  (no duplicate A)

REPAIR_CANCEL_BEFORE = PASS (cancellation before append commits → 0 writes, no current)
REPAIR_CANCEL_DURING = DETERMINISTIC (NonCancellable shield → append reaches COMMITTED/NOT_COMMITTED; exactly one synthetic; no duplicate on reopen)

STARTUP_G7_R = PASS (loadMessages null → controlled fresh recovery)
STARTUP_G8 = PASS (recovery create failure → no current / FAILED)

VALIDATE_G1_R = PASS (sequence invalid → Runtime controlled recovery, FakeStore + Room)
VALIDATE_G3 = PASS (malformed payload → Runtime controlled recovery, FakeStore + Room)
VALIDATE_G4 = PASS (unknown type → Runtime controlled recovery, FakeStore + Room)

REPAIR_G6 = PASS (partial repair failure + retry idempotence)
REPAIR_G7 = PASS (cancellation before repair append commits → no write)
REPAIR_G8 = PASS (cancellation during repair → deterministic commit, no duplicate)

RECOVERY_G1 = PASS (fresh recovery session becomes latest)
RECOVERY_G2 = PASS (restart after recovery does not create another session)
RECOVERY_G3 = PASS (old corrupt session + messages preserved)

INITIALIZE_IDEMPOTENT_AFTER_RECOVERY = YES (2nd initialize returns same recovery id; no re-recovery)

SAME_PROCESS_INTERRUPT_REGRESSION = PASS (InterruptCloseoutTest N1-N10 unchanged)
CROSS_PROCESS_REPAIR_REGRESSION = PASS (file-backed dangling repair + second reopen no duplicate)
D1_D2_D3 = PASS
CANCEL_F1_F6 = PASS

AG_CORE_TESTS = 52 (0 failures)
AG_RUNTIME_TESTS = 85 (0 failures)
APP_TESTS = 156 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

TOP_LEVEL_STATUS_NORMALIZED = YES (STATUS line: Phase G COMPLETED, Phase H MERGED INTO G, Phase I PENDING)
REMAINING_WORK_TABLE_NORMALIZED = YES (F/G/H = COMPLETED, I = PENDING)
ARCHITECTURE_COMPLIANCE_NORMALIZED = YES (PRODUCTION_NOOP=NO, DANGLING=IMPLEMENTED, DURABLE_REPAIR_FIRST=YES, CROSS_PROCESS_REPAIR=IMPLEMENTED)

PRODUCTION_NOOP = NO
DURABLE_REPAIR_FIRST = YES

ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO (version = 1)
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO (loadMessages contract unchanged; typed exception is additive in Records.kt)
GRADLE_CHANGED = NO

TRANSCRIPT_REPROJECTION_ADDED = NO

TASK_OWNED_FILES =
  ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/Records.kt
  ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt
  ag-runtime/src/test/kotlin/com/androidagent/runtime/StartupRestoreTest.kt
  app/src/main/java/com/androidagent/app/persistence/RoomTopicSessionStore.kt
  app/src/test/java/com/androidagent/app/persistence/PhaseGRoomIntegrationTest.kt
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0.md
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (CRLF warnings only, pre-existing)

PHASE_F_STATUS = CLOSED
PHASE_G_STATUS = CLOSED
PHASE_H_STATUS = MERGED_INTO_G / CLOSED
PHASE_I_STATUS = NEXT (PENDING)

DURABLE_RUNTIME_RESUME = PASS
FULL_USER_VISIBLE_RESUME = NOT YET (Transcript re-projection = Phase I)

RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0I

FINAL_VERDICT = PASS — PHASE_G_CONTROLLED_RECOVERY_EVIDENCE_AND_SSOT_NORMALIZATION_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## §19 PHASE I FINAL REPORT — Transcript Re-projection + User-Visible Resume

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0I FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

0I_START_BASELINE = 77 dirty files (36 modified + 38 untracked)
0I_END_BASELINE = 79 dirty files (37 modified + 39 untracked; +2 new untracked test files)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

PHASE_G_STATUS = CLOSED
PHASE_H_STATUS = MERGED_INTO_G / CLOSED

TRANSCRIPT_OWNER = TranscriptModel (pure, Android-free, process-scoped in SessionHolder)
TRANSCRIPT_SSOT = NO (durable DB is the SSOT; TranscriptModel is a presentation projection)

RESTORED_HISTORY_SOURCE = restored Core Session history
RESTORED_HISTORY_READ_SEAM = AgentRuntime.currentHistory() (NEW read-only, host-neutral seam;
  returns a defensive snapshot of Core Session.history() for the current session; empty when
  no current session; no persistence / DB / model / tool side effect)

SECOND_DB_RESTORE_PIPELINE = NO (UI never re-reads Room; DB -> Runtime/Core -> Presentation)

TRANSCRIPT_REPROJECT_API = TranscriptModel.reproject(history: List<Message>)
PROJECTION_REPLACE_SEMANTICS = REPLACE (clear + remap in one atomic withBlocks; never append)
PROJECTION_IDEMPOTENT = YES (re-projecting the same history yields one identical projection)

USERMESSAGE_MAPPING = Block.User(content) — verbatim (no trim/rewrite/summarize)
MODELMESSAGE_MAPPING = no toolCalls -> Block.Assistant(content, final=true);
  with toolCalls -> Block.Assistant(content, final=false) [if content non-empty] + one Block.Shell per call (original order)
TOOLCALL_MAPPING = Block.Shell(commandOf(call)) — same presentation as live onToolCallStarted
TOOLRESULT_MAPPING = Block.Result(renderResult(result)) — same as live onToolCallFinished
SYNTHETIC_UNKNOWN_MAPPING = rendered verbatim by existing renderResult (error wording
  "external execution state may be unknown" preserved; NOT re-interpreted as a confirmed
  failure; no "Failed" block, no "tool mechanism failure")

MULTI_TOOL_ORDER = A,B,C preserved (original durable order)
HISTORY_ORDER = Core Session.history() order preserved exactly (no re-sort)

PROJECTION_DURABILITY_WRITES = 0
PROJECTION_MODEL_CALLS = 0
PROJECTION_TOOL_EXECUTIONS = 0

STARTUP_ORDER = SessionHolder.initialize() -> Runtime READY + Core restored
  -> projectCurrentHistory() (TranscriptModel.reproject) -> notifyUiUpdate -> UI interactive
UI_READY_BOUNDARY = projection happens ONLY after a successful initialize(); a FAILED startup
  leaves the visible transcript untouched (cold process empty; warm process prior state kept).
  The UI accepts no send before projection (send is gated by isBusy + a null runtime).

ACTIVITY_RECREATION_BEHAVIOR = same-process rebind reuses the process-scoped holder's
  TranscriptModel (authoritative); re-projection from the same restored history is idempotent
  (exactly one copy of each item; no duplicates).

PROCESS_REOPEN_BEHAVIOR = file-backed Room: persist history -> close DB -> new
  DB/store/durability/core/runtime -> initialize() -> currentHistory() -> reproject ->
  visible transcript == durable/Core history projection.

NEWSESSION_SUCCESS_BEHAVIOR = durable+Core switch success -> projectCurrentHistory()
  (reprojects the new empty session; old projection cleared ONLY after success)
NEWSESSION_FAILURE_BEHAVIOR = switch fails -> old session stays current -> old transcript
  preserved (no premature clear; reproject is NOT invoked on failure)

CONTROLLED_RECOVERY_TRANSCRIPT = EMPTY (a Phase G controlled recovery creates a fresh empty
  session; currentHistory() = [] -> empty transcript; the corrupt old session is never shown)

RESTORED_THEN_NEW_RUN_BEHAVIOR = PASS (file-backed + FakeStore: restore old history, send a new
  message, combined history = old + new, old User NOT duplicated, same durable session)
OLD_HISTORY_DUPLICATED_ON_NEW_RUN = NO

TRANSCRIPT_I1 = PASS (UserMessage -> one user item, content verbatim incl. unicode/multiline)
TRANSCRIPT_I2 = PASS (final ModelMessage no toolCalls -> final assistant item)
TRANSCRIPT_I3 = PASS (tool-bearing model -> assistant(final=false) + shell, live-equivalent order)
TRANSCRIPT_I4 = PASS (tool result success restored)
TRANSCRIPT_I5 = PASS (tool result failure restored with error)
TRANSCRIPT_I6 = PASS (synthetic UNKNOWN not shown as confirmed failure)
TRANSCRIPT_I7 = PASS (multi-tool order A,B,C preserved)
TRANSCRIPT_I8 = PASS (empty history -> empty transcript)
TRANSCRIPT_I9 = PASS (reproject same history twice -> identical, no accumulation)
TRANSCRIPT_I10 = PASS (project A then B -> only B visible)
TRANSCRIPT_I11 = PASS (projection triggers no durability/model/tool)
TRANSCRIPT_I12 = PASS (no model/tool execution during projection)

HOST_I1 = PASS (cold fresh -> empty transcript; covered by reproject(emptyList) + currentHistory empty)
HOST_I2 = PASS (cold restore -> transcript visible; file-backed reopen vertical slice)
HOST_I3 = PASS (Activity recreation -> exactly one transcript copy; idempotent re-projection)
HOST_I4 = PASS (new session success -> empty new transcript; reproject replaces)
HOST_I5 = PASS (new session failure -> old transcript preserved)

FILE_BACKED_VISIBLE_RESTORE = PASS (phase I file backed reopen restores visible history then continues)
DANGLING_REPAIR_VISIBLE_RESTORE = PASS (Phase G file-backed dangling repair regression intact;
  synthetic UNKNOWN renders via renderResult)
RESTORE_THEN_CONTINUE_VERTICAL_SLICE = PASS (ag-runtime PHASE-I + app file-backed)

LARGE_HISTORY_TEST = PASS (TRANSCRIPT_I11: 2000 messages projected, no truncation/duplicate)
UNICODE_MULTILINE_TEST = PASS (中文 / emoji / multiline / JSON-like preserved verbatim)
LARGE_TOOL_OUTPUT_TEST = PASS (Phase E "large canonical tool result not truncated" regression intact;
  renderResult shows output verbatim, no presentation clipping)

PHASE_G_REGRESSION = PASS (StartupRestoreTest 34 + PhaseGRoomIntegrationTest 10 all green)
SAME_PROCESS_INTERRUPT_REGRESSION = PASS (InterruptCloseoutTest N1-N10 green; AgentRuntimeInterruptTest
  I11 pre-existing flakiness under full-matrix parallel load, passes in isolation — NOT introduced this round)
D1_D2_D3 = PASS
CANCEL_F1_F6 = PASS

AG_CORE_TESTS = 52 (0 failures)
AG_RUNTIME_TESTS = 88 (0 failures)
APP_TESTS = 171 (0 failures)
PROVIDER_TESTS = 26 (0 failures)
ASSEMBLE_DEBUG = PASS

DEVICE_I1 = DEFERRED
DEVICE_I2 = DEFERRED
DEVICE_I3 = DEFERRED
DEVICE_I4 = DEFERRED
DEVICE_I_VERIFICATION = DEFERRED (JVM/Robolectric only; no device this round; NOT claimed as PASS)

ROOM_SCHEMA_CHANGED = NO
DATABASE_VERSION_CHANGED = NO (version = 1)
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO (AgentRuntime.currentHistory() is an additive read seam;
  TopicSessionStore contract unchanged; TranscriptModel.reproject is presentation-only)
GRADLE_CHANGED = NO

AG_CORE_ANDROID_DEPENDENCY = NONE
AG_RUNTIME_ANDROID_DEPENDENCY = NONE

TRANSCRIPT_REPROJECTION = PASS
DURABLE_RUNTIME_RESUME = PASS
FULL_USER_VISIBLE_RESUME = PASS (latest TopicSession -> process restart -> visible transcript
  restore -> continue conversation; NOT a multi-session browser / session management / cloud sync)

TASK_OWNED_FILES =
  ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt        (currentHistory seam)
  app/src/main/java/com/androidagent/app/TranscriptModel.kt                 (reproject)
  app/src/main/java/com/androidagent/app/SessionHolder.kt                   (initialize/newSession projection)
  app/src/test/java/com/androidagent/app/TranscriptModelTest.kt             (TRANSCRIPT-I1..I11)
  app/src/test/java/com/androidagent/app/PhaseITranscriptProjectionTest.kt  (NEW: I11/HOST-I3/HOST-I5)
  ag-runtime/src/test/kotlin/com/androidagent/runtime/StartupRestoreTest.kt (PHASE-I vertical slice)
  app/src/test/java/com/androidagent/app/persistence/PhaseGRoomIntegrationTest.kt (file-backed vertical slice)
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md (this report)
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (CRLF warnings only, pre-existing)

PHASE_I_STATUS = CLOSED

RECOMMENDED_NEXT = device verification (DEV-I1..I4) — per SSOT audit after user-visible resume;
  NOT session browser / delete / rename (out of scope, see §75)

FINAL_VERDICT = PASS — TRANSCRIPT_REPROJECTION_AND_USER_VISIBLE_RESUME_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

---

## §20 DEVICE-I FINAL REPORT — Real-Device User-Visible Resume Verification

```text
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0I.DEVICE-VERIFY-1 FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

DEVICE_VERIFY_START_BASELINE = 80 dirty files (37 modified + 40 untracked)
DEVICE_VERIFY_END_BASELINE = 80 dirty files + new docs/evidence/ + tools/test-fixtures/{echo_fixture,ui_helper,debug_client,db_inspect}.py + provider.local.fixture.json
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES

DEVICE_SERIAL = APLMUT4312005935
DEVICE_MODEL = ALI-AN00 (HONOR)
ANDROID_VERSION = 15
API_LEVEL = 35
BUILD_FINGERPRINT = HONOR/ALI-AN00/HNALI-Q:15/HONORALI-AN00/9DLDLD230CNC00E206:user/release-keys

APK_PATH = app/build/outputs/apk/debug/app-debug.apk
APK_SHA256 = A207AC9EEF0CC7463DA7F4133D7BCF8E27A216F9523322343A4FE2CB01101EC3
APK_SIZE = 6062217 bytes
PACKAGE_NAME = com.androidagent.app
INSTALL_RESULT = Success (adb install -r, streamed)

SOFTWARE BASELINE (JUnit XML, pre-device): ag-core 52 / ag-runtime 88 / app 171 / provider-jdk 26 / 0 failures; assembleDebug PASS (matches Phase I baseline exactly).

DEV_I1 = PASS
DEV_I1_PID_BEFORE = 7130
DEV_I1_PID_AFTER = 13877 (NEW PROCESS CONFIRMED; am kill was a HONOR ROM no-op, used am force-stop as the task-allowed equivalent non-data-clearing kill)
DEV_I1_SESSION_BEFORE = session-5043f80d-d37e-47ba-8255-36b88bd492fd
DEV_I1_SESSION_AFTER = session-5043f80d-d37e-47ba-8255-36b88bd492fd (SAME)
DEV_I1_TRANSCRIPT_EXACT = YES (U1,A1,U2,A2 each exactly once; no duplicate, no missing)
DEV_I1_DUPLICATES = 0

DEV_I2 = PASS
DEV_I2_SAME_SESSION = YES (session-5043f80d-... continued)
DEV_I2_CONTEXT_CONTINUITY = YES (echo fixture msg_count=6 = system + U1,A1,U2,A2 restored + U3 new => restored history actually supplied to the next model call)
DEV_I2_DURABLE_CONTINUITY = YES (DB session-5043f80d count=6 seq 0..5 = U1,A1,U2,A2,U3,A3)
DEV_I2_OLD_HISTORY_DUPLICATED = NO

DEV_I3 = PASS
DEV_I3_SESSION_A = session-5043f80d-d37e-47ba-8255-36b88bd492fd
DEV_I3_SESSION_B = session-721873bf-d7c8-4d86-a56a-39640ba593c7
DEV_I3_TRANSCRIPT_SWITCH = YES (B transcript empty after switch; B-U1 msg_count=2 => no A history leaked into B)
DEV_I3_OLD_SESSION_EXISTS = YES (A row still present)
DEV_I3_OLD_MESSAGES_EXIST = YES (A: count=6 seq 0..5 intact)

DEV_I4 = PASS
DEV_I4_FORCE_STOP_CONFIRMED = YES (pid 13877 -> empty; relaunch -> 16946; second round -> 17818)
DEV_I4_RELAUNCH_SESSION = B (session-721873bf-...)
DEV_I4_TRANSCRIPT_EXACT = YES (B-U1 + answer, each once)
DEV_I4_DUPLICATES = 0
DEV_I4_CROSS_SESSION_MIX = NO

ACTIVITY_RECREATION_SPOTCHECK = NOT SEPARATELY EXERCISED (process-level restart covers the stronger case; Activity recreation reuses the process-scoped holder's TranscriptModel, already covered by HOST-I3 JVM test)

REPROJECT_DURABLE_WRITE = NO (message counts grew only when a NEW user message was sent; restore produced 0 durable writes; no dangling tool calls => no repair appends)
RESTORE_MODEL_CALLS = 0 (no model_turn_start / provider request observed during any kill/force-stop -> relaunch; the echo fixture received no request until the next explicit send)
RESTORE_TOOL_EXECUTIONS = 0

DATABASE_VERSION = 1
TABLE_COUNT = 2 (topic_sessions, messages; + room_master_table / android_metadata internal)
SESSION_COUNT = 2 (A: 6 messages, B: 2 messages)
MESSAGE_COUNT = 8 (USER=4, MODEL=4; sequences contiguous 0..N-1 in both sessions)

FATAL_EXCEPTION_COUNT = 0
ANR_COUNT = 0

KNOWN_I11_FLAKY_OBSERVED = NO (full-matrix run this round was green; the pre-existing I11 full-matrix flakiness did not trigger)
KNOWN_I11_ISOLATED_RESULT = N/A (not needed; suite green)

AG_CORE_TESTS_AFTER = 52 (0 failures)
AG_RUNTIME_TESTS_AFTER = 88 (0 failures)
APP_TESTS_AFTER = 171 (0 failures)
PROVIDER_TESTS_AFTER = 26 (0 failures)
ASSEMBLE_DEBUG_AFTER = PASS

PRODUCTION_CODE_CHANGED_THIS_ROUND = NO
TEST_CODE_CHANGED_THIS_ROUND = NO
DOCS_EVIDENCE_CHANGED_THIS_ROUND = YES (docs/evidence/ag_topic_session_device_i/ + tools/test-fixtures/{echo_fixture,ui_helper,debug_client,db_inspect}.py + provider.local.fixture.json — all test infrastructure, no product/test source)

ROOM_SCHEMA_CHANGED = NO
TOPIC_SESSIONSTORE_CONTRACT_CHANGED = NO

TASK_OWNED_FILES =
  docs/evidence/ag_topic_session_device_i/{00_device_baseline,01_build_baseline,02_dev_i1,03_dev_i2,04_dev_i3,05_dev_i4,06_database_evidence,07_logcat,08_final_matrix}
  tools/test-fixtures/echo_fixture.py      (context-echo OpenAI fixture: proves MODEL_CONTEXT_CONTAINS_RESTORED_HISTORY)
  tools/test-fixtures/ui_helper.py          (adb UI automation: tap/dump; used for New Session + transcript reads)
  tools/test-fixtures/debug_client.py       (AG.DEBUG-0 bridge client: sends via the SAME AgentRuntime.send path)
  tools/test-fixtures/db_inspect.py         (read-only DB pull via `run-as base64` + PC sqlite3)
  provider.local.fixture.json               (loopback fixture provider config; non-secret apiKey)
  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md (this report + §6 table + top STATUS)
UNEXPECTED_FILES = NONE

GIT_DIFF_CHECK = PASS (CRLF warnings only, pre-existing)

DEVICE_I_VERIFICATION = PASS

FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED
  (latest TopicSession -> Android process restart / force-stop -> visible transcript
   restore -> continue conversation. NOT a multi-session browser / session management /
   cloud sync / cross-device resume.)

RECOMMENDED_NEXT = freeze / checkpoint the TopicSession Persistence subsystem + a
  post-Phase-I architecture + implementation closure audit, then decide what AndroidAgent
  actually needs next (session management / tool expansion / model-provider lifecycle /
  Agent-loop capability / Android execution integration / product UX) — NOT further
  persistence expansion.

FINAL_VERDICT = PASS — REAL_DEVICE_USER_VISIBLE_RESUME_VERIFIED

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```

### §20 notes
- **Transport note**: `adb reverse tcp:45678` failed on this host with "Address already in
  use" (no visible PC listener; an adb/Windows quirk). `adb forward tcp:45678 tcp:45678`
  was used instead and worked identically for reaching the on-device debug bridge.
  `adb reverse tcp:18080` (provider fixture) worked normally.
- **Process-kill note**: `am kill` is a no-op on this HONOR ROM and `kill -9` is
  "Operation not permitted" (no root). `am force-stop` (the DEV-I4 mechanism) was used for
  the DEV-I1 kill — it terminates the process without clearing durable data, which is the
  task-allowed "equivalent non-data-clearing method". PID change (7130 -> 13877) confirms a
  real process restart.
- **Context-continuity evidence** is the echo fixture's request `msg_count` (a safe
  non-secret diagnostic), not model "memory" — the restored durable history was provably
  supplied to the next model call.
- **DB evidence** was pulled read-only via `run-as com.androidagent.app base64 <file>`
  (the device has no sqlite3 and the app-UID cannot write /data/local/tmp); the device DB
  was never modified.

---

## §21 POST-I ARCHITECTURE CLOSURE FINAL REPORT

```text
AG.POST-I-ARCHITECTURE-CLOSURE-FREEZE-1 FINAL REPORT

START_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
FINAL_HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (no commit)
BRANCH = main

WORKTREE_START = DIRTY (86 files: 37 modified pre-existing Phases A-I + 46 untracked)
WORKTREE_END   = DIRTY (86 + this round's docs/evidence/ag_post_i_closure/* +
                  docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md +
                  docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md)
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES (no reset/clean/stash/restore/rebase)

LIVE_SSOT_RESIDUE_CLEANED = YES
  (fixed §11 "Recommended Next": Device verification PENDING -> DEVICE-I = VERIFIED (§20),
   TOPIC_SESSION_PERSISTENCE = CLOSED / FREEZE CANDIDATE, NEXT = POST-I closure audit ->
   if PASS: UI + RELEASE PREPARATION. Historical §19 DEFERRED entries left untouched.)

MODULE_COUNT = 9 (+ root)
MODULE_INVENTORY =
  :ag-core (pure JVM, NO project deps)
  :ag-runtime (-> :ag-core)
  :provider-openai-compatible (-> :ag-core)
  :provider-openai-compatible-jdk (-> :provider-openai-compatible)
  :tool-android-shell (-> :integration-at [api], :ag-core)
  :tool-network (-> :ag-core; NO AT)
  :tool-file (-> :ag-core; NO AT)
  :integration-at (AT V4 AARs only; no AG project deps)
  :app (-> ag-core, ag-runtime, provider, all tools, integration-at; Room/AndroidX)

ARCHITECTURE_MODEL =
AndroidAgent = AG Engine + Tool Layer + Host/Product
AG_ENGINE = AG Core + Agent Runtime
AG_CORE = host-neutral intelligence/domain (pure JVM)
AG_RUNTIME = host-neutral lifecycle/session orchestration (pure JVM)
TOOL_LAYER = :tool-android-shell, :tool-network, :tool-file, :integration-at (AT point)
HOST_PRODUCT = :app (AGA)

AG_CORE_PHYSICAL_ISOLATION = PASS
AG_RUNTIME_PHYSICAL_ISOLATION = PASS
TOOL_LAYER_PHYSICAL_ISOLATION = PASS
PROVIDER_PHYSICAL_ISOLATION = PASS
APP_HOST_BOUNDARY = PASS
PERSISTENCE_PHYSICAL_ISOLATION = PASS

CORE_ANDROID_IMPORTS = 0
CORE_ROOM_IMPORTS = 0 (4 comment hits only, zero code refs)
CORE_APP_IMPORTS = 0
CORE_CONCRETE_TOOL_IMPORTS = 0
CORE_CONCRETE_PROVIDER_IMPORTS = 0

RUNTIME_ANDROID_IMPORTS = 0
RUNTIME_ROOM_IMPORTS = 0 (13 comment/doc hits only, zero code refs)
RUNTIME_APP_IMPORTS = 0
RUNTIME_CONCRETE_TOOL_IMPORTS = 0
RUNTIME_CONCRETE_PROVIDER_IMPORTS = 0

CORE_RUNTIME_BOUNDARY = CLEAN
  (Core = loop/domain/contracts; Runtime = session selection, active-run Job, interrupt,
   hydration, currentHistory seam, storage-neutral persistence contracts; no overlap)
AG_ENGINE_BOUNDARY = FREEZE_READY

TOOL_DEPENDENCY_INVERSION = PASS
MODEL_PROVIDER_DEPENDENCY_INVERSION = PASS

SESSION_CREATION_OWNER = ag-runtime AgentRuntime.initialize()
SESSION_CURRENT_OWNER = ag-runtime AgentRuntime
RUN_OWNER = ag-runtime AgentRuntime (single-active-run Job)
DURABILITY_OWNER = app StoreMessageDurability -> RoomTopicSessionStore (core contract only)
RESTORE_OWNER = ag-runtime (hydrate via TopicSessionStore); no second path
TRANSCRIPT_PROJECTION_OWNER = app TranscriptModel.reproject (sole consumer of currentHistory)
DUPLICATE_OWNERSHIP_FOUND = NONE

TOPIC_SESSION_DOMAIN = long-lived multi-turn durable conversation (!= terminal session)
RUN_DOMAIN = one user request -> one Agent loop; max one active Run per TopicSession

TOPIC_SESSION_PERSISTENCE_ARCHITECTURE_STABLE = YES
TOPIC_SESSION_FOUNDATION = FREEZE_READY
  (DATABASE_VERSION = 1; tables = topic_sessions, messages; DURABLE_FIRST; D1/D2/D3 = YES;
   DANGLING_AUTO_REPLAY = NO; controlled recovery = YES; second UI->DB pipeline = NO)

AT_INTEGRATION_STATUS = IMPLEMENTED (AT V4 consumer freeze via :integration-at; one-shot)
AG_AT_BOUNDARY_STATUS = PASS (Core = 0 AT imports/edges; Runtime = 0; Host = 3 files with
  com.at.composition.android.* + 1 debug ATTerminalSession import = MINOR DEBT M1)

CLI_REUSE_READINESS = READY (Core+Runtime pure JVM, host-neutral)

ARCHITECTURE_LAYERING = CLEAR
OWNERSHIP = SINGLE
DEPENDENCY_DIRECTION = CLEAN
PHYSICAL_ISOLATION = EFFECTIVE

RELEASE_BLOCKERS = NONE
ARCHITECTURE_BLOCKERS = NONE
MINOR_DEBT =
  M1 Host direct AT V4 composition imports (3 main files) — future adapter routing
  M2 AGENTS.md current-state not yet synced with persistence line (doc sync)
  M3 AgentRuntimeInterruptTest I11 full-matrix flake (KNOWN TEST STABILITY DEBT)
  M4 86 uncommitted worktree files (Git normalization = separate authorized task)
FUTURE_DESIGN = session browser UI, naming, retention, cloud sync, provider fallback,
  AT tool catalog, CLI syntax, final UI visual style (explicitly NOT frozen)

KNOWN_I11_STATUS = NOT OBSERVED this round (full regression green under full matrix)

AG_CORE_TESTS = PASS (52, JUnit XML actuals)
AG_RUNTIME_TESTS = PASS (88)
APP_TESTS = PASS (171)
PROVIDER_TESTS = PASS (26)
ASSEMBLE_DEBUG = PASS

PRODUCTION_CODE_CHANGED = NO
TEST_CODE_CHANGED = NO
GRADLE_CHANGED = NO

FREEZE_TYPE = DOCUMENTED WORKTREE CHECKPOINT
FREEZE_DOC = docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
HANDOFF_DOC = docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md
FREEZE_COMMIT = NOT CREATED THIS ROUND
FREEZE_TAG = NOT CREATED THIS ROUND

PHASE_A_I_STATUS = CLOSED
DEVICE_I_STATUS = VERIFIED (PASS on HONOR ALI-AN00, Android 15)

DURABLE_RUNTIME_RESUME = PASS
TRANSCRIPT_REPROJECTION = PASS
FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED

POST_I_ARCHITECTURE_CLOSURE = PASS

NEXT = UI + RELEASE PREPARATION
RECOMMENDED_AFTER_RELEASE_PREP = AG MAINLINE CAPABILITY DEVELOPMENT
  (Agent Loop capability, Model/Provider lifecycle, Tool Layer expansion,
   Android execution integration with AT, Context/Skill/planning, APK + future CLI
   productization — NOT more persistence work)

FINAL_VERDICT = PASS — POST_I_ARCHITECTURE_CLOSURE_AND_TOPIC_SESSION_FREEZE_COMPLETE

COMMIT = NO
TAG = NO
PUSH = NO
STOP = YES
```
