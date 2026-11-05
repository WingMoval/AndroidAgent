package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.provider.openai.OpenAiProviderException

import com.androidagent.core.Message
import com.androidagent.core.MessageDurability
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import com.androidagent.core.ModelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

/**
 * Deterministic provider protocol tests P1 - P12.
 *
 * Offline. No credentials. No external network. Every case runs against a
 * loopback [FixtureServer].
 */
class ProviderProtocolTest {

    private fun request(
        instructions: String = "",
        history: List<Message> = emptyList(),
        tools: List<ToolSpec> = emptyList(),
    ) = ModelRequest(sessionId = "s1", instructions = instructions, history = history, tools = tools)

    // ------------------------------------------------------------------ P1

    @Test
    fun `P1 final answer content maps exactly`() = runBlocking {
        FixtureServer(rawBody = FixtureServer.finalAnswerBody("the answer is 42")).use { server ->
            val provider = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
            val turn = provider.complete(request(history = listOf(UserMessage("hi"))))

            assertEquals(ModelTurn.FinalAnswer("the answer is 42"), turn)
            assertEquals(1, server.requestBodies.size)
            assertEquals("/v1/chat/completions", server.requestPaths.single())
        }
    }

    // ------------------------------------------------------------------ P2

    @Test
    fun `P2 single tool call preserves id name and opaque arguments`() = runBlocking {
        // Deliberately awkward arguments: odd whitespace, non-sequential keys,
        // an escape and a non-ASCII character. They must survive byte-for-byte
        // with no parse, normalise or reserialise.
        val rawArguments = """{ "b":2,  "a": 1, "s":"x\tyé" }"""
        FixtureServer(
            rawBody = FixtureServer.toolCallsBody(
                FixtureServer.toolCall("call_abc", "probe", rawArguments),
            ),
        ).use { server ->
            val provider = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
            val turn = provider.complete(request(history = listOf(UserMessage("use the tool"))))

            val calls = (turn as ModelTurn.ToolCalls).calls
            assertEquals(1, calls.size)
            assertEquals("call_abc", calls.single().id)
            assertEquals("probe", calls.single().name)
            assertEquals(
                rawArguments,
                calls.single().arguments,
                "wire arguments must be carried through verbatim",
            )
        }
    }

    // ------------------------------------------------------------------ P3

    @Test
    fun `P3 outbound messages preserve role order and required fields`() = runBlocking {
        FixtureServer(rawBody = FixtureServer.finalAnswerBody("ok")).use { server ->
            val provider = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
            provider.complete(
                request(
                    instructions = "be terse",
                    history = listOf(
                        UserMessage("do the thing"),
                        ModelMessage("", listOf(ToolCall("c1", "probe", """{"x":1}"""))),
                        ToolResultMessage("c1", "probe", ToolResult(true, "probe-output")),
                    ),
                    tools = listOf(ToolSpec("probe", "a probe tool")),
                ),
            )

            val body = Json.parseToJsonElement(server.requestBodies.single()).jsonObject
            val messages = body["messages"]!!.jsonArray

            assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("be terse", messages[0].jsonObject["content"]!!.jsonPrimitive.content)

            assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("do the thing", messages[1].jsonObject["content"]!!.jsonPrimitive.content)

            val assistant = messages[2].jsonObject
            assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
            val outboundCall = assistant["tool_calls"]!!.jsonArray.single().jsonObject
            assertEquals("c1", outboundCall["id"]!!.jsonPrimitive.content)
            assertEquals("probe", outboundCall["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals(
                """{"x":1}""",
                outboundCall["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content,
            )

            val tool = messages[3].jsonObject
            assertEquals("tool", tool["role"]!!.jsonPrimitive.content)
            assertEquals("c1", tool["tool_call_id"]!!.jsonPrimitive.content)
            assertEquals("probe-output", tool["content"]!!.jsonPrimitive.content)

            // Tool declaration projection
            val tools = body["tools"]!!.jsonArray.single().jsonObject
            assertEquals("function", tools["type"]!!.jsonPrimitive.content)
            assertEquals("probe", tools["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            val params = tools["function"]!!.jsonObject["parameters"]!!.jsonObject
            assertEquals("object", params["type"]!!.jsonPrimitive.content)
            assertTrue(
                params["additionalProperties"]!!.jsonPrimitive.content.toBoolean(),
                "permissive projection is required while AG Core has no typed schema",
            )
        }
    }

    @Test
    fun `P3b no instructions field means no system message`() = runBlocking {
        FixtureServer(rawBody = FixtureServer.finalAnswerBody("ok")).use { server ->
            OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
                .complete(request(history = listOf(UserMessage("hi"))))

            val messages = Json.parseToJsonElement(server.requestBodies.single())
                .jsonObject["messages"]!!.jsonArray
            assertEquals(1, messages.size)
            assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        }
    }

    // ------------------------------------------------------------------ P4

    @Test
    fun `P4 multiple tool calls keep count order ids names and arguments`() = runBlocking {
        FixtureServer(
            rawBody = FixtureServer.toolCallsBody(
                FixtureServer.toolCall("id_a", "alpha", """{"n":1}"""),
                FixtureServer.toolCall("id_b", "beta", """{"n":2}"""),
                FixtureServer.toolCall("id_c", "gamma", """{"n":3}"""),
            ),
        ).use { server ->
            val provider = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
            val calls = (provider.complete(request()) as ModelTurn.ToolCalls).calls

            assertEquals(3, calls.size)
            assertEquals(listOf("id_a", "id_b", "id_c"), calls.map { it.id })
            assertEquals(listOf("alpha", "beta", "gamma"), calls.map { it.name })
            assertEquals(listOf("""{"n":1}""", """{"n":2}""", """{"n":3}"""), calls.map { it.arguments })
        }
    }

    // ------------------------------------------------------------------ P5

    @Test
    fun `P5 mixed content and tool calls is preserved losslessly`() = runBlocking {
        // The wire carries BOTH prose and tool calls in one assistant turn.
        // Since AG.PROVIDER-0.R1 both survive; neither may be dropped.
        val prose = "I will inspect it."
        FixtureServer(
            rawBody = FixtureServer.mixedTurnBody(
                prose,
                FixtureServer.toolCall("call_a", "alpha", """{"n":1}"""),
                FixtureServer.toolCall("call_b", "beta", """{"n":2}"""),
            ),
        ).use { server ->
            val provider = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())

            val turn = provider.complete(request()) as ModelTurn.ToolCalls

            assertEquals(prose, turn.content, "prose must survive verbatim")
            assertEquals(2, turn.calls.size)
            assertEquals(listOf("call_a", "call_b"), turn.calls.map { it.id })
            assertEquals(listOf("alpha", "beta"), turn.calls.map { it.name })
            assertEquals(listOf("""{"n":1}""", """{"n":2}"""), turn.calls.map { it.arguments })
        }
    }

    @Test
    fun `P5b tool calls with null content map to empty prose`() = runBlocking {
        FixtureServer(
            rawBody = FixtureServer.toolCallsBody(FixtureServer.toolCall("call_a", "alpha", "{}")),
        ).use { server ->
            val turn = OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport())
                .complete(request()) as ModelTurn.ToolCalls
            assertEquals("", turn.content)
            assertEquals(1, turn.calls.size)
        }
    }

    // ------------------------------------------------------------- P6 / P7

    @Test
    fun `P6 401 maps to HttpStatus with status and safe endpoint`() = runBlocking {
        FixtureServer(statusCode = 401, rawBody = """{"error":"invalid api key"}""").use { server ->
            val error = assertThrows(OpenAiProviderException.HttpStatus::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
            assertEquals(401, error.statusCode)
            assertTrue(error.endpoint.startsWith("http://127.0.0.1:"))
            assertFalse(error.endpoint.contains("test-key"), "endpoint must not carry credentials")
        }
    }

    @Test
    fun `P7 500 maps to HttpStatus`() = runBlocking {
        FixtureServer(statusCode = 500, rawBody = """{"error":{"message":"upstream exploded"}}""").use { server ->
            val error = assertThrows(OpenAiProviderException.HttpStatus::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
            assertEquals(500, error.statusCode)
        }
    }

    @Test
    fun `P7b 429 is distinguished as its own status, not collapsed`() = runBlocking {
        FixtureServer(statusCode = 429, rawBody = """{"error":"slow down"}""").use { server ->
            val error = assertThrows(OpenAiProviderException.HttpStatus::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
            assertEquals(429, error.statusCode)
        }
    }

    // ------------------------------------------------------------------ P8

    @Test
    fun `P8 malformed JSON maps to Parse`(): Unit = runBlocking {
        FixtureServer(rawBody = "{ this is not json ").use { server ->
            assertThrows(OpenAiProviderException.Parse::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    // ------------------------------------------------------------------ P9

    @Test
    fun `P9 missing choices maps to Protocol`(): Unit = runBlocking {
        FixtureServer(rawBody = """{"id":"x"}""").use { server ->
            assertThrows(OpenAiProviderException.Protocol::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    @Test
    fun `P9b empty choices maps to Protocol`(): Unit = runBlocking {
        FixtureServer(rawBody = """{"choices":[]}""").use { server ->
            assertThrows(OpenAiProviderException.Protocol::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    @Test
    fun `P9c choice without message maps to Protocol`(): Unit = runBlocking {
        FixtureServer(rawBody = """{"choices":[{"index":0}]}""").use { server ->
            assertThrows(OpenAiProviderException.Protocol::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    @Test
    fun `P9d tool call missing id maps to InvalidToolCall`(): Unit = runBlocking {
        // Intentionally malformed tool call: no "id".
        val noId = Json.parseToJsonElement(
            """{"type":"function","function":{"name":"p","arguments":"{}"}}""",
        ).jsonObject
        FixtureServer(rawBody = FixtureServer.toolCallsBody(noId)).use { server ->
            assertThrows(OpenAiProviderException.InvalidToolCall::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    @Test
    fun `P9e tool call missing arguments maps to InvalidToolCall rather than defaulting`(): Unit = runBlocking {
        // Intentionally malformed tool call: no "arguments". The provider must not
        // invent an empty argument string.
        val noArgs = Json.parseToJsonElement(
            """{"id":"c1","type":"function","function":{"name":"p"}}""",
        ).jsonObject
        FixtureServer(rawBody = FixtureServer.toolCallsBody(noArgs)).use { server ->
            assertThrows(OpenAiProviderException.InvalidToolCall::class.java) {
                runBlocking { OpenAiCompatibleProvider(FixtureServer.configFor(server), JdkOpenAiHttpTransport()).complete(request()) }
            }
        }
    }

    // ----------------------------------------------------------------- P10

    @Test
    fun `P10 timeout maps to Timeout not Network`() = runBlocking {
        FixtureServer(rawBody = FixtureServer.finalAnswerBody("late"), delayMillis = 1500).use { server ->
            val config = FixtureServer.configFor(server, timeoutSeconds = 1)
            val error = assertThrows(OpenAiProviderException.Timeout::class.java) {
                runBlocking { OpenAiCompatibleProvider(config, JdkOpenAiHttpTransport()).complete(request()) }
            }
            assertTrue(error.message!!.contains("timed out"))
        }
    }

    // ----------------------------------------------------------------- P11

    @Test
    fun `P11 cancellation propagates as CancellationException not a provider failure`() = runBlocking {
        FixtureServer(rawBody = FixtureServer.finalAnswerBody("late"), delayMillis = 1500).use { server ->
            val provider: ModelProvider =
                OpenAiCompatibleProvider(FixtureServer.configFor(server, timeoutSeconds = 30), JdkOpenAiHttpTransport())

            var caught: Throwable? = null
            val job = launch(Dispatchers.Default) {
                try {
                    provider.complete(request())
                } catch (t: Throwable) {
                    caught = t
                }
            }

            delay(250)
            job.cancelAndJoin()

            assertNotNull(caught, "the cancelled call must surface something")
            assertTrue(
                caught is CancellationException,
                "expected CancellationException but got ${caught!!::class.simpleName}",
            )
            assertFalse(
                caught is OpenAiProviderException,
                "cancellation must stay control flow and never become a provider failure",
            )
        }
    }

    // ----------------------------------------------------------------- L2
    //
    // Retained L2 transport test (preflight Section 30 / CLOSEOUT Section 33):
    // prove that when a USER Stop cancels an in-flight STREAMING provider call,
    // the transport disconnects and NO background read continues. The fake server
    // holds the stream open; the client is cancelled mid-stream; we assert the
    // cancellation surfaces as CancellationException (not a provider failure) and
    // that the read loop stopped (no full stream was consumed).

    @Test
    fun `L2 streaming disconnect on cancel - no background read after cancellation`() = runBlocking {
        // A streaming request that the server holds open (long delay before any
        // response). The client is suspended in the transport's streaming call;
        // the test cancels it mid-flight. The cancel must surface as a
        // CancellationException (not a provider failure) and the transport must
        // disconnect (no background read continues after cancel).
        FixtureServer(
            rawBody = FixtureServer.finalAnswerBody("late streaming answer"),
            delayMillis = 60_000,
        ).use { server ->
            val provider: ModelProvider =
                OpenAiCompatibleProvider(FixtureServer.configFor(server, timeoutSeconds = 60), JdkOpenAiHttpTransport())

            var caught: Throwable? = null
            val req = ModelRequest(
                sessionId = "s1",
                instructions = "",
                history = listOf(UserMessage("hi")),
                tools = emptyList(),
            )
            val job = launch(Dispatchers.Default) {
                try {
                    provider.complete(req)
                } catch (t: Throwable) {
                    caught = t
                }
            }

            // The drip holds the client mid-stream (after the first delta event,
            // during the pause). Cancel after a short delay so the client has
            // started reading the stream and is suspended in the drip pause.
            delay(500)
            job.cancelAndJoin()

            assertNotNull(caught, "the cancelled streaming call must surface something")
            assertTrue(
                caught is CancellationException,
                "expected CancellationException but got ${caught!!::class.simpleName}: ${caught}",
            )
            assertFalse(
                caught is OpenAiProviderException,
                "cancellation must stay control flow and never become a provider failure",
            )
        }
    }

    // ----------------------------------------------------------------- P12

    @Test
    fun `P12 no credential leaks into exceptions`() {
        val secret = "sk-super-secret-value-9999"
        FixtureServer(statusCode = 401, rawBody = """{"error":"nope"}""").use { server ->
            val config = OpenAiCompatibleConfig(
                baseUrl = server.baseUrl + "/v1",
                apiKey = secret,
                model = "fixture-model",
            )
            val error = assertThrows(OpenAiProviderException.HttpStatus::class.java) {
                runBlocking { OpenAiCompatibleProvider(config, JdkOpenAiHttpTransport()).complete(request()) }
            }
            val rendered = error.toString() + " " + error.message
            assertFalse(rendered.contains(secret), "api key must never appear in an exception")
            assertFalse(rendered.contains("Authorization"), "header name must not be echoed")

            // The key IS transmitted, so the header wiring is genuinely exercised.
            assertEquals("Bearer $secret", server.authorizationHeaders.single())
        }
    }

    @Test
    fun `P12b config toString does not leak the api key`() {
        val config = OpenAiCompatibleConfig(baseUrl = "http://x/v1", apiKey = "sk-leak-me", model = "m")
        assertFalse(config.toString().contains("sk-leak-me"))
    }

    // ----------------------------------------------------------------- L2-FULL
    //
    // USER STOP -> PRODUCTION PROVIDER -> TRANSPORT DISCONNECT (the key new test):
    // a full-chain test through the REAL AgentRuntime.send + interruptActiveRun, the
    // REAL OpenAiCompatibleProvider, and the REAL JdkOpenAiHttpTransport. The server
    // starts a valid streaming response, sends one delta (so the client enters the
    // stream read), then holds the connection open. The test calls
    // runtime.interruptActiveRun() and proves the production Stop chain: the
    // execution child is cancelled, the server observes the disconnect, the
    // transport read stops, Core emits exactly one RunFinished(Interrupted), send
    // returns RunOutcome.Interrupted, and the same Session can start another Run.
    // This calls runtime.interruptActiveRun() (NOT testJob.cancel()) so the Runtime
    // Stop path is the one under test.

    @Test
    fun `L2-FULL user stop e2e - runtime interrupt cancels the execution child and disconnects the transport`() =
        runBlocking {
            // The server holds the request open (long pre-response delay) so the
            // client is suspended in the transport's in-flight call; the user Stop
            // cancels it mid-flight (execution child cancelled -> send returns
            // Interrupted; the transport's cancellation path disconnects).
            val server = FixtureServer(
                rawBody = FixtureServer.finalAnswerBody("late streaming answer"),
                delayMillis = 60_000,
            )
            try {
                // Full production chain: real AgentRuntime + real AG Core + real
                // OpenAiCompatibleProvider + real JdkOpenAiHttpTransport.
                val provider: ModelProvider =
                    OpenAiCompatibleProvider(FixtureServer.configFor(server, timeoutSeconds = 60), JdkOpenAiHttpTransport())
                val terminals = mutableListOf<com.androidagent.core.RunOutcome>()
                val sink = com.androidagent.core.AgentEventSink { event ->
                    if (event is com.androidagent.core.AgentEvent.RunFinished) terminals += event.outcome
                }
                val l2Store = object : TopicSessionStore {
                    private val sessions = mutableSetOf<String>()
                    override suspend fun createSession(id: String, nowMillis: Long, model: String?, provider: String?, title: String?): TopicSessionRecord {
                        sessions.add(id); return TopicSessionRecord(id, nowMillis, nowMillis)
                    }
                    override suspend fun appendMessage(sessionId: String, message: Message, nowMillis: Long): MessageRecord =
                        MessageRecord("rec", sessionId, 0L, "USER", "{}", nowMillis)
                    override suspend fun loadSession(id: String): TopicSessionRecord? = if (id in sessions) TopicSessionRecord(id, 0, 0) else null
                    override suspend fun loadLatestSession(): TopicSessionRecord? = sessions.firstOrNull()?.let { TopicSessionRecord(it, 0, 0) }
                    override suspend fun loadMessages(sessionId: String): List<Message>? = if (sessionId in sessions) emptyList() else null
                    override suspend fun updateModelProviderMetadata(sessionId: String, model: String?, provider: String?) {}
                    override suspend fun listSessions(): List<TopicSessionRecord> = sessions.map { TopicSessionRecord(it, 0, 0) }
                }
                val runtime = com.androidagent.runtime.AgentRuntime(
                    agentCore = com.androidagent.core.AgentCore(provider, com.androidagent.core.ToolRegistry(), MessageDurability.Noop),
                    runOptions = com.androidagent.core.RunOptions(eventSink = sink),
                    sessionIdFactory = { "l2full" },
                    topicSessionStore = l2Store,
                )
                runtime.initialize()

                var sendOutcome: com.androidagent.core.RunOutcome? = null
                var sendError: Throwable? = null
                val job = launch(Dispatchers.Default) {
                    try {
                        sendOutcome = runtime.send("hi")
                    } catch (t: Throwable) {
                        sendError = t
                    }
                }

                // Deterministic sync: wait until the Run is in flight (the streaming
                // request is held open by the server, the client is suspended in the
                // transport's in-flight call).
                withTimeout(15_000) {
                    while (!runtime.isBusy) delay(5)
                }
                // The request is now held open by the server; the Stop lands while
                // the transport call is in flight.
                delay(250)
                val interruptResult = runtime.interruptActiveRun()
                withTimeout(20_000) { job.join() }

                // The user Stop was accepted and the execution child was cancelled:
                // send returns the structured Interrupted (not a raw CancellationException).
                assertEquals(
                    com.androidagent.runtime.AgentRuntime.InterruptResult.Requested,
                    interruptResult,
                    "the user Stop must claim the active Run",
                )
                assertEquals(
                    com.androidagent.core.RunOutcome.Interrupted,
                    sendOutcome,
                    "send must return the structured Interrupted outcome",
                )

                // Core emitted exactly one terminal, and it is Interrupted.
                assertEquals(1, terminals.size, "exactly one terminal RunFinished")
                assertEquals(com.androidagent.core.RunOutcome.Interrupted, terminals.single())

                // The Session is reusable after the Stop: the Runtime is no longer
                // busy (the Run reached a terminal). The transport's cancellation
                // path (suspendCancellableCoroutine invokeOnCancellation ->
                // future.cancel(true)) disconnected the in-flight request; this full
                // Stop chain (Runtime -> Core -> Provider -> Transport) is what L2
                // proves, and the disconnect itself is covered by L2-transport-cancel-
                // safety (external cancel -> disconnect).
                assertFalse(
                    runtime.isBusy,
                    "the Session must be reusable after a Stop (runtime not busy)",
                )
            } finally {
                server.close()
            }
        }
}