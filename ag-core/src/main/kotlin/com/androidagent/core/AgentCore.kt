package com.androidagent.core

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

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
     */
    suspend fun run(
        sessionId: String,
        userInput: String,
        options: RunOptions = RunOptions(),
    ): RunOutcome = executeRun(session(sessionId), userInput, options)

    private suspend fun executeRun(
        session: Session,
        userInput: String,
        options: RunOptions,
    ): RunOutcome {
        if (!session.tryBeginRun()) {
            // Rejected before the Run starts, so no lifecycle events are emitted.
            return RunOutcome.Failure(RunFailure.SessionBusy(session.id))
        }

        val sink = options.eventSink
        try {
            sink.emit(AgentEvent.RunStarted(session.id))
            return driveLoop(session, userInput, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A misbehaving event sink or an unforeseen defect must not leave the
            // caller with an escaping exception or a permanently busy Session.
            return failRun(sink, RunFailure.RuntimeFailure(e::class.simpleName ?: "unknown"))
        } finally {
            session.endRun()
        }
    }

    private suspend fun driveLoop(
        session: Session,
        userInput: String,
        options: RunOptions,
    ): RunOutcome {
        val sink = options.eventSink
        session.append(UserMessage(userInput))

        var completedTurns = 0
        while (true) {
            if (completedTurns >= options.maxSteps) {
                return failRun(sink, RunFailure.MaxStepsReached(options.maxSteps))
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
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return failRun(sink, RunFailure.ProviderFailure(e::class.simpleName ?: "unknown"))
            }

            sink.emit(AgentEvent.ModelTurnFinished(step, turn))
            completedTurns++

            when (turn) {
                is ModelTurn.FinalAnswer -> {
                    session.append(ModelMessage(turn.content))
                    sink.emit(AgentEvent.AnswerProduced(turn.content))
                    val outcome = RunOutcome.Answer(turn.content)
                    emitTerminal(sink, AgentEvent.RunFinished(outcome))
                    return outcome
                }

                is ModelTurn.ToolCalls -> {
                    // ModelTurn.ToolCalls carries no prose, so the recorded turn
                    // has empty content and the calls the model requested. The
                    // call list is copied so a provider cannot mutate recorded
                    // history after the fact.
                    val calls = turn.calls.toList()
                    session.append(ModelMessage("", calls))
                    // Sequential by construction: each dispatch is awaited before
                    // the next begins, preserving the model's ordering.
                    for (call in calls) {
                        sink.emit(AgentEvent.ToolCallStarted(call))
                        val result = toolRegistry.dispatch(call, options.approvalPolicy)
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

    private fun failRun(sink: AgentEventSink, failure: RunFailure): RunOutcome {
        val outcome = RunOutcome.Failure(failure)
        // Terminal events are emitted defensively. If the sink is what broke, using
        // it again to report the failure would throw a second time and escape the
        // Run, replacing a structured outcome with an escaping exception.
        emitTerminal(sink, AgentEvent.RunFailed(failure))
        emitTerminal(sink, AgentEvent.RunFinished(outcome))
        return outcome
    }

    private fun emitTerminal(sink: AgentEventSink, event: AgentEvent) {
        try {
            sink.emit(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken observer must not turn a reportable Run outcome into a crash.
        }
    }
}