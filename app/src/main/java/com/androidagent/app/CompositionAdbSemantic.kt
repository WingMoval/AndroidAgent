package com.androidagent.app

import com.androidagent.integration.at.AdbSemantic
import com.androidagent.integration.at.AtAdbBootstrap
import com.androidagent.integration.at.AtAdbReadiness
import com.androidagent.integration.at.AtPairingDiscovery
import com.androidagent.integration.at.AtPairingResult
import com.at.composition.android.ATAndroidAdbControl
import com.at.composition.android.ATAdbBootstrapState
import com.at.composition.android.ATAdbReadinessState
import com.at.composition.android.ATAdbPairingDiscovery
import com.at.composition.android.ATAdbPairingResult

/**
 * Host-layer ADB semantic bridge (AG.AT-V4-INTEGRATION-1B).
 *
 * Implements the AG-owned [AdbSemantic] interface by delegating to the AT
 * composition facade ([ATAndroidAdbControl]). This is the Host's (Composition
 * Root's) responsibility — the adapter (`:integration-at`) never imports
 * `com.at.composition.android.*` types.
 *
 * Ownership: AdbRuntime (AT-internal) remains the UNIQUE ADB lifecycle owner.
 * This class is a thin adapter, NOT a second lifecycle owner.
 */
internal class CompositionAdbSemantic(
    private val adbControl: ATAndroidAdbControl,
) : AdbSemantic {

    override fun ensureReady(onStatusLine: (String) -> Unit): Boolean =
        adbControl.ensureReady(onStatusLine)

    override fun readinessState(): AtAdbReadiness =
        when (adbControl.readinessState()) {
            ATAdbReadinessState.READY -> AtAdbReadiness.READY
            ATAdbReadinessState.DISCONNECTED -> AtAdbReadiness.DISCONNECTED
            ATAdbReadinessState.CONNECTING -> AtAdbReadiness.CONNECTING
            else -> AtAdbReadiness.UNKNOWN
        }

    override fun bootstrapState(): AtAdbBootstrap =
        when (adbControl.bootstrapState()) {
            ATAdbBootstrapState.PAIRING_REQUIRED -> AtAdbBootstrap.PAIRING_REQUIRED
            ATAdbBootstrapState.CONNECT_FAILED -> AtAdbBootstrap.CONNECT_FAILED
            ATAdbBootstrapState.READY -> AtAdbBootstrap.READY
            // USER_ACTION_REQUIRED_WD / ENDPOINT_NOT_FOUND / NOT_READY
            // are transient AT-internal states; map to CONNECT_FAILED for
            // the AG consumer (identity exists but not yet ready).
            else -> AtAdbBootstrap.CONNECT_FAILED
        }

    override fun hasIdentity(): Boolean = adbControl.hasIdentity()

    override fun discoverPairing(timeoutMs: Long): AtPairingDiscovery =
        run {
            val d: ATAdbPairingDiscovery = adbControl.discoverPairing(timeoutMs)
            AtPairingDiscovery(
                endpointCount = d.endpoints.size,
                serviceNotFound = d.serviceNotFound,
                multiple = d.multiple,
            )
        }

    override fun pairDiscovered(code: String, timeoutMs: Long): AtPairingResult =
        mapPairingResult(adbControl.pairDiscovered(code, timeoutMs))

    override fun pair(host: String, port: Int, code: String): AtPairingResult =
        mapPairingResult(adbControl.pair(host, port, code))

    private fun mapPairingResult(r: ATAdbPairingResult): AtPairingResult =
        AtPairingResult(
            success = r.success,
            outcome = when (r.outcome) {
                ATAdbPairingResult.Outcome.SUCCESS -> AtPairingResult.PairingOutcome.SUCCESS
                ATAdbPairingResult.Outcome.REJECTED -> AtPairingResult.PairingOutcome.REJECTED
                ATAdbPairingResult.Outcome.SERVICE_NOT_FOUND -> AtPairingResult.PairingOutcome.SERVICE_NOT_FOUND
                ATAdbPairingResult.Outcome.MULTIPLE_ENDPOINTS -> AtPairingResult.PairingOutcome.MULTIPLE_ENDPOINTS
                ATAdbPairingResult.Outcome.FAILED -> AtPairingResult.PairingOutcome.FAILED
            },
            deviceGuid = r.deviceGuid,
            error = r.error,
        )
}
