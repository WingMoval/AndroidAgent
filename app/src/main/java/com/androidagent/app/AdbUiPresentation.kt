package com.androidagent.app

import com.androidagent.integration.at.AtAdbBootstrap
import com.androidagent.integration.at.AtPairingResult

/**
 * AG.ADB-CONNECTION-UX-1R: Host-level shared ADB UI presentation mapping.
 *
 * Single source of truth for:
 *   - Top-right header indicator (color + accessibility text)
 *   - Sidebar indicator (color)
 *   - ADB Management surface (status title, description, button visibility)
 *
 * This is a PURE function: no Android, no AT, no I/O.
 * Consumes the AT bootstrap code string + ready flag, produces UI presentation.
 */

// ---------------------------------------------------------------------------
// Indicator (header + sidebar)
// ---------------------------------------------------------------------------

/** Semantic indicator for the compact "ADB ■" display (header + sidebar). */
data class AdbIndicator(
    val colorRes: Int,
    val textRes: Int,
    val pairOffered: Boolean,
)

/**
 * Maps AT bootstrap state → indicator color + text.
 *
 * @param atStatusText transient status text (e.g. "Connecting..."), or null
 * @param bootstrapCode the AT bootstrap state name, or null if unknown
 */
fun adbIndicator(atStatusText: String?, bootstrapCode: String?): AdbIndicator = when {
    bootstrapCode == null ->
        AdbIndicator(R.color.ws_adb_disconnected, R.string.ws_adb_disconnected, pairOffered = false)

    atStatusText == "Connecting..." ->
        AdbIndicator(R.color.ws_adb_connecting, R.string.ws_adb_connecting, pairOffered = false)

    bootstrapCode == AtAdbBootstrap.READY.name ->
        AdbIndicator(R.color.ws_adb_ready, R.string.ws_adb_connected, pairOffered = false)

    else ->
        AdbIndicator(R.color.ws_adb_disconnected, R.string.ws_adb_disconnected, pairOffered = true)
}

// ---------------------------------------------------------------------------
// Management surface presentation
// ---------------------------------------------------------------------------

/**
 * Full management surface presentation for a given AT bootstrap state.
 *
 * @property titleRes       status title string resource
 * @property descRes        status description string resource
 * @property colorRes       indicator dot/square color
 * @property reconnectVisible whether the Reconnect button is shown
 * @property pairVisible    whether Pair / Re-pair entry is shown (always true in UX-1R)
 * @property settingsVisible whether Open Wireless Debugging Settings is shown (always true)
 * @property refreshVisible whether Refresh is shown (always true)
 */
data class AdbManagementPresentation(
    val titleRes: Int,
    val descRes: Int,
    val colorRes: Int,
    val reconnectVisible: Boolean,
    val pairVisible: Boolean,
    val settingsVisible: Boolean,
    val refreshVisible: Boolean,
)

/**
 * Maps AT bootstrap state → management surface presentation.
 *
 * UX-1R rules:
 *   - Pair / Re-pair is ALWAYS visible (click to expand)
 *   - Open Wireless Debugging Settings is ALWAYS visible
 *   - Refresh is ALWAYS visible
 *   - Reconnect is visible only when a connection attempt makes sense
 *   - NO Disconnect (AT v0.4.4 has no formal disconnect API)
 */
fun adbManagementPresentation(bootstrapCode: String?, atStatusText: String?): AdbManagementPresentation {
    // Transient connecting state
    if (atStatusText == "Connecting...") {
        return AdbManagementPresentation(
            titleRes = R.string.adb_state_connecting,
            descRes = R.string.adb_state_connecting_desc,
            colorRes = R.color.ws_adb_connecting,
            reconnectVisible = false,
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )
    }

    return when (bootstrapCode) {
        null -> AdbManagementPresentation(
            titleRes = R.string.adb_state_unknown,
            descRes = R.string.adb_state_unknown_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = false,
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        AtAdbBootstrap.READY.name -> AdbManagementPresentation(
            titleRes = R.string.adb_state_ready,
            descRes = R.string.adb_state_ready_desc,
            colorRes = R.color.ws_adb_ready,
            reconnectVisible = false, // READY: no reconnect needed
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        AtAdbBootstrap.PAIRING_REQUIRED.name -> AdbManagementPresentation(
            titleRes = R.string.adb_state_pairing_required,
            descRes = R.string.adb_state_pairing_required_desc,
            colorRes = R.color.ws_adb_connecting, // orange
            reconnectVisible = false, // no identity to reconnect
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        "USER_ACTION_REQUIRED_WD" -> AdbManagementPresentation(
            titleRes = R.string.adb_state_wd_off,
            descRes = R.string.adb_state_wd_off_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = false, // can't connect if WD is off
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        AtAdbBootstrap.CONNECT_FAILED.name -> AdbManagementPresentation(
            titleRes = R.string.adb_state_connect_failed,
            descRes = R.string.adb_state_connect_failed_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = true, // identity exists, try reconnect
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        "ENDPOINT_NOT_FOUND" -> AdbManagementPresentation(
            titleRes = R.string.adb_state_endpoint_not_found,
            descRes = R.string.adb_state_endpoint_not_found_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = true,
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        "NOT_READY" -> AdbManagementPresentation(
            titleRes = R.string.adb_state_not_ready,
            descRes = R.string.adb_state_not_ready_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = true,
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )

        else -> AdbManagementPresentation(
            titleRes = R.string.adb_state_unknown,
            descRes = R.string.adb_state_unknown_desc,
            colorRes = R.color.ws_adb_disconnected,
            reconnectVisible = false,
            pairVisible = true,
            settingsVisible = true,
            refreshVisible = true,
        )
    }
}

// ---------------------------------------------------------------------------
// Pairing result presentation
// ---------------------------------------------------------------------------

/**
 * Maps an AT pairing result → user-facing message string resource.
 * Deterministic, no side effects.
 */
fun adbPairingResultMessage(outcome: AtPairingResult.PairingOutcome, error: String?): Int = when (outcome) {
    AtPairingResult.PairingOutcome.SUCCESS -> R.string.adb_pair_success
    AtPairingResult.PairingOutcome.REJECTED -> R.string.adb_pair_rejected
    AtPairingResult.PairingOutcome.SERVICE_NOT_FOUND -> R.string.adb_pair_service_not_found
    AtPairingResult.PairingOutcome.MULTIPLE_ENDPOINTS -> R.string.adb_pair_multiple_endpoints
    AtPairingResult.PairingOutcome.FAILED -> {
        if (error.isNullOrBlank()) R.string.adb_pair_failed
        else R.string.adb_pair_failed_with_detail
    }
}
