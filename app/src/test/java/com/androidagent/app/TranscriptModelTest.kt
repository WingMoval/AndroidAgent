package com.androidagent.app

import com.androidagent.core.RunFailure
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AGA.OBSERVABILITY-0 — T series: the pure Host transcript model.
 *
 * Covers the frozen semantics: Thinking and Thought are the SAME block,
 * Assistant content streams into one block per turn, the final answer never
 * duplicates a streamed block, tool actions/results are visible, and failures
 * carry their reason. No Android, no live endpoint.
 */
class TranscriptModelTest {

    // ------------------------------------------------------------ T1-T6 reasoning

    @Test
    fun T1_reasoning_started_creates_a_thinking_block() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        val block = blocks[0] as TranscriptModel.Block.Thinking
        assertEquals(TranscriptModel.Block.Thinking.Phase.THINKING, block.phase)
        assertEquals("", block.text)
    }

    @Test
    fun T2_reasoning_delta_grows_the_same_thinking_block() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        m.onReasoningDelta("I need ")
        m.onReasoningDelta("to check.")
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        val block = blocks[0] as TranscriptModel.Block.Thinking
        assertEquals("I need to check.", block.text)
    }

    @Test
    fun T3_reasoning_finished_flips_thinking_to_thought_same_block() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        m.onReasoningDelta("done reasoning")
        m.onReasoningFinished()
        val blocks = m.snapshot()
        assertEquals(1, blocks.size, "no second block may be inserted")
        val block = blocks[0] as TranscriptModel.Block.Thinking
        assertEquals(TranscriptModel.Block.Thinking.Phase.THOUGHT, block.phase)
        assertEquals("done reasoning", block.text)
    }

    @Test
    fun T4_reasoning_delta_without_started_creates_thinking_block() {
        val m = TranscriptModel()
        m.onReasoningDelta("direct delta")
        val block = m.snapshot()[0] as TranscriptModel.Block.Thinking
        assertEquals(TranscriptModel.Block.Thinking.Phase.THINKING, block.phase)
        assertEquals("direct delta", block.text)
    }

    @Test
    fun T5_no_reasoning_means_no_thinking_block() {
        val m = TranscriptModel()
        m.onContentStarted()
        m.onContentDelta("hello")
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is TranscriptModel.Block.Assistant)
    }

    @Test
    fun T6_reasoning_then_content_orders_thinking_before_assistant() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        m.onReasoningDelta("r")
        m.onReasoningFinished()
        m.onContentStarted()
        m.onContentDelta("a")
        val blocks = m.snapshot()
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is TranscriptModel.Block.Thinking)
        assertTrue(blocks[1] is TranscriptModel.Block.Assistant)
    }

    // ------------------------------------------------------------ T7-T10 content

    @Test
    fun T7_content_streams_into_one_assistant_block() {
        val m = TranscriptModel()
        m.onContentStarted()
        m.onContentDelta("The ")
        m.onContentDelta("answer")
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        val block = blocks[0] as TranscriptModel.Block.Assistant
        assertEquals("The answer", block.text)
        assertFalse(block.final)
    }

    @Test
    fun T8_content_delta_without_started_creates_assistant_block() {
        val m = TranscriptModel()
        m.onContentDelta("direct")
        val block = m.snapshot()[0] as TranscriptModel.Block.Assistant
        assertEquals("direct", block.text)
    }

    @Test
    fun T9_answer_marks_streamed_block_final_without_duplicate() {
        val m = TranscriptModel()
        m.onContentStarted()
        m.onContentDelta("streamed answer")
        m.onAnswerProduced("streamed answer")
        val blocks = m.snapshot()
        assertEquals(1, blocks.size, "the streamed block is marked final, nothing appended")
        val block = blocks[0] as TranscriptModel.Block.Assistant
        assertTrue(block.final)
        assertEquals("streamed answer", block.text)
    }

    @Test
    fun T10_answer_without_stream_creates_fallback_block() {
        val m = TranscriptModel()
        m.onAnswerProduced("no-stream answer")
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        val block = blocks[0] as TranscriptModel.Block.Assistant
        assertTrue(block.final)
        assertEquals("no-stream answer", block.text)
    }

    // ------------------------------------------------------------ T11-T16 tools

    @Test
    fun T11_tool_call_started_renders_shell_command() {
        val m = TranscriptModel()
        m.onToolCallStarted(ToolCall("c1", "android_shell", """{"command":"id"}"""))
        val block = m.snapshot()[0] as TranscriptModel.Block.Shell
        assertEquals("id", block.command)
    }

    @Test
    fun T12_non_json_arguments_rendered_verbatim() {
        val m = TranscriptModel()
        m.onToolCallStarted(ToolCall("c1", "android_shell", "echo hi"))
        val block = m.snapshot()[0] as TranscriptModel.Block.Shell
        assertEquals("echo hi", block.command)
    }

    @Test
    fun T13_json_without_command_renders_raw_arguments() {
        val m = TranscriptModel()
        m.onToolCallStarted(ToolCall("c1", "android_shell", """{"other":1}"""))
        val block = m.snapshot()[0] as TranscriptModel.Block.Shell
        assertEquals("""{"other":1}""", block.command)
    }

    @Test
    fun T14_tool_result_renders_exit_code_stdout_stderr() {
        val m = TranscriptModel()
        m.onToolCallFinished(
            ToolCall("c1", "android_shell", """{"command":"id"}"""),
            ToolResult(
                success = true,
                output = """{"outcome":"ok","exitCode":0,"stdout":"uid=2000","stderr":""}""",
            ),
        )
        val rendered = (m.snapshot()[0] as TranscriptModel.Block.Result).text
        assertTrue(rendered.contains("exitCode=0"))
        assertTrue(rendered.contains("stdout:"))
        assertTrue(rendered.contains("uid=2000"))
    }

    @Test
    fun T15_tool_failure_is_visible_with_error() {
        val m = TranscriptModel()
        m.onToolCallFinished(
            ToolCall("c1", "android_shell", """{"command":"false"}"""),
            ToolResult(
                success = false,
                output = """{"outcome":"error","exitCode":1,"stdout":"","stderr":"boom"}""",
                error = "exitCode 1",
            ),
        )
        val rendered = (m.snapshot()[0] as TranscriptModel.Block.Result).text
        assertTrue(rendered.contains("exitCode=1"))
        assertTrue(rendered.contains("stderr:"))
        assertTrue(rendered.contains("boom"))
        assertTrue(rendered.contains("error: exitCode 1"))
    }

    @Test
    fun T16_tool_result_non_shell_output_rendered_verbatim() {
        val m = TranscriptModel()
        m.onToolCallFinished(
            ToolCall("c1", "android_shell", "echo hi"),
            ToolResult(success = true, output = "plain output"),
        )
        val rendered = (m.snapshot()[0] as TranscriptModel.Block.Result).text
        assertEquals("plain output", rendered)
    }

    // ------------------------------------------------------------ T17-T21 failures

    @Test
    fun T17_provider_failure_renders_cause() {
        val m = TranscriptModel()
        m.onRunFailed(RunFailure.ProviderFailure("Timeout"))
        val text = (m.snapshot()[0] as TranscriptModel.Block.Failure).text
        assertEquals("Provider failure (Timeout)", text)
    }

    @Test
    fun T18_max_steps_failure_renders_step_count() {
        val m = TranscriptModel()
        m.onRunFailed(RunFailure.MaxStepsReached(8))
        val text = (m.snapshot()[0] as TranscriptModel.Block.Failure).text
        assertEquals("Stopped after 8 steps (maxSteps)", text)
    }

    @Test
    fun T19_session_busy_failure_renders_busy() {
        val m = TranscriptModel()
        m.onRunFailed(RunFailure.SessionBusy("s1"))
        val text = (m.snapshot()[0] as TranscriptModel.Block.Failure).text
        assertEquals("Session busy", text)
    }

    @Test
    fun T20_runtime_failure_renders_cause() {
        val m = TranscriptModel()
        m.onRunFailed(RunFailure.RuntimeFailure("boom"))
        val text = (m.snapshot()[0] as TranscriptModel.Block.Failure).text
        assertEquals("Runtime failure (boom)", text)
    }

    @Test
    fun T21_full_cycle_renders_in_order() {
        val m = TranscriptModel()
        m.onUser("list files")
        m.onReasoningStarted()
        m.onReasoningDelta("I will list files.")
        m.onReasoningFinished()
        m.onContentStarted()
        m.onContentDelta("Let me check.")
        m.onToolCallStarted(ToolCall("c1", "android_shell", """{"command":"ls"}"""))
        m.onToolCallFinished(
            ToolCall("c1", "android_shell", """{"command":"ls"}"""),
            ToolResult(success = true, output = """{"outcome":"ok","exitCode":0,"stdout":"a.txt","stderr":""}"""),
        )
        m.onContentDelta("Here is the file list.")
        m.onAnswerProduced("Here is the file list.")
        val rendered = m.render()
        assertTrue(rendered.indexOf("User") < rendered.indexOf("Thought"))
        assertTrue(rendered.indexOf("Thought") < rendered.indexOf("Assistant"))
        assertTrue(rendered.indexOf("$ ls") > rendered.indexOf("Assistant"))
        assertTrue(rendered.indexOf("exitCode=0") > rendered.indexOf("$ ls"))
        assertTrue(rendered.indexOf("a.txt") > rendered.indexOf("exitCode=0"))
        assertTrue(rendered.contains("I will list files."), "reasoning text survives the flip")
        assertFalse(rendered.contains("Thinking..."), "no lingering Thinking after flip")
    }
}