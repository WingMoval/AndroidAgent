package com.androidagent.runtime.persistence

import com.androidagent.core.ModelMessage
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phase D serializer tests: Message <-> durable payload round-trip.
 *
 * Semantic equality: the domain types are data classes, so
 * assertEquals(original, decoded) is the full field-by-field check.
 */
class MessageSerializerTest {

    private fun roundTrip(message: com.androidagent.core.Message) =
        MessageSerializer.deserialize(MessageSerializer.serialize(message))

    // SER-T1: UserMessage round-trip.
    @Test
    fun `SER-T1 UserMessage round-trip`() {
        val original = UserMessage("hello world")
        val serialized = MessageSerializer.serialize(original)
        assertEquals(MessageType.USER, serialized.type)
        assertEquals(original, roundTrip(original))
    }

    // SER-T2: plain ModelMessage (content only).
    @Test
    fun `SER-T2 plain ModelMessage round-trip`() {
        val original = ModelMessage("final answer content")
        assertEquals(original, roundTrip(original))
        // No tool calls -> empty list, not null.
        assertEquals(emptyList<ToolCall>(), (roundTrip(original) as ModelMessage).toolCalls)
    }

    // SER-T3: single ToolCall — id/name/arguments exact (including empty args).
    @Test
    fun `SER-T3 single ToolCall round-trip exact`() {
        val original = ModelMessage(
            content = "let me check",
            toolCalls = listOf(ToolCall("call_123", "android_shell", "")),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        val call = (decoded as ModelMessage).toolCalls.single()
        assertEquals("call_123", call.id)
        assertEquals("android_shell", call.name)
        assertEquals("", call.arguments)
    }

    // SER-T4: multiple ToolCalls — ordering preserved.
    @Test
    fun `SER-T4 multiple ToolCalls ordering preserved`() {
        val original = ModelMessage(
            content = "",
            toolCalls = listOf(
                ToolCall("a", "t1", "{\"x\":1}"),
                ToolCall("b", "t2", ""),
                ToolCall("c", "t3", "{\"y\":2}"),
            ),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        assertContentEquals(
            listOf("a", "b", "c"),
            (decoded as ModelMessage).toolCalls.map { it.id },
        )
    }

    // SER-T5: ToolResult success — output exact.
    @Test
    fun `SER-T5 ToolResult success round-trip`() {
        val original = ToolResultMessage(
            toolCallId = "c1",
            toolName = "network",
            result = ToolResult(success = true, output = "HTTP 200 OK", error = null),
        )
        assertEquals(original, roundTrip(original))
    }

    // SER-T6: ToolResult failure — error exact.
    @Test
    fun `SER-T6 ToolResult failure round-trip`() {
        val original = ToolResultMessage(
            toolCallId = "c2",
            toolName = "network",
            result = ToolResult(
                success = false,
                output = "",
                error = "DESTINATION_BLOCKED: private network address",
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    // SER-T7: synthetic interrupted UNKNOWN result — round-trip without loss.
    // The serializer does NOT interpret the INTERRUPTED_UNKNOWN semantic; it
    // just preserves it byte-for-byte.
    @Test
    fun `SER-T7 synthetic interrupted unknown result round-trips`() {
        val original = ToolResultMessage(
            toolCallId = "c3",
            toolName = "android_shell",
            result = ToolResult(
                success = false,
                output = "",
                error = "SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT: the previous execution may or may not have taken effect; check current state before retrying",
            ),
        )
        val decoded = roundTrip(original)
        assertEquals(original, decoded)
        assertEquals(
            "SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT: the previous execution may or may not have taken effect; check current state before retrying",
            (decoded as ToolResultMessage).result.error,
        )
    }

    // SER-T8: Unicode (中文 + emoji).
    @Test
    fun `SER-T8 Unicode round-trip`() {
        val original = UserMessage("中文内容 🚀 émojis ümlauts")
        assertEquals(original, roundTrip(original))
        val model = ModelMessage("模型回复 🤖", listOf(ToolCall("u", "tool", "{\"q\":\"中文\"}")))
        assertEquals(model, roundTrip(model))
    }

    // SER-T9: multiline / tabs / shell output.
    @Test
    fun `SER-T9 multiline and shell output round-trip`() {
        val shellOutput = "line1\nline2\twith-tab\r\nwindows-line\n$ ls\nfile1\nfile2"
        val original = ToolResultMessage(
            "c9", "android_shell", ToolResult(true, shellOutput, null),
        )
        assertEquals(original, roundTrip(original))

        val userMulti = UserMessage("multi\nline\nuser input with\ttab")
        assertEquals(userMulti, roundTrip(userMulti))
    }

    // SER-T10: escaping — quotes, backslashes, JSON-looking content.
    @Test
    fun `SER-T10 escaping round-trip`() {
        val tricky = "\"quoted\" \\backslash\\ {\"looks\": \"like json\"} end"
        val original = UserMessage(tricky)
        val decoded = roundTrip(original)
        assertEquals(original, decoded)

        val jsonArg = ToolCall("j", "tool", "{\"nested\": {\"a\": [1,2,3]}, \"s\": \"x\\n\"}")
        val model = ModelMessage("", listOf(jsonArg))
        assertEquals(model, roundTrip(model))
    }

    // SER-T11: empty strings / null error — full recovery.
    @Test
    fun `SER-T11 empty strings and null error round-trip`() {
        assertEquals(UserMessage(""), roundTrip(UserMessage("")))
        assertEquals(ModelMessage("", emptyList()), roundTrip(ModelMessage("", emptyList())))

        val noError = ToolResultMessage("c", "t", ToolResult(true, "", null))
        val decoded = roundTrip(noError) as ToolResultMessage
        assertEquals(noError, decoded)
        assertEquals(null, decoded.result.error)

        val emptyError = ToolResultMessage("c", "t", ToolResult(false, "", ""))
        assertEquals(emptyError, roundTrip(emptyError))
    }

    // SER-T12: malformed payload -> explicit decode failure (no raw payload
    // in the exception message).
    @Test
    fun `SER-T12 malformed payload is explicit decode failure`() {
        val e = assertFailsWith<MessageDecodeException> {
            MessageSerializer.deserialize("USER", "{not valid json")
        }
        assertTrue(e.message!!.contains("USER"))
        assertTrue(!e.message!!.contains("not valid json"))

        // Wrong type for a required field.
        val e2 = assertFailsWith<MessageDecodeException> {
            MessageSerializer.deserialize("USER", """{"content": 42}""")
        }
        assertTrue(e2.message!!.contains("USER"))

        // Missing required field (content is required for MODEL).
        assertFailsWith<MessageDecodeException> {
            MessageSerializer.deserialize("MODEL", """{"toolCalls": []}""")
        }
    }

    // SER-T13: unknown type -> explicit decode failure.
    @Test
    fun `SER-T13 unknown type is explicit decode failure`() {
        val e = assertFailsWith<MessageDecodeException> {
            MessageSerializer.deserialize("RUN", """{"x":1}""")
        }
        assertTrue(e.message!!.contains("RUN"))
    }

    // A large canonical bounded output is NOT truncated by the serializer.
    @Test
    fun `large tool output is not truncated`() {
        val big = "x".repeat(100_000)
        val original = ToolResultMessage("big", "tool", ToolResult(true, big, null))
        val decoded = roundTrip(original) as ToolResultMessage
        assertEquals(big.length, decoded.result.output.length)
        assertEquals(original, decoded)
    }

    // Unknown JSON fields are ignored (forward compatibility).
    @Test
    fun `unknown json fields are ignored`() {
        val withExtra = """{"content":"hi","futureField":123}"""
        val decoded = MessageSerializer.deserialize("USER", withExtra)
        assertEquals(UserMessage("hi"), decoded)
    }
}
