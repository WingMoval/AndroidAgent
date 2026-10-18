package com.androidagent.tool.androidshell

import com.androidagent.core.ToolResult
import com.at.engine.ATExecutionOutcome
import com.at.engine.ATExecutionResult

/**
 * Maps an AT V3 [ATExecutionResult] to the frozen AG [ToolResult] contract.
 *
 * AG semantics: [ToolResult.success] = tool invocation MECHANISM success, NOT
 * "device exit code == 0". Therefore AT [ATExecutionOutcome.COMPLETED] is a tool
 * success even when the device command exited non-zero (e.g. `exit 7`).
 *
 * Only failures of the execution path itself (TRANSPORT_ERROR / UNSUPPORTED)
 * are tool failures.
 *
 * V3 outcome mapping:
 *   COMPLETED     -> success = true  (exit code may be anything)
 *   TRANSPORT_ERROR -> success = false
 *   UNSUPPORTED    -> success = false
 *
 * The V2 outcome names (PROTOCOL_ERROR / TIMED_OUT / CANCELLED) do not exist
 * in V3; they collapse into TRANSPORT_ERROR.
 */
internal object ShellResultMapper {

    fun toToolResult(result: ATExecutionResult): ToolResult = when (result.outcome) {
        ATExecutionOutcome.COMPLETED -> ToolResult(
            success = true,
            output = ShellResultPayload.encode(result),
        )

        else -> ToolResult(
            success = false,
            output = ShellResultPayload.encode(result),
            error = result.error ?: "shell execution failed: ${result.outcome.name}",
        )
    }
}
