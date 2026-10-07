package com.androidagent.tool.androidshell

import com.androidagent.core.ToolCall
import com.androidterminal.runtime.AtExecutionOutcome
import com.androidterminal.runtime.AtExecutionResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidShellToolImplTest {

    // ------------------------------------------------------------------ schema

    @Test
    fun U1_valid_command_parses_and_executes_once() = runBlocking {
        val fake = FakeExecutor(completed(stdout = "uid=2000(shell)\n"))
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = """{"command":"id"}"""))

        assertTrue(result.success)
        assertEquals(listOf("id"), fake.calls)
    }

    @Test
    fun U2_invalid_json_is_failure_with_zero_executor_calls() = runBlocking {
        val fake = FakeExecutor(completed())
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = "{not json"))

        assertFalse(result.success)
        assertEquals(0, fake.calls.size)
        assertTrue(result.error!!.contains("valid JSON"))
    }

    @Test
    fun U3_missing_command_is_failure_with_zero_executor_calls() = runBlocking {
        val fake = FakeExecutor(completed())
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = """{"other":1}"""))

        assertFalse(result.success)
        assertEquals(0, fake.calls.size)
        assertTrue(result.error!!.contains("missing 'command'"))
    }

    @Test
    fun U4_blank_command_is_failure_with_zero_executor_calls() = runBlocking {
        val fake = FakeExecutor(completed())
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = """{"command":"   "}"""))

        assertFalse(result.success)
        assertEquals(0, fake.calls.size)
        assertTrue(result.error!!.contains("blank"))
    }

    @Test
    fun U4b_non_object_arguments_is_failure_with_zero_executor_calls() = runBlocking {
        val fake = FakeExecutor(completed())
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = "42"))

        assertFalse(result.success)
        assertEquals(0, fake.calls.size)
    }

    @Test
    fun U4c_command_non_string_is_failure_with_zero_executor_calls() = runBlocking {
        val fake = FakeExecutor(completed())
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(ToolCall(id = "c1", name = "android_shell", arguments = """{"command":7}"""))

        assertFalse(result.success)
        assertEquals(0, fake.calls.size)
        assertTrue(result.error!!.contains("string"))
    }

    // ------------------------------------------------------------- result mapping

    @Test
    fun U5_COMPLETED_exit0_is_success() = runBlocking {
        val tool = AndroidShellToolImpl(FakeExecutor(completed(stdout = "ok\n")))

        val result = tool.execute(call("""{"command":"echo ok"}"""))

        assertTrue(result.success)
        assertNull(result.error)
    }

    @Test
    fun U6_COMPLETED_exit7_is_still_success() = runBlocking {
        val tool = AndroidShellToolImpl(
            FakeExecutor(completed(stdout = "OUT\n", stderr = "ERR\n", exitCode = 7)),
        )

        val result = tool.execute(call("""{"command":"exit 7"}"""))

        assertTrue(result.success)
        assertNull(result.error)
        assertTrue(result.output.contains("\"exitCode\":7"))
    }

    @Test
    fun U7_stdout_is_preserved_in_payload() = runBlocking {
        val tool = AndroidShellToolImpl(FakeExecutor(completed(stdout = "AG0_STDOUT\n")))

        val result = tool.execute(call("""{"command":"echo x"}"""))

        assertTrue(result.output.contains("AG0_STDOUT"))
    }

    @Test
    fun U8_stderr_is_preserved_separately_and_not_promoted_to_error() = runBlocking {
        val tool = AndroidShellToolImpl(FakeExecutor(completed(stderr = "AG0_STDERR\n")))

        val result = tool.execute(call("""{"command":"echo x >&2"}"""))

        assertTrue(result.output.contains("AG0_STDERR"))
        assertNull(result.error, "device stderr must not become ToolResult.error")
    }

    @Test
    fun U9_TRANSPORT_ERROR_is_failure() = runBlocking {
        val tool = AndroidShellToolImpl(FakeExecutor(transportError("read error")))

        val result = tool.execute(call("""{"command":"id"}"""))

        assertFalse(result.success)
        assertTrue(result.error!!.contains("read error"))
    }

    @Test
    fun U10_PROTOCOL_ERROR_is_failure() = runBlocking {
        val tool = AndroidShellToolImpl(
            FakeExecutor(AtExecutionResult(AtExecutionOutcome.PROTOCOL_ERROR, "", "", -1, true, "protocol")),
        )

        val result = tool.execute(call("""{"command":"id"}"""))

        assertFalse(result.success)
    }

    @Test
    fun U11_UNSUPPORTED_is_failure() = runBlocking {
        val tool = AndroidShellToolImpl(
            FakeExecutor(AtExecutionResult(AtExecutionOutcome.UNSUPPORTED, "", "", -1, true, "unsupported")),
        )

        val result = tool.execute(call("""{"command":"id"}"""))

        assertFalse(result.success)
    }

    // --------------------------------------------------------------- no replay

    @Test
    fun U12_transport_failure_executes_exactly_once() = runBlocking {
        val fake = FakeExecutor(transportError("read error"))
        val tool = AndroidShellToolImpl(fake)

        tool.execute(call("""{"command":"id"}"""))

        assertEquals(1, fake.calls.size, "one ToolCall -> at most one AtRuntime.execute")
    }

    @Test
    fun U13_no_automatic_replay_on_transport_failure() = runBlocking {
        val fake = FakeExecutor(transportError("read error"))
        val tool = AndroidShellToolImpl(fake)

        val result = tool.execute(call("""{"command":"id"}"""))

        assertEquals(1, fake.calls.size, "failed command must NOT be re-executed")
        assertFalse(result.success)
    }

    // ------------------------------------------------------------------ payload

    @Test
    fun U14_payload_contains_outcome_exitCode_stdout_stderr() = runBlocking {
        val tool = AndroidShellToolImpl(
            FakeExecutor(completed(stdout = "OUT", stderr = "ERR", exitCode = 3)),
        )

        val result = tool.execute(call("""{"command":"sh -c 'echo OUT; echo ERR >&2; exit 3'"}"""))

        val root = Json.parseToJsonElement(result.output).jsonObject
        assertEquals("COMPLETED", root["outcome"]!!.jsonPrimitive.content)
        assertEquals(3, root["exitCode"]!!.jsonPrimitive.content.toInt())
        assertEquals("OUT", root["stdout"]!!.jsonPrimitive.content)
        assertEquals("ERR", root["stderr"]!!.jsonPrimitive.content)
        assertFalse(root["hasError"]!!.jsonPrimitive.content.toBoolean())
    }

    // ------------------------------------------------------------------ helpers

    private fun call(arguments: String): ToolCall =
        ToolCall(id = "c1", name = AndroidShellToolImpl.TOOL_NAME, arguments = arguments)

    private class FakeExecutor(var next: AtExecutionResult) : AndroidShellToolImpl.Executor {
        val calls: MutableList<String> = mutableListOf()

        override suspend fun execute(command: String): AtExecutionResult {
            calls.add(command)
            return next
        }
    }

    private fun completed(stdout: String = "", stderr: String = "", exitCode: Int = 0): AtExecutionResult =
        AtExecutionResult(AtExecutionOutcome.COMPLETED, stdout, stderr, exitCode, hasError = false, error = null)

    private fun transportError(error: String): AtExecutionResult =
        AtExecutionResult(AtExecutionOutcome.TRANSPORT_ERROR, "", "", -1, hasError = true, error = error)
}