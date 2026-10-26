package com.androidagent.app

import com.androidagent.app.trace.AgentTraceCollector
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.app.trace.ToolCallSpan
import com.androidagent.app.trace.ToolResultSpan
import com.androidagent.app.trace.RunFailureSpan
import com.androidagent.app.trace.FinalAnswerSpan
import com.androidagent.core.AgentCore
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolSpec
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AGENT-0.PREFLIGHT — T1, T4, T5, T6, T7, T8, T10, T11, T12.
 *
 * Proves the real plumbing against AG Core's frozen contracts using a scripted
 * ModelProvider and a fake tool. No live endpoint, no AT Engine, no Android. The
 * collector is wired exactly the way the Host wires it (RunOptions.eventSink), so
 * what is tested here is what the production composition does.
 */
class AgentPreflightTest {

    // ------------------------------------------------------------------ helpers

    /** Records every [ModelRequest] it receives and replays scripted turns. */
    private class ScriptedProvider(
        private val turns: List<ModelTurn>,
        private val toolSpecs: List<ToolSpec>,
    ) : ModelProvider {
        val requests = mutableListOf<ModelRequest>()
        private var i = 0

        override suspend fun complete(request: ModelRequest): ModelTurn {
            requests.add(request)
            return turns[i++].takeIf { i < turns.size } ?: turns.last()
        }
    }

    private class FakeTool(private val result: ToolResult) : Tool {
        val calls = mutableListOf<ToolCall>()
        override val spec: ToolSpec = ToolSpec("android_shell", "fake shell tool")
        override suspend fun execute(call: ToolCall): ToolResult {
            calls.add(call)
            return result
        }
    }

    private fun newCollector() = AgentTraceCollector(
        modelId = "model-a",
        instructionsVersion = AgentInstructions.VERSION,
        instructionsHash = AgentInstructions.HASH,
        availableToolNames = listOf("android_shell"),
    )

    // ------------------------------------------------------------ T1 instructions

    @Test
    fun T1_instructions_flow_from_options_into_request() = runBlocking {
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("done")), emptyList())
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop)
        val options = RunOptions(instructions = AgentInstructions.CONTENT)

        core.run("s1", "hello", options)

        assertEquals(1, provider.requests.size)
        assertEquals(AgentInstructions.CONTENT, provider.requests[0].instructions)
    }

    // --------------------------------------------------------- T4 user history

    @Test
    fun T4_user_message_enters_next_request_history() = runBlocking {
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("ok")), emptyList())
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop)

        core.run("s1", "find the foreground app", RunOptions(instructions = AgentInstructions.CONTENT))

        val history = provider.requests[0].history
        assertTrue(history.isNotEmpty())
        val user = history.filterIsInstance<com.androidagent.core.UserMessage>()
        assertEquals(1, user.size)
        assertEquals("find the foreground app", user[0].content)
    }

    // --------------------------------------------- T5 tool result feedback

    @Test
    fun T5_tool_result_enters_next_model_request_history() = runBlocking {
        val tool = FakeTool(ToolResult(success = true, output = "{\"stdout\":\"uid=2000\"}"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedProvider(listOf(ModelTurn.ToolCalls(listOf(ToolCall("c1", "android_shell", """{"command":"id"}"""))), ModelTurn.FinalAnswer("answer")), registry.specs())
        val core = AgentCore(provider, registry, MessageDurability.Noop)

        core.run("s1", "whoami?", RunOptions(instructions = AgentInstructions.CONTENT))

        assertEquals(2, provider.requests.size)
        val secondHistory = provider.requests[1].history
        // After the tool ran, history holds: UserMessage, ModelMessage(tool call), ToolResultMessage.
        val toolResult = secondHistory.filterIsInstance<com.androidagent.core.ToolResultMessage>()
        assertEquals(1, toolResult.size)
        assertEquals("android_shell", toolResult[0].toolName)
        assertEquals("uid=2000", toolResult[0].result.output.substringAfter("\"stdout\":\"").substringBefore("\""))
        // The tool call must also be recorded before the result.
        val modelMsgs = secondHistory.filterIsInstance<com.androidagent.core.ModelMessage>()
        assertTrue(modelMsgs.any { it.toolCalls.any { c -> c.id == "c1" } })
    }

    // --------------------------------------------- T6 trace event order

    @Test
    fun T6_trace_captures_event_order() = runBlocking {
        val tool = FakeTool(ToolResult(success = true, output = "ok"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedProvider(listOf(ModelTurn.ToolCalls(listOf(ToolCall("c1", "android_shell", """{"command":"id"}"""))), ModelTurn.FinalAnswer("done")), registry.specs())
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val sink = newCollector()

        core.run("s1", "hi", RunOptions(instructions = AgentInstructions.CONTENT, eventSink = sink))

        val trace = sink.trace
        assertTrue(trace != null)
        val types = trace!!.spans.map { spanType(it) }
        // Order: run_start, model_turn_start, model_turn_end, tool_call, tool_result,
        // model_turn_start, model_turn_end, final_answer.
        assertEquals(
            listOf("run_start", "model_turn_start", "model_turn_end", "tool_call",
                "tool_result", "model_turn_start", "model_turn_end", "final_answer"),
            types,
        )
    }

    // --------------------------------------------- T7 reconstructability

    @Test
    fun T7_trace_reconstructs_tool_args_result_and_order() = runBlocking {
        val tool = FakeTool(ToolResult(success = false, output = "", error = "transport error"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedProvider(listOf(ModelTurn.ToolCalls(listOf(ToolCall("c9", "android_shell", """{"command":"getprop ro.build.version.sdk"}"""))), ModelTurn.FinalAnswer("could not reach device")), registry.specs())
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val sink = newCollector()

        val outcome = core.run("s1", "list packages", RunOptions(instructions = AgentInstructions.CONTENT, eventSink = sink))

        val trace = sink.trace
        assertTrue(trace != null)
        val callSpan = trace!!.spans.filterIsInstance<ToolCallSpan>().single()
        assertEquals("android_shell", callSpan.toolName)
        assertEquals("""{"command":"getprop ro.build.version.sdk"}""", callSpan.arguments)

        val resultSpan = trace.spans.filterIsInstance<ToolResultSpan>().single()
        assertEquals("android_shell", resultSpan.toolName)
        assertFalse(resultSpan.success)
        assertEquals("transport error", resultSpan.error)

        // Order: the tool call span precedes the tool result span.
        val callIdx = trace.spans.indexOf(callSpan)
        val resultIdx = trace.spans.indexOf(resultSpan)
        assertTrue(callIdx < resultIdx)

        // Final outcome captured and matches the returned outcome.
        val finalSpan = trace.spans.filterIsInstance<FinalAnswerSpan>().single()
        assertEquals("could not reach device", finalSpan.content)
        assertEquals("answer", trace.finalOutcome)
        assertTrue(outcome is com.androidagent.core.RunOutcome.Answer)
    }

    @Test
    fun T7b_trace_captures_run_failure_outcome() = runBlocking {
        val provider = ScriptedProvider(listOf(ModelTurn.ToolCalls(listOf(ToolCall("c1", "android_shell", """{"command":"x"}""")))), emptyList())
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop) // no tools registered -> dispatch fails
        val sink = newCollector()

        val outcome = core.run("s1", "hi", RunOptions(instructions = AgentInstructions.CONTENT, eventSink = sink))

        // The tool is unknown, so the tool result is a mechanism failure and the run
        // continues; with maxSteps=1 the single turn is used and the run ends.
        assertTrue(outcome is com.androidagent.core.RunOutcome.Failure)
        val trace = sink.trace
        assertTrue(trace != null)
        assertTrue(trace!!.spans.any { it is ToolResultSpan && !it.success })
        assertTrue(trace.finalOutcome!!.startsWith("failure:"))
    }

    // --------------------------------------------- T8 instructions identity

    @Test
    fun T8_same_instructions_same_hash_and_content_change_changes_hash() {
        // Same content -> same deterministic hash, twice.
        assertEquals(AgentInstructions.HASH, sha256(AgentInstructions.CONTENT))
        // Hash is pinned: it must be a 64-char lowercase sha-256 hex string.
        assertTrue(AgentInstructions.HASH.length == 64)
        assertTrue(AgentInstructions.HASH == AgentInstructions.HASH.lowercase())
        // Any content change changes the hash.
        val changed = AgentInstructions.CONTENT + " "
        assertNotEquals(AgentInstructions.HASH, sha256(changed))
        // Version identity is stable and non-empty.
        assertEquals("AGENT_INSTRUCTIONS_V1", AgentInstructions.VERSION)
    }

    // --------------------------------------------- T10 core boundary

    @Test
    fun T10_ag_core_has_no_trace_or_platform_implementation() {
        val coreFiles = java.io.File("..", "ag-core/src/main/kotlin/com/androidagent/core")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue(coreFiles.isNotEmpty(), "ag-core source must exist")
        val all = coreFiles.joinToString("\n") { it.readText() }
        // No trace collector, no telemetry, no Android, no logging framework.
        assertFalse(all.contains("AgentTraceCollector"))
        assertFalse(all.contains("OpenTelemetry"))
        assertFalse(all.contains("import android"))
        assertFalse(all.contains("import android."))
        assertFalse(all.contains("java.util.logging"))
        assertFalse(all.contains("android.util.Log"))
        // The observability seam is still just the event contract.
        assertTrue(all.contains("AgentEventSink"))
    }

    // --------------------------------------------- T11 runtime boundary

    @Test
    fun T11_ag_runtime_has_no_trace_metrics_or_logging() {
        val runtimeFiles = java.io.File("..", "ag-runtime/src/main/kotlin/com/androidagent/runtime")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue(runtimeFiles.isNotEmpty())
        val all = runtimeFiles.joinToString("\n") { it.readText() }
        assertFalse(all.contains("AgentTraceCollector"))
        assertFalse(all.contains("Metrics"))
        assertFalse(all.contains("Telemetry"))
        assertFalse(all.contains("OpenTelemetry"))
        assertFalse(all.contains("import android"))
        assertFalse(all.contains("java.util.logging"))
        // It still owns only session selection + single-send guard.
        assertTrue(all.contains("currentSessionId"))
        assertTrue(all.contains("newSession"))
    }

    // --------------------------------------------- T12 AT boundary

    @Test
    fun T12_no_direct_at_imports_outside_tool_module() {
        // AG Core, Agent Runtime and :app main must not import AT types.
        // AG.V2-2: AT V3 uses packages com.at.engine / com.adb.engine /
        // com.at.backend.adb (legacy v0 used com.androidterminal.*).
        // AG.V2-2B: :integration-at is the unified AT consumption point.
        // :app debug-only code (DebugBridge) may reference AT types for
        // session validation; this is excluded from the boundary check.
        // Paths are relative to the :app module dir (the unit test working dir),
        // so each sibling module needs a ".." prefix to reach the project root.
        val atImportPatterns = listOf(
            "import com.androidterminal",  // legacy v0
            "import com.at.engine",        // V3 AT Engine
            "import com.at.backend",       // V3 AT Backend
            "import com.adb.engine",       // V3 ADB Engine
        )
        val dirs = listOf(
            java.io.File("..", "ag-core/src/main/kotlin"),
            java.io.File("..", "ag-runtime/src/main/kotlin"),
            java.io.File("src", "main/java"),
        )
        // Debug-only code in :app may reference AT types (session validation).
        val debugDir = java.io.File("src", "main/java/com/androidagent/app/debug")
        for (dir in dirs) {
            val files = dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter {
                    // Exclude debug-only code (path separator varies by OS).
                    val p = it.path.replace('\\', '/')
                    !p.contains("/debug/")
                }
                .toList()
            assertTrue(files.isNotEmpty(), "${dir.path} must have sources")
            for (f in files) {
                val text = f.readText()
                for (pattern in atImportPatterns) {
                    assertFalse(
                        text.contains(pattern),
                        "direct AT import ($pattern) in ${f.path}",
                    )
                }
            }
        }
        // The integration-at module is the unified AT consumption point and
        // must actually use AT types.
        val integrationFiles = java.io.File("..", "integration-at/src/main/java")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(integrationFiles.isNotEmpty(), "integration-at must have sources")
        val integrationUsesAt = integrationFiles.any { f ->
            atImportPatterns.any { p -> f.readText().contains(p) }
        }
        assertTrue(integrationUsesAt, "integration-at must retain its AT imports")
        // The tool module consumes AT through the adapter (via api dependency).
        // It may still import AT types for its Executor seam.
        val toolFiles = java.io.File("..", "tool-android-shell/src/main/java")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val toolUsesAt = toolFiles.any { f ->
            atImportPatterns.any { p -> f.readText().contains(p) }
        }
        assertTrue(toolUsesAt, "tool-android-shell must retain its AT imports")
    }

    // ------------------------------------------------------------ T9 redaction

    @Test
    fun T9_redaction_never_emits_fake_sentinels() = runBlocking {
        val sentinel = "SECRET_SENTINEL"
        val inputs = listOf(
            "Authorization: Bearer $sentinel",
            "Authorization: $sentinel",
            "Bearer $sentinel",
            "{\"api_key\":\"$sentinel\"}",
            "{\"apiKey\":\"$sentinel\"}",
            "key=$sentinel",
            "password: $sentinel",
            "secret=$sentinel",
            "token=$sentinel",
        )
        for (input in inputs) {
            val redacted = TraceFormatter.redact(input)
            assertFalse(redacted.contains(sentinel), "sentinel leaked for: $input -> $redacted")
            assertFalse(redacted.contains("Bearer $sentinel"))
        }

        // A full trace carrying a sentinel in a tool output must redact it end to end.
        val tool = FakeTool(ToolResult(success = true, output = "Authorization: Bearer $sentinel"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedProvider(listOf(ModelTurn.ToolCalls(listOf(ToolCall("c1", "android_shell", """{"command":"env"}"""))), ModelTurn.FinalAnswer("ok")), registry.specs())
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val sink = newCollector()
        core.run("s1", "show env", RunOptions(instructions = AgentInstructions.CONTENT, eventSink = sink))

        val rendered = TraceFormatter.format(sink.trace!!)
        assertFalse(rendered.contains(sentinel), "rendered trace leaked the sentinel:\n$rendered")
        assertTrue(rendered.contains("<redacted>"))
        // The non-secret parts of the trace survive redaction.
        assertTrue(rendered.contains("android_shell"))
        assertTrue(rendered.contains("AGENT_INSTRUCTIONS_V1"))
    }

    // --------------------------------------------------------------- utilities

    private fun spanType(span: com.androidagent.app.trace.Span): String = when (span) {
        is com.androidagent.app.trace.RunStartSpan -> "run_start"
        is com.androidagent.app.trace.ModelTurnStartSpan -> "model_turn_start"
        is com.androidagent.app.trace.ModelTurnEndSpan -> "model_turn_end"
        is ToolCallSpan -> "tool_call"
        is ToolResultSpan -> "tool_result"
        is FinalAnswerSpan -> "final_answer"
        is RunFailureSpan -> "run_failure"
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
