package com.androidagent.app.debug

import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AG.DEBUG-0 — protocol parse/encode, event -> debug JSON, redaction.
 * Pure JVM tests; no Android, no socket, no Agent.
 */
class DebugProtocolTest {

    // ------------------------------------------------------------- parse

    @Test
    fun parse_ping() {
        val msg = DebugProtocol.parseClientLine("""{"type":"ping"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.Ping)
    }

    @Test
    fun parse_send() {
        val msg = DebugProtocol.parseClientLine("""{"type":"send","text":"find the app"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.Send)
        assertEquals("find the app", (msg as DebugProtocol.ClientMessage.Send).text)
    }

    @Test
    fun parse_blank_line_is_null() {
        assertNull(DebugProtocol.parseClientLine(""))
        assertNull(DebugProtocol.parseClientLine("   "))
    }

    @Test
    fun parse_malformed_is_null() {
        assertNull(DebugProtocol.parseClientLine("not json"))
        assertNull(DebugProtocol.parseClientLine("""{"type":"unknown"}"""))
    }

    @Test
    fun parse_send_missing_text_is_empty() {
        val msg = DebugProtocol.parseClientLine("""{"type":"send"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.Send)
        assertEquals("", (msg as DebugProtocol.ClientMessage.Send).text)
    }

    // ------------------------- AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION (additive)

    @Test
    fun parse_stop() {
        val msg = DebugProtocol.parseClientLine("""{"type":"stop"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.Stop)
    }

    @Test
    fun parse_new_session() {
        val msg = DebugProtocol.parseClientLine("""{"type":"new_session"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.NewSession)
    }

    @Test
    fun parse_adb_reconnect() {
        val msg = DebugProtocol.parseClientLine("""{"type":"adb_reconnect"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.AdbReconnect)
    }

    @Test
    fun parse_provider_switch() {
        val msg = DebugProtocol.parseClientLine("""{"type":"provider_switch","node":"OpenRouter"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.ProviderSwitch)
        assertEquals("OpenRouter", (msg as DebugProtocol.ClientMessage.ProviderSwitch).node)
    }

    @Test
    fun parse_provider_switch_missing_node_is_empty() {
        val msg = DebugProtocol.parseClientLine("""{"type":"provider_switch"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.ProviderSwitch)
        assertEquals("", (msg as DebugProtocol.ClientMessage.ProviderSwitch).node)
    }

    @Test
    fun parse_snapshot() {
        val msg = DebugProtocol.parseClientLine("""{"type":"snapshot"}""")
        assertTrue(msg is DebugProtocol.ClientMessage.Snapshot)
    }

    // ---- new responses -------------------------------------------------------

    @Test
    fun stop_result_encodes_all_three_mappings() {
        val requested = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.stopResult("requested")).jsonObject
        assertEquals("stop_result", requested["type"]!!.jsonPrimitive.content)
        assertEquals("requested", requested["result"]!!.jsonPrimitive.content)

        val already = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.stopResult("already_interrupting")).jsonObject
        assertEquals("already_interrupting", already["result"]!!.jsonPrimitive.content)

        val none = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.stopResult("no_active_run")).jsonObject
        assertEquals("no_active_run", none["result"]!!.jsonPrimitive.content)
    }

    @Test
    fun new_session_result_encodes_success() {
        val ok = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.newSessionResult(true)).jsonObject
        assertEquals("new_session_result", ok["type"]!!.jsonPrimitive.content)
        assertEquals("true", ok["success"]!!.jsonPrimitive.content)
    }

    @Test
    fun adb_reconnect_result_encodes_ready_and_state() {
        val r = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.adbReconnectResult(true, "READY")).jsonObject
        assertEquals("adb_reconnect_result", r["type"]!!.jsonPrimitive.content)
        assertEquals("true", r["ready"]!!.jsonPrimitive.content)
        assertEquals("READY", r["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun provider_switch_result_applied_carries_safe_endpoint() {
        val r = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.providerSwitchResult(true, "OpenRouter", "http://127.0.0.1:18080/v1/chat/completions")
        ).jsonObject
        assertEquals("provider_switch_result", r["type"]!!.jsonPrimitive.content)
        assertEquals("true", r["success"]!!.jsonPrimitive.content)
        assertEquals("OpenRouter", r["node"]!!.jsonPrimitive.content)
        // The endpoint is the SAFE endpoint (scheme+host+port+path) — no secret.
        assertEquals("http://127.0.0.1:18080/v1/chat/completions",
            r["endpoint"]!!.jsonPrimitive.content)
    }

    @Test
    fun provider_switch_result_rejected_carries_reason() {
        val r = kotlinx.serialization.json.Json.parseToJsonElement(
            DebugProtocol.providerSwitchResult(false, "EMG", reason = "EMG config not provisioned")
        ).jsonObject
        assertEquals("false", r["success"]!!.jsonPrimitive.content)
        assertEquals("EMG config not provisioned", r["reason"]!!.jsonPrimitive.content)
    }

    // ---- old messages still parse (regression) --------------------------------

    @Test
    fun old_messages_still_parse_after_additive_extension() {
        // The additive extension must NOT break the existing wire shape.
        assertTrue(DebugProtocol.parseClientLine("""{"type":"ping"}""")
            is DebugProtocol.ClientMessage.Ping)
        assertTrue(DebugProtocol.parseClientLine(
            """{"type":"session_open","backend":"adb"}""")
            is DebugProtocol.ClientMessage.SessionOpen)
        assertTrue(DebugProtocol.parseClientLine(
            """{"type":"bootstrap_state"}""")
            is DebugProtocol.ClientMessage.BootstrapState)
        // Unknown type still yields null (no accidental match).
        assertNull(DebugProtocol.parseClientLine("""{"type":"totally_unknown"}"""))
    }

    // ------------------------------------------------------------- encode

    @Test
    fun hello_carries_protocol_id() {
        val hello = DebugProtocol.hello()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(hello).jsonObject
        assertEquals("hello", root["type"]!!.jsonPrimitive.content)
        assertEquals("ag-debug-0", root["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun accepted_busy_pong_error_are_json_lines() {
        assertEquals("""{"type":"accepted"}""", DebugProtocol.accepted())
        assertEquals("""{"type":"busy"}""", DebugProtocol.busy())
        assertEquals("""{"type":"pong"}""", DebugProtocol.pong())
        val err = kotlinx.serialization.json.Json.parseToJsonElement(DebugProtocol.error("bad")).jsonObject
        assertEquals("error", err["type"]!!.jsonPrimitive.content)
        assertEquals("bad", err["message"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------- event -> json

    @Test
    fun tool_call_event_projects_name_and_arguments() {
        val call = ToolCall("c1", "android_shell", """{"command":"id"}""")
        val json = DebugProtocol.eventToJson(AgentEvent.ToolCallStarted(call))
        assertEquals("tool_call", json["type"]!!.jsonPrimitive.content)
        assertEquals("android_shell", json["tool"]!!.jsonPrimitive.content)
        assertEquals("""{"command":"id"}""", json["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun tool_result_event_projects_success_and_output() {
        val call = ToolCall("c1", "android_shell", "{}")
        val result = ToolResult(success = true, output = "uid=2000(shell)\n")
        val json = DebugProtocol.eventToJson(AgentEvent.ToolCallFinished(call, result))
        assertEquals("tool_result", json["type"]!!.jsonPrimitive.content)
        assertEquals("true", json["success"]!!.jsonPrimitive.content)
        assertTrue(json["output"]!!.jsonPrimitive.content.contains("uid=2000"))
    }

    @Test
    fun final_answer_event_projects_content() {
        val json = DebugProtocol.eventToJson(AgentEvent.AnswerProduced("package: com.x"))
        assertEquals("final_answer", json["type"]!!.jsonPrimitive.content)
        assertEquals("package: com.x", json["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun run_finished_projects_outcome() {
        val json = DebugProtocol.eventToJson(AgentEvent.RunFinished(RunOutcome.Answer("ok")))
        assertEquals("run_finished", json["type"]!!.jsonPrimitive.content)
        assertNotNull(json["outcome"])
    }

    @Test
    fun model_turn_finished_projects_visible_content_when_present() {
        val turn = ModelTurn.FinalAnswer("answer text")
        val json = DebugProtocol.eventToJson(AgentEvent.ModelTurnFinished(1, turn))
        assertEquals("model_turn_end", json["type"]!!.jsonPrimitive.content)
        assertEquals("final_answer", json["turnType"]!!.jsonPrimitive.content)
        assertEquals("answer text", json["content"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------- redaction

    @Test
    fun redaction_masks_secret_in_event_json() {
        val sentinel = "SECRET_SENTINEL"
        // Tool result carrying an Authorization header must be redacted.
        val call = ToolCall("c1", "android_shell", """{"command":"env"}""")
        val result = ToolResult(success = true, output = "Authorization: Bearer $sentinel")
        val json = DebugProtocol.eventToJson(AgentEvent.ToolCallFinished(call, result))
        val rendered = json.toString()
        assertFalse(rendered.contains(sentinel), "sentinel must not appear")
        assertTrue(rendered.contains("&lt;redacted&gt;") || rendered.contains("<redacted>"))
    }

    @Test
    fun final_answer_secret_redacted() {
        val sentinel = "sk-or-DEBUG_SENTINEL"
        val json = DebugProtocol.eventToJson(AgentEvent.AnswerProduced("key=$sentinel"))
        assertFalse(json.toString().contains(sentinel))
    }
}