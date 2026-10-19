package com.androidagent.tool.androidshell

import android.content.Context
import com.androidagent.integration.at.AtExecutionAdapter
import com.adb.engine.AdbBootstrapState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AT connection lifecycle binding (Integration-owned, NOT a backend
 * abstraction).
 *
 * AG.V2-2: delegates to the unified [AtExecutionAdapter] for ADB lifecycle
 * and consumer bootstrap.
 *
 * Responsibilities: initialize / ensureReady / bootstrapState / disconnect.
 * The Host decides the lifecycle POLICY (when to connect, when to show
 * pairing UI, what to display); [ShellConnection] performs the adapter calls.
 *
 * Public boundary rule: the public API exposes ONLY integration-owned types.
 * No [AtExecutionAdapter], no AT-owned types are leaked to the Host.
 */
class ShellConnection(
    context: Context,
    private val identityDirName: String = DEFAULT_IDENTITY_DIR,
) {

    private val appContext: Context = context.applicationContext

    /** Unified AT execution adapter (the single AT consumption point). */
    val adapter: AtExecutionAdapter = AtExecutionAdapter(appContext, identityDirName)

    /** Integration-owned connection status. */
    data class Status(
        val ready: Boolean,
        val code: String,
        val message: String,
    ) {
        /** Whether the Host should offer the pairing UI. */
        val pairingOffered: Boolean get() =
            code == AdbBootstrapState.PAIRING_REQUIRED.name ||
            code == AdbBootstrapState.CONNECT_FAILED.name
    }

    /**
     * Wire the ADB runtime platform backends. Idempotent; safe to call
     * before [connect].
     */
    fun initialize() {
        adapter.initializeAdb()
    }

    /**
     * Readiness -> (auto-enable WD) -> discovery -> endpoint select -> connect.
     *
     * Uses [AtExecutionAdapter.ensureAdbReady] which wraps AdbRuntime.ensureReady.
     *
     * @return an integration-owned [Status]; never leaks AT-owned types.
     */
    suspend fun connect(): Status = withContext(Dispatchers.IO) {
        initialize()
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
        val state: AdbBootstrapState = adapter.bootstrapState()
        return Status(
            ready = state.isReady,
            code = state.name,
            message = state.name,
        )
    }

    /** Tear down the connection. Idempotent. */
    suspend fun disconnect(): Status {
        adapter.disconnectAdb()
        return Status(
            ready = false,
            code = "DISCONNECTED",
            message = "disconnected",
        )
    }

    companion object {
        const val DEFAULT_IDENTITY_DIR: String = AtExecutionAdapter.DEFAULT_IDENTITY_DIR
    }
}
