package com.androidagent.app.debug

import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * AG.DEBUG-0 protocol helpers (pure, Android-free so they are JVM-testable).
 *
 * Wire format: TCP + UTF-8 JSON Lines. One message per line. No third-party
 * dependency beyond kotlinx-serialization (already a provider dependency).
 *
 * This object owns message parsing/encoding and the [AgentEvent] -> JSON
 * projection. It does NOT own sockets, threads, or the AgentRuntime.
 */
object DebugProtocol {

    const val PROTOCOL_ID: String = "ag-debug-0"

    /** Client -> device messages. */
    sealed interface ClientMessage {
        /** {"type":"ping"} */
        data object Ping : ClientMessage

        /** {"type":"send","text":"..."} */
        data class Send(val text: String) : ClientMessage
    }

    /** Parses one JSONL line from the client. Returns null on empty/blank/malformed. */
    fun parseClientLine(line: String): ClientMessage? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val root = try {
            Json.parseToJsonElement(trimmed).jsonObject
        } catch (_: Exception) {
            return null
        }
        val type = root["type"]?.let { (it as? JsonPrimitive)?.content }
        return when (type) {
            "ping" -> ClientMessage.Ping
            "send" -> {
                val text = root["text"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                ClientMessage.Send(text)
            }
            else -> null
        }
    }

    /** Encodes a device -> client message as one JSONL line (no trailing newline). */
    fun encode(obj: JsonObject): String = obj.toString()

    // ---------------------------------------------------------- device messages

    fun hello(): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("hello"))
            put("protocol", JsonPrimitive(PROTOCOL_ID))
        },
    )

    fun accepted(): String = encode(buildJsonObject { put("type", JsonPrimitive("accepted")) })

    fun busy(): String = encode(buildJsonObject { put("type", JsonPrimitive("busy")) })

    fun pong(): String = encode(buildJsonObject { put("type", JsonPrimitive("pong")) })

    fun error(message: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("error"))
            put("message", JsonPrimitive(message))
        },
    )

    /**
     * Projects one [AgentEvent] to the debug execution-stream JSON. All content
     * passes through [TraceFormatter.redact] before emission.
     */
    fun eventToJson(event: AgentEvent): JsonObject = buildJsonObject {
        when (event) {
            is AgentEvent.RunStarted -> {
                put("type", JsonPrimitive("run_start"))
                put("sessionId", JsonPrimitive(event.sessionId))
            }

            is AgentEvent.ModelTurnStarted -> {
                put("type", JsonPrimitive("model_turn_start"))
                put("step", JsonPrimitive(event.step))
            }

            is AgentEvent.ModelTurnFinished -> {
                put("type", JsonPrimitive("model_turn_end"))
                put("step", JsonPrimitive(event.step))
                put("turnType", JsonPrimitive(turnTypeOf(event.turn)))
                event.turn.contentOrNull()?.let {
                    put("content", JsonPrimitive(redact(it)))
                }
            }

            is AgentEvent.ToolCallStarted -> {
                put("type", JsonPrimitive("tool_call"))
                put("tool", JsonPrimitive(event.call.name))
                put("arguments", JsonPrimitive(redact(event.call.arguments)))
            }

            is AgentEvent.ToolCallFinished -> {
                put("type", JsonPrimitive("tool_result"))
                put("tool", JsonPrimitive(event.call.name))
                put("success", JsonPrimitive(event.result.success))
                put("output", JsonPrimitive(redact(event.result.output)))
                event.result.error?.let {
                    put("error", JsonPrimitive(redact(it)))
                }
            }

            is AgentEvent.AnswerProduced -> {
                put("type", JsonPrimitive("final_answer"))
                put("content", JsonPrimitive(redact(event.content)))
            }

            is AgentEvent.RunFailed -> {
                put("type", JsonPrimitive("run_failed"))
                put("failure", JsonPrimitive(redact(failureOf(event.failure))))
            }

            is AgentEvent.RunFinished -> {
                put("type", JsonPrimitive("run_finished"))
                put("outcome", JsonPrimitive(event.outcome.toString()))
            }
        }
    }

    /** Convenience for building a simple reply JSON object. */
    fun reply(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(build)

    private fun turnTypeOf(turn: ModelTurn): String =
        if (turn is ModelTurn.FinalAnswer) "final_answer" else "tool_calls"

    private fun ModelTurn.contentOrNull(): String? = when (this) {
        is ModelTurn.FinalAnswer -> content
        is ModelTurn.ToolCalls -> content.ifEmpty { null }
    }

    private fun failureOf(failure: RunFailure): String = when (failure) {
        is RunFailure.ProviderFailure -> "provider_failure: ${failure.cause}"
        is RunFailure.MaxStepsReached -> "max_steps_reached: ${failure.maxSteps}"
        is RunFailure.SessionBusy -> "session_busy"
        is RunFailure.RuntimeFailure -> "runtime_failure: ${failure.cause}"
    }

    private fun redact(s: String): String = TraceFormatter.redact(s)
}