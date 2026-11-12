package com.androidagent.app

import com.androidagent.integration.at.AtAdbBootstrap
import com.androidagent.integration.at.AtPairingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AG.ADB-CONNECTION-UX-1R: Dedicated state presentation mapping tests.
 *
 * Tests the pure functions in AdbUiPresentation.kt:
 *   - adbIndicator()          : AT state → header/sidebar indicator
 *   - adbManagementPresentation() : AT state → management surface buttons
 *   - adbPairingResultMessage()   : pairing outcome → message string
 */
class AdbUiPresentationTest {

    // -----------------------------------------------------------------------
    // adbIndicator — header + sidebar indicator
    // -----------------------------------------------------------------------

    @Test
    fun `READY maps to green indicator`() {
        val ind = adbIndicator(null, AtAdbBootstrap.READY.name)
        assertEquals(R.color.ws_adb_ready, ind.colorRes)
        assertEquals(R.string.ws_adb_connected, ind.textRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun `PAIRING_REQUIRED maps to disconnected gray with pair offer`() {
        val ind = adbIndicator(null, AtAdbBootstrap.PAIRING_REQUIRED.name)
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun `CONNECT_FAILED maps to disconnected gray with pair offer`() {
        val ind = adbIndicator(null, AtAdbBootstrap.CONNECT_FAILED.name)
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun `Connecting text maps to connecting orange`() {
        val ind = adbIndicator("Connecting...", AtAdbBootstrap.READY.name)
        assertEquals(R.color.ws_adb_connecting, ind.colorRes)
        assertEquals(R.string.ws_adb_connecting, ind.textRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun `null bootstrap maps to disconnected`() {
        val ind = adbIndicator(null, null)
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertFalse(ind.pairOffered)
    }

    @Test
    fun `USER_ACTION_REQUIRED_WD maps to disconnected`() {
        val ind = adbIndicator(null, "USER_ACTION_REQUIRED_WD")
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun `ENDPOINT_NOT_FOUND maps to disconnected`() {
        val ind = adbIndicator(null, "ENDPOINT_NOT_FOUND")
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    @Test
    fun `NOT_READY maps to disconnected`() {
        val ind = adbIndicator(null, "NOT_READY")
        assertEquals(R.color.ws_adb_disconnected, ind.colorRes)
        assertTrue(ind.pairOffered)
    }

    // -----------------------------------------------------------------------
    // adbManagementPresentation — management surface
    // -----------------------------------------------------------------------

    @Test
    fun `READY management - reconnect hidden, pair visible, settings visible, refresh visible`() {
        val p = adbManagementPresentation(AtAdbBootstrap.READY.name, null)
        assertEquals(R.string.adb_state_ready, p.titleRes)
        assertEquals(R.color.ws_adb_ready, p.colorRes)
        assertFalse(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `PAIRING_REQUIRED management - reconnect hidden, pair visible`() {
        val p = adbManagementPresentation(AtAdbBootstrap.PAIRING_REQUIRED.name, null)
        assertEquals(R.string.adb_state_pairing_required, p.titleRes)
        assertEquals(R.color.ws_adb_connecting, p.colorRes) // orange
        assertFalse(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `CONNECT_FAILED management - reconnect visible`() {
        val p = adbManagementPresentation(AtAdbBootstrap.CONNECT_FAILED.name, null)
        assertEquals(R.string.adb_state_connect_failed, p.titleRes)
        assertEquals(R.color.ws_adb_disconnected, p.colorRes)
        assertTrue(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `USER_ACTION_REQUIRED_WD management - reconnect hidden, settings prominent`() {
        val p = adbManagementPresentation("USER_ACTION_REQUIRED_WD", null)
        assertEquals(R.string.adb_state_wd_off, p.titleRes)
        assertFalse(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `ENDPOINT_NOT_FOUND management - reconnect visible`() {
        val p = adbManagementPresentation("ENDPOINT_NOT_FOUND", null)
        assertEquals(R.string.adb_state_endpoint_not_found, p.titleRes)
        assertTrue(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `NOT_READY management - reconnect visible`() {
        val p = adbManagementPresentation("NOT_READY", null)
        assertEquals(R.string.adb_state_not_ready, p.titleRes)
        assertTrue(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `null state management - safe defaults`() {
        val p = adbManagementPresentation(null, null)
        assertEquals(R.string.adb_state_unknown, p.titleRes)
        assertFalse(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `Connecting state management - transient`() {
        val p = adbManagementPresentation(AtAdbBootstrap.READY.name, "Connecting...")
        assertEquals(R.string.adb_state_connecting, p.titleRes)
        assertEquals(R.color.ws_adb_connecting, p.colorRes)
        assertFalse(p.reconnectVisible)
        assertTrue(p.pairVisible)
        assertTrue(p.settingsVisible)
        assertTrue(p.refreshVisible)
    }

    @Test
    fun `pair entry is ALWAYS visible across all states`() {
        val states = listOf(
            AtAdbBootstrap.READY.name,
            AtAdbBootstrap.PAIRING_REQUIRED.name,
            AtAdbBootstrap.CONNECT_FAILED.name,
            "USER_ACTION_REQUIRED_WD",
            "ENDPOINT_NOT_FOUND",
            "NOT_READY",
            null,
        )
        for (code in states) {
            val p = adbManagementPresentation(code, null)
            assertTrue("pairVisible should be true for state=$code", p.pairVisible)
        }
    }

    @Test
    fun `settings is ALWAYS visible across all states`() {
        val states = listOf(
            AtAdbBootstrap.READY.name,
            AtAdbBootstrap.PAIRING_REQUIRED.name,
            AtAdbBootstrap.CONNECT_FAILED.name,
            "USER_ACTION_REQUIRED_WD",
            "ENDPOINT_NOT_FOUND",
            "NOT_READY",
            null,
        )
        for (code in states) {
            val p = adbManagementPresentation(code, null)
            assertTrue("settingsVisible should be true for state=$code", p.settingsVisible)
        }
    }

    @Test
    fun `refresh is ALWAYS visible across all states`() {
        val states = listOf(
            AtAdbBootstrap.READY.name,
            AtAdbBootstrap.PAIRING_REQUIRED.name,
            AtAdbBootstrap.CONNECT_FAILED.name,
            "USER_ACTION_REQUIRED_WD",
            "ENDPOINT_NOT_FOUND",
            "NOT_READY",
            null,
        )
        for (code in states) {
            val p = adbManagementPresentation(code, null)
            assertTrue("refreshVisible should be true for state=$code", p.refreshVisible)
        }
    }

    // -----------------------------------------------------------------------
    // adbPairingResultMessage — pairing outcome → message
    // -----------------------------------------------------------------------

    @Test
    fun `SUCCESS maps to success message`() {
        assertEquals(R.string.adb_pair_success, adbPairingResultMessage(AtPairingResult.PairingOutcome.SUCCESS, null))
    }

    @Test
    fun `REJECTED maps to rejected message`() {
        assertEquals(R.string.adb_pair_rejected, adbPairingResultMessage(AtPairingResult.PairingOutcome.REJECTED, null))
    }

    @Test
    fun `SERVICE_NOT_FOUND maps to service not found message`() {
        assertEquals(R.string.adb_pair_service_not_found, adbPairingResultMessage(AtPairingResult.PairingOutcome.SERVICE_NOT_FOUND, null))
    }

    @Test
    fun `MULTIPLE_ENDPOINTS maps to multiple endpoints message`() {
        assertEquals(R.string.adb_pair_multiple_endpoints, adbPairingResultMessage(AtPairingResult.PairingOutcome.MULTIPLE_ENDPOINTS, null))
    }

    @Test
    fun `FAILED with no error maps to generic failed message`() {
        assertEquals(R.string.adb_pair_failed, adbPairingResultMessage(AtPairingResult.PairingOutcome.FAILED, null))
    }

    @Test
    fun `FAILED with blank error maps to generic failed message`() {
        assertEquals(R.string.adb_pair_failed, adbPairingResultMessage(AtPairingResult.PairingOutcome.FAILED, "   "))
    }

    @Test
    fun `FAILED with error maps to failed with detail message`() {
        assertEquals(R.string.adb_pair_failed_with_detail, adbPairingResultMessage(AtPairingResult.PairingOutcome.FAILED, "timeout"))
    }

    // -----------------------------------------------------------------------
    // Indicator consistency: header and sidebar use same mapping
    // -----------------------------------------------------------------------

    @Test
    fun `indicator color is consistent for READY`() {
        val ind = adbIndicator(null, AtAdbBootstrap.READY.name)
        assertEquals(R.color.ws_adb_ready, ind.colorRes)
        // Management surface also uses ready color
        val mgmt = adbManagementPresentation(AtAdbBootstrap.READY.name, null)
        assertEquals(ind.colorRes, mgmt.colorRes)
    }

    @Test
    fun `indicator color is consistent for PAIRING_REQUIRED`() {
        // Header indicator uses gray for non-READY
        val ind = adbIndicator(null, AtAdbBootstrap.PAIRING_REQUIRED.name)
        // Management surface uses orange for PAIRING_REQUIRED
        val mgmt = adbManagementPresentation(AtAdbBootstrap.PAIRING_REQUIRED.name, null)
        // Both are non-green (disconnected or connecting)
        assertTrue(ind.colorRes != R.color.ws_adb_ready)
        assertTrue(mgmt.colorRes != R.color.ws_adb_ready)
    }
}
