package com.androidagent.app.trace

import com.androidagent.core.AgentEvent
import com.androidagent.core.AgentEventSink
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunFailure

/**
 * The Host-side consumer of AG Core's `AgentEvent` stream. It turns the
 * synchronous, ordered event callbacks of one Run into an [AgentTrace].
 *
 * Ownership, frozen:
 *  - `AgentEvent` semantics and the `AgentEventSink` contract are AG Core's.
 *  - This collector is Host/Observability. It adds no agent semantics, no
 *    metrics, no persistence, and no logging framework. It only records.
 *
 * The collector is constructed per Run with the Host-known metadata AG Core
 * cannot supply on its own (model id, instructions identity, available tool
 * names). AG Core's events alone are sufficient to reconstruct tool calls,
 * arguments, results, ordering, and the terminal outcome — no Core change is
 * required for AGENT-0.PREFLIGHT.
 *
 * A trace is one per Run. A Run is admitted by AG Core before the first
 * `RunStarted` event, so a rejected (busy) Run produces no trace by design.
 */
class AgentTraceCollector(
    private val modelId: String,
    private val instructionsVersion: String,
    private val instructionsHash: String,
    private val availableToolNames: List<String>,
) : AgentEventSink {

    private val clock: () -> Long = { System.currentTimeMillis() }
    private val localTraceId: String = "trace-" + java.util.UUID.randomUUID()

    private var sessionId: String? = null
    private var startedAt: Long? = null
    private var endedAt: Long? = null
    private var finalOutcome: String? = null
    private val spans = mutableListOf<Span>()

    /** Whether a Run has been admitted (i.e. this collector has seen RunStarted). */
    val hasRun: Boolean
        get() = sessionId != null

    /** The assembled trace, or null when no Run was admitted. */
    val trace: AgentTrace?
        get() {
            val session = sessionId ?: return null
            val start = startedAt ?: return null
            return AgentTrace(
                localTraceId = localTraceId,
                sessionId = session,
                modelId = modelId,
                instructionsVersion = instructionsVersion,
                instructionsHash = instructionsHash,
                availableToolNames = availableToolNames,
                startedAtEpochMillis = start,
                endedAtEpochMillis = endedAt,
                spans = spans.toList(),
                finalOutcome = finalOutcome,
            )
        }

    override fun emit(event: AgentEvent) {
        when (event) {
            is AgentEvent.RunStarted -> {
                sessionId = event.sessionId
                val start = clock()
                startedAt = start
                spans += RunStartSpan(start)
            }

            is AgentEvent.ModelTurnStarted ->
                spans += ModelTurnStartSpan(clock(), event.step)

            is AgentEvent.ModelTurnFinished ->
                spans += ModelTurnEndSpan(
                    atEpochMillis = clock(),
                    step = event.step,
                    turnType = turnTypeOf(event.turn),
                    content = event.turn.content(),
                    toolCallNames = event.turn.toolCallNames(),
                )

            is AgentEvent.ToolCallStarted ->
                spans += ToolCallSpan(clock(), event.call.name, event.call.arguments)

            is AgentEvent.ToolCallFinished ->
                spans += ToolResultSpan(
                    atEpochMillis = clock(),
                    toolName = event.call.name,
                    success = event.result.success,
                    output = event.result.output,
                    error = event.result.error,
                )

            is AgentEvent.AnswerProduced ->
                spans += FinalAnswerSpan(clock(), event.content)

            is AgentEvent.RunFailed ->
                spans += RunFailureSpan(clock(), describeFailure(event.failure))

            is AgentEvent.RunFinished -> {
                endedAt = clock()
                finalOutcome = outcomeOf(event)
            }
        }
    }

    private fun turnTypeOf(turn: ModelTurn): String =
        if (turn is ModelTurn.FinalAnswer) "final_answer" else "tool_calls"

    private fun ModelTurn.content(): String =
        when (this) {
            is ModelTurn.FinalAnswer -> content
            is ModelTurn.ToolCalls -> content
        }

    private fun ModelTurn.toolCallNames(): List<String> =
        when (this) {
            is ModelTurn.FinalAnswer -> emptyList()
            is ModelTurn.ToolCalls -> calls.map { it.name }
        }

    private fun describeFailure(failure: RunFailure): String = when (failure) {
        is RunFailure.ProviderFailure -> "provider_failure: ${failure.cause}"
        is RunFailure.MaxStepsReached -> "max_steps_reached: ${failure.maxSteps}"
        is RunFailure.SessionBusy -> "session_busy: ${failure.sessionId}"
        is RunFailure.RuntimeFailure -> "runtime_failure: ${failure.cause}"
        is RunFailure.DurabilityFailure -> "durability_failure: ${failure.boundary}: ${failure.cause}"
    }

    private fun outcomeOf(event: AgentEvent.RunFinished): String =
        when (val outcome = event.outcome) {
            is com.androidagent.core.RunOutcome.Answer -> "answer"
            is com.androidagent.core.RunOutcome.Failure -> "failure: ${describeFailure(outcome.failure)}"
            is com.androidagent.core.RunOutcome.Interrupted -> "interrupted"
        }
}
