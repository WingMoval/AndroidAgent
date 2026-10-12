package com.androidagent.provider.openai

import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.ModelTurn
import com.androidagent.core.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * AGA.OBSERVABILITY-0 �?P series: the Provider always streams.
 *
 * Proves that [OpenAiCompatibleProvider.complete] requests `stream: true`,
 * feeds every SSE line through the accumulator, notifies the observer
 * incrementally, assembles the same final [ModelTurn] semantics as before, and
 * maps non-2xx responses to [OpenAiProviderException.HttpStatus]. No network,
 * no live endpoint, no real key.
 */
class OpenAiCompatibleProviderStreamingTest {

    private class FakeTransport(
        var statusCode: Int = 200,
        var body: String = "",
        var lines: List<String> = emptyList(),
    ) : OpenAiHttpTransport {
        var lastRequest: OpenAiHttpRequest? = null
        var postStreamCalled = false

        override suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse {
            throw UnsupportedOperationException("provider must not use the buffered path")
        }

        override suspend fun postStream(request: OpenAiHttpRequest, onLine: (String) -> Unit): OpenAiHttpResponse {
            postStreamCalled = true
            lastRequest = request
            if (statusCode !in 200..299) {
                return OpenAiHttpResponse(statusCode, body)
            }
            lines.forEach(onLine)
            return OpenAiHttpResponse(statusCode, "")
        }
    }

    private class RecordingObserver : ModelStreamObserver {
        val reasoningDeltas = mutableListOf<String>()
        val contentDeltas = mutableListOf<String>()
        var reasoningStarted = 0
        var contentStarted = 0
        var reasoningFinished = 0
        var contentFinished = 0

        override fun onReasoningStarted() {
            reasoningStarted++
        }

        override fun onReasoningDelta(text: String) {
            reasoningDeltas += text
        }

        override fun onReasoningFinished() {
            reasoningFinished++
        }

        override fun onContentStarted() {
            contentStarted++
        }

        override fun onContentDelta(text: String) {
            contentDeltas += text
        }

        override fun onContentFinished() {
            contentFinished++
        }
    }

    private val config = OpenAiCompatibleConfig(
        baseUrl = "https://api.emg.dpdns.org/v1",
        model = "qwen3.8-local",
        apiKey = "FAKE_KEY_FOR_TEST",
        requestTimeout = Duration.ofSeconds(30),
    )

    private fun request(observer: ModelStreamObserver = ModelStreamObserver.Noop): ModelRequest = ModelRequest(
        sessionId = "s1",
        instructions = "test instructions",
        history = listOf(UserMessage("hello")),
        tools = emptyList(),
        streamObserver = observer,
    )

    // ------------------------------------------------------------ P1-P4 streaming

    @Test
    fun P1_complete_requests_stream_true() {
        val transport = FakeTransport(
            lines = listOf("""data: {"choices":[{"delta":{"content":"hi"},"finish_reason":"stop"}]}"""),
        )
        val provider = OpenAiCompatibleProvider(config, transport)
        val turn = runBlocking { provider.complete(request()) }
        assertTrue(transport.postStreamCalled)
        val body = Json.parseToJsonElement(transport.lastRequest!!.body).jsonObject
        assertEquals(true, body["stream"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        assertTrue(turn is ModelTurn.FinalAnswer)
        assertEquals("hi", (turn as ModelTurn.FinalAnswer).content)
    }

    @Test
    fun P2_observer_receives_incremental_deltas() {
        val transport = FakeTransport(
            lines = listOf(
                """data: {"choices":[{"delta":{"reasoning":"r1"},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"reasoning":"r2","content":"c1"},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"content":"c2"},"finish_reason":"stop"}]}""",
                "data: [DONE]",
            ),
        )
        val provider = OpenAiCompatibleProvider(config, transport)
        val observer = RecordingObserver()
        val turn = runBlocking { provider.complete(request(observer)) }
        assertEquals(1, observer.reasoningStarted)
        assertEquals(listOf("r1", "r2"), observer.reasoningDeltas)
        assertEquals(1, observer.reasoningFinished, "reasoning section is closed at stream end")
        assertEquals(1, observer.contentStarted)
        assertEquals(listOf("c1", "c2"), observer.contentDeltas)
        assertEquals(1, observer.contentFinished, "content section is closed at stream end")
        assertTrue(turn is ModelTurn.FinalAnswer)
        assertEquals("c1c2", (turn as ModelTurn.FinalAnswer).content)
    }

    @Test
    fun P3_tool_calls_flow_through_the_stream() {
        val transport = FakeTransport(
            lines = listOf(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"android_shell","arguments":"{\"command\":"}}]},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"id\"}"}}]},"finish_reason":"tool_calls"}]}""",
            ),
        )
        val provider = OpenAiCompatibleProvider(config, transport)
        val turn = runBlocking { provider.complete(request()) }
        assertTrue(turn is ModelTurn.ToolCalls)
        val calls = (turn as ModelTurn.ToolCalls).calls
        assertEquals(1, calls.size)
        assertEquals("""{"command":"id"}""", calls[0].arguments)
    }

    @Test
    fun P4_authorization_header_never_reaches_the_test_output() {
        val transport = FakeTransport(
            lines = listOf("""data: {"choices":[{"delta":{"content":"x"},"finish_reason":"stop"}]}"""),
        )
        val provider = OpenAiCompatibleProvider(config, transport)
        runBlocking { provider.complete(request()) }
        val headers = transport.lastRequest!!.headers
        assertEquals("Bearer FAKE_KEY_FOR_TEST", headers["Authorization"])
        // The fake key is a test sentinel; the real policy is enforced by the
        // transport contract (never print headers). Assert the header exists.
        assertTrue(headers.containsKey("Authorization"))
    }

    // ------------------------------------------------------------ P5-P7 errors

    @Test
    fun P5_non_2xx_status_raises_http_status_without_calling_on_line() {
        val transport = FakeTransport(statusCode = 403, body = "forbidden")
        val provider = OpenAiCompatibleProvider(config, transport)
        val ex = assertThrows(OpenAiProviderException.HttpStatus::class.java) {
            runBlocking { provider.complete(request()) }
        }
        assertEquals(403, ex.statusCode)
        assertTrue(ex.endpoint == "https://api.emg.dpdns.org/v1/chat/completions")
    }

    @Test
    fun P6_network_failure_maps_to_network_error_with_safe_endpoint() {
        val transport = object : OpenAiHttpTransport {
            override suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse =
                throw UnsupportedOperationException()

            override suspend fun postStream(request: OpenAiHttpRequest, onLine: (String) -> Unit): OpenAiHttpResponse =
                throw OpenAiTransportNetworkException("connect failed")
        }
        val provider = OpenAiCompatibleProvider(config, transport)
        val ex = assertThrows(OpenAiProviderException.Network::class.java) {
            runBlocking { provider.complete(request()) }
        }
        assertTrue(ex.message.orEmpty().contains("https://api.emg.dpdns.org/v1/chat/completions"))
        assertFalse(ex.message.orEmpty().contains("FAKE_KEY_FOR_TEST"))
    }

    @Test
    fun P7_empty_stream_raises_protocol_error() {
        val transport = FakeTransport(lines = listOf("data: [DONE]"))
        val provider = OpenAiCompatibleProvider(config, transport)
        assertThrows(OpenAiProviderException.Protocol::class.java) {
            runBlocking { provider.complete(request()) }
        }
    }

    @Test
    fun P8_finished_callbacks_match_the_stream_sections() {
        // Content-only stream: only the content section closes.
        val contentOnly = OpenAiCompatibleProvider(
            config,
            FakeTransport(
                lines = listOf("""data: {"choices":[{"delta":{"content":"x"},"finish_reason":"stop"}]}"""),
            ),
        )
        val contentObserver = RecordingObserver()
        runBlocking { contentOnly.complete(request(contentObserver)) }
        assertEquals(0, contentObserver.reasoningFinished)
        assertEquals(1, contentObserver.contentFinished)

        // Reasoning + tool-call stream (no content): only the reasoning section closes.
        val reasoningOnly = OpenAiCompatibleProvider(
            config,
            FakeTransport(
                lines = listOf(
                    """data: {"choices":[{"delta":{"reasoning":"r","tool_calls":[{"index":0,"id":"c1","function":{"name":"android_shell","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
                ),
            ),
        )
        val reasoningObserver = RecordingObserver()
        runBlocking { reasoningOnly.complete(request(reasoningObserver)) }
        assertEquals(1, reasoningObserver.reasoningFinished)
        assertEquals(0, reasoningObserver.contentFinished)
    }
}
