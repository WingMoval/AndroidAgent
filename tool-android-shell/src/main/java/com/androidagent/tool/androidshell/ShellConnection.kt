package com.androidagent.tool.androidshell

import android.content.Context
import com.androidagent.integration.at.AtExecutionAdapter
import com.adb.engine.AdbRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AT V3 connection lifecycle binding (Integration-owned, NOT a backend
 * abstraction).
 *
 * AG.V2-2B: delegates to the unified [AtExecutionAdapter] for ADB lifecycle.
 *
 * Responsibilities: initialize / connectAuto / readiness / disconnect —
 * exactly the four lifecycle calls the V3 AdbRuntime exposes. The Host decides
 * the lifecycle POLICY (when to connect, when to disconnect, what to show the
 * user); [ShellConnection] performs the adapter calls.
 *
 * Public boundary rule: the public API exposes ONLY integration-owned types.
 * No [AtExecutionAdapter], no V3-owned diagnostic/readiness types, no
 * `com.adb.engine.*` or `com.at.engine.*` import is ever visible to the Host.
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
    )

    /**
     * Wire the V3 AdbRuntime platform backends. Idempotent; safe to call
     * before [connect].
     */
    fun initialize() {
        adapter.initializeAdb()
    }

    /**
     * Readiness -> (auto-enable WD if WRITE_SECURE_SETTINGS) -> discovery ->
     * endpoint select -> connect -> READY.
     *
     * Uses [AtExecutionAdapter.ensureAdbReady] which wraps
     * [com.adb.engine.AdbRuntime.ensureReady].
     *
     * @return an integration-owned [Status]; never leaks V3-owned types.
     */
    suspend fun connect(): Status = withContext(Dispatchers.IO) {
        initialize()
        val ready = adapter.ensureAdbReady()
        val state = adapter.adbState()
        Status(
            ready = ready,
            code = state.name,
            message = if (ready) "connected" else state.name,
        )
    }

    /**
     * Current V3 AdbRuntime state as an integration-owned status (non-blocking).
     */
    fun readiness(): Status {
        val state: AdbRuntimeState = adapter.adbState()
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
        const val DEFAULT_IDENTITY_DIR: String = "at-identity"
    }
}
