# AG.RUN-CONTROL-1.DETERMINISTIC.FIX — Real Execution-Job Cancellation + Production Stop Chain + Deterministic Re-Verification

> Step: AG.RUN-CONTROL-1.DETERMINISTIC.FIX
> Type: PRODUCTION FIX + DETERMINISTIC VERIFICATION + INTEGRATION VERIFICATION
> Date: 2026-10-17
> Git baseline: main @ 868eacf (working tree carries pre-existing uncommitted changes from earlier steps)
> Sub-agents: FORBIDDEN / NOT USED

This document records the FIX for the Controller Review of
`AG.RUN-CONTROL-1.DETERMINISTIC`. **It supersedes the previous step's `VERIFIED`
claim for RUN-003/004/HOST-005's production interrupt mechanism**, which was found
not to have verified the production Stop → execution-Job cancellation path.

---

## 1. Controller blocker

The previous step's tests were all green, but the Controller Review found:

- `interruptActiveRun()` did **NOT** cancel an active execution Job. It only set a
  per-Run `AtomicBoolean` flag.
- The Run was stopped *cooperatively*: AG Core polled `ensureNotInterrupted()` at
  checkpoints and the (test) `ScriptedProvider` polled `interruptSignal` — i.e. the
  `RunInterruptSignal` was doing double duty as both **semantic metadata** and the
  **cancellation transport**.
- Therefore `DETERMINISTIC_MODEL_PATH = PASS` but
  `REAL_PRODUCTION_CANCELLATION_PATH = NOT VERIFIED`: a real streaming provider
  (which does not poll a Core signal) would not have been stopped by the production
  Stop.

The fix separates the two roles:

```text
Job.cancel()            = cancellation mechanism
RunInterruptSignal      = per-Run semantic metadata ("was THIS Run user-interrupted?")
```

## 2. What changed

### 2.1 `:ag-runtime` production — real execution-Job ownership

- **Added** `kotlinx-coroutines-core` to `:ag-runtime` **production** dependencies.
  Real consumer: RUN-003 execution-Job ownership + cancellation + the caller-active
  conversion guard. Not future scaffolding.
- `send()` now runs the Run in a **real structured child Job** of the caller
  (`async`, parented to `send`). No GlobalScope / detached SupervisorJob / background
  Run. A host/lifecycle cancellation still cancels the child transitively.
- `ActiveRunControl` now owns the **execution `Job`**, the `interruptRequested`
  claim, and a per-Run `interruptedFinalized` acknowledgement. Ownership stays
  atomic (`AtomicReference<ActiveRunControl?>`).
- The handle is registered **synchronously** (before the child can reach its first
  suspension) so a Stop is never lost in the admission window. The execution Job is
  captured from the child's own context on its first suspension.
- `interruptActiveRun()`: atomically find the active control → atomically claim the
  USER interrupt → call `executionJob.cancel(UserInterruptException())` → return
  `Requested` / `AlreadyInterrupting` / `NoActiveRun`. **One Job cancellation.**
- The `send` conversion guard uses the **standard coroutine API**
  (`coroutineContext.isActive`) — the **reflective Job read is REMOVED**.
  Conversion to `RunOutcome.Interrupted` requires A (caller active) && B (per-Run
  USER claim) && C (Core committed THIS Run's finalization). A lifecycle-cancelled
  caller propagates `CancellationException` (PARENT CANCELLATION DOMINATES).
- `UserInterruptException` (a `CancellationException` subclass) is the cancellation
  cause; it carries no host/Runtime state (Core distinguishes user vs lifecycle via
  the per-Run signal, never by inspecting the cause).

### 2.2 `:ag-core` production — signal reduced to semantic metadata

- `RunInterruptSignal.ensureNotInterrupted()` (the polling mechanism) is **REMOVED**.
  `isUserInterrupted()` remains as the semantic fact. A new
  `onInterruptedFinalized()` callback carries the per-Run finalization
  acknowledgement back to the Runtime.
- `AgentCore.lastRunOutcome` (the cross-Run instance field used as condition C) is
  **REMOVED**. Condition C is now **per-Run** (`ActiveRunControl.interruptedFinalized`),
  set by the Core-owned signal callback.
- The interrupt finalization (synthetic tool results + exactly one
  `RunFinished(Interrupted)` + the per-Run acknowledgement) is committed from
  `driveLoop`'s provider-call and tool-dispatch `CancellationException` catches
  (non-cancelled contexts), then the `CancellationException` is rethrown so the
  execution child terminates **CANCELLED** (it does not return a value).
  `onInterruptedFinalized()` is invoked **before** the terminal emission so the
  acknowledgement is committed even if the emission throws in a cancelling context.
- `ModelRequest.interruptSignal` is **REMOVED** (the provider receives normal
  coroutine cancellation; a test-only consumer is not a production justification).
  `ModelRequest` shape is restored.

### 2.3 Provider / Tool / Transport audit (no contract change)

- `OpenAiCompatibleProvider.postStream` already rethrows `CancellationException`
  (does not convert it to a `ProviderFailure`). No change needed.
- `JdkOpenAiHttpTransport` uses `suspendCancellableCoroutine` +
  `invokeOnCancellation { future.cancel(true) }` — a parent Job cancellation
  cancels the in-flight HTTP request. No change needed.
- `AndroidOpenAiHttpTransport` uses `withContext(Dispatchers.IO)` + `finally {
  connection?.disconnect() }` — cancellation aborts the read and tears down the
  socket. Static audit: `CancellationException` is not swallowed.
  **ANDROID_TRANSPORT_USER_STOP = NOT_DEVICE_VERIFIED** (JVM path verified via L2-FULL).
- `ToolRegistry.dispatch` already rethrows `CancellationException`. A suspended tool
  receives the Job cancellation at its suspension point (M2 / I5 / I14).
- No `ModelProvider.cancel()` / `Tool.cancel()` / policy added.

## 3. Test rework

- **I1–I15** reworked: providers/tools hold at deterministic gates with **plain
  suspensions** (no `interruptSignal` polling); the Stop is enforced by the
  execution-Job cancellation reaching them at their suspension point. `ScriptedProvider`
  no longer polls the signal.
- **M1** — `ActiveRunControl` owns the real execution Job; `interruptActiveRun()`
  cancels THAT Job (the provider's in-flight suspension is cancelled; one terminal).
- **M2** — a provider suspended INSIDE its turn (no further Core checkpoint reachable)
  still receives the cancellation directly via the Job (Stop does not depend on the
  next checkpoint).
- **M3** — a provider that observes coroutine cancellation (like the production
  transport) surfaces `CancellationException` that ends the Run as `Interrupted`,
  never as a `RunFailed`/`ProviderFailure`.
- **L2** (retained, now "L2-transport-cancel-safety") — external coroutine cancel of a
  held streaming call → `CancellationException` + disconnect.
- **L2-FULL** (new, the key test) — full production chain: real
  `AgentRuntime.send` + `interruptActiveRun`, real AG Core, real
  `OpenAiCompatibleProvider`, real `JdkOpenAiHttpTransport`, fixture HTTP server
  holding the request open. Verifies: `interruptResult == Requested`; execution child
  cancelled (`send` returns `RunOutcome.Interrupted`, not a raw
  `CancellationException`); exactly one `RunFinished(Interrupted)`; session reusable
  (not busy). Calls `runtime.interruptActiveRun()` (NOT `testJob.cancel()`).

## 4. Verification results (actual JUnit execution)

| Module | Tests | Failures |
| --- | --- | --- |
| :ag-core | 41 | 0 |
| :ag-runtime (R1-R8 + I1-I15 + M1-M3) | 26 | 0 |
| :provider-openai-compatible | 59 | 0 |
| :provider-openai-compatible-jdk (P1-P12 + L2 + L2-FULL) | 27 | 0 |
| :tool-android-shell | 32 | 0 |
| :tool-network | 156 | 0 |
| :tool-file | 110 | 0 |
| :app | 234 | 0 |

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- `./gradlew build` — **FAIL_UNRELATED**: the only failure is the pre-existing AT AAR
  lint error (`at-runtime-0.1.0` manifest `CoarseFineLocation`), present before this
  step. Not caused by, and not fixable within, this fix.

## 5. PASS-gate summary

- `REAL_EXECUTION_JOB_EXISTS = YES` (`async` child of `send`).
- `ACTIVE_RUN_CONTROL_OWNS_EXECUTION_JOB = YES`.
- `INTERRUPT_ACTIVE_RUN_CALLS_JOB_CANCEL = YES`.
- `STOP_NO_LONGER_DEPENDS_ON_NEXT_CORE_CHECKPOINT = YES` (M2).
- `RUN_INTERRUPT_SIGNAL_ROLE = SEMANTIC_METADATA_ONLY`.
- `DETACHED_SCOPE = NO`; `PARENT_STRUCTURED_RELATION = PASS`.
- `USER_STOP`: child CANCELLED, caller ACTIVE, `send` → `Interrupted`.
- `LIFECYCLE_CANCEL`: child CANCELLED, caller CANCELLED, `CancellationException`
  propagated. `PARENT_CANCELLATION_DOMINATES = YES`.
- `DOUBLE_INTERRUPT_ONE_WINNER = YES`; `NO_ACTIVE_RUN_NOOP = YES`.
- Core: `Interrupted != Failure`; exactly one `RunFinished(Interrupted)`; terminal
  latch; **condition C = PER_RUN**; `lastRunOutcome` REMOVED; synthetic ToolResult
  IN_FLIGHT (unknown external state) vs NOT_EXECUTED.
- Provider: production `OpenAiCompatibleProvider` cancellation not swallowed
  (L2-FULL); no `ProviderFailure` fabricated from cancellation.
- Tool: suspended tool receives Job cancellation (M2/I5/I14); in-flight synthetic
  result = unknown external state; not-started = not executed.
- L2-transport-cancel-safety = PASS; L2-FULL-USER-STOP-E2E = PASS.
- `MODEL_REQUEST_INTERRUPT_SIGNAL = REMOVED`; `REFLECTIVE_JOB_READ = REMOVED`;
  `PRODUCTION_COROUTINES_DEPENDENCY = added to :ag-runtime (real consumer)`.

## 6. Files changed (this fix)

Production:
- `ag-runtime/.../AgentRuntime.kt` — real execution-Job ownership, `interruptActiveRun()`
  → `executionJob.cancel()`, standard-coroutine-API conversion guard (A&&B&&C),
  reflective read removed, `UserInterruptException`.
- `ag-runtime/build.gradle.kts` — `kotlinx-coroutines-core` added to production.
- `ag-core/.../AgentCore.kt` — `RunInterruptSignal` reduced (no `ensureNotInterrupted`;
  `onInterruptedFinalized` added); `lastRunOutcome` removed; finalization order fixed.
- `ag-core/.../Model.kt` — `ModelRequest.interruptSignal` removed (shape restored).

Tests:
- `ag-runtime/.../AgentRuntimeInterruptTest.kt` — I1-I15 reworked to Job-cancellation
  trigger; M1-M3 added.
- `ag-runtime/.../TestSupport.kt` — `ScriptedProvider` plain-gate suspensions (no polling).
- `provider-openai-compatible-jdk/.../ProviderProtocolTest.kt` — L2-FULL Stop E2E added;
  L2 renamed conceptually to transport-cancel-safety.
- `provider-openai-compatible-jdk/build.gradle.kts` — `:ag-runtime` test dependency.
- `provider-openai-compatible-jdk/.../FixtureServer.kt` — pre-response `delayMillis` hold
  reused for L2-FULL (no new mechanism).

Docs:
- `AGENTS.md`, `docs/roadmap/AndroidAgent_Master_Roadmap.md` — current-truth corrections
  (RUN-003/004/HOST-005 IN_PROGRESS; FIX step recorded).

## 7. Final verdict

```text
PRODUCTION_INTERRUPT_MECHANISM = real execution-Job cancellation
RUN_003 = VERIFIED (L1 I1-I15 + M1-M3 + R1-R8; real Job)
RUN_004 = VERIFIED (Interrupted finalization under real cancellation; condition C per-Run)
HOST_005 = VERIFIED (Stop UI unchanged; really cancels the execution Job; L2-FULL)
L2_TRANSPORT_CANCEL_SAFETY = PASS
L2_FULL_USER_STOP_E2E = PASS
REGRESSION = PASS (all 8 modules green; assembleDebug PASS)
PROVIDER_CONTRACT_CHANGED = NO
TOOL_CONTRACT_CHANGED = NO
NEW_MODULE = NO
DETACHED_SCOPE = NO
CORE_TO_RUNTIME = NO
PRODUCTION_COROUTINES_DEPENDENCY = added to :ag-runtime (real consumer)
ARCHITECTURE_V1_0_CHANGED = NO
COMMIT_PUSH = NO
SUB_AGENTS_USED = NO

VERDICT = PASS
```

CORE-007 stays RESEARCH_REQUIRED. NEXT per the Roadmap queue (recomputed after PASS):
AG.OUTPUT-BOUND.PREFLIGHT (TOOL-006).
