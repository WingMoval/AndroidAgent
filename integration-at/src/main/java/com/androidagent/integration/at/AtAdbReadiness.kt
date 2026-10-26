package com.androidagent.integration.at

/**
 * AG integration-owned ADB readiness state (V4 consumer boundary).
 *
 * Maps the AT composition `ATAdbReadinessState` to an AG-owned type so that
 * the adapter and tool layers never import `com.at.composition.android.*`.
 */
enum class AtAdbReadiness {
    READY,
    DISCONNECTED,
    CONNECTING,
    PAIRING_REQUIRED,
    UNKNOWN,
}
