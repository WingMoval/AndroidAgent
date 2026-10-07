package com.androidagent.app.trace

/**
 * Renders an [AgentTrace] into a deterministic, redacted, line-oriented text
 * trace.
 *
 * Redaction is applied to every field before it is rendered:
 *  - `Authorization: <value>` / `Bearer <value>` -> `Authorization: <redacted>`
 *  - `api_key` / `apiKey` / `key` / `password` / `secret` followed by a value
 *    (JSON or `=`/`:`) -> the value is replaced by `<redacted>`
 *
 * The redaction is a safety net for Host-rendered trace content. It is not a
 * substitute for the Provider's own secret handling (which never logs the
 * Authorization header or the raw config). A test pins the behaviour with a
 * fake sentinel so no real credential is ever exercised.
 */
object TraceFormatter {

    private val secretKeyPattern = Regex(
        "(?i)(\"?(?:api[_-]?key|apikey|key|password|secret|token|authorization)\"?\\s*[:=]\\s*)" +
            "(\"[^\"]*\"|'[^']*'|[^\\s,}]+)",
    )

    private val bearerPattern = Regex("(?i)\\bBearer\\s+\\S+")

    fun format(trace: AgentTrace): String = buildString {
        appendLine("run $trace.localTraceId session=${trace.sessionId} model=${trace.modelId}")
        appendLine("instructions=${trace.instructionsVersion}#${trace.instructionsHash}")
        appendLine("tools=${trace.availableToolNames.joinToString(",")}")
        appendLine("start=${trace.startedAtEpochMillis} end=${trace.endedAtEpochMillis}")
        for (span in trace.spans) {
            appendLine("  " + renderSpan(span))
        }
        append("outcome=${redact(trace.finalOutcome ?: "<none>")}")
    }

    private fun renderSpan(span: Span): String = when (span) {
        is RunStartSpan -> "run_start @${span.atEpochMillis}"

        is ModelTurnStartSpan -> "model_turn_start step=${span.step} @${span.atEpochMillis}"

        is ModelTurnEndSpan -> "model_turn_end step=${span.step} type=${span.turnType}" +
            (if (span.toolCallNames.isNotEmpty()) " tools=[${span.toolCallNames.joinToString(",")}]" else "") +
            " @${span.atEpochMillis}"

        is ToolCallSpan -> "tool_call name=${span.toolName} args=${redact(span.arguments)} @${span.atEpochMillis}"

        is ToolResultSpan -> "tool_result name=${span.toolName} success=${span.success} " +
            "output=${redact(span.output)}" +
            (span.error?.let { " error=${redact(it)}" } ?: "") +
            " @${span.atEpochMillis}"

        is FinalAnswerSpan -> "final_answer=${redact(span.content)} @${span.atEpochMillis}"

        is RunFailureSpan -> "run_failure=${redact(span.failure)} @${span.atEpochMillis}"
    }

    /** Replaces secret-bearing values with a marker. Never alters non-secret text. */
    fun redact(input: String): String {
        var out = input
        // Authorization: <value> where <value> is the whole remainder of the
        // credential (e.g. "Bearer <token>"), not just the first token.
        out = out.replace(Regex("(?i)(authorization\\s*[:=]\\s*)\\S+(?:\\s+\\S+)?"), "$1<redacted>")
        out = out.replace(bearerPattern, "Bearer <redacted>")
        out = out.replace(secretKeyPattern) { m -> m.groupValues[1] + "<redacted>" }
        return out
    }
}
