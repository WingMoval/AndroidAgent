# Boundary Matrix (Core/Runtime/Tool/Provider/Host/Persistence)

Source of each cell: 02 (Gradle project edges), 03 (physical imports), 04 (file ownership).

| From | To | Allowed? | Actual | Evidence | Verdict |
|---|---|---|---|---|---|
| ag-core | ag-runtime | NO | ABSENT | 02 (no project edge), 03 (RUNTIME_PKG_IMPORTS=0) | PASS |
| ag-core | app | NO | ABSENT | 02, 03 (APP_PACKAGE_IMPORTS=0) | PASS |
| ag-core | concrete tool | NO | ABSENT | 02, 03 (TOOL_PKG_IMPORTS=0) | PASS |
| ag-core | concrete provider | NO | ABSENT | 02, 03 (PROVIDER_PKG_IMPORTS=0) | PASS |
| ag-core | Android/Room/AT | NO | ABSENT | 02 (pure JVM classpath), 03 (ANDROID=0, Room mentions all comments) | PASS |
| ag-runtime | ag-core | YES | PRESENT (19 core imports, all domain types) | 02, 03 | PASS |
| ag-runtime | app | NO | ABSENT | 02, 03 (APP_PACKAGE_IMPORTS=0) | PASS |
| ag-runtime | Room/Android | NO | ABSENT | 02 (pure JVM), 03 (ROOM mentions all comments) | PASS |
| ag-runtime | concrete tool | NO | ABSENT | 02, 03 (TOOL_PKG_IMPORTS=0) | PASS |
| ag-runtime | concrete provider | NO | ABSENT | 02, 03 (PROVIDER_PKG_IMPORTS=0) | PASS |
| provider-* | app | NO | ABSENT | 02, 03 | PASS |
| provider-* | tool-* | NO | ABSENT | 02, 03 | PASS |
| provider-jdk | ag-core (direct) | n/a | ABSENT (transitive via :provider-openai-compatible only) | 02 | PASS (clean) |
| app | ag-runtime | YES | PRESENT (40 imports) | 02, 03 | PASS |
| app | ag-core | YES | PRESENT (159 imports) | 02, 03 | PASS |
| app | concrete providers | YES | PRESENT (23 imports) | 02, 03 | PASS |
| app | concrete tools | YES | PRESENT (6 imports) | 02, 03 | PASS |
| tool-shell | ag-core | YES | PRESENT (7 imports) | 02, 03 | PASS |
| tool-shell | integration-at (AT) | YES (only AT consumer path) | PRESENT (api edge) | 02, 03 (AT_PKG imports in tool main) | PASS |
| tool-network | ag-core | YES | PRESENT (6 imports) | 02, 03 | PASS |
| tool-network | AT | NO | ABSENT | 02 (no integration-at edge), 03 (AT_IMPORTS=0) | PASS (counter-example holds) |
| tool-file | ag-core | YES | PRESENT (5 imports) | 02, 03 | PASS |
| tool-file | AT | NO | ABSENT | 02, 03 | PASS |
| integration-at | ag-core / ag-runtime | NO | ABSENT | 02 (no project edge), 03 (0 imports) | PASS |
| integration-at | AT V4 AARs | YES (only point) | PRESENT (at-engine-api:4.0.0, at-cli:4.0.0) | 02 | PASS |
| app | raw AT engine types | NO (frozen: route via :integration-at) | PARTIAL — 3 Host files import com.at.composition.android.* (2) + com.at.engine.ATTerminalSession (1, debug-only) | 03 | MINOR DEBT (documented; Core/Runtime remain AT-free) |
| TranscriptModel | DB/Room | NO | ABSENT | 03 (no Room/Store imports; capability search clean) | PASS |
| TranscriptModel | model/tool execution | NO | ABSENT | 03 (only imports ToolCall/ToolResult as rendering data types) | PASS |

## Core vs Runtime boundary (detailed)

Core currently owns (verified by file ownership + imports):
- Agent loop + Run lifecycle (AgentCore, RunOptions/RunOutcome/RunFailure/RunInterruptSignal)
- TopicSession domain semantics (Session = "long-lived multi-turn conversation")
- Message / ModelMessage / UserMessage / ToolResultMessage, ToolCall/ToolSpec/ToolResult
- ModelProvider contract, Tool contract, ToolRegistry
- AgentEvent stream, MessageDurability contract (storage-agnostic)

Runtime currently owns (verified):
- AgentRuntime: host current-Session selection (initialize/create/restore), single-active-run
  Job ownership, interruptActiveRun (RUN-CONTROL-1 semantics), currentHistory() read seam,
  Core hydration
- persistence/: TopicSessionStore interface + Records (storage-neutral) + MessageSerializer
  (storage-neutral; maps Core Message <-> durable records)

Overlap check:
- Runtime does NOT re-implement the agent loop, policy, or event semantics (imports only Core
  domain types; no duplicate loop/registry/provider code in 04 file map).
- Core does NOT do process lifecycle, session selection, or I/O (no Android, no Room, no
  store; MessageDurability is a pure contract).
- VERDICT: CORE_RUNTIME_BOUNDARY = CLEAN

## Duplicate ownership audit (single authoritative owner per concern)

| Concern | Owner | Others touching it | Verdict |
|---|---|---|---|
| Session creation (durable) | ag-runtime AgentRuntime.initialize() | app SessionHolder only CALLS it | SINGLE |
| Current session selection | ag-runtime AgentRuntime | TranscriptModel renders only | SINGLE |
| History restore (durable -> Core) | ag-runtime AgentRuntime (hydrate) + TopicSessionStore (app impl) | no second path | SINGLE |
| History projection (Core -> UI) | app TranscriptModel.reproject() | only consumer of AgentRuntime.currentHistory() | SINGLE |
| Durability write semantics | app StoreMessageDurability -> RoomTopicSessionStore | Core MessageDurability is contract only | SINGLE |
| Tool Registry | core ToolRegistry (contract), Host composes instances | no second registry | SINGLE |
| ModelProvider selection | Host ChatCompositionRoot | no runtime/router owner | SINGLE |
| Run lifecycle (Job, interrupt) | ag-runtime AgentRuntime | Host only calls interruptActiveRun() | SINGLE |
| DB schema / migration | app TopicSessionDatabase (version=1) | no other schema owner | SINGLE |

DUPLICATE_OWNERSHIP_FOUND = NONE

## TopicSession / Run domain semantics (verified in code)

- `com.androidagent.core.Session` doc: "A long-lived multi-turn conversation." -> TopicSession != terminal session. PASS
- Runtime owns current-session selection + single-active-run -> same TopicSession max one active Run. PASS
- Nothing in Core/Runtime assumes one process = one conceptual session (initialize() restores OR creates; newSession() switches current). PASS
- DATABASE_VERSION = 1 (TopicSessionDatabase, version = 1, no migration wired). PASS
- Tables: topic_sessions, messages (Entities.kt @Entity tableName x2). PASS

TOPIC_SESSION_PERSISTENCE_ARCHITECTURE_STABLE = YES
