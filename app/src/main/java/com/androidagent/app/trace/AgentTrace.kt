package com.androidagent.app.trace

/**
 * Minimal structured trace of one Agent Run, reconstructed from AG Core's
 * `AgentEvent` stream.
 *
 * This is a Host/Observability model. It is NOT an AG Core type, NOT an Agent
 * Runtime type, and NOT a production monitoring schema. Its single purpose is
 * causal reconstruction: given a Run, a debugger must be able to answer which
 * tool was called, with what arguments, what it returned, in what order, and how
 * the Run ended.
 *
 * Secret policy: [ToolSpan.output] carries the tool result, which for the shell
 * tool is the device stdout/stderr. That content passes through [TraceFormatter]
 * redaction before it is rendered or logged. Nothing here ever stores an API
 * key, an Authorization header, or raw provider configuration.
 */
data class AgentTrace(
    val localTraceId: String,
    val sessionId: String,
    val modelId: String,
    val instructionsVersion: String,
    val instructionsHash: String,
    val availableToolNames: List<String>,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val spans: List<Span> = emptyList(),
    val finalOutcome: String? = null,
)

/** One observed event within a Run. */
sealed interface Span

/** The Run was admitted and started. */
data class RunStartSpan(
    val atEpochMillis: Long,
) : Span

/** The provider was asked to produce turn [step]. */
data class ModelTurnStartSpan(
    val atEpochMillis: Long,
    val step: Int,
) : Span

/** The provider returned a turn. [toolCallNames] is non-empty for a tool-call turn. */
data class ModelTurnEndSpan(
    val atEpochMillis: Long,
    val step: Int,
    val turnType: String,
    val content: String,
    val toolCallNames: List<String>,
) : Span

/** A tool call was dispatched. [arguments] is the opaque provider-neutral string. */
data class ToolCallSpan(
    val atEpochMillis: Long,
    val toolName: String,
    val arguments: String,
) : Span {
    // Redacted toString so the opaque arguments can never leak a secret through
    // a log, an assertion message, or a printed trace.
    override fun toString(): String =
        "ToolCallSpan(@$atEpochMillis name=$toolName args=${TraceFormatter.redact(arguments)})"
}

/** A tool call completed. [output]/[error] are the tool's result, already the
 *  mechanism-level view AG Core produces (domain failures stay inside [output]). */
data class ToolResultSpan(
    val atEpochMillis: Long,
    val toolName: String,
    val success: Boolean,
    val output: String,
    val error: String?,
) : Span {
    // The tool output is device stdout/stderr, which is exactly where a shell
    // command could echo a credential. Redact it in every rendered form.
    override fun toString(): String =
        "ToolResultSpan(@$atEpochMillis name=$toolName success=$success " +
            "output=${TraceFormatter.redact(output)}" +
            (error?.let { " error=${TraceFormatter.redact(it)}" } ?: "") + ")"
}

/** The Run produced a final answer. */
data class FinalAnswerSpan(
    val atEpochMillis: Long,
    val content: String,
) : Span {
    override fun toString(): String =
        "FinalAnswerSpan(@$atEpochMillis content=${TraceFormatter.redact(content)})"
}

/** The Run ended without an answer. */
data class RunFailureSpan(
    val atEpochMillis: Long,
    val failure: String,
) : Span {
    override fun toString(): String =
        "RunFailureSpan(@$atEpochMillis failure=${TraceFormatter.redact(failure)})"
}
