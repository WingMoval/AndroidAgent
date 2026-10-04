package com.androidagent.provider.openai

import com.androidagent.core.Message
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelRequest
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
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
    fun buildRequestBody(model: String, request: ModelRequest): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("messages", buildJsonArray { projectMessages(request) })
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray { request.tools.forEach { add(toolDeclaration(it)) } })
        }
        // Non-streaming only: this vertical slice never sets stream.
        put("stream", JsonPrimitive(false))
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

    private const val MAX_SUMMARY_CHARS = 200
}