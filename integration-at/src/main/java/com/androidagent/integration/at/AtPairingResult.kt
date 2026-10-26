package com.androidagent.integration.at

/**
 * AG integration-owned pairing result (V4 consumer boundary).
 *
 * Maps the AT composition `ATAdbPairingResult` to an AG-owned type so that
 * the adapter and tool layers never import `com.at.composition.android.*`.
 */
data class AtPairingResult(
    val success: Boolean,
    val outcome: PairingOutcome,
    val deviceGuid: String?,
    val error: String?,
) {
    enum class PairingOutcome {
        SUCCESS,
        REJECTED,
        SERVICE_NOT_FOUND,
        MULTIPLE_ENDPOINTS,
        FAILED,
    }
}
