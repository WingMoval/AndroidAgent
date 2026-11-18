package com.androidagent.app

import com.androidagent.integration.at.AtAdbBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AG.UI-SHELL-1 鈥?JVM tests for the workspace shell's pure presentation mappings.
 *
 * These exercise the real top-level mapping functions used by ChatActivity:
 *
 *  - [workspaceActionSlot]   : busy state -> Send vs Stop in the SAME action slot
 *  - [workspaceAdbIndicator] : ADB state  -> header indicator (color + accessible text)
 *  - [workspaceModelLabel]   : active model -> composer label (REAL value only, no fake effort)
 *
 * They guard the "no lying about state" rules from the design SSOT, on the JVM
 * without an Activity / Robolectric.
 */
class WorkspaceShellStateTest {

    // ----------------------------------------------------------------- Send/Stop

    @Test
    fun idleShowsSendHidesStop() {
        val slot = workspaceActionSlot(busy = false)
        assertTrue(slot.sendVisible)
        assertFalse(slot.stopVisible)
    }

    @Test
    fun runningShowsStopHidesSend() {
        val slot = workspaceActionSlot(busy = true)
        assertFalse(slot.sendVisible)
        assertTrue(slot.stopVisible)
    }

    @Test
    fun slotIsAlwaysExclusive() {
        val idle = workspaceActionSlot(false)
        val running = workspaceActionSlot(true)
        assertTrue(idle.sendVisible != idle.stopVisible)
        assertTrue(running.sendVisible != running.stopVisible)
    }

    // ----------------------------------------------------------------- ADB indicator

    @Test
    fun adbReadyMapsToConnected() {
        val ind = workspaceAdbIndicator(atStatusText = "Connected", bootstrapCode = AtAdbBootstrap.READY.name)
        assertEquals(R.color.ws_adb_ready, ind.colorRes)
        assertEquals(R.string.ws_adb_connected, ind.textRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun adbConnectingIsTransient() {
        val ind = workspaceAdbIndicator(atStatusText = "Connecting...", bootstrapCode = AtAdbBootstrap.READY.name)
        assertEquals(R.color.ws_adb_connecting, ind.colorRes)
        assertEquals(R.string.ws_adb_connecting, ind.textRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun adbPairingRequiredMapsToDisconnectedWithPairOffered() {
        val ind = workspaceAdbIndicator(
            atStatusText = "Pairing required",
            bootstrapCode = AtAdbBootstrap.PAIRING_REQUIRED.name,
        )
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertEquals(R.string.ws_adb_disconnected, ind.textRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun adbConnectFailedMapsToDisconnectedWithPairOffered() {
        val ind = workspaceAdbIndicator(
            atStatusText = "Not connected",
            bootstrapCode = AtAdbBootstrap.CONNECT_FAILED.name,
        )
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun adbNoShellYetIsDisconnected() {
        val ind = workspaceAdbIndicator(atStatusText = null, bootstrapCode = null)
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertEquals(R.string.ws_adb_disconnected, ind.textRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun adbIndicatorAlwaysCarriesAccessibleText() {
        // Color is never the only signal: every mapping sets a non-zero text resource.
        val states = listOf(
            workspaceAdbIndicator("Connected", AtAdbBootstrap.READY.name),
            workspaceAdbIndicator("Connecting...", AtAdbBootstrap.READY.name),
            workspaceAdbIndicator("Pairing required", AtAdbBootstrap.PAIRING_REQUIRED.name),
            workspaceAdbIndicator("Not connected", AtAdbBootstrap.CONNECT_FAILED.name),
            workspaceAdbIndicator(null, null),
        )
        states.forEach { assertTrue(it.textRes != 0) }
    }

    // ----------------------------------------------------------------- model label
    //
    // AG.PROVIDER-CONFIGURED-MODELS-0: the home composer shows the SHORT display
    // name (alias, else the modelId's provider prefix), never the full long id.

    @Test
    fun modelLabelShowsModelSuffixWhenNoAlias() {
        // AG.PROVIDER-AWARE-MODEL-UX-0: default = substringAfterLast("/")
        assertEquals("deepseek-v4-flash-0731", workspaceModelLabel("deepseek/deepseek-v4-flash-0731"))
    }

    @Test
    fun modelLabelShowsModelIdWhenNoSlash() {
        // A model id without "/" has no provider prefix, so it is shown as-is.
        assertEquals("deepseek-v4-flash-0731", workspaceModelLabel("deepseek-v4-flash-0731"))
    }

    @Test
    fun modelLabelNeverFakesEffort() {
        // No "High"/"Medium"/"Low" effort is invented for the display name.
        val label = workspaceModelLabel("gpt-5.6")
        assertEquals("gpt-5.6", label)
        assertFalse(label.contains("High"))
        assertFalse(label.contains("Medium"))
        assertFalse(label.contains("Low"))
    }

    @Test
    fun modelLabelFallsBackToPlaceholderWhenAbsent() {
        assertEquals("Model", workspaceModelLabel(null))
        assertEquals("Model", workspaceModelLabel("   "))
    }
}
