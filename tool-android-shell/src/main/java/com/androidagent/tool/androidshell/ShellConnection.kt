package com.androidagent.tool.androidshell

import android.content.Context
import com.androidterminal.runtime.AtRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AT Engine lifecycle binding (Integration-owned, NOT a backend abstraction).
 *
 * Responsibilities: initialize / connectAuto / readiness / disconnect —
 * exactly the four lifecycle calls the AT Engine stable contract exposes. The
 * Host decides the lifecycle POLICY (when to connect, when to disconnect, what
 * to show the user); [ShellConnection] performs the AT implementation calls.
 *
 * Public boundary rule: the public API exposes ONLY integration-owned types.
 * No [AtRuntime], no AT-owned diagnostic/readiness types, no
 * `com.androidterminal.*` import is ever visible to the Host.
 */
class ShellConnection(
    context: Context,
    private val identityDirName: String = DEFAULT_IDENTITY_DIR,
) {

    private val appContext: Context = context.applicationContext

    /** Integration-owned connection status. */
    data class Status(
        val ready: Boolean,
        val code: String,
        val message: String,
    )

    /**
     * Wire the AT platform backends. Idempotent; safe to call before [connect].
     * Fast (builds the Android readiness + discovery backends), safe to call on
     * any thread.
     */
    fun initialize() {
        AtRuntime.initialize(appContext)
    }

    /**
     * Readiness -> discovery -> endpoint select -> connect -> READY.
     *
     * Blocking AT work runs on [Dispatchers.IO]. [keyDir] is the AGA app-private
     * sandbox identity directory ([filesDir]/at-identity); the trusted adbkey
     * must already be provisioned there (v0 production precondition: Wireless
     * Debugging enabled + trusted identity present).
     *
     * @return an integration-owned [Status]; never leaks AT-owned types.
     */
    suspend fun connect(): Status = withContext(Dispatchers.IO) {
        initialize()
        ensureIdentityDir()
        val diagnostic = AtRuntime.connectAuto(identityDir())
        Status(
            ready = diagnostic.isReady,
            code = diagnostic.code,
            message = diagnostic.message,
        )
    }

    /**
     * Current engine-level readiness as an integration-owned status (non-blocking).
     */
    fun readiness(): Status {
        val state = AtRuntime.readinessState()
        return Status(
            ready = state.isConnected,
            code = state.name,
            message = state.name,
        )
    }

    /** Tear down the connection. Idempotent. Blocking AT work runs on IO. */
    suspend fun disconnect(): Status = withContext(Dispatchers.IO) {
        val diagnostic = AtRuntime.disconnect()
        Status(
            ready = diagnostic.isReady,
            code = diagnostic.code,
            message = diagnostic.message,
        )
    }

    private fun identityDir(): String = File(appContext.filesDir, identityDirName).absolutePath

    private fun ensureIdentityDir() {
        val dir = File(identityDir())
        if (!dir.exists()) dir.mkdirs()
    }

    companion object {
        const val DEFAULT_IDENTITY_DIR: String = "at-identity"
    }
}