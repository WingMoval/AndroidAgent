package com.androidagent.core

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AG Core's recognition of a *user* interrupt for the Run currently executing.
 *
 * This is the one-way, per-Run SEMANTIC METADATA seam from the host-facing run
 * driver (the Agent Runtime) into AG Core. It is deliberately a tiny, Core-owned
 * type: AG Core knows only that "this Run was interrupted by the user" and never
 * anything about the Runtime, its active-run handle, or how the cancellation was
 * requested. The value is per-Run and travels *with* the Run (passed downward by
 * the caller), never read from any global / static / ThreadLocal Runtime state.
 *
 * This is NOT the cancellation transport. A user Stop is enforced by the Runtime
 * cancelling the Run's REAL execution Job; coroutine cancellation then propagates
 * naturally to the Run's suspension points. The signal only answers "was THIS Run
 * explicitly interrupted by the user?" (so Core can finalize the Interrupted
 * semantic) and carries the per-Run finalization acknowledgement back to the
 * driver (condition C is per-Run, never a cross-Run instance field).
 */
interface RunInterruptSignal {
    /** True when a user Stop has been committed for the current Run (not a lifecycle cancel). */
    fun isUserInterrupted(): Boolean

    /**
     * Invoked exactly once when AG Core has committed the Interrupted terminal
     * finalization for this Run (synthetic tool results + `RunFinished(Interrupted)`).
     * This is the per-Run acknowledgement the Runtime needs for its user-Stop
     * conversion guard (condition C). The Runtime passes its own implementation;
     * Core only ever sees this Core-owned contract and never queries the Runtime.
     */
    fun onInterruptedFinalized() {}

    companion object {
        val Absent: RunInterruptSignal = object : RunInterruptSignal {
            override fun isUserInterrupted() = false
        }

        /** A signal driven by a predicate + per-Run finalization callback (the Runtime's per-Run control). */
        fun of(
            isUserInterrupted: () -> Boolean,
            onInterruptedFinalized: () -> Unit = {},
        ): RunInterruptSignal = object : RunInterruptSignal {
            override fun isUserInterrupted() = isUserInterrupted()
            override fun onInterruptedFinalized() = onInterruptedFinalized()
        }
    }
}

/**
 * Per-Run configuration.
 *
 * [instructions] is the single lightweight instruction input AG.0 supports.
 * There is no skill system, no prompt package framework, and no dynamic
 * instruction discovery.
 */
data class RunOptions(
    val instructions: String = "",
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val approvalPolicy: ToolApprovalPolicy = ToolApprovalPolicy.AllowAll,
    val eventSink: AgentEventSink = AgentEventSink.Noop,
    val streamObserver: ModelStreamObserver = ModelStreamObserver.Noop,
) {
    init {
        require(maxSteps >= 1) { "maxSteps must be >= 1, was $maxSteps" }
    }

    companion object {
        const val DEFAULT_MAX_STEPS: Int = 8
    }
}

/**
 * The Agent runtime: owns sessions, drives the model/tool loop, emits events.
 *
 * AG Core knows exactly two collaborators, both as contracts:
 * [ModelProvider] and [ToolRegistry]. It knows no concrete provider and no
 * concrete tool, and it has no dependency on Android, ADB, or AT Core.
 *
 * The loop is:
 *
 *     append user message
 *     repeat:
 *         ask provider for one turn
 *         record the turn
 *         if final answer -> finish
 *         else run each tool call in order, appending each result
 *
 * Step accounting: one model turn equals one step. [RunOptions.maxSteps] is
 * therefore the maximum number of model turns. The budget is checked before a
 * turn is requested, so a provider scripted to only ever ask for tools is called
 * exactly maxSteps times and then the Run ends with
 * [RunFailure.MaxStepsReached].
 *
 * Tool calls within one turn run strictly serially, in the order the model
 * returned them. This is structural, not a runtime lock: the loop iterates a
 * list sequentially and awaits each dispatch before starting the next. There is
 * no parallel execution, fan-out, or scheduler.
 */
class AgentCore(
    private val modelProvider: ModelProvider,
    private val toolRegistry: ToolRegistry,
) {

    private val sessions = ConcurrentHashMap<String, Session>()

    /**
     * Returns the Session for [id], creating it on first use.
     *
     * Multiple Sessions coexist by design; this is not a single-session
     * singleton.
     */
    fun session(id: String): Session = sessions.computeIfAbsent(id) { Session(it) }

    /**
     * Runs one user request against one Session.
     *
     * This is a suspending function by design. AG Core owns Agent runtime
     * semantics; it does not own blocking, threading, dispatcher, or lifecycle
     * policy. Deciding whether a Run happens on the Android main thread, on a
     * background worker, inside a structured coroutine scope, or under a
     * timeout belongs to the host (AGA, AGC, or any other caller).
     *
     * A blocking wrapper here would pin the caller's thread for the entire Run
     * and would make host-driven cancellation impossible. Hosts that want to
     * block supply their own bridge, for example `runBlocking { }`.
     *
     * The [interruptSignal] carries the per-Run user-interrupt fact
     * ([RunInterruptSignal]) for this Run; it is the one-way seam by which a user
     * Stop becomes the [RunOutcome.Interrupted] semantic without Core ever querying
     * any host/Runtime state.
     */
    suspend fun run(
        sessionId: String,
        userInput: String,
        options: RunOptions = RunOptions(),
        interruptSignal: RunInterruptSignal = RunInterruptSignal.Absent,
    ): RunOutcome = executeRun(session(sessionId), userInput, options, interruptSignal)

    private suspend fun executeRun(
        session: Session,
        userInput: String,
        options: RunOptions,
        interruptSignal: RunInterruptSignal,
    ): RunOutcome {
        if (!session.tryBeginRun()) {
            // Rejected before the Run starts, so no lifecycle events are emitted.
            return RunOutcome.Failure(RunFailure.SessionBusy(session.id))
        }

        // Exactly one terminal outcome/event per admitted Run. The first path that
        // reaches a terminal (Answer / Failure / Interrupted) claims this latch; any
        // later competing terminal is discarded, so a Run can never emit two.
        val terminalClaimed = AtomicBoolean(false)
        // Per-Run bookkeeping of which tool calls had started dispatch, used by the
        // interrupt finalization to keep the in-flight vs not-executed distinction.
        val pairing = InterruptPairing()

        val sink = options.eventSink
        return try {
            sink.emit(AgentEvent.RunStarted(session.id))
            val outcome = driveLoop(session, userInput, options, interruptSignal, terminalClaimed, pairing)
            outcome
        } catch (e: CancellationException) {
            // Control flow, not a Run failure. The interrupt finalization (synthetic
            // tool results + terminal RunFinished(Interrupted) + per-Run
            // finalization acknowledgement) is committed from driveLoop's
            // provider-call and tool-dispatch CancellationException catches —
            // non-cancelled contexts where the finalization reliably runs. This
            // catch (a cancelled context) only propagates. A lifecycle/system
            // cancellation (no per-Run user-interrupt metadata) propagates without
            // any finalization, so the Runtime's conversion guard can never mistake
            // it for a user Stop.
            throw e
        } catch (e: Exception) {
            // A misbehaving event sink or an unforeseen defect must not leave the
            // caller with an escaping exception or a permanently busy Session.
            failRun(sink, RunFailure.RuntimeFailure(e::class.simpleName ?: "unknown"), terminalClaimed)
        } finally {
            session.endRun()
        }
    }

    /**
     * Interrupt semantic finalization (Core-owned). Appends a truthful synthetic
     * [ToolResultMessage] for every declared-but-uncompleted tool call so the
     * declared-call/result pairing invariant holds after an interrupt, then emits
     * exactly one [AgentEvent.RunFinished] with [RunOutcome.Interrupted].
     *
     * In-flight calls (dispatch started, no result observed) get a synthetic result
     * whose error states the external execution state is UNKNOWN; calls that never
     * started dispatch get one that states they were NOT executed. Neither claims the
     * external operation failed, was rolled back, or did not happen.
     */
    /**
     * Core-owned bookkeeping of, per Run, which tool calls have STARTED dispatch
     * (i.e. [AgentEvent.ToolCallStarted] was emitted) but not yet completed (no
     * [ToolResultMessage] appended). This is how the interrupt finalization tells an
     * in-flight call (external state unknown) from a not-started call (not executed).
     * It is per-Run local state, never global.
     */
    private class InterruptPairing {
        val started = HashSet<String>()
        fun markStarted(callId: String) {
            synchronized(this) { started.add(callId) }
        }

        fun snapshot(): Set<String> = synchronized(this) { started.toSet() }
    }

    /**
     * Appends a truthful synthetic [ToolResultMessage] for every declared-but-
     * uncompleted tool call so the declared-call/result pairing invariant holds
     * after an interrupt. Called from the CancellationException catch (a context
     * that is not itself cancelled), so the synthetic results are reliably
     * recorded.
     *
     * In-flight calls (dispatch started, no result observed) get a synthetic result
     * whose error states the external execution state is UNKNOWN; calls that never
     * started dispatch get one that states they were NOT executed. Neither claims
     * the external operation failed, was rolled back, or did not happen.
     */
    private fun appendSyntheticToolResults(
        session: Session,
        pairing: InterruptPairing,
    ) {
        val started = pairing.snapshot()
        for (msg in session.history()) {
            if (msg !is ModelMessage || msg.toolCalls.isEmpty()) continue
            val resultIds = session.history()
                .filterIsInstance<ToolResultMessage>()
                .mapTo(HashSet()) { it.toolCallId }
            for (call in msg.toolCalls) {
                if (call.id in resultIds) continue
                // In-flight: dispatch had started (ToolCallStarted emitted) but no
                // result was observed before the interrupt -> external state unknown.
                // Not-started: dispatch never began -> it did not execute.
                val result = if (call.id in started) {
                    ToolResult(success = false, output = "", error = IN_FLIGHT_INTERRUPTED_ERROR)
                } else {
                    ToolResult(success = false, output = "", error = NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR)
                }
                session.append(ToolResultMessage(call.id, call.name, result))
            }
        }
    }

    /**
     * Emits exactly one [AgentEvent.RunFinished] with [RunOutcome.Interrupted].
     * Called from [driveLoop]'s provider-call CancellationException catch (the
     * non-cancelled context where the interrupt was observed) so the emission is
     * not swallowed by coroutine cancellation.
     */
    private fun emitInterruptedTerminal(
        sink: AgentEventSink,
        terminalClaimed: AtomicBoolean,
    ) {
        if (terminalClaimed.compareAndSet(false, true)) {
            try {
                sink.emit(AgentEvent.RunFinished(RunOutcome.Interrupted))
            } catch (_: Exception) {
                // A broken observer must not prevent the interrupted outcome from
                // being recorded; the outcome is still returned/propagated.
            }
        }
    }

    private suspend fun driveLoop(
        session: Session,
        userInput: String,
        options: RunOptions,
        interruptSignal: RunInterruptSignal,
        terminalClaimed: AtomicBoolean,
        pairing: InterruptPairing,
    ): RunOutcome {
        val sink = options.eventSink
        session.append(UserMessage(userInput))

        var completedTurns = 0
        while (true) {
            if (completedTurns >= options.maxSteps) {
                return failRun(sink, RunFailure.MaxStepsReached(options.maxSteps), terminalClaimed)
            }

            val step = completedTurns + 1
            sink.emit(AgentEvent.ModelTurnStarted(step))

            val turn = try {
                modelProvider.complete(
                    ModelRequest(
                        sessionId = session.id,
                        instructions = options.instructions,
                        history = session.history(),
                        tools = toolRegistry.specs(),
                        streamObserver = options.streamObserver,
                    )
                )
            } catch (e: CancellationException) {
                // A user Stop interrupts the Run mid-turn. This is coroutine control
                // flow, not a provider failure: it must propagate so the Run's
                // execution terminates as cancelled and the interrupt finalization
                // (in executeRun) can run. This catch is a non-cancelled context (the
                // exception was observed, not thrown at this point), so the terminal
                // RunFinished(Interrupted) is emitted here rather than in the
                // executeRun catch (which runs in a cancelled context where emission
                // would be swallowed).
                if (interruptSignal.isUserInterrupted() && !terminalClaimed.get()) {
                    // Interrupt finalization (non-cancelled context): append the
                    // synthetic tool results and emit the terminal. Acknowledge to
                    // the per-Run signal (condition C) BEFORE the terminal emission
                    // so the acknowledgement is committed even if the emission
                    // throws in a cancelling context. The executeRun catch runs in a
                    // cancelled context where this finalization would be skipped.
                    appendSyntheticToolResults(session, pairing)
                    interruptSignal.onInterruptedFinalized()
                    emitInterruptedTerminal(sink, terminalClaimed)
                }
                throw e
            } catch (e: Exception) {
                return failRun(sink, RunFailure.ProviderFailure(e::class.simpleName ?: "unknown"), terminalClaimed)
            }

            sink.emit(AgentEvent.ModelTurnFinished(step, turn))
            completedTurns++

            when (turn) {
                is ModelTurn.FinalAnswer -> {
                    session.append(ModelMessage(turn.content))
                    sink.emit(AgentEvent.AnswerProduced(turn.content))
                    val outcome = RunOutcome.Answer(turn.content)
                    emitTerminal(sink, AgentEvent.RunFinished(outcome), terminalClaimed)
                    return outcome
                }

                is ModelTurn.ToolCalls -> {
                    // Prose emitted in the same turn as the tool calls is recorded
                    // alongside them. Before AG.PROVIDER-0.R1 this was hardcoded
                    // to "", which silently lost any such prose.
                    val calls = turn.calls.toList()
                    session.append(ModelMessage(turn.content, calls))
                    // Sequential by construction: each dispatch is awaited before
                    // the next begins, preserving the model's ordering.
                    for (call in calls) {
                        // Cancellation is cooperative: a user Stop cancels the
                        // execution Job, so a suspension point before the next
                        // dispatch observes it and the remaining calls are recorded
                        // as not-executed.
                        pairing.markStarted(call.id)
                        sink.emit(AgentEvent.ToolCallStarted(call))
                        val result = try {
                            toolRegistry.dispatch(call, options.approvalPolicy)
                        } catch (e: CancellationException) {
                            // An in-flight tool observed the interrupt (or the Run was
                            // cancelled) and threw CancellationException. This catch is
                            // a non-cancelled context, so the full interrupt
                            // finalization (synthetic tool results + outcome +
                            // terminal) is committed here (the executeRun catch runs
                            // in a cancelled context where it would be skipped).
                            if (interruptSignal.isUserInterrupted() && !terminalClaimed.get()) {
                                appendSyntheticToolResults(session, pairing)
                                interruptSignal.onInterruptedFinalized()
                                emitInterruptedTerminal(sink, terminalClaimed)
                            }
                            throw e
                        }
                        // Append before announcing. If a sink faults, the recorded
                        // history must not be left declaring a tool call that has
                        // no matching result.
                        session.append(ToolResultMessage(call.id, call.name, result))
                        sink.emit(AgentEvent.ToolCallFinished(call, result))
                    }
                }
            }
        }
    }

    private fun failRun(
        sink: AgentEventSink,
        failure: RunFailure,
        terminalClaimed: AtomicBoolean,
    ): RunOutcome {
        val outcome = RunOutcome.Failure(failure)
        // Terminal events are emitted defensively. If the sink is what broke, using
        // it again to report the failure would throw a second time and escape the
        // Run, replacing a structured outcome with an escaping exception.
        emitTerminal(sink, AgentEvent.RunFailed(failure), terminalClaimed)
        emitTerminal(sink, AgentEvent.RunFinished(outcome), terminalClaimed)
        return outcome
    }

    private fun emitTerminal(sink: AgentEventSink, event: AgentEvent, terminalClaimed: AtomicBoolean) {
        if (event is AgentEvent.RunFinished && !terminalClaimed.compareAndSet(false, true)) {
            // A terminal RunFinished was already emitted for this Run: the first
            // terminal transition wins, so a competing terminal is dropped.
            return
        }
        try {
            sink.emit(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken observer must not turn a reportable Run outcome into a crash.
        }
    }

    companion object {
        /** Synthetic ToolResult error for a call whose dispatch started but whose result was never observed before the interrupt. */
        const val IN_FLIGHT_INTERRUPTED_ERROR =
            "Run interrupted before a ToolResult was observed; external execution state may be unknown."

        /** Synthetic ToolResult error for a declared call that never started dispatch before the interrupt. */
        const val NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR =
            "Tool call was not executed because the Run was interrupted."
    }
}