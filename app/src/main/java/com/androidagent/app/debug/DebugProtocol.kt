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

        /**
         * AG.NETWORK-0 L2 Direct Tool Validation:
         * {"type":"tool_invoke","name":"network","arguments":"{...}"}
         * Invokes the named production tool directly (no LLM).
         */
        data class ToolInvoke(val name: String, val arguments: String) : ClientMessage

        /**
         * AG.V2-2B: AT persistent session operations (debug-only).
         * {"type":"session_open","backend":"adb|local"}
         */
        data class SessionOpen(val backend: String) : ClientMessage

        /**
         * {"type":"session_write","id":1,"text":"...","ctrlc":false}
         */
        data class SessionWrite(val id: Int, val text: String, val ctrlc: Boolean) : ClientMessage

        /**
         * {"type":"session_read","id":1,"timeoutMs":2000}
         */
        data class SessionRead(val id: Int, val timeoutMs: Int) : ClientMessage

        /**
         * {"type":"session_resize","id":1,"rows":24,"cols":100}
         */
        data class SessionResize(val id: Int, val rows: Int, val cols: Int) : ClientMessage

        /**
         * {"type":"session_close","id":1}
         */
        data class SessionClose(val id: Int) : ClientMessage

        /**
         * {"type":"session_state","id":1}
         */
        data class SessionState(val id: Int) : ClientMessage

        /**
         * AG.V2-2: Query ADB consumer bootstrap state.
         * {"type":"bootstrap_state"}
         */
        data object BootstrapState : ClientMessage

        /**
         * AG.V2-2: Discover pairing service.
         * {"type":"bootstrap_discover"}
         */
        data object BootstrapDiscover : ClientMessage

        /**
         * AG.V2-2: Pair with 6-digit code (auto-discovered endpoint).
         * {"type":"bootstrap_pair","code":"123456"}
         */
        data class BootstrapPair(val code: String) : ClientMessage

        // ---------------------------------------------------- AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION
        // Additive extension. Old messages (ping / send / tool_invoke /
        // session_* / bootstrap_*) keep their exact wire shape and behavior.

        /**
         * Interrupt the active Run through the shared HostActions.stop
         * (AgentRuntime.interruptActiveRun).
         * {"type":"stop"}
         */
        data object Stop : ClientMessage

        /**
         * Start a new durable Session through the shared HostActions.newSession
         * (SessionHolder.newSession -> AgentRuntime.newSession).
         * {"type":"new_session"}
         */
        data object NewSession : ClientMessage

        /**
         * Re-run the ADB connect flow through the shared HostActions.adbReconnect
         * (ShellConnection.connect -> integration-at).
         * {"type":"adb_reconnect"}
         */
        data object AdbReconnect : ClientMessage

        /**
         * Switch the active provider node through the shared
         * HostActions.providerSwitch (resolveProviderSwitch + applyConfig).
         * Only an existing, valid ProviderNode identity is accepted.
         * {"type":"provider_switch","node":"OpenRouter"}
         */
        data class ProviderSwitch(val node: String) : ClientMessage

        /**
         * Read-only automation state (HostActions.snapshot). SECRET-FREE.
         * {"type":"snapshot"}
         */
        data object Snapshot : ClientMessage
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
            "tool_invoke" -> {
                val name = root["name"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                val arguments = root["arguments"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                ClientMessage.ToolInvoke(name, arguments)
            }
            "session_open" -> {
                val backend = root["backend"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                ClientMessage.SessionOpen(backend)
            }
            "session_write" -> {
                val id = root["id"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: -1
                val text = root["text"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                val ctrlc = root["ctrlc"]?.let { (it as? JsonPrimitive)?.content?.toBoolean() } ?: false
                ClientMessage.SessionWrite(id, text, ctrlc)
            }
            "session_read" -> {
                val id = root["id"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: -1
                val timeoutMs = root["timeoutMs"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 2000
                ClientMessage.SessionRead(id, timeoutMs)
            }
            "session_resize" -> {
                val id = root["id"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: -1
                val rows = root["rows"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 24
                val cols = root["cols"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 80
                ClientMessage.SessionResize(id, rows, cols)
            }
            "session_close" -> {
                val id = root["id"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: -1
                ClientMessage.SessionClose(id)
            }
            "session_state" -> {
                val id = root["id"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: -1
                ClientMessage.SessionState(id)
            }
            "bootstrap_state" -> ClientMessage.BootstrapState
            "bootstrap_discover" -> ClientMessage.BootstrapDiscover
            "bootstrap_pair" -> {
                val code = root["code"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                ClientMessage.BootstrapPair(code)
            }
            // ---- AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION (additive)
            "stop" -> ClientMessage.Stop
            "new_session" -> ClientMessage.NewSession
            "adb_reconnect" -> ClientMessage.AdbReconnect
            "provider_switch" -> {
                val node = root["node"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                ClientMessage.ProviderSwitch(node)
            }
            "snapshot" -> ClientMessage.Snapshot
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
     * AG.NETWORK-0 L2: the full ToolResult of a direct tool invocation, so the raw
     * fetch can be judged with zero model tokens. `output` is the tool's JSON
     * payload (redacted before emission, like every other stream).
     */
    fun toolResult(tool: String, success: Boolean, output: String, error: String?): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("tool_result_direct"))
            put("tool", JsonPrimitive(tool))
            put("success", JsonPrimitive(success))
            put("output", JsonPrimitive(redact(output)))
            if (error != null) {
                put("error", JsonPrimitive(redact(error)))
            }
        },
    )

    // ------------------------------------------------- AG.V2-2B session messages

    fun sessionOpened(id: Int, backend: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_opened"))
            put("id", JsonPrimitive(id))
            put("backend", JsonPrimitive(backend))
        },
    )

    fun sessionOpenFailed(backend: String, reason: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_open_failed"))
            put("backend", JsonPrimitive(backend))
            put("reason", JsonPrimitive(reason))
        },
    )

    fun sessionWriteResult(id: Int, bytesWritten: Int): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_write_result"))
            put("id", JsonPrimitive(id))
            put("bytesWritten", JsonPrimitive(bytesWritten))
        },
    )

    fun sessionData(id: Int, text: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_data"))
            put("id", JsonPrimitive(id))
            put("data", JsonPrimitive(redact(text)))
        },
    )

    fun sessionResized(id: Int, rows: Int, cols: Int, ok: Boolean): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_resized"))
            put("id", JsonPrimitive(id))
            put("rows", JsonPrimitive(rows))
            put("cols", JsonPrimitive(cols))
            put("ok", JsonPrimitive(ok))
        },
    )

    fun sessionClosed(id: Int): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_closed"))
            put("id", JsonPrimitive(id))
        },
    )

    fun sessionState(id: Int, state: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("session_state"))
            put("id", JsonPrimitive(id))
            put("state", JsonPrimitive(state))
        },
    )

    // ------------------------------------------------- AG.V2-2 bootstrap messages

    fun bootstrapState(state: String, hasIdentity: Boolean): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("bootstrap_state"))
            put("state", JsonPrimitive(state))
            put("hasIdentity", JsonPrimitive(hasIdentity))
        },
    )

    fun bootstrapDiscovery(endpoints: Int, serviceNotFound: Boolean, multiple: Boolean): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("bootstrap_discovery"))
            put("endpoints", JsonPrimitive(endpoints))
            put("serviceNotFound", JsonPrimitive(serviceNotFound))
            put("multiple", JsonPrimitive(multiple))
        },
    )

    fun bootstrapPairResult(success: Boolean, outcome: String, error: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("bootstrap_pair_result"))
            put("success", JsonPrimitive(success))
            put("outcome", JsonPrimitive(outcome))
            if (error.isNotEmpty()) put("error", JsonPrimitive(error))
        },
    )

    // ------------------------------------- AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION responses

    /**
     * {"type":"stop_result","result":"requested|already_interrupting|no_active_run"}
     * Faithful mapping of AgentRuntime.InterruptResult.
     */
    fun stopResult(result: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("stop_result"))
            put("result", JsonPrimitive(result))
        },
    )

    /**
     * {"type":"new_session_result","success":true|false}
     */
    fun newSessionResult(success: Boolean): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("new_session_result"))
            put("success", JsonPrimitive(success))
        },
    )

    /**
     * {"type":"adb_reconnect_result","ready":true|false,"state":"READY|..."}
     */
    fun adbReconnectResult(ready: Boolean, state: String): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("adb_reconnect_result"))
            put("ready", JsonPrimitive(ready))
            put("state", JsonPrimitive(state))
        },
    )

    /**
     * {"type":"provider_switch_result","success":true|false,
     *  "node":"...","endpoint":"<safe endpoint>","reason":"..."}
     * endpoint is the existing SAFE endpoint (no secret).
     */
    fun providerSwitchResult(success: Boolean, node: String, endpoint: String? = null, reason: String? = null): String = encode(
        buildJsonObject {
            put("type", JsonPrimitive("provider_switch_result"))
            put("success", JsonPrimitive(success))
            put("node", JsonPrimitive(node))
            if (endpoint != null) put("endpoint", JsonPrimitive(endpoint))
            if (reason != null) put("reason", JsonPrimitive(reason))
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

    // ---------------------------------------------------- model stream messages

    /**
     * Projects one model-stream observation to debug JSONL. This is the minimal
     * incremental extension of the v0 protocol: new message types, unchanged
     * framing. Older clients ignore unknown types safely.
     */
    fun streamToJson(kind: String, text: String? = null): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(kind))
        if (text != null) {
            put("text", JsonPrimitive(redact(text)))
        }
    }

    /** Stream message kinds, shared with the bridge. */
    const val STREAM_REASONING_STARTED: String = "reasoning_started"
    const val STREAM_REASONING_DELTA: String = "reasoning_delta"
    const val STREAM_REASONING_FINISHED: String = "reasoning_finished"
    const val STREAM_CONTENT_STARTED: String = "content_started"
    const val STREAM_CONTENT_DELTA: String = "content_delta"
    const val STREAM_CONTENT_FINISHED: String = "content_finished"

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
        is RunFailure.DurabilityFailure -> "durability_failure: ${failure.boundary}: ${failure.cause}"
    }

    private fun redact(s: String): String = TraceFormatter.redact(s)
}