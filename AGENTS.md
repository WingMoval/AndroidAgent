# AndroidAgent — development rules

## Repository roles

```text
AndroidAgent-V2      = ACTIVE DEVELOPMENT SOURCE OF TRUTH
                       (this repository)
AndroidAgent         = V1 FROZEN_KNOWN_GOOD
                       (reference / rollback / evidence source only)
AndroidAgent-Public  = release / export downstream only
```

Flow is one-directional:

```text
AndroidAgent-V2 (development)  ->  explicit Release Step  ->  *-Public
```

Forbidden:

- developing directly in `-Public`
- fixing a bug in `-Public` and copying it back into development
- treating V2 and `-Public` as dual source of truth

Do not create `-Public` for symmetry. Create it at a real release node.

V1 immutability (`D:\codebank\AndCode\AndroidAgent`, tag
`AG.V1-KNOWN-GOOD-FREEZE-V1`): allowed use = reference / rollback / evidence.
Forbidden: normal feature development, bugfix-first-then-copy-to-V2, parallel
active development. V1 may only be modified by an
explicitly-authorized critical freeze-line fix.

## Architecture rules

```text
AG Core  -X->  AT Core
AG Core  -X->  Android framework
AG Core  -X->  ADB / JNI
AG Core  -X->  com.androidterminal.*
```

AG Core reaches Android execution only through the Tool contracts.

```text
AndroidAgent = AG Engine + Tool Layer + Host/Product

AG Engine     = AG Core + Agent Runtime
Tool Layer    = concrete tool modules (one per capability)
                :tool-android-shell   (AG.SHELL-1, AT Engine Handoff)
                :tool-network         (AG.NETWORK-0, HTTPS fetch, no AT)
Host/Product  = AGA (Composition Root): constructs + registers tools
```

Module dependency direction is frozen:

```text
tool-* -> ag-core        allowed (a tool implements the Tool contract)
ag-core  -> tool-*       forbidden
ag-runtime -> tool-*     forbidden
ag-core  -> Android HTTP / any tool   forbidden
```

There is NO `:tool-layer` central module, NO ToolManager / ToolRuntime /
ToolBackend / ToolFactory framework, and NO NetworkBackend / ShellNetworkBridge
/ AT adapter. A tool is a concrete class implementing the frozen `Tool`
contract; the Host registers concrete instances into the `ToolRegistry`.
Adding a new capability = adding a new `:tool-*` module (or a new class in an
existing tool module) that the Host registers. Nothing in AG Core or Agent
Runtime is modified to add a tool.

Concrete Android Tool is the only AndroidAgent execution layer allowed to connect
to an Android-specific execution backend. It may use the Android APIs directly,
connect to the AndroidTerminal execution boundary, or combine both. `:tool-network`
is the counter-example that is correct: it uses platform `HttpsURLConnection`
directly and has NO AT dependency.

```text
AG Core / Agent Runtime / AGA   must not depend on AT implementation
```

`AT Core` lives in the AndroidTerminal project. AndroidAgent must not vendor,
copy, or re-implement it. If AG Core appears to need it, the design is wrong:
introduce a tool instead.

The AT integration mechanism is frozen and implemented (`AG.SHELL-1`): the
concrete Android Shell Tool connects to the AT Engine through the vendored
`third_party/at-engine-handoff-v0` Handoff Bundle v0. AG does not wait for any
future AT work (Pairing, Wireless Bootstrap, PTY, streaming): the AG mainline
and the AT mainline evolve independently.

## Package boundary

```text
com.androidagent.core      frozen
```

Forbidden: `com.androidterminal.*`, `com.androidagent.app.*`.
AG Core is product-independent; AGA, AGC, and any future frontend must be able
to reuse it unchanged.

## Development discipline

```text
No opportunistic refactor
No future-feature scaffolding without real need
Logical boundary before unnecessary abstraction
Do not implement empty architecture layers merely for symmetry
```

Adding a module, abstraction, or dependency requires a real consumer today,
not a hypothetical one. One execution backend does not justify a backend
abstraction layer.

## Verification

Any change must keep these green:

```bash
./gradlew :ag-core:test
./gradlew build
```

New logic in AG Core requires a JVM unit test. AG Core is testable precisely
because it is Android-free; do not erode that.

## Git

```text
No commit / push unless the task explicitly allows it.
```

Branch on demand. Do not pre-build a multi-branch Git flow for a single-line
project.

## Current frozen state

```text
AG.ROADMAP-0    PASS       Master Capability Roadmap SSOT created at
                                 docs/roadmap/AndroidAgent_Master_Roadmap.md:
                                 registry + dependency graph + priority/status
                                 model + execution queue + deferred registry +
                                 intake/update protocol. Original 13-domain
                                 survey classification was later normalized by
                                 REVIEW-1/REVIEW-1A. Docs-only; no production
                                 code, no sub-agents.
AG.ROADMAP-0.   PASS       Roadmap mapped to Controller-approved Architecture
REVIEW-1                   v1.0: 11 v1.0 domains, Run Control de-layered,
                           Status enum pure, P0 corrected, dependency DAG
                           acyclic, capability coverage MISSING=0, queue
                           recomputed. Controller follow-up found Stable-ID
                           reuse in the normalized registry; architecture and
                           coverage conclusions remained valid. ID mapping is
                           superseded by REVIEW-1A below.
AG.ROADMAP-0.   PASS       Stable-ID Integrity Repair. All pre-REVIEW-1
REVIEW-1A                  capability IDs retain their historical capability
                           identity; merged rows split without recycling IDs;
                           PROVIDER-* namespace restored; AT-001..004 retained;
                           TOOL-006=output bounding, TOOL-007=AGENT-0.CONTRAST,
                           STATE-004=compaction, HOST-004=AGC, ORCH-001=
                           Multi-agent/Subagents, AUTH-005=SSRF guard.
                           Capability coverage remains MISSING=0; hard
                           dependency DAG remains acyclic; execution queue
                           remains CURRENT=NONE with NEXT=AG.FILE-0.
                           Docs-only; Architecture v1.0 unchanged; no
                           production code; no sub-agents.
AG.0            FROZEN   (tag ag-0)
AG.PROVIDER-0   FROZEN   (tag ag-provider-0)
AGA-0           device chat PASS (strict multi-turn memory PENDING)
AGA-0.ARCH      DONE
AG.STABLE-1     FROZEN   (tag ag-stable-1)  current working baseline
AG.ENGINE-0     FROZEN   (tag ag-engine-0)  Engine = Runtime + Core, logical
                                boundary audited, no production normalization
AG.SHELL-1      VERIFIED :tool-android-shell integrated with AT Engine Handoff
                                Bundle v0; AndroidShellToolImpl registered in AGA;
                                real-device Tool path S1-S5 PASS
AGENT-0.PREFLIGHT PRE-LIVE READY: AGENT_INSTRUCTIONS_V0 frozen (Host-owned,
                                version+hash); android_shell ToolSpec audited
                                (WHAT/WHEN/INPUT/OUTPUT); Model Request Shape +
                                ToolResult feedback verified; minimal Agent Trace
                                (AgentTraceCollector in :app, redacted) ready.
                                AG Core / Agent Runtime / AT Engine unchanged.
AGA.DEV-CONFIG-0 IMPLEMENTED: AGA auto-loads filesDir/provider.local.json at
                                startup (reuses OpenAiCompatibleConfigLoader.parse);
                                missing/invalid -> manual UI fallback; manual Apply
                                never writes back the file. AG Core / Runtime /
                                AT Engine unchanged.
AGA.HOST-CONTROL-0 PASS (pre-live host baseline): Provider node selector
                                (EMG/OpenRouter) switch-is-active; AT startup
                                auto-connect + manual Reconnect; blank-key Apply
                                401 fixed; run-phase status; single AGA APK
                                (noat flavor & atEnabled & connectTo removed);
                                AG Core / Runtime / AT Engine / Handoff unchanged.
AGENT-0.LIVE    VERIFIED  real adaptive Agent tool loop on device (OpenRouter /
                                deepseek-v4-flash-0731): model autonomously chose
                                android_shell, observed foreground app P, then used
                                P in a dependent ToolCall, final answer matched
                                ground truth. NEXT = AGENT-0.CONTRAST.
AG.DEBUG-0      PASS      PC Debug Bridge & live execution stream (debug builds
                                only): TCP JSONL on 127.0.0.1:45678; PC input
                                routed through the SAME AgentRuntime.send; passive
                                PC client observes the live AgentEvent stream
                                including UI-originated runs (T3); busy reused
                                (T4); disconnect/reconnect safe (T5/T6); release
                                has no listener (T7). :app only; AG Core /
                                Runtime / Tool / Provider / AT Engine unchanged.
AGA.OBSERVABILITY-0 PASS      Active Provider Truth & Streaming Agent
                                Transcript: provider selector is switch-is-active,
                                the screen shows the ACTIVE provider's safe
                                endpoint, failed switches revert honestly;
                                provider always streams (stream:true), reasoning
                                deltas -> Thinking -> Thought (same item),
                                Assistant content streams incrementally, tool
                                actions/results/failures visible in the
                                transcript; ModelStreamObserver is observation
                                only (final ModelTurn semantics unchanged);
                                DebugBridge JSONL v0 gains reasoning/content
                                delta kinds. JVM regression green (ag-core 41,
                                ag-runtime 8, provider-openai-compatible 59,
                                provider-openai-compatible-jdk 25,
                                tool-android-shell 32, app 148); device
                                acceptance PASS (OpenRouter + EMG routes,
                                Thinking->Thought, tool/result/failure
                                 visibility, debug bridge, release build; EMG
                                 content stream deferred until EMG key renewed).
                                 Default provider node is now OPENROUTER.
                                 NEXT = AGENT-0.CONTRAST.
                                 (Two legacy bugs surfaced later on a real device
                                  -- Thinking not flipping to Thought when content/
                                  toolcall blocks follow reasoning, and debug-
                                  originated runs showing no User line -- are
                                  CLOSED by AGA.OBSERVABILITY-1 below.)
 AGA.OBSERVABILITY-1 PASS      Transcript Thinking->Thought flip fixed for real
                                 multi-block turns (onReasoningFinished now flips
                                 the most recent still-THINKING Thinking block, not
                                 blocks.last); debug-bridge-originated runs now
                                 record a User block (DebugBridge.userInputSink ->
                                 TranscriptModel.onUser) exactly like a UI send.
                                 :app only (TranscriptModel / DebugBridge /
                                 ChatActivity + tests). L1 green (TranscriptModel
                                 24: T20 reasoning+content flip, T21 reasoning+
                                 content+toolcall flip, T22 no-reasoning no-op);
                                 regression green (app 92, ag-core, tool-android-
                                 shell, tool-network, assembleDebug); real-device
                                 debug-path PASS (transcript shows User line +
                                 Thought not Thinking..., followed by Assistant).
                                 AG Core / Runtime / Tool / Provider / AT
                                 unchanged; no new AgentEvent, no streaming-protocol
                                 change, no second Thinking block. NEXT = none.
  AG.NETWORK-0     PASS        Tool Layer formalized (AndroidAgent = AG Engine +
                                 Tool Layer + Host); :tool-network added,
                                 NetworkToolImpl (fetch) registered in AGA; Direct
                                 Tool Validation (DebugBridge tool_invoke, no LLM)
                                 wired. L1 JVM green (tool-network 78: args/SSRF
                                 v4+v6/DNS/redirect/HTTP status/content-type/
                                 bounds/cancellation + PublicIpGuard/UrlValidator/
                                 HtmlToText cores). Regression green (ag-core,
                                 tool-android-shell, app assembleDebug). L2
                                 real-device direct fetch PASS (example.com 200 +
                                 HTML extract; 127.0.0.1 DESTINATION_BLOCKED;
                                 http UNSUPPORTED_SCHEME; 404 success=true;
                                 DNS_FAILURE). L3 real-agent E2E PASS
                                 (OpenRouter deepseek-v4-flash-0731: model
                                 autonomously called network, ToolResult entered
                                 history, FinalAnswer depended on fetched content,
                                 OBSERVATION_DEPENDENCY=PASS); android_shell
                                 regression PASS (still autonomously selectable).
                                  AT_DEPENDENCY=NONE; AT/Core/Runtime/Provider
                                  unchanged; no new 3rd-party dep. NEXT = AG.FILE-0.
  AG.FILE-0        PASS        :tool-file added (Tool Layer sibling of shell+network);
                                  FileToolImpl (read/search/list/stat) registered in
                                  AGA; APP_SELF root = filesDir/agent/; PathResolver
                                  (canonical + root confinement + traversal + symlink
                                  escape); UTF-8 + BOM strip + REPLACE; binary NUL-sniff
                                  (8KB window) -> isBinary=true, empty content; bounds
                                  (1MiB read, 30k chars, 200 search matches, 500 list
                                  entries). READ-only: no write/edit/delete. L1 JVM
                                  green (tool-file 55: PathResolver 13 + TextCodec 4 +
                                  binary 3 + FileToolImpl 35: args/read/search/list/
                                  stat/bounds/binary/escape; 3 symlink tests gated
                                  Linux). Regression green (ag-core, ag-runtime,
                                  tool-android-shell, tool-network, app). L2
                                  real-device direct tool_invoke PASS (16/16: F1-F4
                                  basic read/stat/list; F5-F9 security boundary
                                  PATH_OUTSIDE_ROOT/NOT_FOUND/NOT_A_FILE/
                                  NOT_A_DIRECTORY; F10-F12 BOM/binary/maxBytes;
                                  F13-F16 search file/subtree + offset/limit +
                                  maxMatches truncation). L3 real-agent E2E PASS
                                  (OpenRouter deepseek-v4-flash-0731: model
                                  autonomously called file read facts.txt, ToolResult
                                  entered history, FinalAnswer contained SEVENTEEN
                                  from file content, OBSERVATION_DEPENDENCY=PASS);
                                   android_shell regression PASS. Test data created
                                   via androidTest fixture (app UID, filesDir access),
                                    cleaned after. NEXT = AG.RUN-CONTROL-0.PREFLIGHT
                                    (per recomputed Execution Queue).
  AG.CONVERSATION-CONTROL-0 VERIFIED Focused comparative research on Conversation
  .RESEARCH / .CLOSEOUT      Control (Stop/Interrupt, Rewind/Undo/Redo, Fork/Branch,
                              Transcript Blocks, Copy/Selection, Deterministic Model
                              Harness, External Side Effects). 5 agent products
                              (OpenCode, Codex CLI, Claude Code, Gemini CLI, Copilot
                              CLI) + OpenAI protocol family + wire protocols. 15
                              controller questions answered. Protocol Matrix, Product
                              Capability Matrix, Semantic Vocabulary, AndroidAgent
                              Mapping, Transcript Block design, User Block Actions,
                              Deterministic Provider design, Risk Analysis produced.
                              CLOSEOUT corrected two OpenAI protocol facts:
                              (1) Responses background=true DOES have a server-side
                              cancel endpoint (POST /responses/{id}/cancel) — the
                              blanket "no OpenAI HTTP cancel" claim was wrong;
                              (2) Realtime response.cancel IS server-acknowledged
                              (response.done status=cancelled), not "client-side
                              only". Chat Completions + Responses sync/normal-stream
                              remain client-side transport termination, which is
                              what AndroidAgent's current Chat Completions provider
                              uses (implementation direction UNCHANGED; no Responses/
                              Realtime migration implied). Terminology frozen at 3
                              layers: UI=Stop, AG semantic=Interrupt/Interrupted,
                              Runtime=cancellation/CancellationException. Stable-IDs
                              audited against Roadmap (all aligned; no renumber).
                              Redo=DEFERRED (not never); external side-effect
                              rollback=NOT_PROMISED. Docs-only; no production code;
                              no architecture v1.0 change; no sub-agents. Evidence:
                              docs/research/AG.CONVERSATION-CONTROL-0_Focused_Comparative_Research.md
                               + AG.CONVERSATION-CONTROL-0.RESEARCH.CLOSEOUT.md.
                               NEXT = AG.RUN-CONTROL-0.PREFLIGHT (unchanged).
  AG.RUN-CONTROL-0     VERIFIED   Active Run Interrupt preflight (RUN-003/004 +
  .PREFLIGHT             CLOSED   HOST-005 contract + CORE-007 history-on-interrupt
  + .CLOSEOUT                slice). Current-code audit (not research-doc guess):
                               today the HOST owns the Run Job (ChatActivity
                               lifecycleScope / DebugBridge bridgeScope), the
                               Runtime has no interrupt entrypoint, cancellation
                               escapes as CancellationException with NO RunOutcome
                               and NO terminal event, but it DOES reach the
                               transport (finally disconnect) and DOES release
                               the busy flag. Frozen: (1) RUN-003 owner =
                               AgentRuntime (holds the active-Run Job +
                               interruptActiveRun()); Host must NOT hold the Job.
                               (2) NO new manager/module. (3) ModelProvider
                               contract UNCHANGED (suspend + coroutine cancellation
                               suffices). (4) Transport already cancel-safe
                               (withContext + finally disconnect); no background
                               read after cancel. (5) RUN-004 outcome =
                               RunOutcome.Interrupted (NOT a Failure); terminal
                               event = reuse RunFinished(Interrupted), exactly one.
                               (6) CancellationException boundary: rethrow below
                               Core, translate in Core ONLY on a USER reason tag;
                               lifecycle/shutdown cancel escapes (not fabricated
                               as Interrupted). (7) history-on-interrupt: partial
                               stream is transcript-only (NOT in Session); on
                               interrupt during a tool-call turn, append synthetic
                               T(interrupted) per declared-but-uncompleted call to
                               preserve the pairing invariant. (8) In-flight tool
                               = Policy B (cooperative cancel; no per-tool policy).
                               (9) External side effects NOT rolled back. (10)
                               Session reusable when the Run coroutine reaches
                               terminal (not at tap). (11) Double interrupt =
                               idempotent no-op; no-active-run = structured no-op.
                               (12) Race = first terminal transition wins (single
                               latch); interrupt-vs-failure = USER tag wins.
                               (13) Deterministic-first: extend ag-runtime
                               ScriptedProvider (has a gate); NO new module;
                               REAL_LLM not required for first build; L1 I1-I10 +
                               L2 transport disconnect test. (14) HOST-005 Stop
                               contract: direct Stop, no confirmation; IDLE /
                               RUNNING / INTERRUPTING / TERMINAL (INTERRUPTING is
                               Host-transient only, not a Run state). No production
                               code, no architecture v1.0 change, no sub-agents.
                               RUN-003/004 + HOST-005 -> PREFLIGHT_READY;
                               CORE-007 stays RESEARCH_REQUIRED (arbitrary rollback
                                 not frozen). CLOSEOUT + FINAL CONTROLLER CORRECTION
                                 (5 corrections C1-C5; C1 corrected so a cancelled
                                 child is NOT treated as value-returning): (C1)
                                 execution child != send caller; interruptActiveRun()
                                 cancels ONLY the structured execution child, which
                                 TERMINATES AS CANCELLED (it does not return a value);
                                 Core finalizes the Interrupted semantic + emits
                                 exactly one RunFinished(Interrupted) + synthetic T;
                                 the STILL-ACTIVE send() caller then converts THIS
                                 USER child cancellation to a Host-visible
                                 RunOutcome.Interrupted (a value) — IFF caller still
                                 active AND per-Run USER metadata committed AND Core
                                 committed the Interrupted finalization; a raw
                                 CancellationException is NOT the user-Stop Host
                                 terminal API; PARENT_CANCELLATION_DOMINATES (a
                                 lifecycle-cancelled caller propagates, never
                                 converted). (C2) the Runtime owns a reference/
                                control handle but the execution child stays
                                STRUCTURALLY parented to the send caller (NO
                                GlobalScope / detached SupervisorJob / background
                                Run); host lifecycle cancel still cancels the child
                                transitively (RUN-005/006 NOT opened). (C3)
                                interrupt metadata is created/carried by the Runtime
                                and travels PER-RUN into Core (Runtime -> Core);
                                Core MUST NOT query AgentRuntime or any Runtime
                                mutable state (ag-core -> ag-runtime forbidden).
                                (C4) synthetic ToolResult distinguishes
                                IN_FLIGHT_INTERRUPTED (external state MAY BE
                                UNKNOWN; not "failed"/"not executed"/"rolled back")
                                vs NOT_EXECUTED_DUE_TO_INTERRUPT (honestly not
                                started); existing ToolResult schema unchanged, only
                                the error-string semantic is frozen. (C5) active
                                handle claim/clear must be ATOMIC (AtomicReference
                                CAS / minimal critical section); bare @Volatile
                                read-null-cancel is REJECTED; one concurrent caller
                                wins (second = no-op). InterruptReason {USER,
                                HOST_LIFECYCLE} enum REMOVED (REASON_ENUM_NOW = NOT
                                REQUIRED; V0 ships RunOutcome.Interrupted, reason
                                only if the mechanism needs a per-Run type).
                                 Next-build tests I11-I15 specified (I11 user-stop
                                 structured return: child terminates cancelled, send
                                 converts; I12 parent/lifecycle propagation: no
                                 conversion; I13 concurrent double-interrupt; I14
                                 in-flight tool truthfulness; I15 Stop-vs-lifecycle
                                 race: parent cancellation dominates).
                                No production code, no architecture v1.0 change, no
                                sub-agents. RUN-003/004 + HOST-005 -> PREFLIGHT_READY;
                                CORE-007 stays RESEARCH_REQUIRED. Evidence:
                                docs/research/AG.RUN-CONTROL-0.PREFLIGHT_Interrupt_Semantics_and_Deterministic_Verification_Freeze.md
                                + AG.RUN-CONTROL-0.PREFLIGHT.CLOSEOUT.md.
                                NEXT = AG.RUN-CONTROL-1.DETERMINISTIC (authorized,
                                NOT started; awaiting Controller Review).
  AG.RUN-CONTROL-1     VERIFIED   Active Run Interrupt production build +
  .DETERMINISTIC               deterministic verification (RUN-003/004 +
                               HOST-005). Built + L1-green, then Controller Review
                               found the cooperative-flag tests did NOT verify the
                               production Stop -> execution Job cancellation path
                               (interruptActiveRun() set a per-Run flag; it never
                               cancelled a real execution Job). RE-OPENED and
                               RESOLVED by AG.RUN-CONTROL-1.DETERMINISTIC.FIX
                               below. The cooperative flag/checkpoint Stop
                               mechanism is NOT the production mechanism. Evidence
                               (point-in-time):
                               docs/implementation/AG.RUN-CONTROL-1.DETERMINISTIC.md
                               (superseded for the production-mechanism claim by
                               the FIX doc below).
  AG.RUN-CONTROL-1     VERIFIED   Production execution-Job cancellation +
  .DETERMINISTIC.FIX               deterministic re-verification (RESOLVES the
                               Controller blocker). AgentRuntime owns a REAL
                               structured execution-child Job (kotlinx-coroutines-
                               core added to :ag-runtime production — the real
                               consumer is RUN-003 execution Job ownership/cancel,
                               not scaffolding). interruptActiveRun() atomically
                               claims the per-Run USER interrupt and cancels that
                               execution Job (executionJob.cancel()); coroutine
                               cancellation propagates naturally Core -> Provider/
                               Tool -> Transport. RunInterruptSignal is REDUCED to
                               per-Run semantic metadata (was THIS Run
                               user-interrupted?) — NOT the cancellation transport
                               (ensureNotInterrupted polling removed).
                               ModelRequest.interruptSignal REMOVED (provider
                               receives normal coroutine cancellation). The
                               reflective Job read is REMOVED (standard
                               coroutineContext.isActive). Condition C is PER-RUN
                               (ActiveRunControl finalization acknowledgement via a
                               Core-owned callback); AgentCore.lastRunOutcome
                               REMOVED. send() converts the USER child cancellation
                               to Host-visible RunOutcome.Interrupted IFF A (send
                               caller active) && B (per-Run USER claim) && C (Core
                               committed THIS Run's finalization); a lifecycle-
                               cancelled caller propagates CancellationException
                               (PARENT CANCELLATION DOMINATES). Verification: I1-
                               I15 reworked to Job-cancellation trigger; M1 (real
                               execution Job ownership), M2 (Stop does not depend
                               on the next Core checkpoint), M3 (production
                               provider cancellation not swallowed); L2 transport
                               cancel-safety; L2-FULL USER-STOP E2E (real
                               runtime.interruptActiveRun() -> real provider ->
                               real transport; send returns Interrupted; exactly
                               one RunFinished(Interrupted); session reusable).
                               ag-core 41, ag-runtime 26, provider 59,
                               provider-jdk 27, tool-android-shell 32,
                               tool-network 156, tool-file 110, app 234 (0
                               failures); assembleDebug PASS; global build
                               FAIL_UNRELATED (pre-existing AT AAR lint). HOST-005
                               Stop UI unchanged (direct Stop; really cancels the
                               execution Job). No Provider/Tool contract change; no
                               new module; no detached scope; Core never queries
                               Runtime; no Architecture v1.0 change. CORE-007 stays
                               RESEARCH_REQUIRED. Evidence:
                               docs/implementation/AG.RUN-CONTROL-1.DETERMINISTIC.
                               FIX.md.
  AG.HOST-STOP-1         VERIFIED     HOST-005 Stop / Cancel UI (real user-facing
                                 surface) + OpenAI-compatible test fixture for
                                 real-device acceptance. Pre-step SSOT correction:
                                 the previous RUN-CONTROL VERIFIED proved the Host
                                 contract and Runtime backend but no actual
                                 user-facing Stop UI existed on device, so
                                 HOST-005 was re-opened to IN_PROGRESS (RUN-003/004
                                 stay VERIFIED). AGA exposes a real Stop control
                                 wired ONLY to AgentRuntime.interruptActiveRun()
                                 (Host never cancels the Job itself). Host
                                 presentation states IDLE / RUNNING / INTERRUPTING:
                                 RUNNING -> Stop enabled; Requested -> INTERRUPTING
                                 (Stop disabled, "Stopping...");
                                 RunFinished(Interrupted) -> "Stopped" block (NOT a
                                 Failure) + return to IDLE. Partial transcript is
                                 preserved (Stop != Retract / Rewind). Double Stop
                                 and NoActiveRun are structured no-ops. Verification:
                                 H1-H8 host tests + host/runtime integration (real
                                 AgentRuntime + AgentCore + ScriptedProvider, the
                                 Stop action driving the real execution-child
                                 cancellation to RunFinished(Interrupted)). Device
                                 acceptance: a Python OpenAI-compatible streaming
                                 fixture (tools/test-fixtures/, 127.0.0.1:18080) +
                                 ADB reverse, through the production Android
                                 transport (REAL_LLM = NO, REAL_API_COST = 0).
                                 D1/D2/D4 PASS on real device (dispatch-fixed
                                 build); D3 initially INCONCLUSIVE (SocketTimeout-
                                 Exception) — D3 closeout identified root cause as
                                 FIXTURE SSE framing (keep-alive vs close, Case E,
                                 HIGH confidence): byte-level traces proved the
                                 fixture kept the connection open after [DONE]
                                 with no EOF; fix = fixture Connection: close
                                 (matches real OpenAI endpoints); 2 deterministic
                                 JVM transport tests added (golden 592-byte stream,
                                 keep-alive vs close); D3 re-test PASS (2.2 s
                                 natural completion, 12 tokens, no timeout).
                                 Stop smoke PASS (one clean stop, Interrupted,
                                 Stopped.). Pre-existing :app bug found and fixed:
                                 DebugBridge-originated runs did not enable the UI
                                 Stop button (onRunStateChanged callback added).
                                 A :app-scoped fix was applied to
                                 AndroidOpenAiHttpTransport.postStream
                                 (withContext(Dispatchers.IO) wrapper) to make the
                                 streaming read cancellation-responsive; JVM
                                 discriminator test FAIL->PASS. Regression green
                                 (ag-core 41, ag-runtime 26, provider 59, app 109).
                                 D1-D4 ALL PASS. CORE-007 stays RESEARCH_REQUIRED.
                                 Evidence: docs/implementation/AG.HOST-STOP-1.md
                                 (§13: D3 closeout).
```

## Current integration snapshot (canonical pointer)

The single authoritative source for "which AT snapshot AG consumes" is:

```text
docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
```

Current facts (updated by AG.V2-2B.AT-ENGINE-FULL-INTEGRATION; do not restate
the full AT state in other docs — point to the file above):

```text
CURRENT_AT                      = AndroidTerminal V3
                                   (AT.PRE-EMULATOR-FREEZE-V1)
AT_EXECUTION_SUBSYSTEM          = FULL CURRENT CAPABILITY INTEGRATED
PROVIDERS                       = LOCAL + ADB
MODEL_TOOL_EXPOSURE             = android_shell ONE-SHOT ONLY
PERSISTENT_TERMINAL_ENGINE_CAPABILITY = AVAILABLE INTERNALLY
PERSISTENT_TERMINAL_TOOL        = NOT IMPLEMENTED
SCREEN_AWARE_TERMINAL           = NOT IMPLEMENTED (BLOCKED_BY_AT.EMULATOR-0)
LEGACY_AT_V0                    = SUPERSEDED / INACTIVE
AG_CORE_DIRECT_AT_DEPENDENCY    = FORBIDDEN / NONE (verified)
AG_RUNTIME_DIRECT_AT_DEPENDENCY = FORBIDDEN / NONE (verified)
AG_AT_CONSUMPTION_MODE          = GRADLE_COMPOSITE_BUILD
AT_CONTRACT_STATUS              = DRAFT (NO final API freeze)
AT_CONSUMER_BOOTSTRAP_GAP       = YES (no consumer-facing pairing API in Freeze V1;
                                   identity = UNSAFE_KEY_COPY bootstrap)
NEXT_FORMAL_AT_SYNC             = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW (Handoff V2)
```

Strategy note (AG.V1-PRE-FREEZE-NORMALIZATION): the technical finding
`PRODUCTION_REFACTOR_REQUIRED = NO` stands, but the Controller selected
Strategy B (V1 freeze as Known-Good baseline; V2 as future active tree) for
migration isolation / rollback safety / new-AT isolation. Frozen migration
sequence: AG.V1-FREEZE -> AG.V2-1.BASELINE-MIGRATION -> equivalence gate ->
AG.V2-2.AT-INTEGRATION -> AT gate -> AG.OUTPUT-BOUND.PREFLIGHT (TOOL-006,
semantics unchanged; execution order only). See
`docs/v2-research/pre-migration-workspace/docs/architecture/AG.V2-CONTROLLER-DECISION.md`.

## Repository identity (V2)

```text
THIS REPO        = AndroidAgent-V2 (ACTIVE DEVELOPMENT TREE)
V1 SOURCE        = D:\codebank\AndCode\AndroidAgent (FROZEN_KNOWN_GOOD,
                   tag AG.V1-KNOWN-GOOD-FREEZE-V1; reference/rollback/evidence
                   only; no new feature development on V1)
V2 BASELINE      = AG.V2-BASELINE-V1 (equivalent to the V1 freeze; established
                   by AG.V2-1.BASELINE-MIGRATION as a direct clone at the freeze
                   commit — production content byte-equivalent to V1)
V2 ARCHITECTURE  = INHERITED FROM CONFIRMED V1 / UNCHANGED
CURRENT AT       = AndroidTerminal V3 (AT.PRE-EMULATOR-FREEZE-V1)
                   consumed via Gradle composite build (includeBuild)
AT MODULES       = :at-engine, :at-backend-adb, :at-backend-local, :adb-engine
AT ADAPTER       = :integration-at (AtExecutionAdapter — unified AT consumption)
LEGACY AT V0     = SUPERSEDED / INACTIVE (no longer in dependency graph)
NEXT             = AG.OUTPUT-BOUND.PREFLIGHT (TOOL-006, per Roadmap queue)
```

This is a repository/current-state note only: no production contract, module,
package, or AT binary changed by the V2 migration. Migration evidence:
`docs/migration/AG.V2-1.BASELINE-MIGRATION.md` and `docs/migration/AG.V2-BASELINE-V1.md`.

Implemented and **not** to be described as pending:

```text
:ag-core                       AgentCore, Agent Loop, Session / Run
                               ModelProvider contract, Tool contract
                               Tool Registry, Allow / Deny policy
                               Events, RunOutcome
:ag-runtime                    AgentRuntime — host-neutral session selection
                               and single-active-run lifecycle
:provider-openai-compatible    OpenAiCompatibleProvider, config loader/resolver,
                               OpenAiHttpTransport contract
:provider-openai-compatible-jdk JdkOpenAiHttpTransport (java.net.http)
:app                           AGA host: AndroidOpenAiHttpTransport
                               (HttpsURLConnection), minimal chat UI
:integration-at                AG AT Execution Adapter (AtExecutionAdapter):
                                unified AT consumption point. Holds ATEngine
                                composition (Local + ADB one-shot + persistent).
                                The ONLY AG module that directly depends on AT
                                V3 modules. Isolates AT Contract DRAFT.
:tool-android-shell            Concrete Android Shell Tool Integration:
                                AndroidShellToolImpl (Tool) + ShellConnection
                                (AT lifecycle binding via adapter). Consumes AT
                                through :integration-at (api dep). NOT the only
                                AT consumer — the adapter is the consumption
                                point; the tool is a thin one-shot consumer.
:tool-network                  Concrete Network Tool: NetworkToolImpl (Tool)
                                action=fetch, HTTPS-only GET, PUBLIC_NETWORK_ONLY
                                (full IPv4+IPv6 SSRF guard), AppHttpFetcher
                                (platform HttpsURLConnection, zero new 3rd-party
                                dep), byte/char-capped streaming read, redirect
                                re-validation, HtmlToText. AT_DEPENDENCY=NONE.
                                Registered in AGA Composition Root.
:tool-file                     Concrete File Tool: FileToolImpl (Tool)
                                actions=read/search/list/stat, APP_SELF realm
                                (root = filesDir/agent/), PathResolver (canonical +
                                root confinement + traversal + symlink escape),
                                UTF-8 + BOM strip + REPLACE, binary NUL-sniff ->
                                isBinary=true (never force-decoded), bounded
                                (1MiB/30k/200/500). READ-only (no write/edit/delete).
                                No SAF, no AT, no backend abstraction.
                                Registered in AGA Composition Root.
third_party/at-engine-handoff-v0/   vendored AT Engine Handoff Bundle v0
                               (byte-identical; local m2 metadata wrapper)
config/provider.example.json   tracked, no secret
config/provider.local.json     ignored, real key
```

`AgentCore.run`, `ModelProvider.complete`, and `Tool.execute` are all `suspend`.
AG Core ships no blocking wrapper and has no production dependency beyond the
Kotlin standard library. AG Core does not know about HTTP, JSON, API keys,
config files, OpenAI, EMG, Android, or AT Core.

`ModelTurn.ToolCalls` carries `content`, so a single assistant turn can hold
prose and tool calls together. Verified against a real endpoint.

## Frontend boundary

```text
Frontend must not own reusable Agent runtime semantics.

Reusable Session / Run host lifecycle belongs in :ag-runtime.

AGA / AGC should remain thin frontends: input, rendering, platform lifecycle.

Do not move Android or Provider implementation details into :ag-runtime.
A frontend must not call AgentCore.session/run directly, must not generate
Session ids, and must not implement its own busy guard.
```

## AT integration boundary

```text
AG Core       -X-> AT implementation
Agent Runtime -X-> AT implementation
AGA           -X-> AT implementation
```

`Concrete Android Tool` is the only AndroidAgent execution layer allowed to
integrate an Android-specific execution backend. A concrete tool may use the
Android APIs directly, connect to the AndroidTerminal execution boundary, or
combine both. It must not be written as "all Android Tools must depend on AT
Core" — that is wrong.

AndroidAgent must **not** vendor AT source, copy AT source, reimplement AT Core,
or push an AT implementation into `:ag-core`, `:ag-runtime` or `:app`.

**The AT integration mechanism is frozen and implemented (`AG.V2-2` +
`AG.V2-2B`).** The concrete Android Shell Tool consumes AT through the unified
`:integration-at` adapter (AtExecutionAdapter), which holds the ATEngine
composition for both Local and ADB backends (one-shot + persistent). The
V3 AT modules are consumed via Gradle composite build (`includeBuild`), NOT
AAR binary handoff. No direct AT Core linking in AG Core / Runtime / Provider.
No second ADB lifecycle owner. AdbRuntime remains the UNIQUE ADB lifecycle
owner. AG does not wait for future AT Emulator / Screen State work — the AG
mainline and the AT mainline evolve independently.

## Still forbidden without an explicitly opened Step

Do not begin any of the following unless a Step explicitly authorizes it:

```text
additional AGA feature development     AGC / further frontend expansion
Skills loader                         MCP
persistence / RAG                     multi-agent
plugin framework                      provider routing
AIController integration
```

Note: ShellTool / Android Tool and AT Engine integration are **implemented**
(`AG.V2-2` + `AG.V2-2B` PASS; `:integration-at` adapter + `:tool-android-shell`;
full current execution capability integrated; model surface = one-shot only).
AGENT-0.PREFLIGHT is **done**
(instructions/tool/context/trace frozen). AGENT-0.LIVE is **VERIFIED**,
AG.DEBUG-0 is **PASS** (debug bridge / live execution stream, :app only), and
AGA.OBSERVABILITY-0 is **PASS** (active provider truth + streaming agent
transcript; device acceptance PASS on a connected phone). AG.NETWORK-0 is
**PASS**: the Tool Layer is formalized and `:tool-network` (fetch) is
implemented, registered, L1-verified (78 tests), and device-verified (L2 direct
fetch PASS + L3 real-agent E2E PASS with observation dependency + android_shell
regression PASS). AG.FILE-0 is **PASS**: `:tool-file` (read/search/list/stat)
is implemented, registered, L1-verified (55 tests), and device-verified (L2
direct tool_invoke 16/16 PASS). What remains forbidden without a Step: network
Search / Download / Request (POST/auth/cookie/stream) beyond fetch, file write
/ edit / delete, Tool-Layer framework expansion (ToolManager / backends /
factory), AGENT-0.CONTRAST, pairing, AGCLI, persistence, MCP, multi-agent,
AIController.

Note: AGA itself is **implemented and frozen**. What is forbidden is new AGA
feature work and any additional frontend — not AGA's existence.

## Test hygiene

```text
JUnit/Kotlin tests must use normal block-body Unit methods,
or explicitly return Unit.

Do not place @Test methods as local functions.

Test-count claims must come from actual JUnit execution results,
not source-level method counting.
```

Both failure modes have already occurred here: a non-Unit inferred return type,
and a `@Test` on a local function. In both cases JUnit silently executed nothing
while the source appeared correct.

## Secret handling

```text
Never print, log, or commit an API key.
Never echo a secret-bearing file, including via a parser error path.
Never emit an Authorization header.

Only these are allowed in output:
  API_KEY = PRESENT / ABSENT
  safe endpoint identity (scheme + host + port + path)
  model id
```

The development config is `config/provider.local.json`, which is gitignored and
holds a real key. Validate it without any code path that can echo raw content.

The development discipline above still applies in full: one Step at a time, no
opportunistic refactor, respect dependency boundaries, and no push unless the
task explicitly allows it.

## Source of truth (three roles, never merged)

```text
AGENTS.md                            = WHAT IS TRUE NOW
                                       (implemented / frozen architecture facts,
                                        verification baseline, forbidden areas)

docs/roadmap/AndroidAgent_Master_Roadmap.md
                                     = WHERE WE ARE GOING  (LIVE roadmap SSOT)
                                       (capability registry, dependencies,
                                        priority, execution queue, intake,
                                        update protocol)

docs/{research,preflight,implementation}/...
                                     = WHAT CHANGED IN THIS STEP
                                       (point-in-time evidence; never rewritten)
```

This file records only what is true now. Capability planning, dependencies,
priorities, and the execution queue live in the Roadmap. A Step's before/after
evidence lives in the Step doc.

## Roadmap-first development discipline

A new capability must enter the Roadmap before a Step opens it:

```text
Intake -> Registry lookup -> Domain/Owner classification
       -> dependency check -> priority impact -> Execution Queue decision
       -> Controller approval -> then open the Step
```

Exception: a small, clearly-scoped bugfix that reuses existing frozen semantics
and introduces no new capability (e.g. AGA.OBSERVABILITY-1) does not require a
Roadmap entry. Do not build a capability that is not in the Roadmap Registry,
and do not reorder the Execution Queue without logging old -> new -> reason in
the Roadmap.