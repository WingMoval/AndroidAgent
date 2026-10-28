# AG.ARCH-DOC-AUDIT-0 — CONSOLIDATED EVIDENCE

Single-file merge of docs/evidence/ag_arch_doc_audit_0/ (17 files, 00-16).
Date: 2026-10-28. HEAD = edfc603eadb0067bd010c04ca26b4bc28de285 (main).

==============================================================================
FILE: 00_git_baseline.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 00 GIT BASELINE
Date: 2026-10-28

BRANCH        = main
HEAD          = edfc603eadb0067bd010c04ca26b4bc28de2e685
WORKTREE      = DIRTY (2 untracked, non-architectural closeout docs:
                docs/implementation/AG.UI-TRANSCRIPT-CARDS-1.CLOSEOUT-MERGE-0.md
                upload/AG.UI-TRANSCRIPT-CARDS-1.CLOSEOUT-MERGE-0.md)
MODIFIED_TRACKED = 0
REMOTE        = none
PUSH          = N/A (no remote)

TAGS (relevant):
  AG.V1-KNOWN-GOOD-FREEZE-V1   -> 13df1db
  AG.V2-BASELINE-V1            -> c84bf5d
  AG.POST-I-CORE-FREEZE-V1     -> f640ed0
  ag-0 / ag-provider-0 / ag-aga-0 / ag-stable-0 / ag-stable-1 / ag-engine-0
  (AT side: AT.V4-CONSUMER-FREEZE-V1 = 7216d0e, external repo)

LOG (last 8):
  edfc603 (HEAD -> main) AG.UI-TRANSCRIPT-CARDS-1 + AG.UI-SHELL-1
  b7dbc04 docs: normalize post-freeze live SSOT
  f640ed0 (tag: AG.POST-I-CORE-FREEZE-V1) freeze: establish post-I pre-UI AG core baseline
  a6eea61 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: close complete AT V3 consumer subsystem
  4d8aec7 AG.HOST-SESSION-CONTINUITY-0.1
  c4a58fe AG.HOST-SESSION-CONTINUITY-0
  6c4d8f1 AG.AT-FULL-CONSUMER-INTEGRATION-0
  50211c6 AG.AT-CONSUMER-RECONCILIATION-0.1

==============================================================================
FILE: 01_document_inventory.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 01 DOCUMENT INVENTORY
Scope: D:\codebank\AndCode\AndroidAgent-V2, *.md + *.txt,
excluding build/ and .gradle artifacts and third_party bundles.
Note: several filenames contain Chinese characters that PowerShell
renders as mojibake; actual bytes are UTF-8 and the files are intact.

TOTAL_SCANNED = ~100 (docs/ + root + upload/ + tools/)

ROOT
  AGENTS.md (50.9 KB)                      LIVE SSOT (WHAT IS TRUE NOW)
  README.md (2.1 KB)                       repository entry point

docs/
  AndroidAgent_Architecture_Capability_Map_v1.0.md (62.4 KB)  FROZEN architecture basis

docs/architecture/
  AndroidAgent_总体架构.md (21.0 KB)        FROZEN ag-0 era general architecture
  AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md (46.9 KB)         FROZEN TopicSession arch
  AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0_修正1.md (45.7 KB)   SUPERSEDED correction copy
  AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0.md (30.4 KB)   FROZEN preflight
  AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0_修正1.md (30.2 KB) SUPERSEDED copy

docs/baseline/
  AG.BASELINE-FREEZE-0.md (13.4 KB)        FROZEN baseline (ag-baseline-0)
  AG.ENGINE-BOUNDARY-FREEZE.md (7.6 KB)    FROZEN (ag-engine-0)

docs/design/
  AG.SHELL-0_INTEGRATION_DESIGN.md (21.2 KB)  HISTORICAL design (superseded by AG.SHELL-1)
  AGENT-0_First_Real_Android_Agent_Vertical_Slice.md (13.3 KB)  HISTORICAL design
  AndroidAgent_V2_VNext_UI_设计基线.md (14.8 KB)  LIVE design SSOT (user-owned, read-only)

docs/audit/
  AG.AT-CONSUMER-RECONCILIATION-0.md (25.1 KB)  HISTORICAL audit (CLOSED, superseded by V4 work)
  AG.AT-HANDOFF-CONSUMER-AUDIT.md (29.3 KB)     HISTORICAL audit
  AG.AT-V4-Handoff-Audit.md (41.4 KB)           HISTORICAL audit (input to V4 integration)
  AG.CORE-RUNTIME.BOUNDARY-AUDIT.md (26.9 KB)   HISTORICAL audit (ag-stable-1 era)
  AG.SESSION-PERSISTENCE-RESEARCH-0.md (27.8 KB) HISTORICAL research/preflight
  AG.SHELL-1.PREFLIGHT.md (18.3 KB)             HISTORICAL preflight
  AG.UI-BASELINE-AUDIT-1.md (43.0 KB)           HISTORICAL audit (UI baseline, CLOSED)

docs/freeze/
  AG.POST-I-ARCHITECTURE-FREEZE-V1.md (10.9 KB)  FROZEN (tag AG.POST-I-CORE-FREEZE-V1) —
                                                 current most complete architecture freeze
  AG.V1-KNOWN-GOOD-FREEZE-V1.md (9.9 KB)         FROZEN (V1 baseline, external V1 repo anchor)

docs/handoff/
  AG_POST_I_CLOSURE_HANDOFF.md (6.0 KB)           HISTORICAL handoff (post-I closure)

docs/integration/
  AG.V2-2.AT-INTEGRATION.md (5.5 KB)              HISTORICAL (superseded by V4 integration)
  AT_TO_AG_HANDOFF_CURRENT.md (4.6 KB)            LIVE SSOT (canonical AT snapshot pointer)

docs/implementation/  (step docs — point-in-time, never rewritten)
  AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md (98.0 KB)  LIVE persistence SSOT
  AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL_修正1.md (77.7 KB)  SUPERSEDED copy
  AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_修正1.md (68.9 KB)  SUPERSEDED copy
  AG.UI-TRANSCRIPT-CARDS-1.md (6.3 KB)            LIVE (current step, CLOSED/PASS)
  AG.UI-TRANSCRIPT-CARDS-1.CLOSEOUT-MERGE-0.md (9.7 KB)  LIVE closeout (untracked)
  AG.UI-SHELL-1.md (13.9 KB)                      LIVE (current step, closed w/ commit)
  AG.AT-V4-*.md (4 files)                         HISTORICAL V4 integration steps
  AG.RUN-CONTROL-1*.md / AG.HOST-STOP-1.md        HISTORICAL (VERIFIED, frozen semantics)
  AG.SHELL-1_ANDROID_SHELL_TOOL_AT_ENGINE_INTEGRATION.md  HISTORICAL
  AG.NETWORK-0 / AG.FILE-0 / AGENT-0.* / AGA.* / AG.DEBUG-* / AG.V1-PRE-FREEZE-NORMALIZATION.md
  docs/implementation/steps/ (5 files, AG.0 era, Chinese names)  HISTORICAL

docs/migration/
  AG.V2-1.BASELINE-MIGRATION.md (8.9 KB)   HISTORICAL (V1->V2 migration, closed)
  AG.V2-1.DEVICE-BASELINE-SMOKE.md (6.8 KB) HISTORICAL
  AG.V2-BASELINE-V1.md (3.5 KB)            HISTORICAL (tag AG.V2-BASELINE-V1)

docs/regression/
  AG.ADB-LKG-REGRESSION.md (7.9 KB)        HISTORICAL (root-caused, closed)

docs/research/
  AG.MATURE-AGENT-SESSION-RESEARCH-0.md (78.6 KB)  HISTORICAL research (pre-TopicSession)

docs/roadmap/
  AndroidAgent_Master_Roadmap.md (89.4 KB)  LIVE SSOT (WHERE WE ARE GOING)

docs/v2-research/pre-migration-workspace/  (13 files, preserved V1-era workspace)
  AG.V2-0.ARCHITECTURE-RESEARCH.md (23.3 KB)  HISTORICAL (migration research)
  AG.V2-CONTROLLER-DECISION.md (2.5 KB)       HISTORICAL (Strategy B decision)
  AG.V1-PRE-FREEZE-NORMALIZATION.md, AT_TO_AG_HANDOFF_CURRENT.md (old copy)
  evidence/*.txt (6), proposed-changes/FILE_CHANGE_MATRIX.md, READMEs

upload/
  AG.UI-TRANSCRIPT-CARDS-1.md / .evidence.txt / .CLOSEOUT-MERGE-0.md  (exports)

tools/
  tools/test-fixtures/README.md  (fixture server docs)
  tools/ui_automation/  (UI automation scripts, non-architecture)

docs/evidence/ (step evidence dirs — IMPLEMENTATION_EVIDENCE, not architecture SSOT)
  ag_post_i_closure/ ag_pre_ui_core_freeze/ ag_post_freeze_doc_normalize/
  ag_topic_session_device_i/ ag_ui_baseline_audit_1/ ag_ui_shell_1/ ag_ui_transcript_cards_1/

==============================================================================
FILE: 02_ssot_classification.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 02 SSOT CLASSIFICATION
Model: LIVE_SSOT / FROZEN_BASELINE / CURRENT_SUPPORTING / HISTORICAL_POINT_IN_TIME /
       SUPERSEDED / IMPLEMENTATION_EVIDENCE / RESEARCH_ONLY / FUTURE_DESIGN

LIVE_SSOT (authority for "what is true now" / "where going" / "what AT snapshot"):
  1. AGENTS.md
     Role = WHAT IS TRUE NOW. Implemented/frozen facts, verification baseline,
     forbidden areas, step registry. Continuously normalized (b7dbc04).
  2. docs/roadmap/AndroidAgent_Master_Roadmap.md
     Role = WHERE WE ARE GOING. Capability registry, DAG, priority, execution
     queue. LIVE status header. Obeys (never reopens) Architecture v1.0.
  3. docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
     Role = canonical pointer for "which AT snapshot AG consumes".
     Current = AT_V4_CONSUMER_HANDOFF (7216d0e, tag AT.V4-CONSUMER-FREEZE-V1,
     vendored AAR, debug variant). Explicitly supersedes V0/V3 handoffs.
  4. docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md
     Role = persistence implementation SSOT (per AGENTS.md entry: "SSOT: ...").
     Phases A-I + DEVICE-I closed; FROZEN foundation.

FROZEN_BASELINE (point-in-time architecture, valid until reopen conditions):
  5. docs/AndroidAgent_Architecture_Capability_Map_v1.0.md
     Role = WHERE CAPABILITIES BELONG. Controller-approved, FROZEN.
     Upstream basis of the roadmap. Never edited to match roadmap.
  6. docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
     Role = current most complete architecture freeze (layers, dependency
     directions, ownership, persistence, presentation boundary, AT boundary,
     deferred features, reopen conditions). Tag AG.POST-I-CORE-FREEZE-V1.
     NOTE: pre-UI — does not cover the UI shell / transcript cards layer.
  7. docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md
     Role = TopicSession semantics/layering/database tech freeze.
  8. docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0.md
     Role = durability seam preflight freeze.
  9. docs/architecture/AndroidAgent_总体架构.md
     Role = AG.0-era FROZEN general architecture (ag-0/ag-provider-0).
     Contains claims later extended (see 09_drift_matrix D1/D2/D3).
  10. docs/baseline/AG.BASELINE-FREEZE-0.md, docs/baseline/AG.ENGINE-BOUNDARY-FREEZE.md
      Role = early baseline freezes (ag-baseline-0, ag-engine-0).
  11. docs/freeze/AG.V1-KNOWN-GOOD-FREEZE-V1.md
      Role = V1 known-good manifest (anchors external V1 repo).

CURRENT_SUPPORTING_DOC:
  12. docs/design/AndroidAgent_V2_VNext_UI_设计基线.md
      User-owned VNext UI design baseline (UI-generation SSOT, read-only).
      Architecture layers explicitly stated as unchanged.
  13. docs/implementation/AG.UI-TRANSCRIPT-CARDS-1.md + closeout
      Current UI step record (CLOSED/PASS).

HISTORICAL_POINT_IN_TIME (never rewritten; read for history only):
  - All docs/implementation/* step docs (RUN-CONTROL, HOST-STOP, SHELL-1,
    NETWORK-0, FILE-0, AGENT-0.*, AGA.*, DEBUG-*, V4-*, TOPIC-SESSION 修正 copies,
    steps/ AG.0-era docs).
  - All docs/audit/* (7 files): point-in-time audits at their baseline HEADs.
  - docs/migration/* (3), docs/regression/* (1), docs/handoff/* (1),
    docs/design/AG.SHELL-0_INTEGRATION_DESIGN.md,
    docs/design/AGENT-0_First_Real_Android_Agent_Vertical_Slice.md,
    docs/integration/AG.V2-2.AT-INTEGRATION.md.

RESEARCH_ONLY:
  - docs/research/AG.MATURE-AGENT-SESSION-RESEARCH-0.md
  - docs/audit/AG.SESSION-PERSISTENCE-RESEARCH-0.md (research+preflight hybrid)
  - docs/v2-research/pre-migration-workspace/** (13 files)

SUPERSEDED (explicitly marked or factually replaced; must not be used as SSOT):
  - *_修正1 / _修正 copies (TopicSession FREEZE-0, DURABILITY-PREFLIGHT-0,
    TOPIC-SESSION-PERSISTENCE-0_FINAL) — correction copies; original files
    remain the referenced names in SSOT text.
  - third_party/at-engine-handoff-v0/ (AT V0) and third_party/at-consumer-freeze-v1/
    (AT V3) — SUPERSEDED/INACTIVE per AT_TO_AG_HANDOFF_CURRENT §6; V4 is CURRENT.
  - AG.SHELL-0_INTEGRATION_DESIGN.md — superseded by implemented AG.SHELL-1.
  - AT_TO_AG_HANDOFF_CURRENT.md copy inside docs/v2-research/pre-migration-workspace/
    — old (V1-era) copy; live one is docs/integration/.

IMPLEMENTATION_EVIDENCE:
  - docs/evidence/** (7 step evidence dirs, 90+ files), upload/ exports.

FUTURE_DESIGN:
  - AG.POST-I-ARCHITECTURE-FREEZE-V1 §13 list (session browser design, naming,
    retention, cloud sync, provider fallback, AT tool catalog, CLI syntax,
    final visual style) — deferred, not frozen.
  - AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0 §4 POST-V1 list (fork/branch/rewind,
    compaction, multi-session UI, etc.).
  - Roadmap DEFERRED/OUT registry entries.

==============================================================================
FILE: 03_architecture_timeline.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 03 ARCHITECTURE TIMELINE
Chronology reconstructed from git tags + doc dates + step records.

PHASE 0 — V1 era (D:\codebank\AndCode\AndroidAgent, now FROZEN_KNOWN_GOOD)
  ag-baseline-0        Project init; AndroidAgent = AG intelligence/runtime,
                       not a terminal app; AG Core / AGA / AGC roles defined.
  ag-0                 AG Core v0: Agent Loop, Session/Run, ModelProvider +
                       Tool contracts, ToolRegistry, policy, events, RunOutcome.
                       Frozen async boundary: run/complete/execute = suspend;
                       "AG Core has no production deps" (later relaxed, see D1).
  ag-provider-0        First real OpenAI-compatible provider (external module),
                       real endpoint verified. ModelTurn contract frozen.
  ag-aga-0             :app Android host (AGA) + AndroidOpenAiHttpTransport;
                       4-layer boundary frozen (Core / Runtime / Composition /
                       Frontend). AGA = thin frontend.
  ag-stable-0/1        Baseline freezes.
  ag-engine-0          Engine = Runtime + Core logical boundary audit.
  AG.SHELL-1           :tool-android-shell + AT Engine Handoff Bundle v0
                       (first AT consumption; one-shot model-visible).
  AGENT-0.PREFLIGHT/LIVE  Real adaptive agent loop on device; instructions +
                       trace (Host-owned).
  AGA.DEV-CONFIG-0 / HOST-CONTROL-0 / DEBUG-0 / OBSERVABILITY-0/1
                       Provider config, host controls, debug bridge, streaming
                       transcript (TranscriptModel born).
  AG.NETWORK-0 / AG.FILE-0   Tool Layer formalized: one module per capability;
                       no central Tool framework.
  AG.CONVERSATION-CONTROL-0  Research: Stop/Interrupt/Rewind/Fork vocabulary
                       (3-layer terminology frozen: UI=Stop, AG=Interrupt,
                       Runtime=cancellation).
  AG.RUN-CONTROL-0/1(.FIX)   Runtime owns active-Run Job; interruptActiveRun();
                       RunOutcome.Interrupted; coroutines dep added to Runtime.
  AG.HOST-STOP-1       User-facing Stop UI + fixture-based device acceptance.
  13df1db AG.V1-KNOWN-GOOD-FREEZE-V1 (2026-10-18)
                       V1 frozen as known-good baseline; Strategy B chosen
                       (V2 = separate active tree) — AG.V2-CONTROLLER-DECISION.

PHASE 1 — V2 migration (this repo)
  c84bf5d AG.V2-BASELINE-V1            Direct clone at V1 freeze commit.
  5679dbf AG.V2-1 smoke                BLOCKED (post-TLS ADB handshake) —
                                       historical; later superseded by AT V4.
  1410d5f / 22e3f28 AG.V2-2            AT V3 full consumer integration via
                                       :integration-at (AtExecutionAdapter).
  1ea9654..a6eea61 AT consumer reconciliation + full V3 subsystem close.
  4d8aec7 / c4a58fe HOST-SESSION-CONTINUITY  Session survives config change.

PHASE 2 — TopicSession durability (V2)
  AG.SESSION-PERSISTENCE-RESEARCH-0    Research: why durable session needed;
                       UNKNOWN-tool-outcome scenario drives transactional store.
  AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0  Terminology (TopicSession != Run !=
                       ModelCall != ATTerminalSession); layer ownership;
                       SQLite/Room/DBv1 freeze; V1 feature boundary.
  AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0  D1/D2/D3 durable-commit points;
                       DANGLING_AUTO_REPLAY=NO; controlled recovery.
  AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0 (Phases A-I)
                       MessageDurability in Core; TopicSessionStore contract in
                       Runtime; Room impl in :app; TranscriptModel demoted to
                       presentation projection. DEVICE-I verified.

PHASE 3 — Post-I closure + AT V4
  f640ed0 AG.POST-I-CORE-FREEZE-V1     Pre-UI architecture freeze (current
                       canonical freeze for non-UI layers). Known debt M1/M3.
  b7dbc04 docs: normalize post-freeze live SSOT (AGENTS.md sync).
  AG.AT-V4-INTEGRATION-1 (2026-10-22)  AT V4 consumer freeze (7216d0e) vendored
                       AAR replaces V3; AT_TO_AG_HANDOFF_CURRENT updated.
                       V0/V3 bundles marked SUPERSEDED/INACTIVE.

PHASE 4 — VNext UI (current)
  AG.UI-BASELINE-AUDIT-1               UI baseline audit (audit+evidence only).
  AG.UI-SHELL-1                        Workspace shell, provider settings,
                       debug automation, sidebar.
  edfc603 AG.UI-TRANSCRIPT-CARDS-1 + SHELL-1 (2026-10-28, HEAD)
                       RecyclerView transcript cards, per-block actions
                       (Copy/Retry-as-new-turn/Edit&Resend/collapse),
                       TopicSession list + switch (additive Runtime seam).
                       CURRENT HEAD.

INVARANTS THAT SURVIVED ALL PHASES:
  - AG = Intelligence; AT = Execution.
  - ag-core: no Android, no AT, no concrete impl, no Room/HTTP.
  - ag-runtime: no Android/AT/concrete tool/provider.
  - AT consumption only via :integration-at.
  - One Tool = one module; no Tool framework.
  - Host is thin: input/render/lifecycle; no Agent semantics in frontend.

ASSUMPTIONS REPLACED OVER TIME:
  - "AG Core has zero production deps" -> coroutines-core added (RUN-CONTROL-1).
  - "no persistence" -> TopicSession/Room durable SSOT (Phase 2).
  - TranscriptModel as transcript owner -> presentation projection only.
  - AT V0 handoff -> V3 consumer freeze -> V4 consumer freeze (CURRENT).
  - Single-TextView transcript -> RecyclerView cards (UI generation change
    only; architecture unchanged).

==============================================================================
FILE: 04_module_dependency_map.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 04 MODULE DEPENDENCY MAP
Source of truth: settings.gradle.kts + each module build.gradle.kts (read 2026-10-28).

MODULES (9):
  :ag-core                        kotlin("jvm")   pure JVM
  :ag-runtime                     kotlin("jvm")   pure JVM
  :provider-openai-compatible     kotlin("jvm")
  :provider-openai-compatible-jdk kotlin("jvm")
  :tool-android-shell             Android lib
  :tool-network                   Android lib
  :tool-file                      Android lib
  :integration-at                 Android lib
  :app                            Android app (AGA Host / Composition Root)

PRODUCTION DEPENDENCIES (measured):
  :ag-core
    - kotlinx-coroutines-core:1.10.2   (RUN-CONTROL-1: NonCancellable shielding
                                        of durable synthetic ToolResult commit)
    - (NO project deps, NO Android, NO AT, NO Room, NO HTTP/JSON)
  :ag-runtime
    - project(:ag-core)
    - kotlinx-coroutines-core:1.10.2   (execution Job ownership)
    - kotlinx-serialization-json:1.8.0 (TopicSession MessageSerializer;
                                        tree API only, no provider coupling)
  :provider-openai-compatible
    - project(:ag-core), kotlinx-serialization-json, kotlinx-coroutines-core
  :provider-openai-compatible-jdk
    - project(:provider-openai-compatible), JDK java.net.http
  :tool-android-shell
    - project(:ag-core); api(project(:integration-at)); serialization; coroutines
  :tool-network
    - project(:ag-core); serialization; coroutines (platform HttpsURLConnection)
  :tool-file
    - project(:ag-core); serialization; coroutines
  :integration-at
    - com.at.engine.api:at-engine-api:4.0.0 (api)
    - com.at.cli:at-cli:4.0.0 (api)
    - coroutines
    (AT internal modules = transitive only, not imported)
  :app
    - project(:ag-core, :ag-runtime, :provider-openai-compatible,
              :tool-android-shell, :tool-network, :tool-file, :integration-at)
    - AndroidX core/appcompat/activity/lifecycle/recyclerview/room

GRAPH:
  :app --> ag-core, ag-runtime, provider-oai, tool-*, integration-at, AndroidX/Room
  :ag-runtime --> ag-core
  :tool-* --> ag-core
  :tool-android-shell --> integration-at (api)
  :integration-at --> AT V4 AARs (vendored local m2)
  :provider-oai --> ag-core ; :provider-jdk --> :provider-oai

FORBIDDEN EDGES — VERIFIED ABSENT (import scan 2026-10-28):
  ag-core  -> android.*, androidx.*, com.androidterminal.*, com.at.*, com.adb.*,
              com.androidagent.app/runtime/tool  : 0 matches
  ag-runtime -> same set                              : 0 matches
  tool-network/tool-file -> AT                       : absent (dep graph)
  integration-at -> ag-core/ag-runtime/app           : absent (dep graph)

KNOWN DEVIATION (documented, non-blocking debt M1 in POST-I freeze §11):
  :app host files import AT composition types directly (3 files):
    ChatCompositionRoot.kt   -> com.at.composition.android.ATAndroid, ATAndroidConfig
    CompositionAdbSemantic.kt-> com.at.composition.android.ATAndroidAdbControl,
                                ATAdbBootstrapState, ATAdbReadinessState,
                                ATAdbPairingDiscovery, ATAdbPairingResult
    DebugBridge.kt (debug)   -> com.at.engine.ATTerminalSession
  Ruling: AT_TO_AG_HANDOFF_CURRENT §7 allows :integration-at to import
  com.at.engine.*/composition; the POST-I freeze lists these host imports as
  DEFERRED NON-BLOCKING (M1: "route through :integration-at in a future
  cleanup round"). This is a HOST-LEVEL deviation from the ideal
  "AT consumption ONLY through :integration-at", already tracked.
  SEVERITY = LOW (tracked debt, debug + composition only, no Core/Runtime impact).

==============================================================================
FILE: 05_core_runtime_ownership.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 05 CORE / RUNTIME OWNERSHIP
Per current code + POST-I freeze §4 + AGENTS.md.

AG CORE (com.androidagent.core, 11 production files, pure JVM):
  AgentCore.kt        Agent loop (driveLoop), run lifecycle, interrupt
                      reconciliation, MessageDurability integration (D1/D2/D3),
                      restoreSession(), run exclusivity (tryBeginRun/endRun).
  Session.kt          TopicSession domain: ordered List<Message>, restoreHistory.
  Message.kt          UserMessage / ModelMessage (embeds ToolCalls) /
                      ToolResultMessage; seq ownership by persistence.
  Model.kt            ModelProvider contract, ModelTurn (FinalAnswer | ToolCalls
                      with same-turn content), ModelRequest.
  Tool.kt             Tool contract, ToolSpec, ToolResult, ToolCall.
  ToolRegistry.kt     registry: register/lookup/dispatch (no typed schema).
  ToolApproval.kt     allow/deny policy.
  AgentEvent.kt       event stream (observation sink).
  RunOutcome.kt       Answer / Interrupted / Failure (incl. DurabilityFailure).
  MessageDurability.kt storage-agnostic commit contract + Noop.
  AgCoreMarker.kt     marker.

  CORE DOES NOT KNOW: Room, SQLite, DAO, Android, AT, provider wire formats,
  HTTP, JSON, API keys, database paths.

AGENT RUNTIME (com.androidagent.runtime, 4 production files, pure JVM):
  AgentRuntime.kt     process-scoped Agent lifecycle; TopicSession startup
                      (initialize: restore-or-create); current session
                      selection; single-active-run Job ownership;
                      interruptActiveRun() (cancels execution Job); durable
                      write orchestration via TopicSessionStore; dangling
                      repair orchestration; Core hydration; currentHistory();
                      switchSession() + listSessions() (ADDITIVE, UI-era).
  persistence/TopicSessionStore.kt  storage-agnostic contract
                      (save/restore/listSessions).
  persistence/Records.kt            durable record types.
  persistence/MessageSerializer.kt  JSON (de)serialization boundary
                      (kotlinx-serialization-json, tree API only).

  RUNTIME DOES NOT: write SQL, hold DB connections, touch Android/AT,
  own presentation.

GOD-OBJECT CHECK (Runtime):
  AgentRuntime.kt is the single largest orchestration class; it grew
  additively: session init -> durability orchestration -> interrupt Job
  ownership -> listSessions/switchSession (UI-era, additive seams).
  All added responsibilities are within the frozen "Agent Runtime owns"
  list of POST-I freeze §4 EXCEPT listSessions/switchSession which postdate
  the freeze but are additive query/switch seams that do not move
  ownership across layers (session selection was already Runtime-owned).
  VERDICT: NOT a God Object; responsibilities remain single-domain
  (process-scoped session/run orchestration). WATCH only if further
  conversation-control semantics (rewind) are added without a new step.

CORE-OWNED vs RUNTIME-OWNED vs HOST-OWNED (current):
  history SSOT (live domain)      = AG Core Session
  history SSOT (durable)          = Room DB (:app), via Runtime orchestration
  session id generation           = Runtime (not Host, not Core)
  active Run Job                  = Runtime (Host must NOT hold it)
  interrupt decision (user Stop)  = Runtime.interruptActiveRun()
  interrupt semantics             = Core (RunOutcome.Interrupted, synthetic T)
  transcript rendering state      = Host TranscriptModel (projection only)
  live process holder             = Host SessionHolder
  UI                              = Host ChatActivity / TranscriptCardAdapter

==============================================================================
FILE: 06_session_run_persistence.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 06 SESSION / RUN / PERSISTENCE

TERMINOLOGY (frozen, AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0):
  TopicSession = durable long-lived conversation, stable id, ordered
                 Message history, survives process death + app restart.
  Run          = transient, one user input -> final answer/failure/interrupt.
  ModelCall    = transient single provider request.
  TopicSession != Run != ModelCall != ATTerminalSession.
  same TopicSession: max one active Run. newSession != delete old history.

EVOLUTION of the Session concept:
  ag-0 era:            "Session" = in-memory multi-turn context
                       (com.androidagent.core.Session, no persistence).
  OBSERVABILITY era:   TranscriptModel (Host) held rendered transcript;
                       SessionHolder kept process-scoped live objects.
  TOPIC-SESSION-ARCH:  "TopicSession" named + frozen; durable semantics;
                       TranscriptModel demoted to presentation projection.
  PERSISTENCE-0 (A-I): Room/SQLite durable SSOT (topic_sessions + messages,
                       DATABASE_VERSION=1); restore pipeline fixed.
  UI era (CURRENT):    listSessions()/switchSession() additive; session
                       browser UI. Durable schema UNCHANGED (version 1).

CURRENT DURABLE CHAIN (frozen, POST-I §5):
  AgentCore --(MessageDurability.commit)--> StoreMessageDurability (:app)
            --> TopicSessionStore (ag-runtime contract)
            --> RoomTopicSessionStore (:app) --> Room --> SQLite

CURRENT SOURCE OF TRUTH:
  durable SSOT       = Room/SQLite (messages table, seq 0..N-1 per session)
  live domain SSOT   = Core Session (hydrated from DB on restore)
  presentation state = TranscriptModel (READ-ONLY projection; reproject =
                       REPLACE + idempotent; must NOT write/select/execute)
  restore direction  = DB -> Runtime/Core -> Presentation (ONE pipeline;
                       second UI->DB path FORBIDDEN — verified: no Room
                       imports in TranscriptModel; Room imports in
                       ChatCompositionRoot/SessionHolder are composition-
                       level wiring, permitted Host ownership)

RESTORE/REPAIR INVARIANTS (frozen):
  - 0 durable writes on restore (device-verified)
  - 0 model calls, 0 tool executions on restore
  - dangling ToolCall repair = controlled/explicit; DANGLING_AUTO_REPLAY = NO
    (cross-process unresolved tool outcome = UNKNOWN)
  - interrupted in-flight tool = IN_FLIGHT_INTERRUPTED (honest)
  - ToolCall embedded in ModelMessage durable representation

RUN SEMANTICS (frozen):
  RunOutcome = Answer | Interrupted | Failure
  Interrupt: Runtime cancels execution Job; Core finalizes Interrupted +
  synthetic ToolResult per declared-uncompleted call (pairing invariant);
  exactly one RunFinished(Interrupted). Partial stream = transcript-only.
  Retry (UI) = Retry-as-New-Turn: find origin User text -> HostActions.send
  -> new Run. NO history mutation. (Verified in TRANSCRIPT-CARDS-1 closeout.)

PERSISTENCE CLASSIFICATION:
  Persistence is a HOST capability (Room impl in :app) + RUNTIME contract
  (TopicSessionStore/Records/Serializer in ag-runtime, storage-neutral) +
  CORE contract (MessageDurability, storage-agnostic).
  It is NOT an independent subsystem/module; it is a layered contract chain.
  FROZEN decisions: engine (SQLite), access layer (Room), location
  (app-private), version (1), seq owner (persistence), D1/D2/D3 commit
  points, no-auto-replay.
  EXTENSION SEAMS (not frozen): schema migration path (reopen condition #2),
  retention policy, multi-session UI beyond switch, compaction, cloud sync.

==============================================================================
FILE: 07_tool_provider_at_boundaries.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 07 TOOL / PROVIDER / AT BOUNDARIES

TOOL ARCHITECTURE (frozen, verified):
  Contract owner  = AG Core (Tool, ToolSpec, ToolCall, ToolResult,
                    ToolRegistry). ToolCall.arguments = opaque string;
                    typed schema DEFERRED.
  Implementations = Core-external, one module per capability:
    :tool-android-shell  (AndroidShellToolImpl; AT via :integration-at api)
    :tool-network        (NetworkToolImpl fetch; HTTPS GET only; full SSRF
                          guard; NO AT dependency — canonical counter-example)
    :tool-file           (FileToolImpl read/search/list/stat; APP_SELF root =
                          filesDir/agent/; READ-only; bounds enforced)
  NO central :tool-layer module, NO ToolManager/ToolRuntime/ToolBackend/
  ToolFactory (frozen anti-abstraction list; verified absent in code).
  Registration = Host (ChatCompositionRoot) constructs + registers into
  Core ToolRegistry. Adding a tool never touches Core/Runtime.
  ToolResult.success = invocation mechanism OK, NOT domain success
  (exitCode=7 still success=true).
  "Not all Android capabilities go through AT" = CONFIRMED (network/file
  prove the direct-API path).

PROVIDER ARCHITECTURE (frozen, verified):
  Contract owner      = AG Core (ModelProvider, ModelTurn, ModelRequest).
  Implementation      = :provider-openai-compatible (protocol + config
                        loader/resolver + OpenAiHttpTransport contract).
  Transports          = :provider-openai-compatible-jdk (JDK java.net.http,
                        JVM tests) and AndroidOpenAiHttpTransport in :app
                        (HttpsURLConnection, production device path).
  Host configuration  = :app DeviceLocalProviderConfig (filesDir/
                        provider.local.json auto-load; manual UI fallback;
                        ProviderProfile + ModelDiscovery + ModelCatalog
                        added in UI-SHELL-1, all :app-scoped).
  Model catalog       = :app (ModelCatalog/ModelDiscovery) — presentation/
                        config layer, NOT Core, NOT provider module.
  UI-Shell pollution check: ProviderProfile/ModelCatalog/ModelDiscovery/
  SafResource all live in :app; zero new imports of app types in
  ag-core/ag-runtime/provider-* (verified by import scan). NO reverse
  pollution.
  EMG / OpenRouter = provider NODES (endpoints) selected in Host; no
  EMGProvider class exists (documented anti-misreading in 总体架构).

AT / AG BOUNDARY (frozen, verified 2026-10-22):
  AT Engine = AndroidTerminal execution engine (ADB connection/transport,
  shell/PTY execution, identity/pairing). EXTERNAL project.
  AG Engine = AG Core + Agent Runtime (intelligence + orchestration).
  Connection point = :integration-at (AtExecutionAdapter) — the SINGLE
  module on AT AARs. :tool-android-shell is a thin one-shot consumer of
  that adapter.
  Who may depend on whom:
    :integration-at -> com.at.engine.api (public), com.at.composition.android
                       (facade), com.at.cli (optional). ALLOWED.
    :integration-at -> com.adb.engine.* / com.at.backend.*  FORBIDDEN.
    :ag-core / :ag-runtime -> any com.at.* / com.adb.*       FORBIDDEN (0
                       imports verified).
  CURRENT AT snapshot = AT.V4-CONSUMER-FREEZE-V1 (7216d0e), vendored AAR in
  third_party/at-v4-consumer-freeze-v1/, debug variant; release NOT yet
  validated (sync cadence: POST_V4_RELEASE_VALIDATION).
  Model-visible AT capability = ONE_SHOT only. PERSISTENT engine capability
  exists in AT but is adapter-owned, not model-visible.
  AG = Intelligence / AT = Execution still holds: AT knows nothing about
  TopicSession; AG never imports ADB internals.
  SUPERSEDED bundles (retained for rollback only): at-engine-handoff-v0 (V0),
  at-consumer-freeze-v1 (V3); m2 entry for V3 commented out in
  settings.gradle.kts.

==============================================================================
FILE: 08_host_ui_boundary.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 08 HOST / UI BOUNDARY
Source: app/src/main/java/com/androidagent/app/** (24 production kt files,
measured 2026-10-28) + POST-I freeze §4 (Host ownership) + UI step docs.

LAYER ASSIGNMENT of current :app classes:

COMPOSITION ROOT (Host assembly):
  ChatCompositionRoot.kt   constructs AgentCore/AgentRuntime/Provider/tools,
                           registers into Core; also wires Room persistence
                           + AT Android composition (M1 debt: direct AT
                           imports here, tracked).
  CompositionAdbSemantic.kt ADB readiness/pairing semantics for Host UI
                           (direct AT composition imports — M1 debt).

PROCESS-SCOPED HOLDERS:
  SessionHolder.kt         object singleton: holds composition root,
                           TranscriptModel, state strings across Activity
                           recreation; wires RoomTopicSessionStore/
                           StoreMessageDurability (Host persistence wiring).
                           NOT persistent storage (frozen: SessionHolder !=
                           persistent storage).

RUNTIME BRIDGE (Host -> Engine actions):
  HostActions.kt           single action facade: send/stop/newSession/
                           listSessions/switchSession/providerSwitch.
                           SECRET-FREE by construction. All conversation
                           control flows through here (verified in
                           TRANSCRIPT-CARDS-1 closeout: Retry ->
                           HostActions.send -> AgentRuntime.send).
  HostStatusModel.kt       run-phase status projection for UI.

PRESENTATION:
  ChatActivity.kt          Activity: layout wiring, card adapter binding,
                           session switcher dialog, provider settings UI.
                           LARGEST class — see OVERLOAD note.
  TranscriptModel.kt       presentation projection ONLY (frozen): renders
                           AgentEvent stream + Core history reproject.
                           No Room/DB/model/tool imports (verified).
  TranscriptCardAdapter.kt RecyclerView semantic cards (User/Thought/
                           Assistant/ToolExecution/Failure/Interrupted),
                           per-block Copy/Retry/Edit&Resend/collapse.
  ModelCatalog.kt / ModelDiscovery.kt / ProviderProfile.kt /
  DeviceLocalProviderConfig.kt / SafResource.kt
                           provider config + model catalog (Host config layer).

DEBUG (debug builds only):
  debug/DebugBridge.kt     TCP JSONL 127.0.0.1:45678; routes PC input through
                           the SAME AgentRuntime.send; automation commands
                           (stop/new_session/provider_switch/snapshot).
                           One direct AT import (ATTerminalSession) — M1.
  debug/DebugProtocol.kt   protocol types.

PERSISTENCE (Host capability):
  persistence/TopicSessionDatabase.kt (Room DB, v1)
  persistence/Entities.kt, persistence/Daos.kt
  persistence/RoomTopicSessionStore.kt (implements ag-runtime contract)
  persistence/StoreMessageDurability.kt (implements core MessageDurability)

TRACE:
  trace/AgentTrace.kt / AgentTraceCollector.kt / TraceFormatter.kt
  (redacted agent trace; Host-owned per AGENT-0.PREFLIGHT)

OVERLOAD ASSESSMENT (report-only, NO refactor this round):
  ChatActivity.kt          = WATCH. Grew across UI-SHELL-1 + TRANSCRIPT-CARDS-1
                             (composer, provider dialog, session switcher,
                             card actions, status). Functionally cohesive
                             (Activity glue) but large; a future UI step could
                             extract dialogs/handlers. Not an architecture
                             violation (still Host layer, no Engine semantics
                             ownership — actions delegate to HostActions).
  SessionHolder.kt         = OK. Process holder + persistence wiring.
  HostActions.kt           = HEALTHY. Clean facade; single entry for all
                             conversation control; no state of its own beyond
                             references.
  DUPLICATE_OWNER_FOUND    = NO (single Runtime, single Core, single store).
  DUPLICATE_PIPELINE_FOUND = NO (single transcript pipeline DB->Core->
                             projection->cards; verified no UI->DAO transcript
                             path; debug bridge reuses same send path).

==============================================================================
FILE: 09_drift_matrix.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 09 DRIFT MATRIX
DOC says X / CODE does Y. Severity: NONE | LOW | MEDIUM | HIGH | ARCH_BLOCKER.

D1. DEPENDENCY_DRIFT — LOW (doc outdated, code correct)
  DOC: docs/architecture/AndroidAgent_总体架构.md §2/§10:
       "AG Core 没有任何生产依赖，只依赖 Kotlin 标准库；kotlinx-coroutines
        仅用于测试" and ":ag-core 生产 无（仅 Kotlin 标准库）".
  CODE: ag-core/build.gradle.kts has production
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2").
  CAUSE: AG.RUN-CONTROL-1.DETERMINISTIC.FIX added coroutines to Core
       (NonCancellable shielding of durable synthetic ToolResult commit).
       AGENTS.md §"Implemented..." still says "AG Core ... has no production
       dependency beyond the Kotlin standard library" (also outdated).
  RULING: code change was step-authorized and documented in the step doc;
       the frozen general-architecture doc and one AGENTS.md line were not
       re-normalized. No boundary violated (still pure JVM, no Android/AT).
  FIX (deferred to normalization step): update 总体架构 §2/§10 + AGENTS.md
       line to "no production dependency beyond kotlinx-coroutines-core
       (minimal, interrupt closeout)".

D2. DEPENDENCY_DRIFT — LOW (doc outdated, code correct)
  DOC: 总体架构 §10: ":ag-runtime 生产 project(:ag-core) + kotlin-stdlib;
       不引入 Android / AndroidX / provider 实现 / HTTP / JSON".
  CODE: ag-runtime adds kotlinx-serialization-json:1.8.0 (MessageSerializer)
       + kotlinx-coroutines-core:1.10.2.
  CAUSE: AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0 (Phase D) + RUN-CONTROL.
  RULING: authorized step work; "不引入 JSON" predates TopicSession.
       POST-I freeze (newer) already reflects Runtime's persistence contracts.
  FIX (deferred): same normalization as D1.

D3. SCOPE_DRIFT — LOW (doc scope, not error)
  DOC: POST-I freeze is a PRE-UI freeze; it does not mention RecyclerView,
       TranscriptCardAdapter, HostActions, ProviderProfile, session switcher.
  CODE/UI: UI generation (VNext shell + cards) now exists.
  RULING: expected — freeze §13 lists "final UI visual style / session
       browser UI design" as explicitly DEFERRED. UI design SSOT is the
       user-owned VNext design baseline. Not a contradiction; the freeze
       remains authoritative for non-UI layers. AGENTS.md does NOT yet have
       a UI step registry entry for SHELL-1/TRANSCRIPT-CARDS-1 (closeout
       judged NO UPDATE NEEDED; watch item for next SSOT normalization).

D4. SUPERSEDED_BUT_UNMARKED — LOW
  DOC: AGENTS.md "Current integration snapshot" section still states
       AT_CONSUMER_INTEGRATION = AT.AG-CONSUMER-FREEZE-V1 / c9cee21 /
       AT_CONTRACT_STATUS = DRAFT / NEXT_FORMAL_AT_SYNC = POST_AT_EMULATOR...
  CODE/DOC: actual current AT = AT_V4_CONSUMER_HANDOFF (7216d0e), contract
       FROZEN (V4 consumer boundary), NEXT = POST_V4_RELEASE_VALIDATION.
  CAUSE: AG.AT-V4-INTEGRATION-1 updated AT_TO_AG_HANDOFF_CURRENT.md (the
       canonical pointer) but the AGENTS.md snapshot block was not synced.
  RULING: the AGENTS.md block itself says "do not restate the full AT state
       in other docs — point to the file above", so the pointer file wins;
       but the stale values inside AGENTS.md are a misreading risk.
  SEVERITY: LOW (pointer mechanism works; text is stale).

D5. TERMINOLOGY_DRIFT — NONE (consistent)
  "AT Core" (old docs) vs "AT Engine" (new docs): same system
  (AndroidTerminal execution kernel). 总体架构/AGENTS use "AT Core";
  V4 handoff docs use "AT Engine". Not contradictory — AGENTS.md still
  defines the rule as "AT Core lives in the AndroidTerminal project".
  Recorded for vocabulary normalization only.

D6. DEAD_FUTURE_DESIGN — NONE FOUND
  No orphan future-design docs present without a FUTURE/DEFERRED marker.
  Deferred items are consistently tracked in POST-I §13 + Roadmap registry.

D7. OWNERSHIP_DRIFT — NONE
  No case found where a newer doc silently moved ownership across the
  frozen layer boundary (Core/Runtime/Host). listSessions/switchSession
  additions to Runtime stay within Runtime's frozen "session selection"
  ownership.

SUMMARY: 2 LOW dependency-doc drifts (D1/D2, both in the ag-0-era general
architecture doc + one AGENTS.md line), 1 LOW scope note (D3), 1 LOW stale
AT snapshot block in AGENTS.md (D4). No MEDIUM/HIGH/ARCH_BLOCKER drift.
CODE matches the NEWER frozen docs (POST-I freeze, TopicSession freeze,
V4 handoff) on every checked boundary.

==============================================================================
FILE: 10_contradiction_matrix.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 10 CONTRADICTION MATRIX
Cross-era doc conflicts. Format: CONTRADICTION | OLD | NEW | CURRENT CANONICAL | EVIDENCE.

C1. AG Core production dependencies
  OLD (ag-0 总体架构, still in file): "AG Core 没有任何生产依赖，只依赖
      Kotlin 标准库" / "kotlinx-coroutines 仅用于测试".
  NEW (AG.RUN-CONTROL-1.DETERMINISTIC.FIX step doc): coroutines-core added
      to :ag-core production (NonCancellable shielding).
  CURRENT CANONICAL: coroutines-core IS a legitimate minimal Core production
      dependency (pure JVM, no platform leak). The ag-0 doc is a point-in-time
      freeze of the ag-0 state — its claim was true AT ag-0.
  STATUS: SUPERSEDED_BUT_UNMARKED inside the ag-0 doc (it is marked FROZEN,
  which is why the drift is low-severity: frozen docs are snapshots).

C2. ag-runtime "no JSON"
  OLD (ag-0 总体架构): ":ag-runtime 不引入 ... JSON".
  NEW (TopicSession persistence step): kotlinx-serialization-json added.
  CURRENT CANONICAL: Runtime owns MessageSerializer (storage-neutral JSON
  boundary) per POST-I freeze §4. Old doc snapshot predates TopicSession.
  STATUS: same as C1.

C3. AT snapshot identity
  OLD (AGENTS.md integration snapshot block): AT.AG-CONSUMER-FREEZE-V1,
      c9cee21, contract DRAFT, NEXT = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW.
  NEW (AT_TO_AG_HANDOFF_CURRENT.md, updated 2026-10-22): AT_V4_CONSUMER_HANDOFF,
      7216d0e, contract FROZEN, NEXT = POST_V4_RELEASE_VALIDATION.
  CURRENT CANONICAL: V4 (handoff file is explicitly the canonical pointer;
  AGENTS.md defers to it). AGENTS.md block = stale residue.
  STATUS: SUPERSEDED_BUT_UNMARKED (D4).

C4. "AT Core" vs "AT Engine"
  OLD docs: "AT Core" (AndroidTerminal execution kernel).
  NEW docs: "AT Engine" (com.at.engine.* public API surface).
  CURRENT CANONICAL: one system, two names for kernel vs public engine API.
  AGENTS.md rule text ("AT Core lives in the AndroidTerminal project") remains
  the governing statement. No semantic conflict.
  STATUS: TERMINOLOGY only (D5).

C5. TranscriptModel role
  OLD (OBSERVABILITY-0 era practice): TranscriptModel held the rendered
      transcript as the UI's conversation state.
  NEW (TopicSession freeze): TranscriptModel = presentation projection ONLY;
      durable SSOT = Room; live SSOT = Core Session.
  CURRENT CANONICAL: projection-only (frozen POST-I §8; verified in code —
  TranscriptModel has no persistence imports).
  STATUS: RESOLVED & FROZEN. No live contradiction.

C6. Session naming
  "Session" (ag-0, in-memory) vs "TopicSession" (durable) vs
  "ATTerminalSession" (AT-side PTY) vs "Host session continuity".
  CURRENT CANONICAL: TopicSession = AG conversation (frozen terminology);
  explicitly != ATTerminalSession; AT knows nothing about TopicSession.
  STATUS: RESOLVED by AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0 §1.

C7. Retry vs Redo vs Rewind vocabulary
  Risk: UI-era docs could conflate "Retry" with "Redo/Rewind".
  CURRENT CANONICAL (CONVERSATION-CONTROL-0 + TRANSCRIPT-CARDS-1 closeout):
  Retry = Retry-as-New-Turn (new Run, history unchanged); Redo = DEFERRED;
  Rewind = NOT IMPLEMENTED; no display-only fake rewind exists.
  STATUS: CONSISTENT across docs. No contradiction found.

C8. AGENTS.md "AG Core ... no production dependency beyond Kotlin stdlib"
  vs measured coroutines dep (same fact as C1, different document).
  STATUS: SUPERSEDED_BUT_UNMARKED (D1).

CONTRADICTIONS REQUIRING ACTION: C1/C2/C8 (normalize ag-0 doc or add
supersession note) + C3 (sync AGENTS.md AT block). All LOW.
NO high-severity semantic contradictions between eras.

==============================================================================
FILE: 11_terminology.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 11 TERMINOLOGY (canonical vocabulary)

PRODUCT / SYSTEMS
  AG                  = AndroidAgent (this project) — Agent intelligence/runtime.
  AG Engine           = AG Core + Agent Runtime (logical, = :ag-core + :ag-runtime).
  AG Core             = :ag-core, com.androidagent.core — intelligence/domain kernel.
  Agent Runtime       = :ag-runtime, com.androidagent.runtime — host-neutral
                        lifecycle/session orchestration.
  AGA                 = Android GUI host product (= :app). "AGA" also used in
                        step IDs for host-side steps.
  AGC                 = future CLI/TUI frontend (NOT IMPLEMENTED; shares
                        AG Engine). Do not confuse with "AG Core".
  AT                  = AndroidTerminal (external project).
  AT Engine / AT Core = AT execution kernel (ADB transport, shell/PTY exec,
                        identity/pairing). "AT Core" = older name used in
                        AGENTS.md rules; "AT Engine" = name in V4-era docs.
                        Same system.
  Tool Layer          = concrete tool modules, one per capability (no central
                        framework module).

CONVERSATION DOMAIN (frozen)
  TopicSession        = durable long-lived conversation (stable id, ordered
                        messages, survives restart). == "Session" in most docs
                        after TopicSession freeze.
  Run                 = transient: one user input -> terminal outcome.
  ModelCall           = transient single provider request.
  Message             = UserMessage | ModelMessage (embeds ToolCalls) |
                        ToolResultMessage; durable, seq-ordered.
  Durable Message     = message committed via MessageDurability (D1/D2/D3).
  Transcript          = user-visible rendering. TranscriptModel = presentation
                        projection ONLY (not SSOT).
  RunOutcome          = Answer | Interrupted | Failure.

CONVERSATION CONTROL (frozen 3-layer vocabulary)
  Stop                = UI-layer term (the button).
  Interrupt /
  Interrupted         = AG semantic layer (RunOutcome.Interrupted;
                        "Stopped" block in transcript).
  cancellation /
  CancellationException = Runtime layer (coroutine cancellation of execution
                        Job; Runtime.interruptActiveRun()).
  Retry               = UI action: Retry-as-New-Turn (find origin user text,
                        send again, NEW Run; history NOT mutated).
  Redo                = DEFERRED (not never). NOT the same as Retry.
  Rewind / Undo       = NOT IMPLEMENTED (CORE-007 RESEARCH_REQUIRED).
  Fork / Branch       = NOT IMPLEMENTED (POST-V1 deferred).
  Checkpoint          = NOT IMPLEMENTED.

HOST / CONFIG
  Host / Product      = :app (AGA). Composition Root + presentation + config.
  Composition Root    = ChatCompositionRoot: constructs/registers Engine parts.
  SessionHolder       = process-scoped live holder (NOT storage).
  HostActions         = Host action facade (send/stop/session/provider).
  Provider (node)     = endpoint identity (EMG, OpenRouter, custom) — a config
                        value, NOT a class. There is exactly one concrete
                        provider implementation (OpenAiCompatibleProvider).
  Model               = model id string (e.g. deepseek-v4-flash-0731).
  ProviderProfile /
  ModelCatalog /
  ModelDiscovery      = :app config/catalog types (UI-SHELL-1 era).

DEPRECATED / CONFLATION-RISK TERMS
  "LLMSession"        = rejected name (TopicSession survives provider changes).
  "ATTerminalSession" = AT-side PTY session; NEVER an AG TopicSession.
  "Host Session"      = informal for process-scoped holder state; use
                        SessionHolder.
  "PTY Session"       = AT-side only; not AG.
  "Session" alone     = post-freeze means TopicSession; pre-freeze (ag-0 docs)
                        meant in-memory Session. Check doc era.
  "AGC"               = CLI frontend; NOT "AG Core" (both abbreviate to 3
                        letters; historical misreading risk).
  "EMGProvider"       = does NOT exist; EMG is an endpoint of
                        OpenAiCompatibleProvider.
  "Tool layer module" = forbidden concept (:tool-layer does not exist).

==============================================================================
FILE: 12_recommended_reread_set.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 12 RECOMMENDED REREAD SET (TOP 8)
Ordered by value to a Controller re-entering the project.

1. AGENTS.md
   WHY: the only LIVE "what is true now" SSOT; step registry, frozen rules,
   forbidden areas, current AT pointer, repo roles.
   SOLVES: current ground truth in one file.
   AUTHORITATIVE: YES (continuously normalized; last: b7dbc04).
   IGNORE: the "Current integration snapshot" values block (stale vs V4 —
   read AT_TO_AG_HANDOFF_CURRENT.md instead); the "no production dependency
   beyond Kotlin stdlib" line for AG Core (D1 drift).

2. docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
   WHY: the current most complete architecture freeze: layers, allowed/
   forbidden dependency directions, ownership per layer, persistence chain,
   presentation boundary, AT boundary, deferred list, reopen conditions.
   SOLVES: "what is the frozen architecture right now (pre-UI)".
   AUTHORITATIVE: YES for non-UI layers (tag AG.POST-I-CORE-FREEZE-V1).
   IGNORE: nothing, except it predates the UI generation (UI is deferred by
   design, not an error).

3. docs/AndroidAgent_Architecture_Capability_Map_v1.0.md
   WHY: Controller-approved capability placement ("where capabilities belong");
   upstream basis of the roadmap; the 11 domains + anti-abstraction rules.
   SOLVES: capability ownership questions without reading every step doc.
   AUTHORITATIVE: YES (FROZEN; roadmap obeys it, never reopens it).
   IGNORE: none (frozen by design; implementation detail intentionally out).

4. docs/roadmap/AndroidAgent_Master_Roadmap.md
   WHY: LIVE plan — capability registry, dependency DAG, execution queue,
   deferred registry, intake protocol.
   SOLVES: "what is next / what is deferred / why".
   AUTHORITATIVE: YES for planning (LIVE status).
   IGNORE: none; note it explicitly does not describe current code state.

5. docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
   WHY: canonical AT snapshot pointer (V4, vendored AAR, boundary rules).
   SOLVES: "which AT does AG consume, and what may import what".
   AUTHORITATIVE: YES (updated by each AT sync round).
   IGNORE: nothing; it is short by design.

6. docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md
   WHY: TopicSession terminology + layer ownership + DB tech freeze +
   V1/POST-V1 feature boundary (rewind/fork listed as POST-V1).
   SOLVES: session/run/durability semantics — the foundation for any future
   conversation-control work (Rewind preflight will build on this).
   AUTHORITATIVE: YES (FROZEN).
   IGNORE: the _修正1 companion file (correction copy; this file is the
   referenced name in SSOT text).

7. docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md
   WHY: the persistence implementation SSOT (Phases A-I + DEVICE-I); exact
   commit points D1/D2/D3, repair rules, device evidence summary.
   SOLVES: "how durability actually works end to end".
   AUTHORITATIVE: YES (named SSOT in AGENTS.md; 98 KB — read §1-2 + invariants).
   IGNORE: the two _修正 companion copies (superseded); phase-by-phase diff
   tables are point-in-time.

8. docs/architecture/AndroidAgent_总体架构.md
   WHY: the ag-0-era general architecture; the clearest single statement of
   dependency direction, Tool/Provider inversion, AGA/AGC roles, and the
   runtime-call-flow vs code-dependency distinction.
   SOLVES: foundational mental model of the whole system.
   AUTHORITATIVE: PARTIAL — FROZEN snapshot of ag-0 state.
   IGNORE: dependency claims for :ag-core/:ag-runtime (superseded, D1/D2);
   "Android Tool / AT integration NOT IMPLEMENTED" (superseded by AG.SHELL-1+);
   roadmap section §10 (historical).

HONORABLE MENTIONS (read only when needed):
  - docs/design/AndroidAgent_V2_VNext_UI_设计基线.md (UI-generation target).
  - docs/audit/AG.UI-BASELINE-AUDIT-1.md (current UI state -> VNext mapping).
  - docs/research/AG.MATURE-AGENT-SESSION-RESEARCH-0.md (session research basis).

==============================================================================
FILE: 13_do_not_use_as_ssot.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 13 DOCUMENTS THAT MUST NOT DRIVE NEW DESIGN
READ FOR HISTORY ONLY. DO NOT USE AS CURRENT SSOT.

1. docs/architecture/AndroidAgent_总体架构.md (PARTIAL)
   ag-0-era FROZEN snapshot. Dependency/module-state sections outdated
   (D1/D2; "AT integration NOT IMPLEMENTED"). Use only for foundational
   boundary concepts, and verify against POST-I freeze + Gradle before use.

2. docs/v2-research/pre-migration-workspace/**  (all 13 files)
   Pre-migration V1-era research workspace: AG.V2-0.ARCHITECTURE-RESEARCH,
   AG.V2-CONTROLLER-DECISION, old AT handoff copy, V1 module graph evidence.
   Preserved for migration provenance. Its "current module graph" and AT
   baseline facts describe the V1 tree and pre-V4 AT.

3. docs/audit/* (all 7 files)
   Point-in-time audits anchored to specific baseline HEADs
   (e.g. CORE-RUNTIME.BOUNDARY-AUDIT at ag-stable-1; AT-V4-Handoff-Audit
   pre-integration). Their "current" findings were true at their baseline.
   Superseded by later steps where they conflict with current code.

4. docs/migration/* + docs/regression/AG.ADB-LKG-REGRESSION.md
   Closed migration/regression records (V1->V2 baseline, ADB LKG root cause).
   Historical context for why V2 exists and why AT V4 replaced V3.

5. docs/implementation/steps/* (5 AG.0-era step docs)
   Earliest implementation records (AG.0 Core v0, PROVIDER-0, AGA-0,
   BASELINE-0, R1 async boundary). Historical.

6. docs/design/AG.SHELL-0_INTEGRATION_DESIGN.md
   Superseded by implemented AG.SHELL-1 (+ V4 integration). Design intent
   history only.

7. docs/design/AGENT-0_First_Real_Android_Agent_Vertical_Slice.md
   Historical design for the first live agent loop (implemented + closed).

8. docs/integration/AG.V2-2.AT-INTEGRATION.md
   Superseded by AG.AT-V4-INTEGRATION-1 + AT_TO_AG_HANDOFF_CURRENT (V4).

9. docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md
   Point-in-time handoff at post-I closure; facts superseded by UI-era steps.

10. AG.CONVERSATION-CONTROL-0 research docs (referenced in AGENTS.md;
    docs/research/AG.MATURE-AGENT-SESSION-RESEARCH-0.md)
    Research inputs. Valid for vocabulary (Stop/Interrupt/cancellation) but
    NOT a design authority for Rewind — Rewind needs its own preflight.

11. *_修正 / _修正1 companion copies (3 files in architecture/ + implementation/)
    Correction copies of earlier drafts. The files WITHOUT the suffix are the
    names referenced by SSOT text. Do not read the copies as current.

12. third_party/at-engine-handoff-v0/ + third_party/at-consumer-freeze-v1/
    SUPERSEDED AT bundles (V0, V3). Rollback reference only; not in the
    dependency graph (V3 m2 entry commented out).

==============================================================================
FILE: 14_current_architecture.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 14 CURRENT ARCHITECTURE (reconstructed)
Basis: settings.gradle.kts + build.gradle.kts (measured) + POST-I freeze +
AGENTS.md + TopicSession freeze + V4 handoff. HEAD = edfc603.

CODE DEPENDENCY (compile-time):

  ┌────────────────────────────────────────────────────────────────┐
  │  :app (AGA / Host / Product / Composition Root)               │
  │  ChatActivity · TranscriptCardAdapter · TranscriptModel       │
  │  HostActions · SessionHolder · HostStatusModel                │
  │  ProviderProfile/ModelCatalog/ModelDiscovery/SafResource      │
  │  Room persistence (DB v1) · AndroidOpenAiHttpTransport        │
  │  DebugBridge (debug) · AgentTrace · AgentInstructions         │
  └───┬─────────┬──────────┬───────────┬──────────┬────────────────┘
      │         │          │           │          │
      ▼         ▼          ▼           ▼          ▼
 :ag-runtime :ag-core  :provider-  :tool-*    :integration-at
 (lifecycle/ session)   oai-compat (shell/net/ (AtExecutionAdapter)
  session orch.)        (+jdk)      file)        │
      │            ▲ ▲ ▲ ▲ ▲ ▲ ▲                    ▼
      └────────────┘ │ │ │ │ │ │          AT V4 AARs (vendored)
                     │ │ │ │ │ │          com.at.engine.api:4.0.0
   (tools + provider implement Core contracts)
                     │
        :provider-openai-compatible-jdk --> :provider-openai-compatible

RUNTIME CALL FLOW (not code dependency):

  User
   │
   ▼
  Host / UI (ChatActivity + Composer)
   │  HostActions.send / stop / switchSession / providerSwitch
   ▼
  Agent Runtime (:ag-runtime)
   │  session selection · single-active-Run Job · interruptActiveRun()
   │  durable orchestration (TopicSessionStore) · hydration
   ▼
  AG Core (:ag-core)
   │  Agent Loop · Session/Run semantics · policy · events
   │  MessageDurability.commit (D1/D2/D3)
   ├──▶ ModelProvider (OpenAiCompatibleProvider -> Android transport)
   └──▶ ToolRegistry -> concrete Tool
            ├── :tool-android-shell ──▶ :integration-at ──▶ AT Engine ──▶ ADB/Android
            ├── :tool-network        ──▶ platform HttpsURLConnection (no AT)
            └── :tool-file           ──▶ app-private filesDir/agent (no AT)

  Persistence (side chain, Host-owned):
  Core MessageDurability ──▶ StoreMessageDurability (:app)
      ──▶ RoomTopicSessionStore ──▶ Room ──▶ SQLite (durable SSOT)

  Presentation (side chain, Host-owned):
  AgentEvent stream + Core currentHistory() ──▶ TranscriptModel
      ──▶ TranscriptCardAdapter ──▶ Cards (User/Thought/Assistant/
      ToolExecution/Failure/Interrupted)

INARIANTS VERIFIED AGAINST CODE:
  ✓ ag-core: 0 forbidden imports (Android/AT/app/runtime/tool)
  ✓ ag-runtime: 0 forbidden imports
  ✓ AT AARs referenced only by :integration-at (dep graph)
  ✓ no :tool-layer / ToolManager / framework types
  ✓ single transcript pipeline (no UI->DAO transcript path)
  ✓ single Runtime / single Core / single store (no duplicate owner)
  ✓ DB schema version still 1 (UI era made no schema change)

==============================================================================
FILE: 15_current_flows.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 15 CURRENT DATA / CONTROL FLOWS (5 paths)

A. NORMAL USER SEND
  owner: Host (input capture) -> Runtime (run lifecycle) -> Core (loop)
  path:
    Composer text -> ChatActivity -> HostActions.send(text)
      -> AgentRuntime.send():
         - session busy check (single-active-Run)
         - execution Job started (Runtime-owned)
      -> AgentCore.run():
         - tryBeginRun; D1: commit UserMessage (durability) then append
         - ModelProvider.complete (streaming; AndroidOpenAiHttpTransport)
         - D2: commit ModelMessage per turn; tool calls dispatched via
           ToolRegistry (Tool.execute); D3: commit ToolResultMessage
         - final: RunOutcome.Answer + RunFinished event
  boundaries: Host->Runtime = HostActions facade; Runtime->Core = direct
  (same AG Engine); Core->Provider/Tool = Core contracts.
  SSOT: durable = Room (messages); live = Core Session; UI = projection.

B. MODEL -> TOOL -> MODEL
  owner: Core (orchestration), Tool module (execution)
  path:
    ModelTurn.ToolCalls -> Core dispatch loop:
      ToolRegistry.lookup(name) -> Tool.execute(spec args)  [suspend]
        shell: AndroidShellToolImpl -> ShellConnection -> AtExecutionAdapter
               (:integration-at) -> AT one-shot exec -> ADB -> device
        network: NetworkToolImpl -> AppHttpFetcher (HttpsURLConnection)
        file: FileToolImpl -> PathResolver -> app-private FS
      ToolResult (success = mechanism OK) -> D3 commit -> back into history
      -> next ModelProvider.complete with tool results appended
  boundaries: Core never sees ADB/HTTP/FS details; tool args opaque string.
  SSOT: ToolResultMessage in Core history + Room.

C. STOP / INTERRUPT
  owner: Host (button) -> Runtime (Job cancel) -> Core (semantics)
  path:
    Stop tap (enabled only when RUNNING) -> HostActions.stop()
      -> AgentRuntime.interruptActiveRun():
         atomic per-Run USER claim (CAS) + executionJob.cancel()
      -> coroutine cancellation propagates: Core -> Provider transport
         (finally: disconnect) / in-flight Tool (cooperative)
      -> Core finalizes: RunOutcome.Interrupted, synthetic ToolResult per
         declared-uncompleted call (IN_FLIGHT_INTERRUPTED), exactly one
         RunFinished(Interrupted)
      -> send() converts (caller active && USER claim && Core committed)
         to Host-visible RunOutcome.Interrupted
    UI: INTERRUPTING -> "Stopped" block (NOT a Failure) -> IDLE.
  invariants: Host never cancels the Job itself; lifecycle cancel propagates
  (parent cancellation dominates, NOT converted); double-stop = no-op;
  partial stream = transcript-only (not in durable history beyond committed
  messages); session reusable at terminal.
  SSOT: durable committed messages unchanged; Interrupted = RunOutcome +
  event; transcript shows "Stopped".

D. TOPICSESSION PERSIST / RESUME / SWITCH
  owner: Runtime (orchestration), Room (storage), Core (hydrate)
  paths:
  persist: (see A) D1/D2/D3 commit -> StoreMessageDurability ->
    RoomTopicSessionStore -> Room (per message, seq contiguous).
  resume (app/process start):
    SessionHolder (process holder) -> ChatCompositionRoot ->
    AgentRuntime.initialize(): restore-or-create latest TopicSession
      -> TopicSessionStore.restore (Room read) -> MessageSerializer
      -> Core restoreSession(history) + dangling ToolCall repair
         (controlled; NO auto-replay; UNKNOWN outcomes marked)
      -> TranscriptModel reproject (REPLACE, idempotent) -> Cards
    invariants: 0 durable writes / 0 model calls / 0 tool exec on restore.
  switch (UI era, additive):
    session switcher dialog -> HostActions.switchSession(id)
      -> AgentRuntime.switchSession(id): load + repair + hydrate target
         session into Core, set current session
      -> reproject -> cards. DB untouched except no writes.
  SSOT: Room DB is durable SSOT; current session selection = Runtime.

E. UI TRANSCRIPT PROJECTION
  owner: Host (TranscriptModel + adapter)
  path:
    AgentEvent stream (Core) + Core currentHistory() (Runtime read-only seam)
      -> TranscriptModel (presentation projection; reasoning delta ->
         Thinking -> flips to Thought on finish; tool action/result blocks)
      -> TranscriptCardAdapter (RecyclerView) -> 6 card types with
         per-block Copy / Retry(as-new-turn) / Edit&Resend / collapse
  invariants: projection only — no persistence writes, no session
  selection, no tool/model calls; single pipeline (no UI->DAO transcript);
  reproject is REPLACE + idempotent (safe after switch/restore).
  SSOT: NOT here. Here is derived state only.

==============================================================================
FILE: 16_rewind_impact_map.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 16 REWIND IMPACT MAP (audit only — NO design)
Next candidate = AG.CONVERSATION-REWIND-0.PREFLIGHT.
Question answered: which FROZEN boundaries would a future Rewind most
likely touch? Based only on current frozen docs + code.

IMPACT AREAS (ordered by coupling):

1. TopicSession durable history (Room, DB v1)  [HIGHEST coupling]
   - Rewind semantics = "which messages count as current history".
     Current freeze: seq 0..N-1 contiguous, seq owner = persistence,
     durable SSOT = DB. Any rewind that mutates/invalidates durable
     messages touches: DATABASE_VERSION (reopen condition #2/#3),
     MessageSerializer, Records, RoomTopicSessionStore, Daos.
   - Frozen reopen conditions (POST-I §14) are the ONLY legitimate door:
     schema change / new durable domain requirement / verified bug.

2. Core Session + history semantics
   - Session.restoreHistory / append / pairing invariant (every ToolCall
     has a ToolResult). Rewind must preserve pairing for whatever
     truncated history remains; synthetic ToolResult rules (RUN-CONTROL)
     interact with "rewind after interrupt".
   - Core history is live SSOT after hydration; Core owns run exclusivity.

3. Runtime restore/hydrate + session selection
   - initialize()/switchSession() assume "latest durable state = truth".
     Rewind introduces a second notion of "current head" (logical head vs
     durable tail) — new Runtime state, possibly a new seam.
   - Dangling repair orchestration would need a rewind-aware variant
     (rewound ToolCalls whose external side effects DID happen = the same
     UNKNOWN-outcome problem DANGLING_AUTO_REPLAY=NO was created for).

4. MessageDurability contract (Core)
   - Currently append-only commit(sessionId, message). Rewind needs either
     a new contract member (e.g. truncate/invalidation) or a new durable
     domain concept (rewind marker / versioned history). Both are
     "new durable domain requirement" class changes.

5. Transcript projection + UI
   - Cards are derived; a rewind reprojects. Low risk technically, but the
     frozen projection boundary (TranscriptModel must NOT own policy) means
     rewind DECISION must live in Runtime/Core, never in the adapter.
   - Retry/Edit&Resend already reuse send(); rewind must not be implemented
     as a display-only truncation (explicitly forbidden pattern).

6. Run / interrupt semantics
   - Interrupt already produces partial-transcript-only behavior +
     synthetic results. Rewind + interrupt interaction (rewind to before an
     interrupted tool whose external effect is UNKNOWN) is the sharpest
     semantic edge.

7. Host actions + debug bridge
   - New HostActions entry (rewind) + DebugProtocol command; debug bridge
     must route through the same Runtime seam (single-pipeline invariant).

BOUNDARIES THAT STAY INTACT UNDER ANY REASONABLE REWIND:
  - AG Core -X-> Android/AT (unchanged)
  - AT knows nothing about TopicSession (unchanged)
  - Tool/Provider contracts (unchanged)
  - Single transcript pipeline (rewind must flow through it)

KEY RISK REGISTERED: external side effects of already-executed tools.
The architecture already answers "unknown outcome = never auto-replay";
rewind inherits this. Any rewind design that implies undoing external
effects violates the frozen NOT_PROMISED position (CONVERSATION-CONTROL-0).

NO REWIND DESIGN OR IMPLEMENTATION PERFORMED THIS ROUND.

==============================================================================
FILE: 17_final_verdict.txt
==============================================================================

AG.ARCH-DOC-AUDIT-0 — 17 FINAL VERDICT

VERDICT = PASS_WITH_DRIFT

Drift found: 4 LOW items (D1 ag-core dep doc, D2 ag-runtime dep doc,
D3 pre-UI freeze scope note, D4 stale AT snapshot block in AGENTS.md).
No MEDIUM / HIGH / ARCHITECTURE_BLOCKER drift.
No duplicate owner. No duplicate pipeline. No forbidden dependency edge.
Code matches the newest frozen docs on every checked boundary.

DOCUMENTS_SCANNED        = ~100 (.md/.txt, excl. build artifacts)
ARCHITECTURE_DOCS_FOUND  = 24 core architecture docs + 20 step/audit docs
LIVE_SSOT_COUNT          = 4 (AGENTS.md, Master_Roadmap,
                           AT_TO_AG_HANDOFF_CURRENT,
                           TOPIC-SESSION-PERSISTENCE-0_FINAL)
FROZEN_BASELINE_COUNT    = 7 (Capability_Map_v1.0, POST-I freeze,
                           TopicSession FREEZE-0, Durability PREFLIGHT-0,
                           总体架构, BASELINE-FREEZE-0, ENGINE-BOUNDARY-FREEZE,
                           V1-KNOWN-GOOD-FREEZE = 8 total)
HISTORICAL_COUNT         = ~25 (step docs, audits, migration, regression,
                           pre-migration workspace)
SUPERSEDED_COUNT         = 6 (3 修正 copies, 2 old AT bundles,
                           SHELL-0 design, old V2-2 integration doc,
                           old handoff copy in v2-research)
UNKNOWN_COUNT            = 0

TOP RE-READ SET          = see 12_recommended_reread_set.txt (8 docs)
DO-NOT-USE-AS-SSOT SET   = see 13_do_not_use_as_ssot.txt (12 groups)

ARCHITECTURE HEALTH:
  AG Core        = HEALTHY (0 forbidden imports; minimal coroutines dep
                   is step-authorized; doc drift only)
  Agent Runtime  = HEALTHY (single-domain orchestration; not a god object;
                   additive UI-era seams within frozen ownership)
  Provider       = HEALTHY (one contract, one impl, two transports;
                   UI-era catalog types correctly Host-scoped)
  Tool Layer     = HEALTHY (one module per capability; no framework;
                   AT isolation verified)
  Persistence    = HEALTHY (frozen chain intact; DB v1 unchanged by UI era;
                   restore invariants device-verified)
  AT Integration = HEALTHY (V4 vendored AAR; single consumption point;
                   M1 host-import debt tracked, LOW)
  Host / UI      = WATCH (ChatActivity growing; no violation; HostActions
                   clean facade; single pipeline verified)
  Documentation  = DRIFT (LOW) — 4 low items; recommend one small
                   SSOT-normalization pass after Controller review

INvariants HOLDS: AG=Intelligence, AT=Execution; dependency directions;
anti-abstraction list; single transcript SSOT chain; no AT in Core/Runtime.

PRODUCTION_CODE_CHANGED = NO
TEST_CODE_CHANGED       = NO
GRADLE_CHANGED          = NO
DB_CHANGED              = NO
COMMIT / TAG / PUSH     = NO

NEXT = CONTROLLER_REVIEW
STOP = YES
