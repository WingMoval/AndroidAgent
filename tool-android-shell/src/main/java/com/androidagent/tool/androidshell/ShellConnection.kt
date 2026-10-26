package com.androidagent.tool.androidshell

import com.androidagent.integration.at.AtAdbBootstrap
import com.androidagent.integration.at.AtExecutionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AT connection lifecycle binding (Integration-owned, NOT a backend
 * abstraction).
 *
 * AG.AT-V4-INTEGRATION-1B: delegates to the unified [AtExecutionAdapter]
 * for ADB lifecycle and consumer bootstrap. All ADB semantic types are
 * AG integration-owned (AtAdbBootstrap / AtAdbReadiness / AtPairingResult).
 *
 * Responsibilities: ensureReady / bootstrapState / disconnect.
 * The Host decides the lifecycle POLICY (when to connect, when to show
 * pairing UI, what to display); [ShellConnection] performs the adapter calls.
 *
 * Public boundary rule: the public API exposes ONLY integration-owned types.
 * No [AtExecutionAdapter], no AT-owned types are leaked to the Host.
 */
class ShellConnection(
    val adapter: AtExecutionAdapter,
) {

    /** Integration-owned connection status. */
    data class Status(
        val ready: Boolean,
        val code: String,
        val message: String,
    ) {
        /** Whether the Host should offer the pairing UI. */
        val pairingOffered: Boolean get() =
            code == AtAdbBootstrap.PAIRING_REQUIRED.name ||
            code == AtAdbBootstrap.CONNECT_FAILED.name
    }

    /**
     * Ensure the ADB connection is READY.
     *
     * Uses [AtExecutionAdapter.ensureAdbReady] which wraps the AdbSemantic
     * interface (Host-implemented via AT composition facade).
     *
     * @return an integration-owned [Status]; never leaks AT-owned types.
     */
    suspend fun connect(): Status = withContext(Dispatchers.IO) {
        val ready = adapter.ensureAdbReady()
        val state = adapter.bootstrapState()
        Status(
            ready = ready,
            code = state.name,
            message = if (ready) "connected" else state.name,
        )
    }

    /**
     * Current consumer-facing ADB bootstrap state (non-blocking, read-only).
     *
     * Returns PAIRING_REQUIRED when no identity exists (first use),
     * READY when connected, CONNECT_FAILED when identity exists but
     * connection failed.
     */
    fun readiness(): Status {
        val state: AtAdbBootstrap = adapter.bootstrapState()
        return Status(
            ready = adapter.isAdbReady(),
            code = state.name,
            message = state.name,
        )
    }

    /**
     * Whether the AG has usable ADB identity material.
     */
    fun hasIdentity(): Boolean = adapter.hasIdentity()

    /** Tear down the connection. Idempotent. */
    suspend fun disconnect(): Status =
        // V4: no explicit disconnect API on ATAndroidAdbControl.
        // The ADB connection is managed by AdbRuntime internally.
        // This is a no-op placeholder for API compatibility.
        Status(
            ready = false,
            code = "DISCONNECTED",
            message = "disconnected",
        )
}
