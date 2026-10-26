package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiCompatibleProvider

import com.androidagent.core.AgentCore
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The deterministic form of the vertical slice:
 *
 *   real provider -> AG Core loop -> real Tool -> AG Core loop -> real provider
 *
 * Every component is the production type. Only the network endpoint is a
 * loopback fixture, so this runs offline and deterministically. The live
 * equivalent lives in LiveApiTest and is gated behind @Tag("live").
 */
class VerticalSliceTest {

    private class ProbeTool : Tool {
        override val spec: ToolSpec = ToolSpec("probe", "Returns a fixed probe result.")
        var calls = 0
        var lastArguments: String? = null

        override suspend fun execute(call: ToolCall): ToolResult {
            calls++
            lastArguments = call.arguments
            return ToolResult(success = true, output = "probe-ok")
        }
    }

    @Test
    fun `provider to tool to provider produces a final answer through the real core`() = runBlocking {
        // First turn: the model asks for the tool. Second turn: it answers.
        var turn = 0
        val bodies = listOf(
            FixtureServer.toolCallsBody(FixtureServer.toolCall("call_1", "probe", """{"depth":2}""")),
            FixtureServer.finalAnswerBody("probe reported success"),
        )
        FixtureServer(rawBody = bodies[0]).use { server ->
            val tool = ProbeTool()
            val registry = ToolRegistry().apply { register(tool) }

            // Serve a different body per request while reusing one server.
            server.responder = { bodies[turn++] }

            val core = AgentCore(OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()), registry, MessageDurability.Noop)

            val outcome = core.run("s1", "please probe", RunOptions(maxSteps = 4))

            assertEquals(RunOutcome.Answer("probe reported success"), outcome)
            assertEquals(1, tool.calls, "the tool ran exactly once")
            assertEquals("""{"depth":2}""", tool.lastArguments, "arguments survived the round trip")

            assertEquals(2, server.requestBodies.size, "exactly two provider requests")

            // Request 1 carried the tool declaration.
            val first = Json.parseToJsonElement(server.requestBodies[0]).jsonObject
            val declared = first["tools"]!!.jsonArray.single().jsonObject
            assertEquals(
                "probe",
                declared["function"]!!.jsonObject["name"]!!.jsonPrimitive.content,
            )

            // Request 2 must replay the assistant tool-call turn AND the tool result.
            val second = Json.parseToJsonElement(server.requestBodies[1]).jsonObject
            val messages = second["messages"]!!.jsonArray
            val assistant = messages.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "assistant"
            }.jsonObject
            val replayedCall = assistant["tool_calls"]!!.jsonArray.single().jsonObject
            assertEquals("call_1", replayedCall["id"]!!.jsonPrimitive.content)
            assertEquals(
                """{"depth":2}""",
                replayedCall["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content,
            )

            val toolMessage = messages.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "tool"
            }.jsonObject
            assertEquals("call_1", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
            assertEquals("probe-ok", toolMessage["content"]!!.jsonPrimitive.content)

            // Order: user -> assistant(tool_calls) -> tool(result)
            val roles = messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }
            assertTrue(
                roles.indexOf("assistant") < roles.indexOf("tool"),
                "tool result must follow the assistant turn that requested it: $roles",
            )
        }
    }

    @Test
    fun `V4 mixed prose and tool call survives into the second provider request`() = runBlocking {
        val prose = "Let me look at the device first."
        val callA = FixtureServer.toolCall("call_a", "probe", """{"depth":2}""")
        val bodies = listOf(
            FixtureServer.mixedTurnBody(prose, callA),
            FixtureServer.finalAnswerBody("done"),
        )
        FixtureServer(rawBody = bodies[0]).use { server ->
            var turn = 0
            server.responder = { bodies[turn++] }

            val tool = ProbeTool()
            val registry = ToolRegistry().apply { register(tool) }
            val core = AgentCore(OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()), registry, MessageDurability.Noop)

            val outcome = core.run("s1", "probe it", RunOptions(maxSteps = 4))

            assertEquals(RunOutcome.Answer("done"), outcome)
            assertEquals(1, tool.calls)

            val second = Json.parseToJsonElement(server.requestBodies[1]).jsonObject
            val messages = second["messages"]!!.jsonArray

            // The assistant turn must be replayed with BOTH the original prose and
            // the original tool call.
            val assistant = messages.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "assistant"
            }.jsonObject
            assertEquals(prose, assistant["content"]!!.jsonPrimitive.content)
            val replayed = assistant["tool_calls"]!!.jsonArray.single().jsonObject
            assertEquals("call_a", replayed["id"]!!.jsonPrimitive.content)
            assertEquals("probe", replayed["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals(
                """{"depth":2}""",
                replayed["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content,
            )

            // And the tool result correlates by id.
            val toolMessage = messages.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "tool"
            }.jsonObject
            assertEquals("call_a", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
            assertEquals("probe-ok", toolMessage["content"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `a failing tool is reported to the model as tool result content, not dropped`() = runBlocking {
        val bodies = listOf(
            FixtureServer.toolCallsBody(FixtureServer.toolCall("call_9", "probe", "{}")),
            FixtureServer.finalAnswerBody("understood"),
        )
        FixtureServer(rawBody = bodies[0]).use { server ->
            var turn = 0
            server.responder = { bodies[turn++] }

            val failing = object : Tool {
                override val spec: ToolSpec = ToolSpec("probe", "always fails")
                override suspend fun execute(call: ToolCall): ToolResult =
                    ToolResult(success = false, output = "", error = "could not reach device")
            }
            val registry = ToolRegistry().apply { register(failing) }
            val core = AgentCore(OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()), registry, MessageDurability.Noop)

            val outcome = core.run("s1", "probe it")

            assertEquals(RunOutcome.Answer("understood"), outcome)
            val second = Json.parseToJsonElement(server.requestBodies[1]).jsonObject
            val toolMessage = second["messages"]!!.jsonArray.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "tool"
            }.jsonObject
            assertTrue(
                toolMessage["content"]!!.jsonPrimitive.content.contains("could not reach device"),
                "the model must see why the tool failed",
            )
        }
    }

    @Test
    fun `an unknown tool yields a structured tool result the model can react to`() = runBlocking {
        val bodies = listOf(
            FixtureServer.toolCallsBody(FixtureServer.toolCall("call_x", "no_such_tool", "{}")),
            FixtureServer.finalAnswerBody("I cannot use that tool"),
        )
        FixtureServer(rawBody = bodies[0]).use { server ->
            var turn = 0
            server.responder = { bodies[turn++] }

            val registry = ToolRegistry()
            val core = AgentCore(OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()), registry, MessageDurability.Noop)

            val outcome = core.run("s1", "use no_such_tool")

            assertEquals(RunOutcome.Answer("I cannot use that tool"), outcome)
            val second = Json.parseToJsonElement(server.requestBodies[1]).jsonObject
            val toolMessage = second["messages"]!!.jsonArray.first {
                it.jsonObject["role"]!!.jsonPrimitive.content == "tool"
            }.jsonObject
            assertEquals("call_x", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
            assertTrue(
                toolMessage["content"]!!.jsonPrimitive.content.contains("unknown tool"),
                "the model must learn the tool does not exist",
            )
        }
    }
}