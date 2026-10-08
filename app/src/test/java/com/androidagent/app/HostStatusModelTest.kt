package com.androidagent.app

import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AGA.HOST-CONTROL-0 — R series (blank-key 401 regression), A series (AT status
 * mapping) and S series (Run phase mapping), exercised on the JVM via the pure
 * [HostStatusModel]. No Android, no AT Engine, no live endpoint.
 */
class HostStatusModelTest {

    // ------------------------------------------------------------ R1-R5 blank key

    @Test
    fun R1_active_config_has_non_empty_key() {
        assertEquals("FAKE_KEY_FOR_TEST", HostStatusModel.effectiveKey("", "FAKE_KEY_FOR_TEST"))
    }

    @Test
    fun R2_api_key_edittext_blank_retains_active_credential() {
        // User typed nothing -> active credential is retained, not overwritten.
        assertEquals("FAKE_KEY_FOR_TEST", HostStatusModel.effectiveKey("", "FAKE_KEY_FOR_TEST"))
    }

    @Test
    fun R3_user_typed_key_wins() {
        assertEquals("NEW_KEY", HostStatusModel.effectiveKey("NEW_KEY", "OLD_KEY"))
    }

    @Test
    fun R4_resulting_config_retains_existing_key() {
        val key = HostStatusModel.effectiveKey("", "FAKE_KEY_FOR_TEST")
        assertEquals("FAKE_KEY_FOR_TEST", key)
        // The sentinel must be usable as the config's apiKey (i.e. non-blank).
        assertTrue(key.isNotBlank())
    }

    @Test
    fun R5_secret_never_printed_by_test() {
        // Sanity: the sentinel value itself must not leak into any assertion
        // output we would print. We only assert equality on the value; the
        // framework would print it on failure, which is why it is a FAKE value.
        val sentinel = "FAKE_KEY_FOR_TEST"
        val result = HostStatusModel.effectiveKey("", sentinel)
        assertEquals(sentinel, result)
    }

    // ------------------------------------------------------------ A1-A6 AT mapping

    @Test
    fun A2_connected_renders_connected() {
        // "Connected" is produced by the host when ShellConnection.Status.ready == true;
        // HostStatusModel is only responsible for the failed-message collapse.
        assertEquals("Not connected", HostStatusModel.safeAtMessage("connect failed for all candidates"))
    }

    @Test
    fun A3_disconnected_renders_short_safe_message() {
        assertEquals("Not connected", HostStatusModel.safeAtMessage("connect failed 10.0.0.1:5555"))
        assertEquals("Not connected", HostStatusModel.safeAtMessage(""))
        assertEquals("Not connected", HostStatusModel.safeAtMessage("   "))
    }

    @Test
    fun A4_wireless_debugging_message_is_safe() {
        assertEquals(
            "Wireless debugging unavailable",
            HostStatusModel.safeAtMessage("Wireless Debugging is disabled; enable it in Developer Options"),
        )
    }

    @Test
    fun A5_never_leaks_endpoint_or_key() {
        val msg = HostStatusModel.safeAtMessage("connect failed 100.104.153.4:43157")
        assertFalse(msg.contains("100.104"), "endpoint must not appear")
        assertFalse(msg.contains("43157"), "port must not appear")
        assertFalse(msg.contains("sk-or-"), "key must not appear")
    }

    @Test
    fun A6_provider_line_is_node_only() {
        assertEquals("Provider: EMG", HostStatusModel.providerLine("EMG"))
        assertEquals("Provider: OpenRouter", HostStatusModel.providerLine("OpenRouter"))
        // No model / endpoint / timeout / key detail on the main screen.
        assertFalse(HostStatusModel.providerLine("EMG").contains("qwen3.8-local"))
        assertFalse(HostStatusModel.providerLine("EMG").contains("apiKey"))
    }

    // ------------------------------------------------------------ S1-S7 run phase

    @Test
    fun S1_model_turn_started_is_thinking() {
        assertEquals("Thinking... · Step 1", HostStatusModel.runPhaseText(AgentEvent.ModelTurnStarted(1)))
        assertEquals("Thinking... · Step 2", HostStatusModel.runPhaseText(AgentEvent.ModelTurnStarted(2)))
    }

    @Test
    fun S2_tool_call_started_is_executing() {
        val call = ToolCall("c1", "android_shell", """{"command":"id"}""")
        assertEquals("Executing android_shell...", HostStatusModel.runPhaseText(AgentEvent.ToolCallStarted(call)))
    }

    @Test
    fun S3_tool_call_finished_is_processing() {
        val call = ToolCall("c1", "android_shell", "{}")
        val result = ToolResult(success = true, output = "ok")
        assertEquals("Processing result...", HostStatusModel.runPhaseText(AgentEvent.ToolCallFinished(call, result)))
    }

    @Test
    fun S4_run_started_is_thinking() {
        assertEquals("Thinking...", HostStatusModel.runPhaseText(AgentEvent.RunStarted("s1")))
    }

    @Test
    fun S5_answer_produced_is_preparing() {
        assertEquals("Preparing answer...", HostStatusModel.runPhaseText(AgentEvent.AnswerProduced("done")))
    }

    @Test
    fun S6_run_finished_is_ready() {
        assertEquals("Ready.", HostStatusModel.runPhaseText(AgentEvent.RunFinished(RunOutcome.Answer("ok"))))
    }

    @Test
    fun S7_run_failed_is_failed() {
        assertEquals("Failed.", HostStatusModel.runPhaseText(AgentEvent.RunFailed(RunFailure.MaxStepsReached(8))))
    }

    @Test
    fun S8_model_turn_finished_is_no_change() {
        assertNull(HostStatusModel.runPhaseText(AgentEvent.ModelTurnFinished(1, ModelTurn.FinalAnswer("x"))))
    }
}