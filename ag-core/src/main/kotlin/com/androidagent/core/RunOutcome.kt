package com.androidagent.core

/**
 * How one Run ended.
 */
sealed interface RunOutcome {

    /** The Run produced a final answer. */
    data class Answer(val content: String) : RunOutcome

    /**
     * The Run was interrupted by the user (a Stop). This is NOT a [Failure]: a user
     * Stop is deliberate control, not an error, so it must not render as one.
     *
     * Produced by AG Core's interrupt semantic finalization (see [AgentCore]): Core
     * recognises the per-Run user-interrupt fact carried into the Run, finalises the
     * interrupted history (including any synthetic tool results required to keep the
     * declared-call/result pairing intact), selects this outcome, and emits exactly
     * one [AgentEvent.RunFinished]. A Run interrupted this way terminates its
     * execution as *cancelled* — the structured value the host observes is produced
     * by the host-facing run driver (the Agent Runtime's send boundary), which
     * converts the user-caused child cancellation into this outcome only when the
     * caller is still active and this Run's user-interrupt was committed.
     */
    data object Interrupted : RunOutcome

    /** The Run ended without an answer. */
    data class Failure(val failure: RunFailure) : RunOutcome
}

/**
 * Why a Run produced no answer.
 *
 * Kept to four kinds on purpose. Tool-level failures are deliberately NOT here:
 * a failed tool call is appended to history and handed back to the model, which
 * then decides what to do. Only conditions that end the Run itself appear below.
 */
sealed interface RunFailure {

    /**
     * The [ModelProvider] could not produce a turn.
     *
     * [cause] is the exception's type name, not its message, for the same
     * leakage reason as [ToolResult.error].
     */
    data class ProviderFailure(val cause: String) : RunFailure

    /** The Run used its whole step budget without reaching a final answer. */
    data class MaxStepsReached(val maxSteps: Int) : RunFailure

    /** Another Run already holds this Session. See [Session]. */
    data class SessionBusy(val sessionId: String) : RunFailure

    /** An unexpected exception escaped the Run loop. */
    data class RuntimeFailure(val cause: String) : RunFailure
}