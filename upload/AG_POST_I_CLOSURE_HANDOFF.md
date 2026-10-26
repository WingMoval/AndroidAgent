# AG_POST_I_CLOSURE_HANDOFF — Handoff to the Next Phase (UI + Release Preparation)

Quick-read handoff. History (Phases A-I, DEVICE-I) lives in
`docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md` (§1-§20)
— do not re-read it all; this file is the current-state pointer.

## CURRENT ARCHITECTURE

```text
AndroidAgent = AG Engine + Tool Layer + Host/Product

:ag-core        pure JVM intelligence/domain (Agent loop, Session/Run, Message,
                ModelProvider contract, Tool contract, ToolRegistry, events,
                RunOutcome, MessageDurability contract). NO project deps.
:ag-runtime     pure JVM host-neutral lifecycle/session orchestration (AgentRuntime:
                current-session selection, single-active-run Job, interrupt,
                currentHistory seam; persistence contracts: TopicSessionStore,
                Records, MessageSerializer — storage-neutral).
:provider-openai-compatible / -jdk   OpenAI-compatible ModelProvider + JDK transport.
:tool-android-shell  shell Tool; AT via :integration-at (api).
:tool-network        fetch Tool (HTTPS GET, SSRF guard); no AT.
:tool-file           read/search/list/stat Tool (APP_SELF, READ-only); no AT.
:integration-at      unified AT consumption point (ONLY module on AT V4 AARs).
:app (AGA)          Host: Android lifecycle, Room persistence impl, Composition Root,
                    SessionHolder, TranscriptModel, ChatActivity/UI, provider wiring,
                    tool registration, DebugBridge (debug builds).
```

## CURRENT MODULE MAP

9 subprojects + root (see `docs/evidence/ag_post_i_closure/01_module_inventory.md`).
Dependency direction is frozen and verified: Core <- Runtime <- Host;
Core <- concrete tools/providers <- Host composition; AT only via :integration-at.

## CURRENT VERIFIED CAPABILITIES

```text
- Multi-turn Agent loop with real model + tool calls (device-verified, AGENT-0.LIVE)
- Active Run Interrupt/Stop (production Job cancellation, deterministic L1+L2 verified)
- Tool Layer: android_shell (AT one-shot), network fetch, file read family (L1+L2+L3 verified)
- Provider streaming + observability (Thinking->Thought, deltas, tool visibility)
- Debug Bridge (debug builds; same AgentRuntime.send path)
- TopicSession durable persistence (Room/SQLite, version 1):
    DURABLE_RUNTIME_RESUME = PASS
    TRANSCRIPT_REPROJECTION = PASS
    FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED
    (kill/force-stop -> relaunch -> visible transcript restore -> continue;
     new session preserves old; 0 durable writes on restore; 0 model calls on restore)
- Regression baseline (JUnit XML actuals): ag-core 52 / ag-runtime 88 / app 171 /
  provider-jdk 26, 0 failures; assembleDebug PASS
```

## FROZEN BOUNDARIES

Full freeze: `docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md`

Key lines:
- AG Core: no Android/Room/App/AT/concrete-tool/concrete-provider (Gradle + import verified)
- Agent Runtime: same, storage-neutral persistence contracts only
- TranscriptModel: presentation projection only
- Restore: DB -> Runtime/Core -> Presentation (single pipeline)
- AG = Intelligence, AT = Execution; AT only via :integration-at
- D1/D2/D3, controlled recovery, no auto-replay of unknown tool outcomes

## KNOWN DEBT (non-blocking)

```text
M1. Host direct AT V4 composition imports (3 files) — future cleanup via adapter
M2. AGENTS.md current-state not yet synced with persistence line (doc sync)
M3. AgentRuntimeInterruptTest I11 full-matrix flake (KNOWN TEST STABILITY DEBT)
M4. 86 uncommitted worktree files (37 pre-existing modified + 46 untracked) —
    Git normalization is a separate authorized task
```

## DO NOT REOPEN (TopicSession persistence core)

Reopen ONLY for: verified correctness bug / DB migration / new durable domain the
schema cannot represent / security-privacy defect / multi-process requirement /
explicit product requirement violating a frozen assumption.
See freeze doc §14. "Nice to rewrite" is not a reason.

## NEXT DEVELOPMENT DIRECTION

```text
TOPIC SESSION FOUNDATION = DONE / FROZEN

NEXT MAJOR PRODUCT PHASE = UI + RELEASE PREPARATION
  (recorded scope, NOT to be implemented in the audit round):
  - UI visual consistency, chat transcript presentation, tool-call presentation
  - loading/running/error states, settings/config UX, model/provider UX if exposed
  - release version identity, app naming/icon/about/version
  - permissions / first-run UX, release build sanity, device smoke test, docs/changelog

AFTER UI / RELEASE BASELINE:
  return to AG MAINLINE capability development:
  Agent Loop capability, Model/Provider lifecycle, Tool Layer expansion,
  Android execution integration with AT, Context/Skill/planning capability,
  APK + future CLI productization
  (NOT more persistence work — the persistence foundation is frozen)
```

## Quick pointers

```text
Freeze doc        docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
Closure evidence  docs/evidence/ag_post_i_closure/ (00-08)
Implementation SSOT  docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md
Architecture SSOT  docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md
                   docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0.md
Device-I evidence  docs/evidence/ag_topic_session_device_i/
Device fixtures    tools/test-fixtures/ (echo_fixture, ui_helper, debug_client, db_inspect)
```
