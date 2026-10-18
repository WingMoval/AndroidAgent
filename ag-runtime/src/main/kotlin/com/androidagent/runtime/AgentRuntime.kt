package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.RunFailure
import com.androidagent.core.RunInterruptSignal
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

/**
 * Host-neutral Agent lifecycle: which Session a host is currently talking to, and
 * how to drive one Run at a time — including the user-facing Stop.
 *
 * This is deliberately **not** a second intelligence core. It owns no history, no
 * Agent Loop, no tool registry and no provider. The Agent history SSOT remains the
 * AG Core Session; every Run is executed by AG Core's [AgentCore.run]. This type
 * decides *which* Session the host is using, serialises access, and owns the
 * mechanism for interrupting the active Run.
 *
 * Layer contract:
 *  - AG Core owns Session/Run *semantics* (including the interrupted outcome and the
 *    interrupted-history finalization).
 *  - [AgentRuntime] owns the host's *current Session selection* and the *cancellation
 *    mechanism* (the interrupt entrypoint + the active Run's execution Job).
 *  - The Composition Root owns provider/transport *construction*.
 *  - The frontend owns input, rendering and platform lifecycle.
 *
 * Active Run interruption (frozen semantics, real Job cancellation):
 *  - Each Run executes in a structured child Job of the `send` caller's coroutine
 *    (a real kotlinx.coroutines [Job], structurally parented to `send`). No detached
 *    scope, no GlobalScope, no background Run: a host/lifecycle cancellation still
 *    cancels the Run transitively.
 *  - [interruptActiveRun] atomically claims the active Run (the user Stop), marks the
 *    per-Run USER interrupt metadata, and cancels the Run's execution Job
 *    (`executionJob.cancel()`). Coroutine cancellation then propagates naturally
 *    through AG Core down to the Run's deepest suspension point (the provider /
 *    tool), which is how the Run is stopped.
 *  - AG Core recognizes the user interrupt (via the per-Run signal it was given),
 *    finalizes the interrupted history, and emits exactly one
 *    `RunFinished(Interrupted)`. The execution child then terminates as CANCELLED
 *    (it does not return a value).
 *  - The still-active `send` caller converts THIS user child cancellation into the
 *    structured [RunOutcome.Interrupted] value (IFF the caller is still active AND
 *    this Run's USER interrupt was committed AND Core committed this Run's
 *    Interrupted finalization). A host/lifecycle cancellation (the host's own scope
 *    is cancelled) is NOT converted: the Run's `CancellationException` propagates to
 *    the dying host (PARENT CANCELLATION DOMINATES).
 */
class AgentRuntime(
    private val agentCore: AgentCore,
    private val runOptions: RunOptions = RunOptions(),
    private val sessionIdFactory: () -> String = { "session-" + UUID.randomUUID() },
) {

    /**
     * The per-Run control handle: the actual execution [Job], the user-interrupt
     * claim, and the per-Run acknowledgement that AG Core committed the Interrupted
     * terminal finalization. This is the only active-Run state the Runtime holds.
     * It is a small state holder, not a subsystem.
     *
     * [interruptRequested] is claimed atomically (exactly one concurrent caller wins).
     * [interruptedFinalized] is set by the Core-owned [RunInterruptSignal] callback
     * when Core commits the terminal finalization for THIS Run (condition C is
     * per-Run, never a cross-Run instance field).
     */
    private class ActiveRunControl(
        val interruptRequested: AtomicBoolean,
        // Set synchronously before the execution child can reach its first
        // suspension; a placeholder is used until the async Job is available.
        @Volatile var executionJob: Job,
    ) {
        val interruptedFinalized = AtomicBoolean(false)
    }

    // The active Run's control handle (atomic ownership; never a bare @Volatile Job).
    private val activeRun = AtomicReference<ActiveRunControl?>(null)

    // Backward-compatible busy flag, driven in lock-step with [activeRun].
    private val runActive = AtomicBoolean(false)

    @Volatile
    private var sessionId: String = sessionIdFactory()

    /** Identifier of the Session this runtime is currently using. */
    val currentSessionId: String get() = sessionId

    /**
     * Whether a Run is in flight.
     *
     * A host-level guard so AGA and AGC both get "one send at a time" for free.
     * It does **not** replace the frozen AG Core rule (one active Run per Session);
     * that remains the final protection.
     */
    val isBusy: Boolean get() = runActive.get()

    /**
     * The result of an interrupt *request*. This describes whether the request was
     * accepted, **not** whether the Run has finished with [RunOutcome.Interrupted]
     * (that is learned from the terminal event / the `send` result).
     */
    sealed interface InterruptResult {
        /** This call claimed the active Run and initiated the interrupt. */
        data object Requested : InterruptResult

        /** No active Run to interrupt; nothing was cancelled. */
        data object NoActiveRun : InterruptResult

        /** A prior call already claimed the interrupt for this Run; no-op. */
        data object AlreadyInterrupting : InterruptResult
    }

    /**
     * Runs one user turn against the current Session in the caller's structured
     * coroutine and returns the AG Core outcome verbatim.
     *
     * The Run executes in a structured child Job of the caller ([async]): it is
     * structurally parented to this `send` call, so a host/lifecycle cancellation
     * cancels the Run transitively, and no Run is ever detached or backgrounded.
     *
     * @return the AG Core outcome. A rejected send returns [RunOutcome.Failure] with
     *   [RunFailure.SessionBusy]. A user Stop yields [RunOutcome.Interrupted] (the
     *   structured value); a host/lifecycle cancellation propagates as
     *   [CancellationException].
     * @throws CancellationException if the host's own scope is cancelled (lifecycle);
     *   the Run's `CancellationException` propagates to the dying host and is never
     *   converted.
     */
    suspend fun send(userInput: String): RunOutcome {
        if (!runActive.compareAndSet(false, true)) {
            return RunOutcome.Failure(RunFailure.SessionBusy(sessionId))
        }
        // Captured here (before the finally clears [activeRun]) so the catch below
        // can read the per-Run state even after the handle has been unregistered.
        var controlRef: ActiveRunControl? = null
        try {
            // A structured child of THIS send caller: real Job, parented to the
            // caller. The handle is registered SYNCHRONOUSLY, before the child can
            // reach its first suspension, so a Stop is never lost in the window
            // between the Run starting and the handle becoming visible (the
            // execution Job is already started, so the child is a live Job whose
            // cancellation can be issued the moment the handle is registered).
            // Core only ever sees the per-Run RunInterruptSignal contract; it never
            // queries the Runtime.
            // A structured child of THIS send caller: real Job, parented to the
            // caller. The handle is registered SYNCHRONOUSLY (before the child can
            // reach its first suspension), so a Stop is never lost in the window
            // between the Run starting and the handle becoming visible. The execution
            // Job is captured from the child's own context on its first suspension
            // (synchronously, before the provider is called), so it is real and
            // cancellable. Core only ever sees the per-Run RunInterruptSignal
            // contract; it never queries the Runtime.
            val control = ActiveRunControl(
                interruptRequested = AtomicBoolean(false),
                executionJob = Job(),
            )
            controlRef = control
            activeRun.set(control)
            val outcome = coroutineScope {
                val executionJob = async {
                    control.executionJob = coroutineContext[Job]!!
                    agentCore.run(
                        sessionId = sessionId,
                        userInput = userInput,
                        options = runOptions,
                        interruptSignal = RunInterruptSignal.of(
                            isUserInterrupted = { control.interruptRequested.get() },
                            onInterruptedFinalized = { control.interruptedFinalized.set(true) },
                        ),
                    )
                }
                executionJob.await()
            }
            return outcome
        } catch (e: CancellationException) {
            // The execution child terminated CANCELLED. Convert to the structured
            // user-Stop outcome only under the frozen A && B && C guard; otherwise
            // (a lifecycle-cancelled caller) the CancellationException propagates.
            val control = controlRef
            // A — the send caller is still ACTIVE. A cancelled caller (lifecycle /
            //      shutdown) means PARENT CANCELLATION DOMINATES: never converted.
            //      Standard coroutine API (no reflection).
            val callerActive = coroutineContext.isActive
            if (callerActive &&
                control != null &&
                // B — THIS Run committed a per-Run USER interrupt (the claim flag).
                control.interruptRequested.get() &&
                // C — Core committed THIS Run's Interrupted finalization (per-Run
                //      acknowledgement set by the Core-owned signal callback).
                control.interruptedFinalized.get()
            ) {
                return RunOutcome.Interrupted
            }
            throw e
        } finally {
            activeRun.set(null)
            runActive.set(false)
        }
    }

    /**
     * Requests interruption of the active Run (the user Stop).
     *
     * Atomic: exactly one concurrent caller wins the claim
     * ([InterruptResult.Requested]); a concurrent loser gets
     * [InterruptResult.AlreadyInterrupting]. With no active Run it is a structured
     * no-op ([InterruptResult.NoActiveRun]) — it never throws, never quits, and never
     * mutates Session state.
     *
     * The winner marks the per-Run USER interrupt metadata and cancels the Run's
     * REAL execution Job (`executionJob.cancel()`). Coroutine cancellation then
     * propagates naturally through AG Core to the Run's deepest suspension point
     * (the provider / tool), stopping the Run. AG Core finalizes the interrupted
     * history and emits exactly one `RunFinished(Interrupted)`; the still-active
     * `send` caller then converts the result to the structured Interrupted outcome.
     */
    fun interruptActiveRun(): InterruptResult {
        val control = activeRun.get() ?: return InterruptResult.NoActiveRun
        if (!control.interruptRequested.compareAndSet(false, true)) {
            return InterruptResult.AlreadyInterrupting
        }
        // The claim is recorded (per-Run USER metadata). Cancel the execution Job:
        // the in-flight provider/tool suspension observes the cancellation at its
        // suspension point exactly as any coroutine cancellation would.
        control.executionJob.cancel(UserInterruptException())
        return InterruptResult.Requested
    }

    private fun controlFor(job: Job): ActiveRunControl? =
        activeRun.get()?.takeIf { it.executionJob === job }

    /**
     * Switches to a brand-new Session. The previous one stays intact inside AG
     * Core — it is never deleted, only no longer current.
     *
     * Rejected while a Run is active: switching Sessions mid-Run would be a way to
     * quietly escape the single-active-run guarantee.
     *
     * @return true if switched, false if rejected because busy.
     */
    fun newSession(): Boolean {
        if (isBusy) return false
        sessionId = sessionIdFactory()
        return true
    }
}

/**
 * The cause attached to the execution Job when a user Stop is committed. It carries
 * no host/Runtime state: AG Core distinguishes a user interrupt from a lifecycle
 * cancellation through the per-Run [RunInterruptSignal] it was given, never by
 * inspecting the cancellation cause.
 */
private class UserInterruptException : CancellationException("Run interrupted by user Stop")
