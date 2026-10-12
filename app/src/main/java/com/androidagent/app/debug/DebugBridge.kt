package com.androidagent.app.debug

import android.util.Log
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.AgentEventSink
import com.androidagent.core.ModelStreamObserver
import com.androidagent.runtime.AgentRuntime
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private val port: Int = DEFAULT_PORT,
    scope: CoroutineScope? = null,
) {

    private val bridgeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

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

    /** Starts the listener. Returns true when this call is now listening. */
    fun start(): Boolean {
        if (running) return true
        val ss = try {
            ServerSocket().apply {
                // SO_REUSEADDR: survive TIME_WAIT sockets left by a previous
                // process/instance on the same port (instrumentation restarts).
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", port))
            }
        } catch (e: Exception) {
            Log.w(TAG, "debug bridge failed to bind 127.0.0.1:$port: ${e.message}")
            return false
        }
        serverSocket = ss
        running = true
        acceptJob = bridgeScope.launch { acceptLoop(ss) }
        Log.i(TAG, "debug bridge listening on 127.0.0.1:$port")
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
        val outcome = try {
            runtime.send(text)
        } catch (e: Exception) {
            writer.send(DebugProtocol.error(e::class.java.simpleName ?: "unknown"))
            return
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