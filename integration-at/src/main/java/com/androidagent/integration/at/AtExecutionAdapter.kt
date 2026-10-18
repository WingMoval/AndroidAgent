package com.androidagent.integration.at

import android.content.Context
import com.adb.engine.AdbConnectionRequest
import com.adb.engine.AdbRuntime
import com.adb.engine.AdbRuntimeState
import com.at.backend.adb.AdbOneShotBackend
import com.at.backend.adb.AdbPersistentBackend
import com.at.backend.local.LocalOneShotBackend
import com.at.backend.local.LocalPersistentBackend
import com.at.engine.ATEngine
import com.at.engine.ATExecutionRequest
import com.at.engine.ATExecutionResult
import com.at.engine.ATOpenSessionResult
import com.at.engine.ATPersistentBackend
import com.at.engine.ATSessionEvent
import com.at.engine.ATTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AG AT Execution Adapter — the unified AT consumption point.
 *
 * Sits between AG Concrete Tools and the AT Engine. Provides a single,
 * backend-neutral execution abstraction covering AT Freeze V1's full current
 * execution capability:
 *
 *   - One-shot execution (Local + ADB)
 *   - Persistent terminal session (Local + ADB)
 *   - Raw byte I/O (write / read / listener)
 *   - Ctrl-C / control byte input
 *   - Resize
 *   - Lifecycle / events (open / active / exited / closed)
 *   - Multi-session
 *
 * The adapter is THIN: it holds the AT Engine composition and exposes AT's
 * existing capability. It does NOT redefine AT execution semantics.
 *
 * ADB lifecycle ownership: [AdbRuntime] remains the UNIQUE ADB lifecycle
 * owner. The adapter calls [AdbRuntime.connectAuto] / [AdbRuntime.initialize]
 * but never creates a second connect/recovery owner.
 *
 * @param context Android application context.
 * @param identityDirName AG app-private identity directory name (default
 *   "at-identity"). The trusted adbkey must be provisioned there.
 */
class AtExecutionAdapter(
    context: Context,
    private val identityDirName: String = DEFAULT_IDENTITY_DIR,
) {

    private val appContext: Context = context.applicationContext

    // ---- Engine composition (built once, reused) ----------------------------

    private val adbEngine: ATEngine by lazy {
        val placeholder = AdbConnectionRequest("127.0.0.1", 0, identityDir())
        ATEngine(
            oneShot = AdbOneShotBackend(placeholder),
            persistent = AdbPersistentBackend(placeholder),
        )
    }

    private val localEngine: ATEngine by lazy {
        ATEngine(
            oneShot = LocalOneShotBackend(),
            persistent = LocalPersistentBackend(),
        )
    }

    // ---- ADB lifecycle (delegates to AdbRuntime, the unique owner) ----------

    /**
     * Wire the ADB runtime platform backends. Idempotent.
     */
    fun initializeAdb() {
        AdbRuntime.initialize(appContext)
    }

    /**
     * Ensure the ADB connection is READY (readiness gate + auto-WD + discovery
     * + connect). Delegates to [AdbRuntime.ensureReady].
     *
     * @return true when the ADB connection is READY.
     */
    suspend fun ensureAdbReady(): Boolean = withContext(Dispatchers.IO) {
        initializeAdb()
        ensureIdentityDir()
        AdbRuntime.ensureReady(identityDir()) { /* status message consumed by Host via adbState() */ }
        AdbRuntime.readinessState().isReady
    }

    /** Current ADB lifecycle state (non-blocking). */
    fun adbState(): AdbRuntimeState = AdbRuntime.readinessState()

    /** Disconnect ADB. Delegates to [AdbRuntime.disconnect]. */
    suspend fun disconnectAdb() {
        withContext(Dispatchers.IO) { AdbRuntime.disconnect() }
    }

    // ---- One-shot execution -------------------------------------------------

    /**
     * Execute one command via the ADB backend (one-shot).
     *
     * The ADB connection must be READY (call [ensureAdbReady] first). If the
     * connection is not ONLINE, the backend returns TRANSPORT_ERROR.
     */
    suspend fun executeAdb(command: String, timeoutMs: Int = 0): ATExecutionResult =
        withContext(Dispatchers.IO) {
            adbEngine.execute(ATExecutionRequest(command, timeoutMs))
        }

    /**
     * Execute one command via the Local backend (one-shot).
     */
    suspend fun executeLocal(command: String, timeoutMs: Int = 0): ATExecutionResult =
        withContext(Dispatchers.IO) {
            localEngine.execute(ATExecutionRequest(command, timeoutMs))
        }

    // ---- Persistent session -------------------------------------------------

    /**
     * Open a persistent ADB terminal session (remote PTY).
     *
     * The ADB connection must be READY. Returns the [ATTerminalSession] on
     * success, or null on failure.
     */
    suspend fun openAdbSession(
        command: String = "/system/bin/sh",
        initialRows: Int = 24,
        initialCols: Int = 80,
    ): ATTerminalSession? = withContext(Dispatchers.IO) {
        when (val result = adbEngine.openSession(
            ATPersistentBackend.Launch(command, initialRows, initialCols)
        )) {
            is ATOpenSessionResult.Success -> result.session
            is ATOpenSessionResult.Failure -> {
                android.util.Log.w("AtExecutionAdapter", "openAdbSession failed: ${result.reason}")
                null
            }
        }
    }

    /**
     * Open a persistent Local terminal session (Local PTY → /system/bin/sh -i).
     */
    suspend fun openLocalSession(
        command: String = "/system/bin/sh",
        initialRows: Int = 24,
        initialCols: Int = 80,
    ): ATTerminalSession? = withContext(Dispatchers.IO) {
        when (val result = localEngine.openSession(
            ATPersistentBackend.Launch(command, initialRows, initialCols)
        )) {
            is ATOpenSessionResult.Success -> result.session
            is ATOpenSessionResult.Failure -> {
                android.util.Log.w("AtExecutionAdapter", "openLocalSession failed: ${result.reason}")
                null
            }
        }
    }

    // ---- Session helpers (thin wrappers over ATTerminalSession) -------------

    /**
     * Write a command + newline to a session.
     * @return bytes written, or -1.
     */
    fun writeCommand(session: ATTerminalSession, command: String): Int {
        val bytes = (command + "\n").toByteArray(Charsets.UTF_8)
        return session.write(bytes)
    }

    /**
     * Write a Ctrl-C (0x03) to a session.
     * @return bytes written, or -1.
     */
    fun writeCtrlC(session: ATTerminalSession): Int {
        return session.write(byteArrayOf(0x03.toByte()))
    }

    /**
     * Read available output from a session (blocking with timeout).
     * @return bytes, empty on timeout, or null on EOF/error.
     */
    fun readOutput(session: ATTerminalSession, timeoutMs: Int = 2000): ByteArray? {
        return session.read(timeoutMs)
    }

    /**
     * Resize the terminal.
     * @return true when accepted.
     */
    fun resize(session: ATTerminalSession, rows: Int, cols: Int): Boolean {
        return session.resize(rows, cols)
    }

    /**
     * Close the session.
     */
    fun closeSession(session: ATTerminalSession, terminateProcess: Boolean = true) {
        session.close(terminateProcess)
    }

    /**
     * Set a listener for session events (push mode).
     */
    fun setListener(session: ATTerminalSession, listener: (ATSessionEvent) -> Unit) {
        session.setListener(listener)
    }

    /**
     * Get the current session state (as a string).
     */
    fun sessionState(session: ATTerminalSession): String {
        return session.state.name
    }

    // ---- Identity ------------------------------------------------------------

    private fun identityDir(): String = File(appContext.filesDir, identityDirName).absolutePath

    private fun ensureIdentityDir() {
        val dir = File(identityDir())
        if (!dir.exists()) dir.mkdirs()
    }

    companion object {
        const val DEFAULT_IDENTITY_DIR: String = "at-identity"
    }
}
