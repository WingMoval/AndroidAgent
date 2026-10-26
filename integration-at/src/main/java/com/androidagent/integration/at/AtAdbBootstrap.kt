package com.androidagent.integration.at

/**
 * AG integration-owned ADB bootstrap state (V4 consumer boundary).
 *
 * Maps the AT composition `ATAdbBootstrapState` to an AG-owned type so that
 * the adapter and tool layers never import `com.at.composition.android.*`.
 */
enum class AtAdbBootstrap {
    /** No usable identity; user must pair. */
    PAIRING_REQUIRED,
    /** Identity present but no usable connection. */
    CONNECT_FAILED,
    /** Identity present + connection ready. */
    READY,
}
