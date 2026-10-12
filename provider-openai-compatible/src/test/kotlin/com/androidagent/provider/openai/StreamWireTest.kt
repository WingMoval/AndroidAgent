package com.androidagent.provider.openai

import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.ModelTurn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AGA.OBSERVABILITY-0 — W series: SSE stream parsing and accumulation.
 *
 * Exercises [ChatCompletionWire.parseStreamLine] / [ChatCompletionWire.toModelTurn]
 * against the real wire shape (delta.reasoning / delta.content interleaved in
 * the same chunks, finish_reason, tool_calls accumulated per index). No
 * network, no live endpoint.
 */
class StreamWireTest {

    private class RecordingObserver : ModelStreamObserver {
        val reasoningStarted = mutableListOf<Unit>()
        val reasoningDeltas = mutableListOf<String>()
        val reasoningFinished = mutableListOf<Unit>()
        val contentStarted = mutableListOf<Unit>()
        val contentDeltas = mutableListOf<String>()
        val contentFinished = mutableListOf<Unit>()

        override fun onReasoningStarted() {
            reasoningStarted += Unit
        }

        override fun onReasoningDelta(text: String) {
            reasoningDeltas += text
        }

        override fun onReasoningFinished() {
            reasoningFinished += Unit
        }

        override fun onContentStarted() {
            contentStarted += Unit
        }

        override fun onContentDelta(text: String) {
            contentDeltas += text
        }

        override fun onContentFinished() {
            contentFinished += Unit
        }
    }

    private fun parse(lines: List<String>, acc: ChatCompletionWire.StreamAccumulator, observer: ModelStreamObserver) {
        for (line in lines) {
            ChatCompletionWire.parseStreamLine(line, acc, observer)
        }
    }

    // ------------------------------------------------------------ W1-W4 reasoning

    @Test
    fun W1_reasoning_streams_incrementally_and_interleaves_with_content() {
        val acc = ChatCompletionWire.StreamAccumulator()
        val observer = RecordingObserver()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"reasoning":"I need "},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"reasoning":"to check.","content":"The "},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"content":"answer"},"finish_reason":"stop"}]}""",
                "data: [DONE]",
            ),
            acc,
            observer,
        )
        assertEquals("I need to check.", acc.reasoning.toString())
        assertEquals("The answer", acc.content.toString())
        assertEquals("stop", acc.finishReason)
        assertTrue(acc.done)
        assertEquals(1, observer.reasoningStarted.size)
        assertEquals(listOf("I need ", "to check."), observer.reasoningDeltas)
        assertEquals(1, observer.contentStarted.size)
        assertEquals(listOf("The ", "answer"), observer.contentDeltas)
    }

    @Test
    fun W2_reasoning_only_stream_assembles_final_answer_without_reasoning() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"reasoning":"deep thought"},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"content":"final"},"finish_reason":"stop"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        val turn = ChatCompletionWire.toModelTurn(acc)
        assertTrue(turn is ModelTurn.FinalAnswer)
        assertEquals("final", (turn as ModelTurn.FinalAnswer).content)
    }

    @Test
    fun W3_content_only_stream_has_no_reasoning_events() {
        val acc = ChatCompletionWire.StreamAccumulator()
        val observer = RecordingObserver()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"content":"a"},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"content":"b"},"finish_reason":"stop"}]}""",
            ),
            acc,
            observer,
        )
        assertTrue(observer.reasoningStarted.isEmpty())
        assertTrue(observer.reasoningDeltas.isEmpty())
        assertEquals("ab", acc.content.toString())
    }

    @Test
    fun W4_non_data_lines_are_ignored() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                "",
                ": keep-alive comment",
                "event: message",
                """data: {"choices":[{"delta":{"content":"x"},"finish_reason":null}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        assertEquals("x", acc.content.toString())
        assertFalse(acc.done)
    }

    // ------------------------------------------------------------ W5-W8 tools

    @Test
    fun W5_tool_call_arguments_accumulate_across_chunks() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"android_shell","arguments":"{\"command\":"}}]},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"id\"}"}}]},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        val turn = ChatCompletionWire.toModelTurn(acc)
        assertTrue(turn is ModelTurn.ToolCalls)
        val calls = (turn as ModelTurn.ToolCalls).calls
        assertEquals(1, calls.size)
        assertEquals("c1", calls[0].id)
        assertEquals("android_shell", calls[0].name)
        assertEquals("""{"command":"id"}""", calls[0].arguments)
    }

    @Test
    fun W6_tool_call_with_prose_preserves_both() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"content":"Let me check.","tool_calls":[{"index":0,"id":"c1","function":{"name":"android_shell","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        val turn = ChatCompletionWire.toModelTurn(acc)
        assertTrue(turn is ModelTurn.ToolCalls)
        assertEquals("Let me check.", (turn as ModelTurn.ToolCalls).content)
        assertEquals(1, turn.calls.size)
    }

    @Test
    fun W7_missing_tool_call_id_is_an_invalid_tool_call() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"android_shell","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        assertThrows(OpenAiProviderException.InvalidToolCall::class.java) {
            ChatCompletionWire.toModelTurn(acc)
        }
    }

    @Test
    fun W8_two_tool_calls_accumulate_by_index() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"android_shell","arguments":"{\"command\":\"a\"}"}},{"index":1,"id":"c2","function":{"name":"android_shell","arguments":"{\"command\":\"b\"}"}}]},"finish_reason":"tool_calls"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        val turn = ChatCompletionWire.toModelTurn(acc)
        assertTrue(turn is ModelTurn.ToolCalls)
        assertEquals(listOf("c1", "c2"), (turn as ModelTurn.ToolCalls).calls.map { it.id })
        assertEquals("""{"command":"a"}""", turn.calls[0].arguments)
        assertEquals("""{"command":"b"}""", turn.calls[1].arguments)
    }

    // ------------------------------------------------------------ W9-W12 protocol

    @Test
    fun W9_malformed_data_line_is_a_parse_error() {
        val acc = ChatCompletionWire.StreamAccumulator()
        assertThrows(OpenAiProviderException.Parse::class.java) {
            ChatCompletionWire.parseStreamLine("data: not json", acc, RecordingObserver())
        }
    }

    @Test
    fun W10_empty_stream_is_a_protocol_error() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(listOf("data: [DONE]"), acc, RecordingObserver())
        assertThrows(OpenAiProviderException.Protocol::class.java) {
            ChatCompletionWire.toModelTurn(acc)
        }
    }

    @Test
    fun W11_chunk_without_choices_is_a_protocol_error() {
        val acc = ChatCompletionWire.StreamAccumulator()
        assertThrows(OpenAiProviderException.Protocol::class.java) {
            ChatCompletionWire.parseStreamLine("""data: {"other":1}""", acc, RecordingObserver())
        }
    }

    @Test
    fun W12_empty_choices_chunk_is_ignored() {
        val acc = ChatCompletionWire.StreamAccumulator()
        parse(
            listOf(
                """data: {"choices":[]}""",
                """data: {"choices":[{"delta":{"content":"x"},"finish_reason":"stop"}]}""",
            ),
            acc,
            RecordingObserver(),
        )
        assertEquals("x", acc.content.toString())
    }
}