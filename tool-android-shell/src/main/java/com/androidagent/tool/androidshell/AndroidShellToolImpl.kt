package com.androidagent.tool.androidshell

import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolSpec
import com.androidagent.integration.at.AtExecutionAdapter
import com.at.engine.ATExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete Android Shell Tool — the ONLY AndroidAgent execution layer that
 * connects to the AT Engine (V3 Freeze V1).
 *
 * AG.V2-2B: the tool now consumes AT through the unified [AtExecutionAdapter]
 * instead of directly constructing an [com.at.engine.ATEngine]. The adapter is
 * the single AT consumption point; the tool is a thin consumer of the adapter's
 * one-shot ADB execution.
 *
 * Responsibilities, frozen:
 *
 * ```
 * ToolCall.arguments ({"command": "..."})
 *   ↓ parse
 * AtExecutionAdapter.executeAdb(command)
 *   ↓ ATExecutionResult
 * ToolResult
 * ```
 *
 * It does NOT own: connectAuto / initialize / disconnect / discover / recover /
 * endpoint / port. Connection lifecycle belongs to [ShellConnection] and is
 * owned by the Host.
 *
 * No-replay HARD rule: one ToolCall performs at most one execute.
 * A TRANSPORT_ERROR result is returned faithfully.
 *
 * The [Executor] seam is tool-local and test-only: JVM unit tests cannot load
 * the Android/native runtime, so they inject a fake executor to verify
 * argument / result mapping and the no-replay guarantee.
 */
class AndroidShellToolImpl internal constructor(
    private val executor: Executor,
) : Tool {

    /**
     * Production constructor: consumes one-shot ADB execution through the
     * unified [AtExecutionAdapter]. The adapter must be initialized (ADB
     * connection READY) before the tool is used; the Host handles lifecycle.
     */
    constructor(adapter: AtExecutionAdapter) : this(
        Executor { command -> adapter.executeAdb(command) }
    )

    override val spec: ToolSpec = ToolSpec(
        name = TOOL_NAME,
        description =
            "WHAT: Execute one non-interactive shell command on the connected Android " +
                "device and return its result.\n" +
                "WHEN: Use when the task depends on actual Android device state or a " +
                "shell-accessible Android operation, and the answer is not already known " +
                "from earlier observations.\n" +
                "INPUT: A single JSON object with one string field \"command\", " +
                "for example {\"command\":\"id\"}. No other fields are supported.\n" +
                "OUTPUT: A JSON object with fields \"outcome\", \"exitCode\", \"stdout\", " +
                "\"stderr\", \"hasError\", and \"error\" (only on a mechanism failure). " +
                "A completed command reports success even when exitCode is non-zero; " +
                "the exitCode, stdout and stderr carry the real result. Only a tool " +
                "mechanism failure (transport / protocol / unsupported / timeout / " +
                "cancelled) reports success = false with an \"error\" field.",
    )

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        when (val parsed = ShellToolSchema.parse(call.arguments)) {
            is ShellToolSchema.ParseResult.Error ->
                ToolResult(success = false, output = "", error = parsed.reason)

            is ShellToolSchema.ParseResult.Ok -> {
                val result = executor.execute(parsed.command)
                ShellResultMapper.toToolResult(result)
            }
        }
    }

    /** Minimal tool-local seam (fake in JVM unit tests, real AT call in prod). */
    fun interface Executor {
        suspend fun execute(command: String): ATExecutionResult
    }

    companion object {
        const val TOOL_NAME: String = "android_shell"
    }
}
