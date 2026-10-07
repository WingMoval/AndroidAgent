package com.androidagent.tool.androidshell

import com.androidagent.core.ToolResult
import com.androidterminal.runtime.AtExecutionOutcome
import com.androidterminal.runtime.AtExecutionResult

/**
 * Maps an AT [AtExecutionResult] to the frozen AG [ToolResult] contract.
 *
 * AG semantics: [ToolResult.success] = tool invocation MECHANISM success, NOT
 * "device exit code == 0". Therefore AT [AtExecutionOutcome.COMPLETED] is a tool
 * success even when the device command exited non-zero (e.g. `exit 7`).
 *
 * Only failures of the execution path itself (TRANSPORT / PROTOCOL / UNSUPPORTED
 * / TIMED_OUT / CANCELLED) are tool failures.
 */
internal object ShellResultMapper {

    fun toToolResult(result: AtExecutionResult): ToolResult = when (result.outcome) {
        AtExecutionOutcome.COMPLETED -> ToolResult(
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