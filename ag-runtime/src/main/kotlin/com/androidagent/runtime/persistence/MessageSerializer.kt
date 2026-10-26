package com.androidagent.runtime.persistence

import com.androidagent.core.Message
import com.androidagent.core.ModelMessage
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Durable message-type discriminator (frozen set — do NOT add types here).
 *
 *  - [USER]        = UserMessage
 *  - [MODEL]       = ModelMessage (final answer AND tool-call turns; a
 *                    ToolCall is part of the ModelMessage payload, never a
 *                    separate row)
 *  - [TOOL_RESULT] = ToolResultMessage
 *
 * There is deliberately NO RUN, EVENT, TOOL_CALL, or FINAL_ANSWER type:
 * a Run is transient, an AgentEvent is an observation, and a FinalAnswer is
 * just a ModelMessage with no tool calls.
 */
enum class MessageType {
    USER,
    MODEL,
    TOOL_RESULT;

    companion object {
        /** Wire name for each type. */
        fun wireName(type: MessageType): String = when (type) {
            USER -> "USER"
            MODEL -> "MODEL"
            TOOL_RESULT -> "TOOL_RESULT"
        }

        /**
         * Parses a wire type string.
         *
         * @throws MessageDecodeException for an unknown type (explicit
         *         failure — restore must be able to detect unsupported rows
         *         and report SESSION_UNRECOVERABLE, never silently ignore).
         */
        fun fromWire(value: String): MessageType =
            entries.firstOrNull { wireName(it) == value }
                ?: throw MessageDecodeException("Unknown message type", value)
    }
}

/**
 * Thrown when a durable payload cannot be decoded into a domain [Message].
 *
 * The message is a SAFE category description — it never embeds the raw
 * payloadJson (a tool output may contain secrets; leaking it through a
 * decode error into logs would violate the secret hygiene rule).
 */
class MessageDecodeException(
    val type: String,
    private val detail: String,
) : Exception("Message decode failure [$type]: $detail") {
    /** Safe detail for diagnostics (never the raw payload). */
    val safeDetail: String get() = detail
}

/**
 * Thrown when a domain [Message] cannot be serialized to a durable payload.
 *
 * This indicates a programming error (the serializer must cover all Message
 * subtypes); a well-formed domain message always serializes.
 */
class MessageSerializationException(
    val type: String,
    cause: Throwable? = null,
) : Exception("Message serialization failure [$type]", cause)

/**
 * Pure Kotlin serializer between the AG domain [Message] and its durable
 * persistence payload (a type discriminator + a JSON payload string).
 *
 * Boundary (frozen):
 *  - Persists the AG DOMAIN message, never a provider wire response, never a
 *    TranscriptModel block, never an AgentEvent/RunOutcome.
 *  - NO truncation: a ToolResult's canonical bounded output is saved as-is
 *    (the Tool Layer owns the exact bound).
 *  - NO reasoning/thinking persistence (V1).
 *  - Decoder ignores unknown JSON fields (forward compatibility) but fails
 *    explicitly on missing required fields, malformed JSON, or unknown type.
 *
 * Uses the kotlinx-serialization-json TREE API (JsonElement) — the same
 * dependency the provider modules already use, no compiler plugin.
 */
object MessageSerializer {

    // Lenient on unknown fields (forward compatibility); strict enough that
    // a malformed or type-incompatible payload still fails to decode.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * Serializes a domain [message] into its durable representation.
     *
     * @return the wire type string and the JSON payload.
     * @throws MessageSerializationException on internal inconsistency.
     */
    fun serialize(message: Message): SerializedMessage = when (message) {
        is UserMessage -> SerializedMessage(
            type = MessageType.USER,
            payloadJson = buildJsonObject {
                put("content", message.content)
            }.toString(),
        )

        is ModelMessage -> SerializedMessage(
            type = MessageType.MODEL,
            payloadJson = buildJsonObject {
                put("content", message.content)
                put(
                    "toolCalls",
                    buildJsonArray {
                        message.toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.id)
                                    put("name", call.name)
                                    put("arguments", call.arguments)
                                },
                            )
                        }
                    },
                )
            }.toString(),
        )

        is ToolResultMessage -> SerializedMessage(
            type = MessageType.TOOL_RESULT,
            payloadJson = buildJsonObject {
                put("toolCallId", message.toolCallId)
                put("toolName", message.toolName)
                put("success", message.result.success)
                put("output", message.result.output)
                message.result.error?.let { put("error", it) }
            }.toString(),
        )
    }

    /**
     * Deserializes a durable payload back into the domain [Message].
     *
     * @param type the wire type string (see [MessageType.fromWire]).
     * @param payloadJson the JSON payload.
     * @throws MessageDecodeException for unknown type, malformed JSON, or
     *         missing/wrong-typed required fields. The exception never embeds
     *         the raw payload.
     */
    fun deserialize(type: String, payloadJson: String): Message {
        val messageType = MessageType.fromWire(type)
        return try {
            val element = json.parseToJsonElement(payloadJson)
            val obj = element as? JsonObject
                ?: throw MessageDecodeException(type, "payload is not a JSON object")
            decode(messageType, type, obj)
        } catch (e: MessageDecodeException) {
            throw e
        } catch (e: Exception) {
            // Malformed JSON or structural mismatch. The raw payload is NOT
            // embedded in the exception (secret hygiene).
            throw MessageDecodeException(type, "malformed payload: ${e::class.simpleName}")
        }
    }

    /**
     * Convenience overload for [SerializedMessage].
     */
    fun deserialize(serialized: SerializedMessage): Message =
        deserialize(MessageType.wireName(serialized.type), serialized.payloadJson)

    private fun decode(messageType: MessageType, type: String, obj: JsonObject): Message =
        when (messageType) {
            MessageType.USER -> UserMessage(
                content = obj.stringValue(type, "content"),
            )

            MessageType.MODEL -> {
                val toolCalls = obj["toolCalls"]?.let { array ->
                    (array as? JsonArray)?.map { callElement ->
                        val callObj = callElement as? JsonObject
                            ?: throw MessageDecodeException(type, "toolCall entry is not an object")
                        ToolCall(
                            id = callObj.stringValue(type, "id"),
                            name = callObj.stringValue(type, "name"),
                            arguments = callObj.stringValue(type, "arguments", defaultValue = ""),
                        )
                    } ?: throw MessageDecodeException(type, "toolCalls is not an array")
                } ?: emptyList()
                ModelMessage(
                    content = obj.stringValue(type, "content"),
                    toolCalls = toolCalls,
                )
            }

            MessageType.TOOL_RESULT -> {
                val errorElement = obj["error"]
                val error = when {
                    errorElement == null -> null
                    errorElement is JsonPrimitive && errorElement.isString -> errorElement.content
                    else -> throw MessageDecodeException(type, "error must be a string when present")
                }
                ToolResultMessage(
                    toolCallId = obj.stringValue(type, "toolCallId"),
                    toolName = obj.stringValue(type, "toolName"),
                    result = ToolResult(
                        success = obj.booleanValue(type, "success"),
                        output = obj.stringValue(type, "output"),
                        error = error,
                    ),
                )
            }
        }

    private fun JsonObject.stringValue(
        type: String,
        key: String,
        defaultValue: String? = null,
    ): String {
        val element = this[key] ?: return defaultValue
            ?: throw MessageDecodeException(type, "missing required field '$key'")
        val primitive = element as? JsonPrimitive
            ?: throw MessageDecodeException(type, "field '$key' is not a string")
        if (!primitive.isString) {
            throw MessageDecodeException(type, "field '$key' is not a string")
        }
        return primitive.content
    }

    private fun JsonObject.booleanValue(type: String, key: String): Boolean {
        val element = this[key]
            ?: throw MessageDecodeException(type, "missing required field '$key'")
        val primitive = element as? JsonPrimitive
            ?: throw MessageDecodeException(type, "field '$key' is not a boolean")
        // Boolean literals only: reject "true"/"false" strings so a corrupted
        // payload fails explicitly instead of being reinterpreted.
        return when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> throw MessageDecodeException(type, "field '$key' is not a boolean")
        }
    }
}

/**
 * The durable representation of one message: a type discriminator plus the
 * JSON payload. The Store maps this (plus session id, sequence, id, created
 * timestamp) into a [MessageRecord] row.
 *
 * This type deliberately does NOT carry sequence / sessionId / createdAt —
 * those belong to the Store record mapping, not the domain payload.
 */
data class SerializedMessage(
    val type: MessageType,
    val payloadJson: String,
) {
    /** Wire type string for persistence. */
    val wireName: String get() = MessageType.wireName(type)
}
