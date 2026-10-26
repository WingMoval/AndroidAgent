package com.androidagent.app

import com.androidagent.core.ModelMessage
import com.androidagent.core.RunFailure
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
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

    // ------------------------------------------------- AGA.OBSERVABILITY-1 (T20+)
    // Real turn order: reasoning streams, THEN content (and possibly a tool call)
    // is appended, and only then does the provider signal reasoning-finished.
    // onReasoningFinished must flip THIS turn's Thinking block even though it is
    // no longer the last block.

    @Test
    fun T20_reasoning_finished_flips_when_content_already_appended() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        m.onReasoningDelta("thinking about it")
        m.onContentStarted()
        m.onContentDelta("the answer")
        // provider.complete() signals reasoning-finished AFTER the content streamed
        m.onReasoningFinished()
        val blocks = m.snapshot()
        assertEquals(2, blocks.size, "no second block may be inserted")
        val thinking = blocks[0] as TranscriptModel.Block.Thinking
        assertEquals(TranscriptModel.Block.Thinking.Phase.THOUGHT, thinking.phase,
            "the Thinking block must flip to Thought even though it is not last")
        assertEquals("thinking about it", thinking.text)
        val assistant = blocks[1] as TranscriptModel.Block.Assistant
        assertEquals("the answer", assistant.text)
    }

    @Test
    fun T21_reasoning_finished_flips_when_content_and_toolcall_already_appended() {
        val m = TranscriptModel()
        m.onReasoningStarted()
        m.onReasoningDelta("plan")
        m.onContentStarted()
        m.onContentDelta("I will run a command")
        m.onToolCallStarted(ToolCall("c1", "android_shell", """{"command":"id"}"""))
        m.onToolCallFinished(
            ToolCall("c1", "android_shell", """{"command":"id"}"""),
            ToolResult(success = true, output = """{"outcome":"COMPLETED","exitCode":0,"stdout":"uid=0","stderr":"","hasError":false}"""),
        )
        m.onReasoningFinished()
        val blocks = m.snapshot()
        // Thinking, Assistant, Shell, Result
        assertEquals(4, blocks.size)
        val thinking = blocks[0] as TranscriptModel.Block.Thinking
        assertEquals(TranscriptModel.Block.Thinking.Phase.THOUGHT, thinking.phase,
            "the Thinking block must flip even with Assistant+Shell+Result after it")
        assertTrue(blocks[1] is TranscriptModel.Block.Assistant)
        assertTrue(blocks[2] is TranscriptModel.Block.Shell)
        assertTrue(blocks[3] is TranscriptModel.Block.Result)
    }

    @Test
    fun T22_reasoning_finished_with_no_reasoning_is_a_noop() {
        val m = TranscriptModel()
        m.onContentStarted()
        m.onContentDelta("hello")
        m.onReasoningFinished()
        val blocks = m.snapshot()
        assertEquals(1, blocks.size, "no Thinking block must be created")
        assertTrue(blocks[0] is TranscriptModel.Block.Assistant)
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

    // ------------------------------------------------------------ T23 interrupt

    @Test
    fun T23_interrupted_block_renders_stopped_not_failed() {
        val m = TranscriptModel()
        m.onUser("do a long thing")
        m.onInterrupted()
        val rendered = m.render()
        assertTrue(rendered.contains("Stopped"), "interrupt renders a Stopped block: $rendered")
        assertTrue(rendered.contains("Run stopped by user."))
        assertFalse(rendered.contains("Failed"), "an interrupt is NOT a failure")
        val block = m.snapshot().last()
        assertTrue(block is TranscriptModel.Block.Interrupted, "last block is Interrupted, got $block")
    }

    // ================================================================
    // PHASE I — Transcript re-projection from restored Core history
    // ================================================================

    @Test
    fun TRANSCRIPT_I1_user_message_projects_one_user_block() {
        val m = TranscriptModel()
        m.reproject(listOf(UserMessage("hello 世界 🌍\nline two")))
        val blocks = m.snapshot()
        assertEquals(1, blocks.size)
        val b = blocks[0] as TranscriptModel.Block.User
        // Content preserved verbatim (no trim / rewrite / summarize).
        assertEquals("hello 世界 🌍\nline two", b.text)
    }

    @Test
    fun TRANSCRIPT_I2_final_model_message_projects_final_assistant_block() {
        val m = TranscriptModel()
        m.reproject(listOf(ModelMessage("The answer.", emptyList())))
        val b = m.snapshot()[0] as TranscriptModel.Block.Assistant
        assertEquals("The answer.", b.text)
        assertTrue(b.final, "a model message without tool calls is the final answer")
    }

    @Test
    fun TRANSCRIPT_I3_tool_bearing_model_projects_assistant_then_shell_in_live_order() {
        val m = TranscriptModel()
        m.reproject(
            listOf(
                ModelMessage(
                    "I will check.",
                    listOf(
                        ToolCall("c1", "android_shell", """{"command":"ls"}"""),
                        ToolCall("c2", "android_shell", """{"command":"pwd"}"""),
                    ),
                ),
            ),
        )
        val blocks = m.snapshot()
        assertEquals(3, blocks.size)
        val a = blocks[0] as TranscriptModel.Block.Assistant
        assertEquals("I will check.", a.text)
        assertFalse(a.final, "a tool-bearing model turn is NOT the final answer")
        val s1 = blocks[1] as TranscriptModel.Block.Shell
        assertEquals("ls", s1.command)
        val s2 = blocks[2] as TranscriptModel.Block.Shell
        assertEquals("pwd", s2.command)
    }

    @Test
    fun TRANSCRIPT_I4_tool_result_success_projects_result_block() {
        val m = TranscriptModel()
        m.reproject(
            listOf(
                ToolResultMessage(
                    "c1",
                    "android_shell",
                    ToolResult(
                        success = true,
                        output = """{"outcome":"ok","exitCode":0,"stdout":"uid=2000","stderr":""}""",
                    ),
                ),
            ),
        )
        val rendered = (m.snapshot()[0] as TranscriptModel.Block.Result).text
        assertTrue(rendered.contains("exitCode=0"))
        assertTrue(rendered.contains("uid=2000"))
    }

    @Test
    fun TRANSCRIPT_I5_tool_result_failure_projects_result_block_with_error() {
        val m = TranscriptModel()
        m.reproject(
            listOf(
                ToolResultMessage(
                    "c1",
                    "android_shell",
                    ToolResult(
                        success = false,
                        output = """{"outcome":"error","exitCode":1,"stdout":"","stderr":"boom"}""",
                        error = "exitCode 1",
                    ),
                ),
            ),
        )
        val rendered = (m.snapshot()[0] as TranscriptModel.Block.Result).text
        assertTrue(rendered.contains("exitCode=1"))
        assertTrue(rendered.contains("error: exitCode 1"))
    }

    @Test
    fun TRANSCRIPT_I6_synthetic_unknown_result_not_shown_as_confirmed_failure() {
        val m = TranscriptModel()
        m.reproject(
            listOf(
                ModelMessage("checking", listOf(ToolCall("c1", "android_shell", """{"command":"long"}"""))),
                // The canonical Phase-G synthetic interrupted result (in-flight):
                ToolResultMessage(
                    "c1",
                    "android_shell",
                    ToolResult(
                        success = false,
                        output = "",
                        error = "Run interrupted before a ToolResult was observed; external execution state may be unknown.",
                    ),
                ),
            ),
        )
        val rendered = m.render()
        // The unknown-state wording is preserved verbatim.
        assertTrue(rendered.contains("external execution state may be unknown"))
        // It must NOT be re-interpreted as a confirmed tool failure.
        assertFalse(rendered.contains("Failed"), "a synthetic UNKNOWN is not a Run failure block")
        assertFalse(rendered.contains("error: tool mechanism failure"))
        val blocks = m.snapshot()
        val result = blocks.last()
        assertTrue(result is TranscriptModel.Block.Result, "the unknown outcome is a Result, got $result")
    }

    @Test
    fun TRANSCRIPT_I7_multi_tool_preserves_call_order() {
        val m = TranscriptModel()
        m.reproject(
            listOf(
                ModelMessage(
                    "",
                    listOf(
                        ToolCall("A", "android_shell", """{"command":"alpha"}"""),
                        ToolCall("B", "android_shell", """{"command":"beta"}"""),
                        ToolCall("C", "android_shell", """{"command":"gamma"}"""),
                    ),
                ),
            ),
        )
        val commands = m.snapshot().filterIsInstance<TranscriptModel.Block.Shell>().map { it.command }
        assertEquals(listOf("alpha", "beta", "gamma"), commands, "tool call order preserved A,B,C")
    }

    @Test
    fun TRANSCRIPT_I8_empty_history_projects_empty_transcript() {
        val m = TranscriptModel()
        m.onUser("stale from previous session")
        m.reproject(emptyList())
        assertEquals(0, m.snapshot().size, "empty history clears any prior projection")
        assertEquals("", m.render().trim())
    }

    @Test
    fun TRANSCRIPT_I9_reproject_same_history_is_idempotent() {
        val m = TranscriptModel()
        val history = listOf(
            UserMessage("old"),
            ModelMessage("answer-old", emptyList()),
        )
        m.reproject(history)
        val first = m.snapshot()
        m.reproject(history)
        val second = m.snapshot()
        assertEquals(first, second, "re-projecting the same history yields an identical projection")
        assertEquals(2, second.size, "no accumulation / duplicates")
    }

    @Test
    fun TRANSCRIPT_I10_project_B_replaces_project_A() {
        val m = TranscriptModel()
        m.reproject(listOf(UserMessage("A-old"), ModelMessage("A-answer", emptyList())))
        m.reproject(listOf(UserMessage("B-only")))
        val blocks = m.snapshot()
        assertEquals(1, blocks.size, "only B is visible after projecting B")
        val b = blocks[0] as TranscriptModel.Block.User
        assertEquals("B-only", b.text)
    }

    @Test
    fun TRANSCRIPT_I11_large_history_projects_completely_without_truncation_or_duplicate() {
        val m = TranscriptModel()
        // 1000 alternating user/model messages.
        val history = buildList {
            for (i in 0 until 1000) {
                add(UserMessage("user-$i 中文🌍\nline"))
                add(ModelMessage("answer-$i", emptyList()))
            }
        }
        m.reproject(history)
        val blocks = m.snapshot()
        assertEquals(2000, blocks.size, "every message projected, no truncation / duplicate")
        // Deterministic: content of each is preserved.
        assertEquals("user-999 中文🌍\nline", (blocks[1998] as TranscriptModel.Block.User).text)
        assertEquals("answer-999", (blocks[1999] as TranscriptModel.Block.Assistant).text)
        // Unicode / multiline preserved verbatim.
        assertEquals("user-0 中文🌍\nline", (blocks[0] as TranscriptModel.Block.User).text)
    }
}