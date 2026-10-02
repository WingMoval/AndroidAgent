package com.androidagent.core

/**
 * How one Run ended.
 */
sealed interface RunOutcome {

    /** The Run produced a final answer. */
    data class Answer(val content: String) : RunOutcome

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