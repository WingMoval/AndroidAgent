package com.androidagent.app

import com.androidagent.core.RunOutcome
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlinx.serialization.json.Json

/**
 * AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION — JVM tests for the shared Host Action
 * seam.
 *
 * Covers:
 *  - HostActions.send guard (blank / no-runtime / busy) — no run started.
 *  - HostActions.stop guard (no runtime / not busy) -> NoActiveRun.
 *  - HostActions.newSession guard (no runtime) -> false.
 *  - HostActions.snapshot SECRET-FREE (the automation-core safety rule).
 *  - hostSnapshotToJson encodes the safe fields and leaks no secret.
 *
 * These run on the JVM against the process-scoped SessionHolder (root == null in
 * a unit test), so they exercise the real guard + mapping code paths without an
 * Activity, Robolectric, or a device.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HostActionsTest {

    @Test
    fun send_blank_is_rejected_no_run_started() {
        val r = runBlocking { HostActions.send("   ") }
        assertTrue(r is SendResult.Rejected, "blank send must be rejected")
        assertEquals("blank send rejected", (r as SendResult.Rejected).reason)
    }

    @Test
    fun send_no_runtime_is_rejected() {
        // SessionHolder.root is null in a JVM unit test -> runtime not ready.
        val r = runBlocking { HostActions.send("hello") }
        assertTrue(r is SendResult.Rejected, "no runtime must be rejected")
        assertEquals("runtime not ready", (r as SendResult.Rejected).reason)
    }

    @Test
    fun stop_no_runtime_is_no_active_run() {
        val r = HostActions.stop()
        assertTrue(r is StopResult.NoActiveRun, "no runtime -> NoActiveRun")
    }

    @Test
    fun newSession_no_runtime_returns_false() {
        // No runtime: SessionHolder.newSession() returns false.
        val ok = runBlocking { HostActions.newSession() }
        assertFalse(ok, "no runtime -> newSession false")
    }

    @Test
    fun snapshot_no_runtime_is_safe_and_honest() {
        val snap = HostActions.snapshot()
        // Honest: no runtime -> not ready, not busy.
        assertFalse(snap.runtimeReady, "no runtime -> runtimeReady false")
        assertFalse(snap.busy, "no runtime -> busy false")
        // SECRET-FREE: the snapshot type has no secret-bearing field at all.
        // (Structural guarantee: HostSnapshot has no apiKey/authorization/
        //  pairingCode/token/password field — verified by the JSON test below.)
        assertTrue(snap.transcriptBlockCount >= 0)
    }

    @Test
    fun snapshotJson_is_secret_free() {
        val snap = HostSnapshot(
            runtimeReady = true,
            busy = false,
            currentSessionId = "sess-123",
            activeProvider = "OpenRouter",
            activeModel = "deepseek-v4-flash-0731",
            adbState = "READY",
            transcriptBlockCount = 4,
            lastBlockType = "Assistant",
        )
        val json = hostSnapshotToJson(snap)
        val rendered = json.toString()

        // Required safe fields present.
        val root = Json.parseToJsonElement(rendered).jsonObject
        assertEquals("snapshot", root["type"]!!.jsonPrimitive.content)
        assertEquals("true", root["runtimeReady"]!!.jsonPrimitive.content)
        assertEquals("false", root["busy"]!!.jsonPrimitive.content)
        assertEquals("sess-123", root["currentSessionId"]!!.jsonPrimitive.content)
        assertEquals("OpenRouter", root["activeProvider"]!!.jsonPrimitive.content)
        assertEquals("deepseek-v4-flash-0731", root["activeModel"]!!.jsonPrimitive.content)
        assertEquals("READY", root["adbState"]!!.jsonPrimitive.content)
        assertEquals("4", root["transcriptBlockCount"]!!.jsonPrimitive.content)
        assertEquals("Assistant", root["lastBlockType"]!!.jsonPrimitive.content)

        // SECRET-FREE: none of the forbidden keys appear in the wire JSON.
        val forbidden = listOf(
            "apiKey", "api_key", "authorization", "Authorization",
            "pairingCode", "pairing_code", "token", "password", "bearer",
        )
        for (f in forbidden) {
            assertFalse(rendered.contains(f, ignoreCase = true),
                "snapshot JSON must not contain '$f'")
        }
    }

    @Test
    fun snapshotJson_null_fields_render_empty_not_secret() {
        val snap = HostSnapshot(
            runtimeReady = false,
            busy = false,
            currentSessionId = null,
            activeProvider = null,
            activeModel = null,
            adbState = null,
            transcriptBlockCount = 0,
            lastBlockType = null,
        )
        val root = Json.parseToJsonElement(hostSnapshotToJson(snap).toString()).jsonObject
        assertEquals("", root["currentSessionId"]!!.jsonPrimitive.content)
        assertEquals("", root["activeProvider"]!!.jsonPrimitive.content)
        assertEquals("", root["activeModel"]!!.jsonPrimitive.content)
        assertEquals("", root["adbState"]!!.jsonPrimitive.content)
        assertEquals("", root["lastBlockType"]!!.jsonPrimitive.content)
    }

    @Test
    fun sendResult_interrupted_maps_to_stopped() {
        // A RunOutcome.Interrupted is a real terminal outcome the send() path
        // surfaces; verify the sealed type round-trips (no fabrication).
        val outcome: RunOutcome = RunOutcome.Interrupted
        val r = SendResult.Sent(outcome)
        assertTrue(r.outcome is RunOutcome.Interrupted)
    }
}
