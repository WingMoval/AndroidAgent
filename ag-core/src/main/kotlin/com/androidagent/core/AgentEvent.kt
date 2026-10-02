package com.androidagent.core

/**
 * The observable lifecycle of a single Run.
 *
 * Events exist so a frontend or a test can observe the Run without AG Core
 * knowing anything about UI. This is a plain synchronous callback contract, not
 * a reactive stream: AG.0 has no need for a Flow dependency, and adding one
 * would only add a runtime dependency and an asynchronous ordering contract that
 * nothing currently needs.
 */
sealed interface AgentEvent {

    /** The Run was admitted and is starting. Never emitted for a rejected Run. */
    data class RunStarted(val sessionId: String) : AgentEvent

    /** About to ask the provider for turn number [step] (1-based). */
    data class ModelTurnStarted(val step: Int) : AgentEvent

    /** The provider returned [turn] for [step]. */
    data class ModelTurnFinished(val step: Int, val turn: ModelTurn) : AgentEvent

    /** About to execute [call]. */
    data class ToolCallStarted(val call: ToolCall) : AgentEvent

    /** [call] finished with [result], successfully or as a structured failure. */
    data class ToolCallFinished(val call: ToolCall, val result: ToolResult) : AgentEvent

/** The Run produced its final answer. */
    data class AnswerProduced(val content: String) : AgentEvent

/** The Run ended without an answer. */
    data class RunFailed(val failure: RunFailure) : AgentEvent

/**
 * *Best-effort* terminal event for an admitted Run.
     *
     * Every ordinary exit emits exactly one of these. The exceptions are: a Run
     * that is cancelled, and a Run whose own terminal emission faults. In both
     * cases no terminal event is delivered, because a cancelled coroutine must
     * not be made to do more work. Callers that need an unconditional outcome
     * should read the value returned by `run`.
     */
    data class RunFinished(val outcome: RunOutcome) : AgentEvent
}

/**
 * Receives [AgentEvent]s.
 *
 * An exception thrown by a sink is treated as a runtime failure of the Run, not
 * as a reason to let frontend state corruption escape into agent state.
 */
fun interface AgentEventSink {
    fun emit(event: AgentEvent)

    companion object {
        /** Default sink: discards everything. */
        val Noop: AgentEventSink = AgentEventSink { }
    }
}