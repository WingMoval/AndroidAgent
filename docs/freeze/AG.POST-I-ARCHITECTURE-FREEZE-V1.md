# AG.POST-I-ARCHITECTURE-FREEZE-V1 — Post-Phase-I Architecture Freeze

```text
FREEZE_IDENTITY
  FREEZE_NAME   = AG.POST-I-CORE-FREEZE-V1
  FREEZE_TYPE   = PRE_UI_GIT_FREEZE
  BASE_HEAD     = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4 (parent of freeze commit)
  BRANCH        = main
  WORKTREE      = CLEAN after freeze commit (M4 dirty-worktree debt CLOSED)
  DEVICE_VERIFICATION = PASS (HONOR ALI-AN00, Android 15, serial APLMUT4312005935)
  DEVICE_IDENTIFIER_POLICY = PRIVATE_REPO_RETAINED (no remote; internal real-device
                evidence; redaction deferred to a future public-repo normalization)
  FREEZE_COMMIT = RESOLVE VIA TAG (authoritative: `git rev-parse AG.POST-I-CORE-FREEZE-V1^{}
                `); the real hash is recorded in the round's terminal Final Report,
                NOT hard-coded into this file (a file cannot know its own commit SHA)
  FREEZE_TAG    = AG.POST-I-CORE-FREEZE-V1 (annotated)
  EVIDENCE      = docs/evidence/ag_post_i_closure/ (00-08) + docs/evidence/ag_pre_ui_core_freeze/
  AUDIT_ROUND   = AG.POST-I-ARCHITECTURE-CLOSURE-FREEZE-1
  GIT_NORMALIZATION_ROUND = AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1
```

Freeze scope (this is a PRE-UI release baseline, NOT a final product release):

```text
  AG Engine architecture (AG Core + Agent Runtime)
  AG Core / Agent Runtime / Tool / Provider dependency boundaries
  TopicSession Foundation (durable persistence + verified resume)
  current verified Android execution integration baseline (AT V4 via :integration-at)

  FINAL_UI_FROZEN      = NO
  FINAL_PRODUCT_RELEASE = NO
```

This freeze is realized as a real Git baseline commit + annotated tag
(`AG.POST-I-CORE-FREEZE-V1`). It supersedes the earlier "DOCUMENTED WORKTREE
CHECKPOINT" identity from the closure round: the worktree is now committed and
clean. If a final release commit/tag is ever needed, that is a separate release
task after the UI baseline.

---

## 1. Architecture Layers (frozen)

```text
AndroidAgent
  = AG Engine
  + Tool Layer
  + Host / Product

AG Engine
  = AG Core          (:ag-core,      pure JVM intelligence/domain)
  + Agent Runtime    (:ag-runtime,   pure JVM host-neutral lifecycle/session orchestration)

Tool Layer
  = concrete tool modules, one per capability:
      :tool-android-shell   (shell, one-shot; AT via :integration-at)
      :tool-network         (fetch, HTTPS GET; no AT)
      :tool-file            (read/search/list/stat, APP_SELF; no AT)
      :integration-at       (unified AT consumption point; ONLY module on AT V4 AARs)

Host / Product
  = :app (AGA): Android lifecycle, Room persistence impl, Composition Root,
    SessionHolder, TranscriptModel, ChatActivity/UI, provider wiring,
    tool registration, permission/approval UX, product settings

ModelProvider implementations (outside Core)
  = :provider-openai-compatible (+ :provider-openai-compatible-jdk transport)
```

## 2. Allowed Dependency Directions

```text
ag-runtime  -> ag-core
provider-*  -> ag-core (provider-jdk -> provider-openai-compatible -> ag-core)
tool-*      -> ag-core
tool-android-shell -> integration-at (api)
integration-at -> AT V4 AARs (vendored local m2 only)
app -> ag-core, ag-runtime, provider-openai-compatible, tool-*, integration-at
app -> AndroidX/Room
```

## 3. Forbidden Dependency Directions

```text
ag-core -> ag-runtime | app | tool-* | provider-* | Android | Room | AT   (ALL forbidden)
ag-runtime -> app | Android | Room | tool-* | provider-* | AT              (ALL forbidden)
provider-* -> app | tool-* | Android | Room                                (ALL forbidden)
tool-network / tool-file -> AT                                             (forbidden; AT only via tool-android-shell -> integration-at)
integration-at -> ag-core | ag-runtime | app                                (forbidden)
TranscriptModel -> DB/Room | model | tool execution                         (forbidden)
any second UI -> DB restore pipeline                                        (forbidden)
```

## 4. Ownership

```text
AG Core owns:
  Agent loop, Run domain (RunOptions/RunOutcome/RunFailure/RunInterruptSignal),
  TopicSession domain semantics (Session), Message/ModelMessage/UserMessage/
  ToolResultMessage/ToolCall/ToolSpec/ToolResult, ModelProvider contract,
  Tool contract, ToolRegistry, AgentEvent stream, MessageDurability contract
  (storage-agnostic), interrupt reconciliation semantics, policy-level orchestration.

Agent Runtime owns:
  process-scoped Agent lifecycle, TopicSession startup lifecycle
  (initialize: restore-or-create), current session selection, single-active-run
  Job ownership, interruptActiveRun(), durable TopicSession orchestration,
  dangling repair orchestration, Core hydration, currentHistory() read-only seam,
  persistence contracts (TopicSessionStore/Records/MessageSerializer — storage-neutral).

Tool Layer owns: concrete Tool implementations (Core-external);
  :integration-at = the single AT consumption point.

ModelProvider ownership: OpenAI-compatible protocol + transport, outside Core.

Host/App owns: Android lifecycle, Room implementation (entities/DAOs/Database/
  RoomTopicSessionStore/StoreMessageDurability), Composition Root, SessionHolder
  (process-scoped holder), TranscriptModel (presentation projection only),
  Activity/UI, provider selection/config wiring, concrete tool registration,
  permission/approval UX, product settings.
```

## 5. Persistence Ownership (frozen)

```text
Durable chain: AgentCore -> MessageDurability (core contract)
             -> StoreMessageDurability (app adapter)
             -> TopicSessionStore (ag-runtime contract)
             -> RoomTopicSessionStore (app) -> Room -> SQLite

DB            = durable SSOT (topic_sessions, messages; DATABASE_VERSION = 1)
Core Session  = live domain materialization
TranscriptModel = presentation projection (read-only projection of Core history)

Restore     = DB -> Runtime/Core -> Presentation (one direction, one pipeline)
DURABLE_FIRST = YES   D1 = YES   D2 = YES   D3 = YES
DANGLING_AUTO_REPLAY = NO   (cross-process unresolved Tool = UNKNOWN; controlled recovery only)
SEQUENCE_OWNER = persistence (contiguous 0..N-1 per session)
```

## 6. TopicSession / Run Semantics (frozen)

```text
TopicSession != terminal/PTY session
TopicSession = long-lived multi-turn durable Agent conversation
Run          = one user request triggering one Agent loop
same TopicSession   : max one active Run
different TopicSessions : independent existence allowed
newSession         != delete old history (old sessions durable-preserved)
```

## 7. Durability / Restore / Repair Invariants (frozen)

```text
- No durable write on restore/projection (restore produced 0 durable writes; device-verified)
- No model call on restore (RESTORE_MODEL_CALLS = 0; device-verified)
- No tool execution on restore (RESTORE_TOOL_CALLS = 0)
- Dangling tool-call repair = controlled, explicit; NEVER auto-replay of an
  external side effect whose outcome is UNKNOWN
- ToolCall embedded in ModelMessage durable representation (single message store)
- Interrupted in-flight tool = IN_FLIGHT_INTERRUPTED (outcome unknown, honest)
```

## 8. Presentation Projection Boundary (frozen)

```text
TranscriptModel = presentation only:
  renders AgentEvent stream + Core history projection (reproject, REPLACE, idempotent)
  must NOT: write persistence, select durable session, execute tool, call model,
            own Agent policy
```

## 9. Android Execution Boundary (frozen)

```text
AG = Intelligence    AT = Execution
AG Core    -X-> AT (verified 0 imports, 0 Gradle edge)
Agent Runtime -X-> AT (verified 0 imports, 0 Gradle edge)
AT consumption ONLY through :integration-at (AtExecutionAdapter)
Future: AG Core -> Tool contract; concrete Android Tool / execution adapter ->
        AT consumer API. AG Core -> AT Core is and remains forbidden.
AT_INTEGRATION_STATUS = IMPLEMENTED (V4 consumer freeze, one-shot; :integration-at)
```

## 10. APK / Future CLI Reuse Boundary (frozen)

```text
AGA (APK) and future AGC (CLI) both compose:
  AG Engine = AG Core + Agent Runtime (pure JVM, host-neutral)
CLI must NOT copy: Agent loop, Session, Run, Provider contract, Tool contract,
TopicSession lifecycle — all already host-neutral in :ag-core/:ag-runtime.
CLI_REUSE_READINESS = READY
```

## 11. Known Non-Blocking Debt

Active non-blocking debt (still open):

```text
M1. Host direct AT V4 composition imports (ChatCompositionRoot, CompositionAdbSemantic,
    DebugBridge) — DEFERRED NON-BLOCKING; route through :integration-at in a future
    cleanup round.
M3. AgentRuntimeInterruptTest I11 = KNOWN TEST STABILITY DEBT (full-matrix flake;
    not a product blocker).
```

Closed during AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1:

```text
M2. CLOSED — AGENTS.md current-state now synced with the TopicSession persistence
    / freeze line.
M4. CLOSED — worktree formally Git-frozen (PRE-UI core freeze commit + annotated tag
    AG.POST-I-CORE-FREEZE-V1); worktree clean.
```

## 12. Frozen Decisions (summary — full list as authorized)

```text
AG = Intelligence; AT = Execution
AndroidAgent = AG Engine + Tool Layer + Host/Product
AG Engine = AG Core + Agent Runtime
AG Core = host-neutral intelligence/domain
Agent Runtime = host-neutral lifecycle/session orchestration
AG Core cannot import concrete Tools / Providers / Android / Room / App / AT
Agent Runtime cannot import Android / Room / App / concrete tool / concrete provider / AT
TopicSession != Run; TopicSession != terminal/PTY session
Room/SQLite durable DB = TopicSession durable SSOT
TranscriptModel = presentation projection only
Restore = DB -> Runtime/Core -> Presentation; second UI->DB pipeline FORBIDDEN
ToolCall embedded in ModelMessage durable representation
cross-process unresolved Tool = UNKNOWN, NO AUTO REPLAY
D1/D2/D3 = FROZEN; controlled fresh recovery = FROZEN
FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED
```

## 13. Explicitly Deferred Product Features (NOT frozen, future design)

```text
session browser UI design
session naming strategy
retention policy
cloud sync architecture
provider fallback policy
AT concrete tool catalog
CLI command syntax
final UI visual style
```

## 14. Conditions That Allow Reopening TopicSession Persistence Core

ONLY the following may reopen this freeze:

```text
1. verified correctness bug (durable data corruption / lost messages / wrong session)
2. database migration requirement (schema change)
3. new durable domain requirement the current schema cannot represent
4. security/privacy defect in the persistence path
5. multi-process requirement
6. explicit product requirement that violates a frozen assumption
```

NOT valid reasons to reopen:
"convenience cleanup", "feels like it should be rewritten", "another library is nicer",
aesthetics.

---

```text
POST_I_ARCHITECTURE_CLOSURE = PASS
TOPIC_SESSION_FOUNDATION    = FROZEN
NEXT                        = UI + RELEASE PREPARATION
```
