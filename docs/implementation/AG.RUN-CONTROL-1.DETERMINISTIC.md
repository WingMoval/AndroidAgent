# AG.RUN-CONTROL-1.DETERMINISTIC — Active Run Interrupt (RUN-003/004) + HOST-005 Stop, Deterministic Verification

> Step: AG.RUN-CONTROL-1.DETERMINISTIC
> Type: PRODUCTION IMPLEMENTATION (ag-core + ag-runtime + :app Stop UI) + DETERMINISTIC VERIFICATION
> Date: 2026-10-17
> Git baseline: main @ 868eacf (working tree carries pre-existing uncommitted changes from earlier steps; this step's changes are the ag-core / ag-runtime / :app files + the new I-test + the L2 provider test)
> Sub-agents: FORBIDDEN / NOT USED

Implements and deterministically verifies the frozen AG.RUN-CONTROL-0 design
(preflight + CLOSEOUT C1-C5 + FINAL C1 correction): the production Active Run
Interrupt (RUN-003/004) in `AgentRuntime` / `AgentCore`, with the I1-I15
deterministic test suite and the retained L2 fake-HTTP transport disconnect test.
No real LLM/provider, no sub-agents, no new module, no Provider/Tool contract
change, no Architecture v1.0 change, no commit/push.

---

## 1. Scope

This step builds the Run Control capability slice frozen by
`AG.RUN-CONTROL-0.PREFLIGHT` + `.CLOSEOUT` (+ FINAL C1 correction):

- RUN-003 — Active Run cancellation mechanism (Agent Runtime owns the interrupt
  entrypoint + atomic active handle).
- RUN-004 — Cancellation outcome + session-reusable-after-cancel semantics
  (`RunOutcome.Interrupted`, exactly one terminal `RunFinished(Interrupted)`,
  per-Run user-interrupt metadata, synthetic tool-result pairing).
- HOST-005 — Stop UI (direct Stop, no confirmation; INTERRUPTING is
  Host-transient only).
- Deterministic verification: L1 I1-I15 + L2 transport disconnect.

Out of scope (NOT touched, NOT re-opened): Retract (STATE-001), Rewind (STATE-002),
Fork (STATE-005), background Run (RUN-005/006), file rollback, CORE-007 arbitrary
rollback (stays RESEARCH_REQUIRED), any Provider/Tool/Session contract change,
Architecture v1.0.

## 2. Design realization (what was built)

### 2.1 Core (`ag-core`)

- `RunOutcome.Interrupted` — a `data object`, NOT a `Failure`.
- `RunInterruptSignal` (Core-owned interface): `isUserInterrupted()` +
  `ensureNotInterrupted()` (throws `CancellationException` when a user Stop has
  been committed). This is the one-way per-Run metadata seam from the Runtime into
  Core; Core never queries the Runtime (C3).
- `AgentCore.run` gained a `interruptSignal: RunInterruptSignal = Absent` parameter
  (default keeps existing callers). `driveLoop` calls
  `interruptSignal.ensureNotInterrupted()` before each provider turn and before each
  tool dispatch.
- Interrupt semantic finalization (Core-owned): on a committed user Stop, Core
  appends a truthful synthetic `ToolResultMessage` per declared-but-uncompleted tool
  call — IN_FLIGHT_INTERRUPTED (external state may be unknown) vs
  NOT_EXECUTED_DUE_TO_INTERRUPT (honestly not started) — and emits exactly one
  `RunFinished(Interrupted)` via a per-Run `AtomicBoolean` terminal latch (first
  terminal wins).
- `AgentCore.lastRunOutcome` (private set): the one-way signal that lets the
  Runtime confirm Core committed the Interrupted finalization (conversion guard
  condition C). It is set ONLY on the user-Stop path, never on a lifecycle
  cancellation.

  Implementation note (C1 + cancelled-context): the finalization is committed from
  `driveLoop`'s provider-call and tool-dispatch `CancellationException` catches
  (non-cancelled contexts where the interrupt was *observed*), NOT from
  `executeRun`'s catch (which runs in a *cancelled* context where emission and
  assignment are unreliable). `executeRun`'s catch only propagates the
  `CancellationException`.

- `ModelRequest` gained a `interruptSignal` field (default `Absent`) so a streaming
  provider can cooperatively honor the interrupt at its suspension point (mirrors a
  real provider honoring coroutine cancellation). Final `ModelTurn` semantics are
  unchanged.

### 2.2 Runtime (`ag-runtime`)

- `AgentRuntime.interruptActiveRun()` — the user Stop entrypoint. Atomically claims
  the per-Run control (C5: `AtomicReference<ActiveRunControl?>` + a per-control
  `AtomicBoolean` claim, exactly one winner). Returns `Requested` / `NoActiveRun` /
  `AlreadyInterrupting`.
- `ActiveRunControl` — a small per-Run state holder (the interrupt claim flag), not
  a subsystem. Created after Run admission, cleared in `send`'s `finally`.
- `send` conversion guard (frozen A && B && C):
  - A — the send caller is still ACTIVE (a cancelled caller = PARENT CANCELLATION
    DOMINATES; never converted). Detected reflectively (the module has no
    kotlinx-coroutines dependency in production; the coroutine context's Job element
    is read by class name, no kotlinx reference).
  - B — this Run committed a per-Run USER interrupt (the claim flag).
  - C — Core committed the Interrupted finalization (`lastRunOutcome == Interrupted`).
  Only when A && B && C does `send` return `RunOutcome.Interrupted`; otherwise it
  propagates the `CancellationException`. A bare `catch { return Interrupted }` is
  not used (forbidden pattern, Section 5.2 of the CLOSEOUT).

  Implementation note: because the production classpath has no kotlinx-coroutines,
  the Run is interrupted *cooperatively*: `interruptActiveRun()` sets the per-Run
  flag, and the Run (Core checkpoints + the provider's `ensureNotInterrupted`)
  throws `CancellationException` at its suspension point. This is the same
  mechanism a real streaming provider uses to honor coroutine cancellation.

### 2.3 Host Stop UI (`:app`, HOST-005)

- A Stop button (disabled unless a Run is in flight) calls
  `runtime.interruptActiveRun()`. On `Requested` the status shows "Stopping..." and
  the button disables (INTERRUPTING is Host-transient). On the terminal
  `RunFinished(Interrupted)` event the transcript renders a "Stopped" block (NOT a
  Failure) and the button re-enables for the next Run. A double Stop is a structured
  no-op (`AlreadyInterrupting`); a Stop with no active Run is a no-op.

## 3. Verification results (actual JUnit execution)

All green:

| Module | Tests | Failures |
| --- | --- | --- |
| :ag-core | 41 | 0 |
| :ag-runtime (R1-R8 + I1-I15) | 23 | 0 |
| :provider-openai-compatible-jdk (P1-P12 + L2) | 25 | 0 |
| :app (incl. TranscriptModel T23 interrupted block) | 127 | 0 |

- `:app:assembleDebug` BUILD SUCCESSFUL.
- I1-I15 (the C1-C5 deterministic suite) all PASS: I1-I3 (interrupt before/during
  provider stream, transcript-only), I4 (interrupt in tool phase), I5 (interrupt
  during tool execution), I6 (new Run works after interrupt), I7 (interrupt vs
  complete race — exactly one terminal), I8 (double interrupt — one winner), I9
  (no active Run — structured no-op), I10 (Session history consistent after
  interrupt), I11 (user Stop structured return — send returns Interrupted, no raw
  cancellation to the Host), I12 (parent/lifecycle cancel propagates, NOT
  converted), I13 (concurrent double-interrupt — one atomic winner, one terminal),
  I14 (in-flight tool truthfulness — IN_FLIGHT vs NOT_EXECUTED distinguishable),
  I15 (Stop vs lifecycle race — parent cancellation dominates).
- L2 (retained transport test): a streaming call held open by a fake HTTP server is
  cancelled mid-flight; the cancellation surfaces as `CancellationException` (not a
  provider failure) and the transport disconnects (no background read continues).

## 4. Contract / dependency compliance

- No Provider contract change (no `cancel`/`abort`/`interrupt` on `ModelProvider`;
  `suspend` + cooperative cancellation suffices).
- No new module / manager. `ActiveRunControl` is a Runtime-internal holder only.
- No detached scope / background Run (the Run stays in the host's structured
  coroutine; lifecycle cancel still cancels it transitively).
- One-way dependency preserved: ag-runtime -> ag-core only; Core never queries the
  Runtime (the interrupt metadata is a Core-owned type passed downward).
- No ToolResult schema extension (the existing `success`/`output`/`error` is reused;
  only the `error`-string semantic is frozen for the two synthetic cases).
- No global interrupt flag (per-Run only).
- No Architecture v1.0 change.

### Dependency note (flagged, no production coroutines dependency added)

`:ag-runtime` and `:ag-core` production have **no kotlinx-coroutines on the compile
classpath** (it is `testImplementation` only). This step does NOT add it. The
interrupt mechanism is therefore *cooperative* (the per-Run flag + `ensureNotInterrupted`
checkpoints + the provider honoring the signal at its suspension point), and the
send-caller-active check (guard A) reads the coroutine Job reflectively by class
name. This keeps the frozen "no production coroutines dependency" module contract
intact. The deterministic tests model a well-behaved streaming provider/tool that
honzors the interrupt at its suspension point (exactly as the real transport does
via coroutine cancellation).

## 5. Files changed (this step)

Production:
- `ag-core/.../AgentCore.kt` — `RunInterruptSignal`, interrupt checkpoints,
  finalization (synthetic T + single terminal + `lastRunOutcome`), committed from
  the non-cancelled catch points.
- `ag-core/.../Model.kt` — `ModelRequest.interruptSignal` (default `Absent`).
- `ag-core/.../RunOutcome.kt` — `RunOutcome.Interrupted`.
- `ag-runtime/.../AgentRuntime.kt` — `interruptActiveRun()`, atomic
  `ActiveRunControl`, `send` conversion guard (A && B && C, reflective caller-active
  check).
- `app/.../ChatActivity.kt` — Stop button wiring (`onStopRun`), INTERRUPTING
  transient state, terminal `RunFinished(Interrupted)` handling.
- `app/.../TranscriptModel.kt` — `Block.Interrupted` + `onInterrupted()` + render.
- `app/.../trace/AgentTraceCollector.kt` — `Interrupted` branch in `outcomeOf`.
- `app/src/main/res/layout/activity_chat.xml` — Stop button.

Tests:
- `ag-runtime/.../AgentRuntimeInterruptTest.kt` (new) — I1-I15.
- `ag-runtime/.../TestSupport.kt` — `ScriptedProvider` cooperatively honors the
  interrupt signal at its gate suspension points.
- `app/.../TranscriptModelTest.kt` — T23 (interrupted block renders "Stopped", not
  "Failed").
- `provider-openai-compatible-jdk/.../ProviderProtocolTest.kt` — L2 streaming
  disconnect test.
- `provider-openai-compatible-jdk/.../FixtureServer.kt` — unchanged streaming
  behavior (the L2 test uses the existing `delayMillis` hold).
- `provider-openai-compatible-jdk/.../LiveApiTest.kt` — exhaustive `when` for the
  new `Interrupted` outcome.

## 6. Final verdict

```text
PRODUCTION_INTERRUPT_IMPLEMENTED        = YES (AgentRuntime + AgentCore, cooperative,
                                            per-Run, atomic handle, A&&B&&C guard)
RUN_003                                 = VERIFIED (L1 I1-I15 + R1-R8 regression)
RUN_004                                 = VERIFIED (Interrupted outcome, one terminal,
                                            synthetic T, session reusable)
HOST_005                                = VERIFIED (Stop UI; :app tests + assembleDebug)
I1_I15                                  = PASS (23 ag-runtime tests, 0 failures)
L2_TRANSPORT_DISCONNECT                 = PASS (25 provider-jdk tests, 0 failures)
REGRESSION                              = PASS (ag-core 41, app 127, assembleDebug)
PROVIDER_CONTRACT_CHANGED               = NO
NEW_MODULE                              = NO
DETECTED_SCOPE_OR_BACKGROUND_RUN        = NO
GLOBAL_INTERRUPT_FLAG                   = NO
CORE_QUERIES_RUNTIME                    = NO (one-way per-Run metadata)
PRODUCTION_COROUTINES_DEPENDENCY_ADDED  = NO (cooperative mechanism + reflective Job read)
ARCHITECTURE_V1_0_CHANGED               = NO
COMMIT_PUSH                             = NO
SUB_AGENTS_USED                         = NO

VERDICT = PASS
```

RUN-003/004 + HOST-005 -> VERIFIED. CORE-007 stays RESEARCH_REQUIRED (only the
history-on-interrupt slice was built; arbitrary rollback is NOT frozen by this step).
NEXT per the recomputed Execution Queue: AG.OUTPUT-BOUND.PREFLIGHT (TOOL-006).
