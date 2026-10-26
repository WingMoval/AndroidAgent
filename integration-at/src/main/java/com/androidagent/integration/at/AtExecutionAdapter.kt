// AG.AT-V4-INTEGRATION-1B — AG AT Execution Adapter (V4 Consumer Boundary, corrected).
//
// This is the AG-side unified AT consumption point. It sits between AG Concrete
// Tools and the AT Engine, providing a single, thin execution abstraction.
//
//   :tool-android-shell  →  AtExecutionAdapter  →  ATEngine (public contract)
//                                              →  AdbSemantic (AG-owned interface, Host-implemented)
//
// V4 Consumer Boundary compliance (frozen contract §10):
//   - This adapter ONLY imports com.at.engine.* (public contract) +
//     com.at.cli.* (optional frontend).
//   - It does NOT import com.at.composition.android.* (Host's responsibility).
//   - It does NOT import com.adb.engine.* (AdbRuntime / AdbConsumerBootstrap / backends).
//   - It does NOT import com.at.backend.* (backend implementations).
//   - It does NOT directly construct ATEngine. The Host injects ATEngine instances.
//   - ADB lifecycle / identity / pairing / recovery are AT-owned; the adapter
//     delegates to the AdbSemantic interface (implemented by the Host via
//     the AT composition facade).
//
// AG Core / AG Runtime do NOT depend on this module. Only :app (composition
// root) and :tool-android-shell (concrete AT consumer) do.

package com.androidagent.integration.at

import com.at.cli.AdbCliBridgeServer
import com.at.cli.ATCliExecution
import com.at.cli.ATCliPairingResult
import com.at.cli.ATCliPairingState
import com.at.cli.LocalCliBootstrap
import com.at.engine.ATEngine
import com.at.engine.ATExecutionRequest
import com.at.engine.ATExecutionResult
import com.at.engine.ATOpenSessionResult
import com.at.engine.ATSessionEvent
import com.at.engine.ATSessionLaunch
import com.at.engine.ATTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AG AT Execution Adapter — the unified AT consumption point (V4, boundary-corrected).
 *
 * Sits between AG Concrete Tools and the AT Engine. Provides a single,
 * execution abstraction covering AT's V4 consumer capability:
 *
 *   - One-shot execution (ADB + Local)
 *   - Persistent terminal session (ADB + Local)
 *   - Raw byte I/O (write / read / listener)
 *   - Ctrl-C / control byte input
 *   - Resize
 *   - Lifecycle / events (open / active / exited / closed)
 *   - Multi-session
 *   - First-use bootstrap (via AdbSemantic interface, Host-implemented)
 *
 * The adapter is THIN: it holds injected ATEngine instances + an AdbSemantic
 * interface (implemented by the Host). It does NOT redefine AT execution
 * semantics. It does NOT directly construct ATEngine or call AdbRuntime.
 * It does NOT hold ATAndroidComposition.
 *
 * Identity: the composition owns the identity location (files/<identityProfile>/).
 * On first use the identity is empty → PAIRING_REQUIRED. Pairing via
 * [pairDiscovered] / [pair] creates the AG self-generated key material.
 *
 * @param localEngine  AT Engine for Local execution (one-shot + persistent).
 * @param adbEngine    AT Engine for ADB execution (one-shot + persistent).
 * @param adbSemantic  AG-owned ADB semantic interface (Host-implemented via
 *                     AT composition facade). The adapter never imports
 *                     com.at.composition.android.* types.
 * @param cliBridgeDir Directory for the CLI bridge UDS socket (Host-provided).
 */
class AtExecutionAdapter(
    private val localEngine: ATEngine,
    private val adbEngine: ATEngine,
    private val adbSemantic: AdbSemantic,
    private val cliBridgeDir: File,
) {

    /** App-private files directory (CWD for Local one-shot execution). */
    val appFilesDir: File = cliBridgeDir.parentFile ?: cliBridgeDir

    // ---- ADB lifecycle (delegates to AdbSemantic interface) ------------------

    /**
     * Ensure the ADB connection is READY (readiness gate + auto-WD + discovery
     * + connect). Delegates to [AdbSemantic.ensureReady].
     *
     * @return true when the ADB connection is READY.
     */
    suspend fun ensureAdbReady(): Boolean = withContext(Dispatchers.IO) {
        adbSemantic.ensureReady { /* status consumed via adbReadiness() */ }
    }

    /** Current ADB readiness state (non-blocking). */
    fun adbReadiness(): AtAdbReadiness = adbSemantic.readinessState()

    /** Whether ADB is currently READY. */
    fun isAdbReady(): Boolean = adbSemantic.readinessState() == AtAdbReadiness.READY

    // ---- Consumer bootstrap (via AdbSemantic interface) ----------------------

    /**
     * Consumer-facing ADB bootstrap state (read-only).
     *
     * Returns PAIRING_REQUIRED when no identity exists (first use),
     * READY when connected, CONNECT_FAILED when identity exists but
     * connection failed (does NOT assert "must pair").
     */
    fun bootstrapState(): AtAdbBootstrap = adbSemantic.bootstrapState()

    /** Whether the AG has usable ADB identity material. */
    fun hasIdentity(): Boolean = adbSemantic.hasIdentity()

    /**
     * Discover the pairing service. The system "Pair device with pairing code"
     * screen must be open. Returns 0/1/N endpoints.
     */
    suspend fun discoverPairing(timeoutMs: Long = 10_000L): AtPairingDiscovery =
        withContext(Dispatchers.IO) {
            adbSemantic.discoverPairing(timeoutMs)
        }

    /**
     * Pair against an auto-discovered endpoint using the 6-digit [code].
     * On success the AG's self-generated identity is created in the
     * app-private identity directory.
     */
    suspend fun pairDiscovered(code: String, timeoutMs: Long = 10_000L): AtPairingResult =
        withContext(Dispatchers.IO) {
            adbSemantic.pairDiscovered(code, timeoutMs)
        }

    /**
     * Pair against an explicit endpoint.
     */
    suspend fun pair(host: String, port: Int, code: String): AtPairingResult =
        withContext(Dispatchers.IO) {
            adbSemantic.pair(host, port, code)
        }

    // ---- AT CLI bridge (shell-visible adb) ----------------------------------

    private val cliBridge: AdbCliBridgeServer by lazy {
        cliBridgeDir.mkdirs()
        AdbCliBridgeServer(cliBridgeDir, cliExecution)
    }

    /**
     * Start the CLI bridge (idempotent).
     */
    fun startCliBridge(): Boolean {
        val ok = cliBridge.start()
        if (ok) {
            android.util.Log.i(TAG, "CLI bridge started: ${cliBridge.socketPath}")
        } else {
            android.util.Log.e(TAG, "CLI bridge FAILED to start: ${cliBridge.socketPath}")
        }
        return ok
    }

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
    var appApkPath: String = ""

    /** Canonical bootstrap file: always reflects the current install instance. */
    private val canonicalBootstrapFile: File
        get() = File(File(appFilesDir, "at-cli"), "adb_bootstrap.sh")

    /**
     * Ensure the canonical CLI bootstrap script is valid for the current install
     * instance. If the file is absent or references a stale APK path, it is
     * atomically regenerated. Returns the source line to inject into a Local
     * shell session, or null when [appApkPath] is not yet set.
     *
     * This is the single lifecycle owner for the `adb` shell function:
     *   - executeLocal calls it before sourcing (one-shot path).
     *   - openBootstrappedLocalSession calls it before PTY injection.
     *
     * No manual bootstrap deletion or local_cli session prerequisite is needed.
     */
    fun ensureCliBootstrapCurrent(): String? {
        if (appApkPath.isEmpty()) return null

        val file = canonicalBootstrapFile
        val dir = file.parentFile
        if (dir != null) dir.mkdirs()

        // Staleness check: if the file exists and contains the current APK path,
        // it is still valid (no rewrite needed).
        if (file.exists() && file.length() > 0) {
            val content = file.readText()
            if (content.contains(appApkPath)) {
                return cliBootstrap.sourceLine(file.absolutePath)
            }
        }

        // Stale or absent: atomically rewrite (write .tmp then rename).
        val tmp = File(dir, "adb_bootstrap.sh.tmp")
        cliBootstrap.writeScript(tmp.absolutePath, appApkPath, cliBridgeSocketPath, 0)
        if (!tmp.renameTo(file)) {
            // Fallback for filesystems where renameTo fails across the same dir:
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                android.util.Log.e(TAG, "ensureCliBootstrapCurrent: atomic rename failed")
                return null
            }
        }
        return cliBootstrap.sourceLine(file.absolutePath)
    }

    /**
     * Write a per-session CLI bootstrap script and return the source line
     * to inject into a Local PTY session.
     *
     * DEPRECATED lifecycle: prefer [ensureCliBootstrapCurrent] which maintains
     * a single canonical file. This method is retained for backward
     * compatibility with existing per-session PTY flows.
     */
    fun writeCliBootstrapScript(sessionId: Int): String {
        val scriptDir = File(appFilesDir, "at-cli").apply { mkdirs() }
        val scriptPath = File(scriptDir, "adb_bootstrap_$sessionId.sh").absolutePath
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
            if (isAdbReady()) {
                onStatusLine("connected")
                return true
            }
            if (!hasIdentity()) {
                onStatusLine("pairing required: no ADB identity found")
                onStatusLine(
                    "open Settings -> Wireless Debugging -> Pair device with pairing code, " +
                        "then run: adb pair <host>:<port> <code>"
                )
                return false
            }
            val ready = adbSemantic.ensureReady { line -> onStatusLine(line) }
            if (!ready) {
                onStatusLine("not connected (identity present)")
            }
            return ready
        }

        override fun pairingState(): ATCliPairingState = when {
            hasIdentity() && isAdbReady() -> ATCliPairingState.READY
            !hasIdentity() -> ATCliPairingState.PAIRING_REQUIRED
            else -> ATCliPairingState.CONNECT_FAILED
        }

        override fun pair(host: String, port: Int, code: String): ATCliPairingResult =
            toCliResult(adbSemantic.pair(host, port, code))

        override fun pairDiscovered(code: String): ATCliPairingResult =
            toCliResult(adbSemantic.pairDiscovered(code))
    }

    private fun toCliResult(r: AtPairingResult): ATCliPairingResult =
        ATCliPairingResult(
            success = r.success,
            outcome = when (r.outcome) {
                AtPairingResult.PairingOutcome.SUCCESS -> ATCliPairingResult.Outcome.SUCCESS
                AtPairingResult.PairingOutcome.REJECTED -> ATCliPairingResult.Outcome.REJECTED
                AtPairingResult.PairingOutcome.SERVICE_NOT_FOUND -> ATCliPairingResult.Outcome.SERVICE_NOT_FOUND
                AtPairingResult.PairingOutcome.MULTIPLE_ENDPOINTS -> ATCliPairingResult.Outcome.MULTIPLE_ENDPOINTS
                AtPairingResult.PairingOutcome.FAILED -> ATCliPairingResult.Outcome.FAILED
            },
            deviceGuid = r.deviceGuid ?: "",
            error = r.error ?: "",
        )

    // ---- One-shot execution -------------------------------------------------

    /**
     * Execute one command via the ADB backend (one-shot).
     */
    suspend fun executeAdb(command: String, timeoutMs: Int = 30_000): ATExecutionResult =
        withContext(Dispatchers.IO) {
            adbEngine.execute(ATExecutionRequest(command, timeoutMs))
        }

    /**
     * Execute one command via the Local backend (one-shot).
     *
     * The command runs with CWD = app files directory, so relative paths
     * like `files/agent/foo.txt` resolve correctly. The shell's default
     * CWD is `/`, which is not useful for app-private file access.
     *
     * The canonical CLI bootstrap ([ensureCliBootstrapCurrent]) is sourced
     * first so the `adb` shell function is available in one-shot commands.
     * If the bootstrap references a stale APK path (after install -r), it is
     * automatically regenerated — no manual intervention required.
     */
    suspend fun executeLocal(command: String, timeoutMs: Int = 30_000): ATExecutionResult =
        withContext(Dispatchers.IO) {
            // sourceLine returns ". '<path>'\n" — strip trailing newline so the
            // "&&" chain stays on one line for /system/bin/sh.
            val sourceLine = ensureCliBootstrapCurrent()?.trimEnd('\n', ' ')
            val sourcePrefix = sourceLine?.let { "$it && " } ?: ""
            val wrapped = "cd ${shellQuote(appFilesDir.absolutePath)} && ${sourcePrefix}$command"
            localEngine.execute(ATExecutionRequest(wrapped, timeoutMs))
        }

    private fun shellQuote(s: String): String = "'$s'"

    // ---- Persistent session -------------------------------------------------

    /**
     * Open a persistent ADB terminal session (remote PTY).
     */
    suspend fun openAdbSession(
        command: String = "/system/bin/sh",
        initialRows: Int = 24,
        initialCols: Int = 80,
    ): ATTerminalSession? = withContext(Dispatchers.IO) {
        when (val result = adbEngine.openSession(
            ATSessionLaunch(command, initialRows, initialCols)
        )) {
            is ATOpenSessionResult.Success -> result.session
            is ATOpenSessionResult.Failure -> {
                android.util.Log.w(TAG, "openAdbSession failed: ${result.reason}")
                null
            }
        }
    }

    /**
     * Open a persistent Local terminal session.
     */
    suspend fun openLocalSession(
        command: String = "/system/bin/sh",
        initialRows: Int = 24,
        initialCols: Int = 80,
    ): ATTerminalSession? = withContext(Dispatchers.IO) {
        when (val result = localEngine.openSession(
            ATSessionLaunch(command, initialRows, initialCols)
        )) {
            is ATOpenSessionResult.Success -> result.session
            is ATOpenSessionResult.Failure -> {
                android.util.Log.w(TAG, "openLocalSession failed: ${result.reason}")
                null
            }
        }
    }

    /**
     * Open a Local PTY session AND inject the AT CLI `adb()` shell frontend.
     *
     * Uses [ensureCliBootstrapCurrent] for the same canonical bootstrap that
     * [executeLocal] uses, so the `adb` function is always backed by the
     * current install instance's APK path.
     */
    suspend fun openBootstrappedLocalSession(
        cliSessionId: Int,
        initialRows: Int = 24,
        initialCols: Int = 80,
    ): ATTerminalSession? = withContext(Dispatchers.IO) {
        val session = openLocalSession(initialRows = initialRows, initialCols = initialCols)
            ?: return@withContext null
        try {
            val sourceLine = ensureCliBootstrapCurrent() ?: run {
                android.util.Log.e(TAG, "openBootstrappedLocalSession: bootstrap unavailable (appApkPath not set)")
                session.close(true)
                return@withContext null
            }
            val injected = session.write((sourceLine + "\n").toByteArray(Charsets.UTF_8))
            if (injected < 0) {
                android.util.Log.e(TAG, "CLI bootstrap injection failed (write returned $injected)")
                session.close(true)
                return@withContext null
            }
            android.util.Log.i(TAG, "Local PTY bootstrapped with adb() frontend (cliSessionId=$cliSessionId)")
            session
        } catch (e: Exception) {
            android.util.Log.e(TAG, "openBootstrappedLocalSession failed: ${e.message}", e)
            session.close(true)
            null
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

    companion object {
        private const val TAG: String = "AtExecutionAdapter"

        /** The :at-cli AtCliMain entrypoint (shared in the AAR; loaded via app_process). */
        const val CLI_MAIN_CLASS: String = "com.at.cli.AtCliMain"

        /**
         * AG app-private identity profile (passed to ATAndroidConfig by the Host).
         * This is the AG's OWN profile — never the AndroidTerminal app's.
         */
        const val IDENTITY_PROFILE: String = "at-consumer/identity"
    }
}
