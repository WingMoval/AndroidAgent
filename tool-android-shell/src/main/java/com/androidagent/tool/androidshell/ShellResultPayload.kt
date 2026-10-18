package com.androidagent.tool.androidshell

import com.at.engine.ATExecutionResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deterministic, machine-readable and model-readable result payload.
 *
 * The model must be able to see the full AT execution facts without guessing:
 * outcome, exitCode, stdout, stderr, hasError, and (only on mechanism failure)
 * error. Device stderr is NEVER promoted to [com.androidagent.core.ToolResult.error]
 * — stderr is genuine device output and stays inside the payload.
 *
 * V3 [ATExecutionResult] field mapping (identical to V2):
 *   outcome, stdout, stderr, exitCode, hasError, error
 */
internal object ShellResultPayload {

    fun encode(result: ATExecutionResult): String = buildJsonObject {
        put("outcome", JsonPrimitive(result.outcome.name))
        put("exitCode", JsonPrimitive(result.exitCode))
        put("stdout", JsonPrimitive(result.stdout))
        put("stderr", JsonPrimitive(result.stderr))
        put("hasError", JsonPrimitive(result.hasError))
        result.error?.let { put("error", JsonPrimitive(it)) }
    }.toString()
}
