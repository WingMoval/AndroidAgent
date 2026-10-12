package com.androidagent.provider.openai

import com.androidagent.core.Message
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.ModelTurn
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CancellationException

/**
 * Maps between AG Core's provider-neutral domain and the OpenAI-compatible
 * `/chat/completions` wire shape.
 *
 * All OpenAI wire knowledge lives here and nowhere else. AG Core never sees a
 * JSON structure, and this module never stores OpenAI JSON in AG Core history.
 */
internal object ChatCompletionWire {

    /**
     * Builds the request body from an AG Core [ModelRequest].
     *
     * Message order is preserved exactly as AG Core recorded it.
     */
    fun buildRequestBody(model: String, request: ModelRequest, stream: Boolean = false): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", buildJsonArray { projectMessages(request) })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray { request.tools.forEach { add(toolDeclaration(it)) } })
            }
            put("stream", JsonPrimitive(stream))
        }

    private fun JsonArrayBuilder.projectMessages(request: ModelRequest) {
        // ModelRequest.instructions is a first-class field, not a Message, so it
        // becomes a leading system message when present.
        if (request.instructions.isNotBlank()) {
            add(
                buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(request.instructions))
                },
            )
        }
        request.history.forEach { message ->
            when (message) {
                is UserMessage -> add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(message.content))
                    },
                )

                is ModelMessage -> add(assistantMessage(message))

                is ToolResultMessage -> add(
                    buildJsonObject {
                        put("role", JsonPrimitive("tool"))
                        put("tool_call_id", JsonPrimitive(message.toolCallId))
                        put("content", JsonPrimitive(renderToolResult(message)))
                    },
                )
            }
        }
    }

    private fun assistantMessage(message: ModelMessage): JsonObject = buildJsonObject {
        put("role", JsonPrimitive("assistant"))
        put("content", JsonPrimitive(message.content))
        if (message.toolCalls.isNotEmpty()) {
            put(
                "tool_calls",
                buildJsonArray {
                    message.toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive(call.id))
                                put("type", JsonPrimitive("function"))
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", JsonPrimitive(call.name))
                                        // Passed through verbatim. Never parsed,
                                        // normalised, or re-serialised.
                                        put("arguments", JsonPrimitive(call.arguments))
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    /**
     * Tool result body sent back to the model.
     *
     * On mechanism failure the error is appended so the model can see it. AG
     * Core's `ToolResult.success` is a mechanism flag and is never serialised as
     * such, because the wire has no such concept.
     */
    private fun renderToolResult(message: ToolResultMessage): String {
        val error = message.result.error
        return if (error.isNullOrBlank()) {
            message.result.output
        } else {
            message.result.output + "\n[error] " + error
        }
    }

    /**
     * Projects an AG Core [ToolSpec] onto an OpenAI function declaration.
     *
     * AG Core's ToolSpec carries only name and description - there is no typed
     * argument schema. This uses a permissive object schema, which is a
     * Provider vertical slice projection and NOT a finished typed tool schema.
     */
    private fun toolDeclaration(spec: ToolSpec): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put(
            "function",
            buildJsonObject {
                put("name", JsonPrimitive(spec.name))
                put("description", JsonPrimitive(spec.description))
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("additionalProperties", JsonPrimitive(true))
                    },
                )
            },
        )
    }

    /**
     * Extracts the single assistant message from a chat-completions response.
     *
     * @throws OpenAiProviderException.Protocol when the envelope is malformed.
     */
    fun assistantMessageOf(body: JsonElement): JsonObject {
        val root = body.jsonObject
        val choices = root["choices"] as? JsonArray
            ?: throw OpenAiProviderException.Protocol("response has no 'choices' array")
        val first = choices.firstOrNull() as? JsonObject
            ?: throw OpenAiProviderException.Protocol("'choices' was empty")
        val message = first["message"] as? JsonObject
            ?: throw OpenAiProviderException.Protocol("choice has no 'message' object")
        return message
    }

    /** Raw content string, or null when absent or JSON null. */
    fun contentOf(message: JsonObject): String? =
        (message["content"] as? JsonPrimitive)?.contentOrNull

    /** Raw tool_calls array, or null when absent. */
    fun toolCallsOf(message: JsonObject): JsonArray? = message["tool_calls"] as? JsonArray

    /**
     * Reads one tool call.
     *
     * [arguments] is the wire string, returned byte-for-byte. Missing required
     * fields raise [OpenAiProviderException.InvalidToolCall] rather than being
     * defaulted, so nothing is silently invented.
     */
    fun toolCallOf(element: JsonElement, index: Int): WireToolCall {
        val call = element as? JsonObject
            ?: throw OpenAiProviderException.InvalidToolCall("tool_calls[$index] is not an object")

        val id = (call["id"] as? JsonPrimitive)?.contentOrNull
            ?: throw OpenAiProviderException.InvalidToolCall("tool_calls[$index] has no 'id'")

        val function = call["function"] as? JsonObject
            ?: throw OpenAiProviderException.InvalidToolCall("tool_calls[$index] has no 'function'")

        val name = (function["name"] as? JsonPrimitive)?.contentOrNull
            ?: throw OpenAiProviderException.InvalidToolCall("tool_calls[$index].function has no 'name'")

        val argumentsElement = function["arguments"]
        if (argumentsElement == null || argumentsElement is JsonNull) {
            throw OpenAiProviderException.InvalidToolCall("tool_calls[$index].function has no 'arguments'")
        }
        val arguments = (argumentsElement as? JsonPrimitive)?.content
            ?: throw OpenAiProviderException.InvalidToolCall(
                "tool_calls[$index].function.arguments is not a string",
            )

        return WireToolCall(id = id, name = name, arguments = arguments)
    }

    /** Builds a short, redacted provider error summary from an error body. */
    fun errorSummary(body: String): String {
        val collapsed = body.filterNot { it.isWhitespace() }
        if (collapsed.isEmpty()) return ""
        val truncated = collapsed.take(MAX_SUMMARY_CHARS)
        return truncated + if (collapsed.length > MAX_SUMMARY_CHARS) "..." else ""
    }

    /** Tool call exactly as it appeared on the wire. */
    data class WireToolCall(val id: String, val name: String, val arguments: String)

    // ------------------------------------------------------------- Streaming

    /**
     * Accumulates one SSE streamed chat-completions response.
     *
     * Reasoning and content fragments are concatenated in wire order; tool
     * calls are accumulated per `index` (id/name are set once, arguments are
     * concatenated across chunks because the wire fragments the JSON string).
     */
    class StreamAccumulator {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = mutableListOf<AccumulatedToolCall>()
        var finishReason: String? = null
        var done: Boolean = false
    }

    /** One streamed tool call slot, keyed by the wire `index`. */
    class AccumulatedToolCall(val index: Int) {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()

        /** True once the wire carried an `arguments` field for this slot. */
        var argumentsSeen: Boolean = false
    }

    /**
     * Parses one SSE line of a streamed chat-completions response into [acc],
     * notifying [observer] as reasoning/content deltas arrive.
     *
     * Non-`data:` lines (keep-alives, blank separators) are ignored. A
     * `data: [DONE]` line marks the stream complete.
     *
     * @throws OpenAiProviderException.Parse when a data line is not valid JSON.
     * @throws OpenAiProviderException.Protocol when the chunk shape is unusable.
     * @throws OpenAiProviderException.InvalidToolCall for a malformed tool call.
     */
    fun parseStreamLine(line: String, acc: StreamAccumulator, observer: ModelStreamObserver) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        if (!trimmed.startsWith(DATA_PREFIX)) return
        val data = trimmed.removePrefix(DATA_PREFIX).trim()
        if (data == DONE_MARKER) {
            acc.done = true
            return
        }

        val root = try {
            Json.parseToJsonElement(data).jsonObject
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw OpenAiProviderException.Parse("stream chunk was not valid JSON", e)
        }
        val choices = root["choices"] as? JsonArray
            ?: throw OpenAiProviderException.Protocol("stream chunk has no 'choices' array")
        val first = choices.firstOrNull() as? JsonObject ?: return
        (first["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { acc.finishReason = it }

        val delta = first["delta"] as? JsonObject ?: return

        (delta["reasoning"] as? JsonPrimitive)?.contentOrNull?.let { text ->
            if (text.isNotEmpty()) {
                if (acc.reasoning.isEmpty()) observer.onReasoningStarted()
                acc.reasoning.append(text)
                observer.onReasoningDelta(text)
            }
        }

        (delta["content"] as? JsonPrimitive)?.contentOrNull?.let { text ->
            if (text.isNotEmpty()) {
                if (acc.content.isEmpty()) observer.onContentStarted()
                acc.content.append(text)
                observer.onContentDelta(text)
            }
        }

        (delta["tool_calls"] as? JsonArray)?.forEach { element ->
            val call = element as? JsonObject
                ?: throw OpenAiProviderException.InvalidToolCall(
                    "stream tool_calls element is not an object",
                )
            val index = (call["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            val slot = acc.toolCalls.firstOrNull { it.index == index }
                ?: AccumulatedToolCall(index).also { acc.toolCalls += it }
            (call["id"] as? JsonPrimitive)?.contentOrNull?.let { slot.id = it }
            val function = call["function"] as? JsonObject
            if (function != null) {
                (function["name"] as? JsonPrimitive)?.contentOrNull?.let { slot.name = it }
                (function["arguments"] as? JsonPrimitive)?.contentOrNull?.let {
                    slot.arguments.append(it)
                    slot.argumentsSeen = true
                }
            }
        }
    }

    /**
     * Assembles the final [ModelTurn] from an accumulated stream, with the same
     * semantics as the non-streaming path: tool calls win, prose is preserved
     * alongside them, and an empty turn is a protocol failure.
     */
    fun toModelTurn(acc: StreamAccumulator): ModelTurn {
        val content = acc.content.toString()
        val calls = acc.toolCalls.map { slot ->
            val id = slot.id
                ?: throw OpenAiProviderException.InvalidToolCall("stream tool call has no 'id'")
            val name = slot.name
                ?: throw OpenAiProviderException.InvalidToolCall("stream tool call has no 'name'")
            if (!slot.argumentsSeen) {
                throw OpenAiProviderException.InvalidToolCall("stream tool call has no 'arguments'")
            }
            ToolCall(id = id, name = name, arguments = slot.arguments.toString())
        }
        val hasContent = content.isNotBlank()
        val hasToolCalls = calls.isNotEmpty()
        return when {
            hasToolCalls -> ModelTurn.ToolCalls(calls = calls, content = content)
            hasContent -> ModelTurn.FinalAnswer(content)
            else -> throw OpenAiProviderException.Protocol(
                "streamed assistant message carried neither content nor tool_calls",
            )
        }
    }

    private const val DATA_PREFIX = "data:"
    private const val DONE_MARKER = "[DONE]"

    private const val MAX_SUMMARY_CHARS = 200
}