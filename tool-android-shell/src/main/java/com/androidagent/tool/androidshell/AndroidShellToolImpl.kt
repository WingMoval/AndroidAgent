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
     * Production constructor: consumes one-shot LOCAL execution through the
     * unified [AtExecutionAdapter].
     *
     * AG.AT-V4-AGENT-USE-0: Local-first execution semantics. The default
     * android_shell tool uses AT V4 Local one-shot execution (app UID shell),
     * NOT ADB. This is the primary execution path on all devices. ADB is
     * available via the adapter for tasks that specifically need ADB
     * capabilities (uid=2000 shell, system commands), but is not the default.
     */
    constructor(adapter: AtExecutionAdapter) : this(
        Executor { command -> adapter.executeLocal(command) }
    )

    override val spec: ToolSpec = ToolSpec(
        name = TOOL_NAME,
        description =
            "WHAT: Execute one non-interactive command in AndroidAgent's local shell " +
                "environment and return its result.\n" +
                "CONTEXT: Commands run locally under AndroidAgent's application execution " +
                "identity by default. File paths are relative to the app's private storage.\n" +
                "ADB: An `adb` CLI (provided by AndroidTerminal) is available inside this " +
                "shell. It is a limited embedded CLI, NOT the full desktop platform-tools " +
                "adb: do not assume arbitrary desktop subcommands (e.g. `adb devices`) are " +
                "available. The main execution path is `adb shell ...`, which runs under " +
                "the Android shell principal (a different execution identity from the " +
                "local app shell; inspect with `adb shell id`).\n" +
                "  - Simple command: pass argv directly, e.g. `adb shell cat /path/file` " +
                "or `adb shell getprop ro.product.model`. Do NOT wrap a multi-word command " +
                "in double quotes like `adb shell \"cat /path/file\"` - the quoted string " +
                "is forwarded as ONE argument, so the remote shell looks for an executable " +
                "with that whole name and fails.\n" +
                "  - Commands needing remote shell syntax (pipes |, redirection >, ;, " +
                "&&, globs): use an explicit remote shell, e.g. " +
                "`adb shell sh -c 'ls /system/bin | grep sh | wc -l'`.\n" +
                "WHEN: Use the local shell for command execution, process operations, " +
                "environment inspection, build/test commands, or behavior that requires " +
                "shell semantics. Use the `adb` CLI inside this shell when the task " +
                "requires ADB-specific operations, ADB shell privileges, pairing, or " +
                "device-side shell access.\n" +
                "INPUT: A single JSON object with one string field \"command\", " +
                "for example {\"command\":\"id\"}. No other fields are supported.\n" +
                "OUTPUT: A JSON object with fields \"outcome\", \"exitCode\", \"stdout\", " +
                "\"stderr\", \"hasError\", and \"error\" (only on a mechanism failure). " +
                "IMPORTANT: success=true only means the execution mechanism completed; it " +
                "does NOT mean the command succeeded. A completed command reports success " +
                "even when exitCode is non-zero. Judge command success from exitCode " +
                "(0 = success, non-zero = failure) and read stdout/stderr for the real " +
                "result or the reason for failure. Only a tool mechanism failure " +
                "(transport / protocol / unsupported / timeout / cancelled) reports " +
                "success = false with an \"error\" field.",
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
