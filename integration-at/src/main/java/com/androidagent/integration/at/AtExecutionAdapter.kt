// AG.V2-2 — AG AT Execution Adapter.
//
// This is the AG-side unified AT consumption point. It sits between AG Concrete
// Tools and the AT Engine, providing a single, thin ADB execution abstraction.
//
//   :tool-android-shell  →  AtExecutionAdapter  →  ATEngine  →  ADB Backend  →  AdbEngine  →  adbd
//
// The adapter:
//   1. Holds the AT Engine composition (ADB one-shot + persistent backends).
//   2. Exposes AdbConsumerBootstrap for first-use pairing.
//   3. Exposes AT's current execution capability (one-shot, persistent, raw I/O,
//      write, resize, Ctrl-C, lifecycle, multi-session).
//   4. Isolates AT Contract DRAFT from other AG code.
//
// AG Core / AG Runtime do NOT depend on this module. Only :app (composition
// root) and :tool-android-shell (concrete AT consumer) do.
//
// ADB lifecycle ownership: AdbRuntime remains the UNIQUE ADB lifecycle owner.
// AdbConsumerBootstrap is a READ-ONLY consumer projection (not a second owner).

package com.androidagent.integration.at

import android.content.Context
import com.adb.engine.AdbBootstrapState
import com.adb.engine.AdbConsumerBootstrap
import com.adb.engine.AdbPairingDiscovery
import com.adb.engine.AdbPairingResultV2
import com.adb.engine.AdbConnectionRequest
import com.adb.engine.AdbRuntime
import com.adb.engine.AdbRuntimeState
import com.at.backend.adb.AdbOneShotBackend
import com.at.backend.adb.AdbPersistentBackend
import com.at.backend.local.LocalOneShotBackend
import com.at.backend.local.LocalPersistentBackend
import com.at.cli.AdbCliBridgeServer
import com.at.cli.ATCliExecution
import com.at.cli.LocalCliBootstrap
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
 * ADB-centric execution abstraction covering AT Consumer Freeze V1's
 * current execution capability:
 *
 *   - One-shot execution (ADB)
 *   - Persistent terminal session (ADB)
 *   - Raw byte I/O (write / read / listener)
 *   - Ctrl-C / control byte input
 *   - Resize
 *   - Lifecycle / events (open / active / exited / closed)
 *   - Multi-session
 *   - First-use bootstrap (AdbConsumerBootstrap)
 *
 * The adapter is THIN: it holds the AT Engine composition and exposes AT's
 * existing capability. It does NOT redefine AT execution semantics.
 *
 * Identity: the adapter uses the AG app's OWN app-private identity directory
 * (default "at-consumer/identity"). On first use the directory is empty →
 * [AdbConsumerBootstrap.bootstrapState] reports PAIRING_REQUIRED. Pairing
 * via [bootstrap] creates the AG self-generated key material.
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
        AdbRuntime.ensureReady(identityDir()) { /* status consumed via adbState() */ }
        AdbRuntime.readinessState().isReady
    }

    /** Current ADB lifecycle state (non-blocking). */
    fun adbState(): AdbRuntimeState = AdbRuntime.readinessState()

    /** Disconnect ADB. Delegates to [AdbRuntime.disconnect]. */
    suspend fun disconnectAdb() {
        withContext(Dispatchers.IO) { AdbRuntime.disconnect() }
    }

    // ---- Consumer bootstrap (AdbConsumerBootstrap — AT-owned) ---------------

    /**
     * The AG's own AdbConsumerBootstrap instance. This is the ONLY path for
     * first-use pairing. The bootstrap is parameterized by the AG app-private
     * identity directory — it is NOT the AndroidTerminal app's identity.
     */
    val bootstrap: AdbConsumerBootstrap by lazy {
        ensureIdentityDir()
        AdbConsumerBootstrap(identityDir())
    }

    /**
     * Consumer-facing ADB readiness projection (read-only).
     *
     * Returns PAIRING_REQUIRED when no identity exists (first use),
     * READY when connected, CONNECT_FAILED when identity exists but
     * connection failed (does NOT assert "must pair").
     */
    fun bootstrapState(): AdbBootstrapState {
        initializeAdb()
        return bootstrap.bootstrapState()
    }

    /**
     * Discover the pairing service. The system "Pair device with pairing code"
     * screen must be open. Returns 0/1/N endpoints.
     */
    suspend fun discoverPairing(timeoutMs: Long = 10_000L): AdbPairingDiscovery =
        withContext(Dispatchers.IO) {
            bootstrap.discoverPairing(timeoutMs)
        }

    /**
     * Pair against an auto-discovered endpoint using the 6-digit [code].
     * On success the AG's self-generated identity is created in the
     * app-private identity directory.
     */
    suspend fun pairDiscovered(code: String, timeoutMs: Long = 10_000L): AdbPairingResultV2 =
        withContext(Dispatchers.IO) {
            bootstrap.pairDiscovered(code, timeoutMs)
        }

    /**
     * Pair against an explicit endpoint.
     */
    suspend fun pair(host: String, port: Int, code: String): AdbPairingResultV2 =
        withContext(Dispatchers.IO) {
            bootstrap.pair(host, port, code)
        }

    // ---- AT CLI bridge (shell-visible adb) ----------------------------------

    private val cliBridge: AdbCliBridgeServer by lazy {
        AdbCliBridgeServer(File(appContext.filesDir, "cli-bridge"), cliExecution)
    }

    /** Start the CLI bridge (idempotent). */
    fun startCliBridge(): Boolean = cliBridge.start()

    /** Stop the CLI bridge. */
    fun stopCliBridge() = cliBridge.stop()

    /** Whether the CLI bridge is running. */
    fun cliBridgeRunning(): Boolean = cliBridge.isRunning()

    /** The CLI bridge UDS socket path. */
    val cliBridgeSocketPath: String get() = cliBridge.socketPath

    /** The host's shell-visible `adb` function bootstrap. */
    val cliBootstrap: LocalCliBootstrap by lazy {
        LocalCliBootstrap(CLI_MAIN_CLASS)
    }

    /** The host's base.apk absolute path (for the `adb` shell function). */
    val appApkPath: String
        get() = appContext.applicationInfo.sourceDir

    /**
     * Write the per-session CLI bootstrap script and return the source line
     * to inject into a Local PTY session.
     */
    fun writeCliBootstrapScript(sessionId: Int): String {
        val scriptPath = File(appContext.filesDir, "at-cli/adb_bootstrap_$sessionId.sh").apply {
            parentFile?.mkdirs()
        }.absolutePath
        cliBootstrap.writeScript(scriptPath, appApkPath, cliBridgeSocketPath, sessionId)
        return cliBootstrap.sourceLine(scriptPath)
    }

    /** Forward a Local session resize to the inner Remote PTY (if active). */
    fun relayInteractiveResize(sessionId: Int, rows: Int, cols: Int) {
        cliBridge.relayInteractiveResize(sessionId, rows, cols)
    }

    private val cliExecution = object : ATCliExecution {
        override fun adbEngine(): ATEngine = adbEngine

        override fun ensureReady(sessionId: Int, onStatusLine: (String) -> Unit): Boolean {
            if (AdbRuntime.readinessState().isReady) {
                onStatusLine("connected")
                return true
            }
            val bs = bootstrap
            if (!bs.hasIdentity()) {
                onStatusLine("pairing required: no ADB identity found")
                onStatusLine(
                    "open Settings -> Wireless Debugging -> Pair device with pairing code, " +
                        "then run: adb pair <host>:<port> <code>"
                )
                return false
            }
            initializeAdb()
            val finalState = AdbRuntime.ensureReady(identityDir()) { line ->
                onStatusLine(line)
            }
            if (finalState != AdbRuntimeState.READY) {
                onStatusLine("not connected (identity present)")
            }
            return finalState == AdbRuntimeState.READY
        }

        override fun adbIdentityDir(): String = identityDir()
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
     * Spawns /system/bin/sh -c (app UID; does NOT require ADB).
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
     * Does NOT require ADB / Wireless Debugging / AdbRuntime READY.
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

    fun writeCommand(session: ATTerminalSession, command: String): Int {
        val bytes = (command + "\n").toByteArray(Charsets.UTF_8)
        return session.write(bytes)
    }

    fun writeCtrlC(session: ATTerminalSession): Int {
        return session.write(byteArrayOf(0x03.toByte()))
    }

    fun readOutput(session: ATTerminalSession, timeoutMs: Int = 2000): ByteArray? {
        return session.read(timeoutMs)
    }

    fun resize(session: ATTerminalSession, rows: Int, cols: Int): Boolean {
        return session.resize(rows, cols)
    }

    fun closeSession(session: ATTerminalSession, terminateProcess: Boolean = true) {
        session.close(terminateProcess)
    }

    fun setListener(session: ATTerminalSession, listener: (ATSessionEvent) -> Unit) {
        session.setListener(listener)
    }

    fun sessionState(session: ATTerminalSession): String {
        return session.state.name
    }

    // ---- Identity ------------------------------------------------------------

    private fun identityDir(): String =
        File(appContext.filesDir, identityDirName).apply { mkdirs() }.absolutePath

    private fun ensureIdentityDir() {
        File(identityDir()).mkdirs()
    }

    companion object {
        /**
         * AG app-private identity directory (relative to filesDir).
         * This is the AG's OWN directory — never the AndroidTerminal app's.
         * On first use it is empty → PAIRING_REQUIRED.
         */
        const val DEFAULT_IDENTITY_DIR: String = "at-consumer/identity"

        /** The :at-cli AtCliMain entrypoint (shared in the AAR; loaded via app_process). */
        const val CLI_MAIN_CLASS: String = "com.at.cli.AtCliMain"
    }
}
