package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * A controlled OpenAI-compatible fixture endpoint built on the JDK's own
 * `com.sun.net.httpserver`. No MockWebServer or other test dependency is added.
 *
 * Binds to an ephemeral loopback port, so tests are offline, deterministic and
 * parallel-safe. Every request body is captured so outbound mapping can be
 * asserted, and the Authorization header is captured separately so tests can
 * prove it is transmitted without ever asserting on a real secret.
 */
class FixtureServer(
    private val statusCode: Int = 200,
    private val rawBody: String = "{}",
    private val contentType: String = "application/json",
    private val delayMillis: Long = 0,
) : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    /** Every request body received, in order. */
    val requestBodies: MutableList<String> = mutableListOf()

    /** Authorization header values received, in order. Never printed by this class. */
    val authorizationHeaders: MutableList<String?> = mutableListOf()

    /** Full request paths received, in order. */
    val requestPaths: MutableList<String> = mutableListOf()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    /**
     * Optional per-request body source. Lets one server answer a sequence of
     * different responses, which is how the two-turn vertical slice is driven.
     */
    @Volatile
    var responder: (() -> String)? = null

    init {
        server.createContext("/v1/chat/completions") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        requestBodies += body
        authorizationHeaders += exchange.requestHeaders.getFirst("Authorization")
        requestPaths += exchange.requestURI.path

        if (delayMillis > 0) Thread.sleep(delayMillis)

        val text = responder?.invoke() ?: rawBody
        // A real OpenAI-compatible endpoint frames stream:true responses as SSE.
        // The fixture mirrors that so the Provider's streaming path is exercised.
        val streaming = statusCode in 200..299 && body.contains("\"stream\":true")
        val bytes = if (streaming) {
            sseFrame(text).toByteArray(StandardCharsets.UTF_8)
        } else {
            text.toByteArray(StandardCharsets.UTF_8)
        }
        exchange.responseHeaders.add(
            "Content-Type",
            if (streaming) "text/event-stream" else contentType,
        )
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /**
     * Frames one JSON chunk as SSE. A real streaming endpoint emits `delta`
     * objects, so a fixture body written in the non-streaming `message` shape is
     * projected onto `choices[0].delta` (finish_reason preserved), then framed
     * as a single data line followed by [DONE].
     */
    private fun sseFrame(json: String): String {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return "data: $json\n\n" + "data: [DONE]\n\n"
        }
        val choices = root["choices"] as? JsonArray ?: return "data: $json\n\n" + "data: [DONE]\n\n"
        val first = choices.firstOrNull() as? JsonObject ?: return "data: $json\n\n" + "data: [DONE]\n\n"
        val message = first["message"] as? JsonObject ?: return "data: $json\n\n" + "data: [DONE]\n\n"
        val finishReason = first["finish_reason"]
        val chunk = buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("index", JsonPrimitive(0))
                            put(
                                "delta",
                                buildJsonObject {
                                    message.forEach { (k, v) ->
                                        if (k == "tool_calls") {
                                            // Streaming tool calls are keyed by index; the
                                            // non-streaming shape has none, so one is added.
                                            val calls = v as? JsonArray ?: return@forEach
                                            put(
                                                "tool_calls",
                                                buildJsonArray {
                                                    calls.forEachIndexed { i, call ->
                                                        val obj = call as? JsonObject ?: return@forEachIndexed
                                                        add(
                                                            buildJsonObject {
                                                                put("index", JsonPrimitive(i))
                                                                obj.forEach { (ck, cv) -> put(ck, cv) }
                                                            },
                                                        )
                                                    }
                                                },
                                            )
                                        } else {
                                            put(k, v)
                                        }
                                    }
                                },
                            )
                            if (finishReason != null) put("finish_reason", finishReason)
                        },
                    )
                },
            )
        }.toString()
        return "data: $chunk\n\n" + "data: [DONE]\n\n"
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        /** Builds a config pointed at this fixture, with a distinctive fake key. */
        fun configFor(server: FixtureServer, timeoutSeconds: Long = 5) = OpenAiCompatibleConfig(
            baseUrl = server.baseUrl + "/v1",
            apiKey = "test-key-not-a-real-secret",
            model = "fixture-model",
            requestTimeout = java.time.Duration.ofSeconds(timeoutSeconds),
        )

        /**
         * Builds response bodies with the JSON library rather than string
         * templates, so argument payloads are escaped correctly and a fixture can
         * never be malformed by hand.
         */
        private fun body(message: JsonObject): String =
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("index", JsonPrimitive(0))
                                put("message", message)
                                put("finish_reason", JsonPrimitive("stop"))
                            },
                        )
                    },
                )
            }.toString()

        /** A minimal well-formed assistant final-answer response. */
        fun finalAnswerBody(content: String): String = body(
            buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonPrimitive(content))
            },
        )

        /** A response whose assistant turn carries tool calls only. */
        fun toolCallsBody(vararg calls: JsonObject): String = body(
            buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonNull)
                put("tool_calls", JsonArray(calls.toList()))
            },
        )

        /**
         * One tool call entry. [arguments] is the RAW argument string exactly as a
         * model would emit it; it is embedded as a JSON string without alteration.
         */
        fun toolCall(id: String, name: String, rawArguments: String): JsonObject = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("type", JsonPrimitive("function"))
            put(
                "function",
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("arguments", JsonPrimitive(rawArguments))
                },
            )
        }

        /** An assistant turn carrying prose AND tool calls, for the P5 contract gap. */
        fun mixedTurnBody(prose: String, vararg calls: JsonObject): String = body(
            buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonPrimitive(prose))
                put("tool_calls", JsonArray(calls.toList()))
            },
        )

        /** An arbitrary raw body, for malformed-response cases. */
        fun raw(rawBody: String): String = rawBody
    }
}