package com.androidagent.integration.at

/**
 * AG integration-owned ADB semantic interface (V4 consumer boundary).
 *
 * The adapter receives this interface via constructor injection from the
 * Host (Composition Root). The Host implements it by delegating to the AT
 * composition facade (`ATAndroidAdbControl`). The adapter itself never
 * imports `com.at.composition.android.*` types.
 *
 * This interface expresses ONLY the ADB semantic capabilities the AG adapter
 * needs. It does NOT replicate AT Runtime or Recovery implementation.
 *
 * Ownership: AdbRuntime (AT-internal) remains the UNIQUE ADB lifecycle owner.
 * This interface is a READ-ONLY consumer projection + controlled operations.
 */
interface AdbSemantic {
    /**
     * Ensure the ADB connection is READY (readiness gate + auto-WD +
     * discovery + connect).
     */
    fun ensureReady(onStatusLine: (String) -> Unit): Boolean

    /** Current ADB readiness state (non-blocking). */
    fun readinessState(): AtAdbReadiness

    /** Consumer-facing ADB bootstrap state (read-only). */
    fun bootstrapState(): AtAdbBootstrap

    /** Whether the AG has usable ADB identity material. */
    fun hasIdentity(): Boolean

    /**
     * Discover the pairing service. Returns 0/1/N endpoints.
     */
    fun discoverPairing(timeoutMs: Long = 10_000L): AtPairingDiscovery

    /**
     * Pair against an auto-discovered endpoint using the 6-digit [code].
     */
    fun pairDiscovered(code: String, timeoutMs: Long = 10_000L): AtPairingResult

    /**
     * Pair against an explicit endpoint.
     */
    fun pair(host: String, port: Int, code: String): AtPairingResult
}
