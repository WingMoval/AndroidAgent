package com.androidagent.tool.androidshell

import com.androidagent.core.ToolCall
import com.at.engine.ATExecutionOutcome
import com.at.engine.ATExecutionResult
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
    fun U10_UNSUPPORTED_is_failure() = runBlocking {
        val tool = AndroidShellToolImpl(
            FakeExecutor(ATExecutionResult(ATExecutionOutcome.UNSUPPORTED, "", "", -1, true, "unsupported")),
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

        assertEquals(1, fake.calls.size, "one ToolCall -> at most one ATEngine.execute")
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

    // ------------------------------------------------- model-visible semantics

    @Test
    fun U15_spec_describes_local_shell_and_app_identity() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        assertTrue(desc.contains("local shell"), "must mention local shell")
        assertTrue(desc.contains("application execution identity"), "must mention app execution identity")
    }

    @Test
    fun U16_spec_describes_adb_cli_availability() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        assertTrue(desc.contains("adb"), "must mention adb")
        assertTrue(desc.contains("AndroidTerminal"), "must mention AndroidTerminal as adb provider")
    }

    @Test
    fun U17_spec_distinguishes_local_and_adb_shell_principals() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        assertTrue(desc.contains("adb shell"), "must mention adb shell")
        assertTrue(desc.contains("different execution identity"), "must state principal distinction")
    }

    @Test
    fun U18_spec_preserves_result_semantics() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        assertTrue(desc.contains("outcome"), "must document outcome field")
        assertTrue(desc.contains("exitCode"), "must document exitCode field")
        assertTrue(desc.contains("success even when exitCode is non-zero"), "must preserve COMPLETED semantics")
    }

    @Test
    fun U19_tool_name_unchanged() {
        assertEquals("android_shell", AndroidShellToolImpl(FakeExecutor(completed())).spec.name)
    }

    @Test
    fun U20_spec_does_not_claim_workspace_file_reading() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        // The WHEN clause must not list "files" as a primary use case
        val whenSection = desc.substringAfter("WHEN:").substringBefore("INPUT:")
        assertFalse(whenSection.contains("(files"), "WHEN must not claim files as primary use case")
        assertFalse(whenSection.contains("file reading"), "WHEN must not claim file reading")
        assertFalse(whenSection.contains("file listing"), "WHEN must not claim file listing")
    }

    @Test
    fun U21_spec_does_not_use_files_as_primary_when_example() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        val whenSection = desc.substringAfter("WHEN:").substringBefore("INPUT:")
        assertFalse(whenSection.contains("files, local state"), "WHEN must not use 'files, local state' as example")
    }

    @Test
    fun U22_spec_preserves_command_execution_when() {
        val desc = AndroidShellToolImpl(FakeExecutor(completed())).spec.description
        val whenSection = desc.substringAfter("WHEN:").substringBefore("INPUT:")
        assertTrue(whenSection.contains("command execution"), "WHEN must mention command execution")
        assertTrue(whenSection.contains("shell semantics"), "WHEN must mention shell semantics")
    }

    // ------------------------------------------------------------------ helpers

    private fun call(arguments: String): ToolCall =
        ToolCall(id = "c1", name = AndroidShellToolImpl.TOOL_NAME, arguments = arguments)

    private class FakeExecutor(var next: ATExecutionResult) : AndroidShellToolImpl.Executor {
        val calls: MutableList<String> = mutableListOf()

        override suspend fun execute(command: String): ATExecutionResult {
            calls.add(command)
            return next
        }
    }

    private fun completed(stdout: String = "", stderr: String = "", exitCode: Int = 0): ATExecutionResult =
        ATExecutionResult(ATExecutionOutcome.COMPLETED, stdout, stderr, exitCode, hasError = false, error = null)

    private fun transportError(error: String): ATExecutionResult =
        ATExecutionResult(ATExecutionOutcome.TRANSPORT_ERROR, "", "", -1, hasError = true, error = error)
}
