package com.androidagent.app.debug

import android.util.Log
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.AgentEventSink
import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.ToolApprovalPolicy
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.integration.at.AtExecutionAdapter
import com.androidagent.runtime.AgentRuntime
import com.at.engine.ATTerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * AG.DEBUG-0 — PC Debug Bridge (host/tooling layer only).
 *
 * A minimal TCP JSONL server bound to 127.0.0.1 that lets a PC debug client:
 *   - submit user input into the SAME [AgentRuntime] the UI uses (via
 *     [AgentRuntime.send]), and
 *   - observe the live [AgentEvent] execution stream as a side channel
 *     (also for UI-originated runs).
 *
 * This is NOT a second Agent, NOT an Agent loop, NOT part of AG Core / Runtime /
 * Tool / Provider. It only forwards input and re-emits events. The runtime's
 * existing single-active-run busy guard is reused: a [send] while busy is
 * answered with {"type":"busy"} and never queued.
 *
 * Security: binds 127.0.0.1 only. [start] is a no-op when not enabled; the Host
 * (ChatCompositionRoot) only calls it in debug builds.
 */
class DebugBridge(
    private val runtimeProvider: () -> AgentRuntime?,
    // AG.NETWORK-0: optional reference to the SAME ToolRegistry the agent uses, so
    // a debug client can DIRECTLY invoke a production tool (bypassing
    // ModelProvider / Agent Loop / LLM). Debug/test-only; never model-visible.
    private val toolRegistryProvider: (() -> ToolRegistry?)? = null,
    // AGA.OBSERVABILITY-1: optional Host hook invoked when a debug client sends a
    // prompt, so the Host can record the User block in the transcript exactly as
    // it does for a UI-originated send. Debug-only; no-op when unwired or release.
    private val userInputSink: ((String) -> Unit)? = null,
    // HOST-005: optional Host hook so a debug-originated Run updates the SAME
    // busy/Stop button state the UI path uses. Without this the Stop button
    // stays disabled (grey) during a bridge-originated Run and the user cannot
    // exercise the Stop control from the UI. Debug-only; no-op when unwired.
    private val onRunStateChanged: ((Boolean) -> Unit)? = null,
    // AG.V2-2B: optional AT Execution Adapter for persistent session validation.
    // Debug-only; null when not wired (release builds never wire it).
    private val adapterProvider: (() -> AtExecutionAdapter?)? = null,
    private val port: Int = DEFAULT_PORT,
    scope: CoroutineScope? = null,
) {

    private val bridgeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    // AG.V2-2B: persistent session registry (debug-only).
    private val sessions = ConcurrentHashMap<Int, ATTerminalSession>()
    private val sessionIds = AtomicInteger(0)

    @Volatile
    private var running = false

    /** Connected client writers, synchronized. */
    private val clients = mutableListOf<ClientWriter>()

    /**
     * All broadcast socket writes go through this single daemon thread: the
     * AgentEvent stream is emitted on the host's thread (the UI main thread for
     * a UI-originated Run), and a blocking socket write there trips Android's
     * NetworkOnMainThreadException and silently loses the stream. One dedicated
     * thread also keeps the JSONL lines in strict emit order.
     */
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ag-debug-send").apply { isDaemon = true }
    }

    /**
     * Sink wired into the Host's CompositeSink; broadcasts every AgentEvent to
     * all connected debug clients.
     */
    val eventSink: AgentEventSink = DebugStreamSink(this)

    /**
     * Model-stream observer wired by the Host alongside the transcript;
     * broadcasts reasoning/content deltas to all connected debug clients as
     * JSONL v0 incremental messages.
     */
    val streamSink: ModelStreamObserver = DebugStreamObserver(this)

    /**
     * Starts the listener. Returns true when this call is now listening.
     *
     * AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: binds 0.0.0.0 (all interfaces) in
     * debug builds so a PC on the same LAN can reach the bridge directly
     * without ADB reverse (which is unreliable on some USB/OTG setups).
     * This is a DEBUG-ONLY surface; release builds never create a DebugBridge.
     */
    fun start(): Boolean {
        if (running) return true
        val ss = try {
            ServerSocket().apply {
                // SO_REUSEADDR: survive TIME_WAIT sockets left by a previous
                // process/instance on the same port (instrumentation restarts).
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            Log.w(TAG, "debug bridge failed to bind 0.0.0.0:$port: ${e.message}")
            return false
        }
        serverSocket = ss
        running = true
        acceptJob = bridgeScope.launch { acceptLoop(ss) }
        Log.i(TAG, "debug bridge listening on 0.0.0.0:$port")
        return true
    }

    /** Stops accepting and closes all clients. Existing Agent Run is unaffected. */
    fun stop() {
        running = false
        acceptJob?.cancel()
        serverSocket?.close()
        synchronized(clients) {
            clients.forEach { it.close() }
            clients.clear()
        }
        sendExecutor.shutdownNow()
        bridgeScope.cancel()
    }

    val isRunning: Boolean get() = running

    private suspend fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val socket = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            handleClient(socket)
        }
    }

    private fun handleClient(socket: Socket) {
        val writer = ClientWriter(socket)
        synchronized(clients) { clients.add(writer) }
        Log.i(TAG, "debug client connected: ${socket.inetAddress}")
        writer.send(DebugProtocol.hello())
        bridgeScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                while (running) {
                    val line = reader.readLine() ?: break
                    val msg = DebugProtocol.parseClientLine(line)
                    when (msg) {
                        is DebugProtocol.ClientMessage.Ping -> writer.send(DebugProtocol.pong())

                        is DebugProtocol.ClientMessage.Send -> {
                            // Handle in a separate coroutine so the reader keeps
                            // reading; a second send during an active Run then
                            // reaches the runtime busy guard instead of being
                            // serialised behind the first run.
                            bridgeScope.launch { handleSend(msg.text, writer) }
                        }

                        is DebugProtocol.ClientMessage.ToolInvoke -> {
                            // AG.NETWORK-0 L2 Direct Tool Validation: call the SAME
                            // production Tool directly, no ModelProvider / Agent Loop
                            // / LLM. Runs in its own coroutine so the reader continues.
                            bridgeScope.launch { handleToolInvoke(msg.name, msg.arguments, writer) }
                        }

                        // AG.V2-2B: AT persistent session operations.
                        is DebugProtocol.ClientMessage.SessionOpen -> {
                            bridgeScope.launch { handleSessionOpen(msg.backend, writer) }
                        }

                        is DebugProtocol.ClientMessage.SessionWrite -> {
                            bridgeScope.launch { handleSessionWrite(msg.id, msg.text, msg.ctrlc, writer) }
                        }

                        is DebugProtocol.ClientMessage.SessionRead -> {
                            bridgeScope.launch { handleSessionRead(msg.id, msg.timeoutMs, writer) }
                        }

                        is DebugProtocol.ClientMessage.SessionResize -> {
                            bridgeScope.launch { handleSessionResize(msg.id, msg.rows, msg.cols, writer) }
                        }

                        is DebugProtocol.ClientMessage.SessionClose -> {
                            bridgeScope.launch { handleSessionClose(msg.id, writer) }
                        }

                        is DebugProtocol.ClientMessage.SessionState -> {
                            handleSessionState(msg.id, writer)
                        }

                        // AG.V2-2: ADB consumer bootstrap operations.
                        is DebugProtocol.ClientMessage.BootstrapState -> {
                            handleBootstrapState(writer)
                        }

                        is DebugProtocol.ClientMessage.BootstrapDiscover -> {
                            bridgeScope.launch { handleBootstrapDiscover(writer) }
                        }

                        is DebugProtocol.ClientMessage.BootstrapPair -> {
                            bridgeScope.launch { handleBootstrapPair(msg.code, writer) }
                        }

                        null -> writer.send(DebugProtocol.error("unrecognized message"))
                    }
                }
            } catch (_: Exception) {
                // Client disconnect: do NOT cancel or affect the Agent Run.
            } finally {
                synchronized(clients) { clients.remove(writer) }
                writer.close()
                Log.i(TAG, "debug client disconnected")
            }
        }
    }

    /**
     * AG.NETWORK-0 L2 — direct tool invocation. Calls the SAME production tool the
     * agent uses (via the shared [ToolRegistry].dispatch, with [ToolApprovalPolicy
     * .AllowAll] so a debug probe is never blocked by host policy), bypassing
     * ModelProvider / Agent Loop / LLM entirely. The full [ToolResult] is returned
     * as JSON so the raw fetch (success/output/error) can be judged with zero model
     * tokens. This is debug/test-only and shares the production implementation.
     */
    private suspend fun handleToolInvoke(name: String, arguments: String, writer: ClientWriter) {
        val registry = toolRegistryProvider?.invoke()
        if (registry == null) {
            writer.send(DebugProtocol.error("tool registry not wired (direct tool validation unavailable)"))
            return
        }
        val call = ToolCall(id = "debug-direct", name = name, arguments = arguments)
        val result = try {
            registry.dispatch(call, ToolApprovalPolicy.AllowAll)
        } catch (e: Exception) {
            writer.send(DebugProtocol.error("direct tool invoke failed: ${e::class.java.simpleName ?: "unknown"}"))
            return
        }
        writer.send(DebugProtocol.toolResult(name, result.success, result.output, result.error))
        Log.i(TAG, "direct tool invoke finished: $name success=${result.success}")
    }

    private suspend fun handleSend(text: String, writer: ClientWriter) {
        if (text.isBlank()) {
            writer.send(DebugProtocol.error("blank send rejected"))
            return
        }
        val runtime = runtimeProvider()
        if (runtime == null) {
            writer.send(DebugProtocol.error("runtime not ready"))
            return
        }
        if (runtime.isBusy) {
            writer.send(DebugProtocol.busy())
            return
        }
        writer.send(DebugProtocol.accepted())
        // AGA.OBSERVABILITY-1: mirror the UI send path so a debug-originated Run
        // also records its User block in the transcript (the Host decides how to
        // render it; the bridge only reports that a user input was accepted).
        userInputSink?.invoke(text)
        // HOST-005: mirror the UI busy state so the Stop button is enabled during
        // a bridge-originated Run, exactly as the UI onSend() path does.
        onRunStateChanged?.invoke(true)
        val outcome = try {
            runtime.send(text)
        } catch (e: Exception) {
            writer.send(DebugProtocol.error(e::class.java.simpleName ?: "unknown"))
        } finally {
            onRunStateChanged?.invoke(false)
        }
        Log.i(TAG, "debug run finished: $outcome")
    }

    private fun broadcast(event: AgentEvent) {
        val line = DebugProtocol.eventToJson(event).toString()
        broadcastLine(line)
    }

    private fun broadcastLine(line: String) {
        // Off the emitting (host/main) thread and strictly ordered: the send
        // executor is single-threaded. A writer whose socket/transport died
        // mid-run is dropped here (the reader eventually sees EOF too); without
        // this a half-dead tunnel silently swallows the stream.
        sendExecutor.execute {
            try {
                synchronized(clients) {
                    clients.removeAll { !it.send(line) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "broadcast failed: ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }

    private fun emitStream(kind: String, text: String?) {
        broadcastLine(DebugProtocol.streamToJson(kind, text).toString())
    }

    private class DebugStreamSink(private val bridge: DebugBridge) : AgentEventSink {
        override fun emit(event: AgentEvent) {
            bridge.broadcast(event)
        }
    }

    private class DebugStreamObserver(private val bridge: DebugBridge) : ModelStreamObserver {
        override fun onReasoningStarted() {
            bridge.emitStream(DebugProtocol.STREAM_REASONING_STARTED, null)
        }

        override fun onReasoningDelta(text: String) {
            bridge.emitStream(DebugProtocol.STREAM_REASONING_DELTA, text)
        }

        override fun onReasoningFinished() {
            bridge.emitStream(DebugProtocol.STREAM_REASONING_FINISHED, null)
        }

        override fun onContentStarted() {
            bridge.emitStream(DebugProtocol.STREAM_CONTENT_STARTED, null)
        }

        override fun onContentDelta(text: String) {
            bridge.emitStream(DebugProtocol.STREAM_CONTENT_DELTA, text)
        }

        override fun onContentFinished() {
            bridge.emitStream(DebugProtocol.STREAM_CONTENT_FINISHED, null)
        }
    }

    // ------------------------------------------------- AG.V2-2B session handlers

    private suspend fun handleSessionOpen(backend: String, writer: ClientWriter) {
        val adapter = adapterProvider?.invoke()
        if (adapter == null) {
            writer.send(DebugProtocol.error("adapter not wired (persistent session unavailable)"))
            return
        }
        val session = when {
            backend.equals("adb", true) -> adapter.openAdbSession()
            backend.equals("local", true) -> adapter.openLocalSession()
            backend.equals("local_cli", true) -> {
                // AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: Local PTY with AT CLI adb() frontend.
                val cliId = sessionIds.incrementAndGet()
                adapter.openBootstrappedLocalSession(cliSessionId = cliId)
            }
            else -> {
                writer.send(DebugProtocol.error("unknown backend: $backend (use 'adb', 'local', or 'local_cli')"))
                return
            }
        }
        if (session == null) {
            writer.send(DebugProtocol.sessionOpenFailed(backend, "openSession returned null"))
            return
        }
        val id = sessionIds.incrementAndGet()
        sessions[id] = session
        writer.send(DebugProtocol.sessionOpened(id, backend))
        Log.i(TAG, "session opened: id=$id backend=$backend")
    }

    private suspend fun handleSessionWrite(id: Int, text: String, ctrlc: Boolean, writer: ClientWriter) {
        val session = sessions[id]
        if (session == null) {
            writer.send(DebugProtocol.error("no session with id=$id"))
            return
        }
        val adapter = adapterProvider?.invoke() ?: run {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        val bytes = if (ctrlc) {
            adapter.writeCtrlC(session)
        } else {
            adapter.writeCommand(session, text)
        }
        writer.send(DebugProtocol.sessionWriteResult(id, bytes))
    }

    private suspend fun handleSessionRead(id: Int, timeoutMs: Int, writer: ClientWriter) {
        val session = sessions[id]
        if (session == null) {
            writer.send(DebugProtocol.error("no session with id=$id"))
            return
        }
        val adapter = adapterProvider?.invoke() ?: run {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        val data = adapter.readOutput(session, timeoutMs)
        if (data == null) {
            // EOF or error: session terminated.
            sessions.remove(id)
            writer.send(DebugProtocol.sessionData(id, "<EOF>"))
        } else {
            val text = String(data, Charsets.UTF_8)
            if (text.isNotEmpty()) {
                writer.send(DebugProtocol.sessionData(id, text))
            } else {
                // Timeout with no data.
                writer.send(DebugProtocol.sessionData(id, ""))
            }
        }
    }

    private suspend fun handleSessionResize(id: Int, rows: Int, cols: Int, writer: ClientWriter) {
        val session = sessions[id]
        if (session == null) {
            writer.send(DebugProtocol.error("no session with id=$id"))
            return
        }
        val adapter = adapterProvider?.invoke() ?: run {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        val ok = adapter.resize(session, rows, cols)
        writer.send(DebugProtocol.sessionResized(id, rows, cols, ok))
    }

    private suspend fun handleSessionClose(id: Int, writer: ClientWriter) {
        val session = sessions.remove(id)
        if (session == null) {
            writer.send(DebugProtocol.error("no session with id=$id"))
            return
        }
        val adapter = adapterProvider?.invoke() ?: run {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        adapter.closeSession(session)
        writer.send(DebugProtocol.sessionClosed(id))
        Log.i(TAG, "session closed: id=$id")
    }

    private fun handleSessionState(id: Int, writer: ClientWriter) {
        val session = sessions[id]
        if (session == null) {
            writer.send(DebugProtocol.error("no session with id=$id"))
            return
        }
        val adapter = adapterProvider?.invoke() ?: run {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        writer.send(DebugProtocol.sessionState(id, adapter.sessionState(session)))
    }

    // ------------------------------------------------- AG.V2-2 bootstrap handlers

    private fun handleBootstrapState(writer: ClientWriter) {
        val adapter = adapterProvider?.invoke()
        if (adapter == null) {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        val state = adapter.bootstrapState()
        val hasId = adapter.hasIdentity()
        writer.send(DebugProtocol.bootstrapState(state.name, hasId))
    }

    private suspend fun handleBootstrapDiscover(writer: ClientWriter) {
        val adapter = adapterProvider?.invoke()
        if (adapter == null) {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        val d = adapter.discoverPairing()
        writer.send(DebugProtocol.bootstrapDiscovery(d.endpointCount, d.serviceNotFound, d.multiple))
    }

    private suspend fun handleBootstrapPair(code: String, writer: ClientWriter) {
        val adapter = adapterProvider?.invoke()
        if (adapter == null) {
            writer.send(DebugProtocol.error("adapter not wired"))
            return
        }
        if (code.isBlank()) {
            writer.send(DebugProtocol.error("pairing code required"))
            return
        }
        val r = adapter.pairDiscovered(code)
        // DO NOT log the pairing code plaintext.
        writer.send(DebugProtocol.bootstrapPairResult(r.success, r.outcome.name, r.error ?: ""))
        Log.i(TAG, "bootstrap pair: success=${r.success} outcome=${r.outcome.name}")
    }

    /** One connected debug client (writer only; read handled by the caller). */
    private class ClientWriter(private val socket: Socket) {
        private val out: BufferedWriter = BufferedWriter(
            OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
        )

        /** Returns false when the client can no longer be written to. */
        @Synchronized
        fun send(line: String): Boolean = try {
            out.write(line)
            out.newLine()
            out.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "client send failed: ${e::class.java.simpleName}: ${e.message}")
            false
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG: String = "AG.DebugBridge"
        const val DEFAULT_PORT: Int = 45678
    }
}