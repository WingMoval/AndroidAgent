package com.androidagent.app.provider

import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiTransportNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM regression for the Android transport's CANCELLABLE streaming read
 * (AG.HOST-STOP-1.ANDROID-TRANSPORT-CANCEL.FIX).
 *
 * The production Stop path is: `AgentRuntime` execution-Job cancel -> the
 * transport's streaming suspension observes cancellation -> it actively tears the
 * connection down (`connection.disconnect()`) so the thread blocked in a
 * `HttpURLConnection` `readLine()` is interrupted -> the suspension resumes as
 * CANCELLED -> cancellation propagates -> exactly one `RunFinished(Interrupted)`.
 *
 * The bug this guards against: a plain blocking `readLine()` on the body does NOT
 * react to coroutine cancellation, so a user Stop leaves the stream running to
 * natural completion (observed on a real device: the fixture streamed all 600
 * chunks despite a Stop). These tests drive the REAL `HttpURLConnection` (against a
 * local loopback socket server, no Android framework needed) through the real
 * `AndroidOpenAiHttpTransport.postStream` and prove the cancellation bridge.
 *
 * No new transport abstraction is introduced: the production transport is used
 * as-is; only a local loopback server stands in for the endpoint.
 */
class AndroidTransportCancelTest {

    // ---------------------------------------------------------------------------
    // Local loopback streaming server (real socket; the client is a real
    // HttpURLConnection). Sends lines with a delay, then either closes (normal
    // EOF) or holds the connection open (so the client blocks in readLine).
    // ---------------------------------------------------------------------------

    private class StreamServer(
        private val lines: Int,
        private val delayMillis: Long,
        private val holdAfterLines: Boolean,
        private val clientGone: AtomicBoolean,
        // AG.HOST-STOP-1.NATURAL-COMPLETION.CLOSEOUT (D3): when true, send a
        // final `finish_reason` chunk and a `data: [DONE]` line before closing,
        // mirroring the OpenAI-compatible fixture's natural-completion shape.
        private val emitDone: Boolean = false,
    ) : AutoCloseable {
        lateinit var serverSocket: ServerSocket
        @Volatile var port: Int = 0
        @Volatile var accepted = CountDownLatch(1)
        @Volatile var serverError: String? = null
        @Volatile private var client: Socket? = null
        @Volatile private var serverThread: Thread? = null

        fun start() {
            serverSocket = ServerSocket(0)
            port = serverSocket.localPort
            serverThread = thread(name = "test-stream-server") {
                try {
                    val c = serverSocket.accept()
                    client = c
                    accepted.countDown()
                    runCatching {
                        // Consume the request (headers until the blank line).
                        val in_ = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
                        while (true) {
                            val h = in_.readLine() ?: break
                            if (h.isEmpty()) break
                        }
                    }
                    val out = c.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.flush()
                    var sendFailed = false
                    for (i in 1..lines) {
                        if (sendFailed) break
                        runCatching {
                            out.write("fixture token ${i.toString().padStart(4, '0')}\r\n".toByteArray(Charsets.UTF_8))
                            out.flush()
                        }.onFailure {
                            sendFailed = true
                            clientGone.set(true)
                        }
                        if (delayMillis > 0) Thread.sleep(delayMillis)
                    }
                    if (emitDone && !sendFailed) {
                        // Natural completion: terminal finish_reason chunk + [DONE].
                        runCatching {
                            out.write(
                                "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\r\n"
                                    .toByteArray(Charsets.UTF_8)
                            )
                            out.write("data: [DONE]\r\n".toByteArray(Charsets.UTF_8))
                            out.flush()
                        }.onFailure {
                            sendFailed = true
                            clientGone.set(true)
                        }
                    }
                    if (holdAfterLines && !sendFailed) {
                        // Hold the connection open so the client blocks in readLine.
                        Thread.sleep(60_000)
                    }
                    // Normal EOF path (or post-hold close): close so the client's
                    // readLine returns null. Mark clientGone for the tests that watch
                    // the disconnect.
                    runCatching { c.close() }
                    clientGone.set(true)
                } catch (t: Throwable) {
                    serverError = t.toString()
                    // Unblock a waiting test even on failure.
                    if (accepted.count == 1L) accepted.countDown()
                }
            }
        }

        override fun close() {
            runCatching { client?.close() }
            runCatching { serverSocket.close() }
            serverThread?.interrupt()
        }
    }

    private fun request(port: Int): OpenAiHttpRequest =
        OpenAiHttpRequest(
            endpoint = "http://127.0.0.1:$port/v1/chat/completions",
            // Connection: close disables HttpURLConnection's keep-alive pool so each
            // call opens a fresh socket (a pooled dead socket from a prior test would
            // otherwise be reused and fail). No Authorization header is sent here on
            // purpose (the local server ignores it); this keeps the request secret-free.
            headers = mapOf("Connection" to "close", "Content-Type" to "application/json"),
            body = "{}",
            timeout = Duration.ofSeconds(30),
        )

    /**
     * The production transport, minus its `android.util.Log` debug logging (which is
     * unavailable in a JVM unit test). Everything else �?the real
     * `HttpURLConnection`, the cancellable read bridge, the disconnect teardown �?is
     * exactly the production code under test.
     */
    private fun jvmTransport(): AndroidOpenAiHttpTransport =
        object : AndroidOpenAiHttpTransport() {
            override fun debugLog(message: String) {
                // No-op: android.util.Log is not available on the JVM test runtime.
            }
        }

    // ---------------------------------------------------------------------------
    // E. normal stream without cancel -> all lines delivered, clean completion.
    // ---------------------------------------------------------------------------

    @Test
    fun normalStreamWithoutCancelDeliversAllLinesAndCompletes() = runBlocking {
        StreamServer(lines = 5, delayMillis = 20, holdAfterLines = false, clientGone = AtomicBoolean(false))
            .use { server ->
                server.start()
                // Do NOT block on accept here: if the client fails to connect we want
                // to run postStream and observe the client error, not time out first.
                val received = mutableListOf<String>()
                val transport = jvmTransport()
                var clientError: Throwable? = null
                val resp = runCatching {
                    transport.postStream(request(server.port)) { line ->
                        if (line.isNotBlank()) received += line
                    }
                }.onFailure { clientError = it }.getOrNull()
                assertTrue(
                    resp != null,
                    "postStream must complete; clientError=$clientError (cause=${clientError?.cause}) serverError=${server.serverError} received=$received",
                )
                assertEquals(200, resp!!.statusCode)
                assertEquals(
                    listOf(
                        "fixture token 0001", "fixture token 0002", "fixture token 0003",
                        "fixture token 0004", "fixture token 0005",
                    ),
                    received,
                )
            }
    }

    // ---------------------------------------------------------------------------
    // C. cancel while readLine is BLOCKED -> the blocked read is interrupted
    //    (CANCEL_WHILE_READLINE_BLOCKED = INTERRUPTS_IO): the stream is cut far
    //    short of natural completion, the server observes the disconnect, and no
    //    background reader is leaked.
    // ---------------------------------------------------------------------------

    @Test
    fun cancelWhileReadBlockedInterruptsIoAndStopsStreamEarly() = runBlocking {
        val clientGone = AtomicBoolean(false)
        StreamServer(lines = 600, delayMillis = 40, holdAfterLines = true, clientGone)
            .use { server ->
                server.start()
                val transport = jvmTransport()
                val received = mutableListOf<String>()

                // Let a few lines arrive so the client is inside the read loop, then
                // cancel the surrounding call (the production Stop cancels the
                // execution child's Job; cancelling this call is the equivalent).
                val runner = launch {
                    transport.postStream(request(server.port)) { line ->
                        if (line.isNotBlank()) received += line
                    }
                }
                // The connection is live as soon as lines start arriving; waiting for
                // the first lines both proves the connect succeeded and puts the
                // client inside the read loop. (Do NOT gate on server.accept: the
                // client can be flowing before the server's accept() returns, so that
                // await is a race.)
                withTimeout(10000) {
                    while (received.size < 3) {
                        kotlinx.coroutines.delay(5)
                    }
                }
                // Stop: cancel the in-flight transport call.
                runner.cancel()
                runner.join()

                // CANCEL_WHILE_READLINE_BLOCKED = INTERRUPTS_IO: the stream was cut
                // far short of its natural 600 lines. Before the fix the blocking
                // readLine() ignored cancellation and the stream ran to natural
                // completion (600/600, observed on a real device); now the Stop tears
                // the socket down and the blocked read is interrupted.
                assertTrue(
                    received.size < 600,
                    "cancellation must stop the stream early (got ${received.size}/600)",
                )
                // And the transport call completed (cancellation resumed the
                // suspension) rather than hanging in the blocked read.
                assertTrue(
                    !runner.isActive,
                    "the transport call must have completed after the Stop",
                )
            }
    }

    // ---------------------------------------------------------------------------
    // Cancellation semantics: a Stop must surface as a CancellationException at the
    // transport boundary (preserving the structured Interrupted semantic), NEVER as
    // a network/provider failure.
    // ---------------------------------------------------------------------------

    @Test
    fun stopSurfacesAsCancellationNotNetworkFailure() = runBlocking {
        StreamServer(lines = 600, delayMillis = 40, holdAfterLines = true, clientGone = AtomicBoolean(false))
            .use { server ->
                server.start()
                val transport = jvmTransport()

                var thrown: Throwable? = null
                val runner = launch {
                    try {
                        transport.postStream(request(server.port)) { }
                    } catch (t: Throwable) {
                        thrown = t
                    }
                }
                // Let the read start (a couple of lines delivered) before the Stop.
                // Do NOT gate on server.accept: the client can be flowing before the
                // server's accept() returns, so that await is a race.
                withTimeout(10000) {
                    kotlinx.coroutines.delay(400)
                }
                runner.cancel()
                runner.join()

                // A user Stop must be a CANCELLATION, never a transport network
                // failure (which would become a Provider Failure / RunFinished(Failure)).
                assertTrue(
                    thrown !is OpenAiTransportNetworkException,
                    "a Stop must never be classified as a transport network failure (got: $thrown)",
                )
                assertTrue(
                    thrown == null || thrown is CancellationException,
                    "a Stop must surface as a cancellation at the transport boundary (got: $thrown)",
                )
            }
    }

    // ---------------------------------------------------------------------------
    // Dispatch: the Stop-induced cancellation unwind must NOT run inline on the
    // thread that cancels the stream. The production Host cancels from
    // Dispatchers.Main.immediate; Dispatchers.Unconfined models those resumption
    // semantics here (a suspension registered on the caller's context resumes
    // INLINE on the cancelling thread). If the unwind ran inline there, the whole
    // downstream terminal pipeline (Core finalization -> RunFinished -> Host
    // render -> send() completion) would execute synchronously inside the
    // caller's Stop handler — on a real device that reordered the Host's own
    // status writes and left the UI stuck at "Stopping..." after a successful
    // Stop (AG.HOST-STOP-1 residue). The streaming suspension is bound to the IO
    // dispatcher precisely so the unwind is dispatched, never inline.
    // ---------------------------------------------------------------------------

    @Test
    fun stopDoesNotUnwindInlineOnTheCancellingThread() = runBlocking {
        val clientGone = AtomicBoolean(false)
        StreamServer(lines = 600, delayMillis = 40, holdAfterLines = true, clientGone)
            .use { server ->
                server.start()
                val transport = jvmTransport()
                val received = mutableListOf<String>()
                val unwindThread = AtomicReference<Thread?>(null)

                val runner = launch(Dispatchers.Unconfined) {
                    try {
                        transport.postStream(request(server.port)) { line ->
                            if (line.isNotBlank()) received += line
                        }
                    } finally {
                        // Records exactly where the cancellation unwind lands.
                        unwindThread.set(Thread.currentThread())
                    }
                }
                // Let the read loop start (same gate as the C test above; do NOT
                // gate on server.accept: the client can be flowing before accept).
                withTimeout(10000) {
                    while (received.size < 3) {
                        kotlinx.coroutines.delay(5)
                    }
                }

                // Stop from THIS thread (the caller's thread). A Main.immediate-like
                // context would resume the cancelled continuation right here, inside
                // cancel() itself, running the unwind inline on this thread.
                val cancellingThread = Thread.currentThread()
                runner.cancel()
                runner.join()

                assertTrue(!runner.isActive, "the transport call must complete after the Stop")
                val unwoundOn = unwindThread.get()
                assertTrue(
                    unwoundOn != null && unwoundOn !== cancellingThread,
                    "the cancellation unwind must be dispatched, not inline on the cancelling " +
                        "thread (unwoundOn=$unwoundOn, cancellingThread=$cancellingThread); " +
                        "an inline unwind runs the downstream terminal pipeline inside the " +
                        "caller's Stop handler (AG.HOST-STOP-1 residue)",
                )
            }
    }

    // ---------------------------------------------------------------------------
    // D3 (AG.HOST-STOP-1.NATURAL-COMPLETION.CLOSEOUT): a VALID, properly-terminated
    // stream (content chunks + finish_reason + `data: [DONE]` + physical EOF) must
    // make postStream return NORMALLY — with a bounded, fast completion and NO
    // timeout. This is the deterministic half of the D3 adjudication: it proves
    // the transport's normal-completion path is sound (readLine loop -> EOF ->
    // resume -> finally disconnect). The D3 device failure is therefore NOT a
    // transport normal-path defect: the byte-level trace of the Python fixture
    // showed the fixture emitted all 7 data events but never closed the socket
    // after `data: [DONE]` (HTTP/1.1 keep-alive, no Content-Length, no chunked
    // framing), so the client correctly waited for an EOF that the fixture never
    // produced, until its read timeout fired.
    // ---------------------------------------------------------------------------

    @Test
    fun naturalStreamWithDoneAndEofCompletesWithoutTimeout() = runBlocking {
        StreamServer(
            lines = 5,
            delayMillis = 20,
            holdAfterLines = false,
            clientGone = AtomicBoolean(false),
            emitDone = true,
        ).use { server ->
            server.start()
            val received = mutableListOf<String>()
            val transport = jvmTransport()
            var clientError: Throwable? = null
            val startedAt = System.currentTimeMillis()
            // Bounded wait: if the transport waits for a timeout instead of
            // returning at EOF, withTimeout trips long before the 30 s socket
            // read timeout would. NO timeout-based PASS is possible here.
            val resp = runCatching {
                withTimeout(10_000) {
                    transport.postStream(request(server.port)) { line ->
                        if (line.isNotBlank()) received += line
                    }
                }
            }.onFailure { clientError = it }.getOrNull()
            val elapsedMs = System.currentTimeMillis() - startedAt
            assertTrue(
                resp != null,
                "a properly-terminated stream must make postStream return normally; " +
                    "clientError=$clientError (cause=${clientError?.cause}) " +
                    "serverError=${server.serverError} received=$received",
            )
            assertEquals(200, resp!!.statusCode)
            assertTrue(
                received.size == 7,
                "all 7 data lines (5 content + finish_reason + [DONE]) must be delivered, got: $received",
            )
            assertTrue(
                elapsedMs < 5_000,
                "natural completion must be fast (took ${elapsedMs} ms); waiting means the " +
                    "client was still blocked in readLine waiting for an EOF",
            )
        }
    }

    // ---------------------------------------------------------------------------
    // D3 GOLDEN DISCRIMINATOR: the ONLY difference between these two servers is
    // the connection header after the final `data: [DONE]` byte:
    //
    //   (a) `Connection: keep-alive` (the pre-fix fixture): the server sends the
    //       full stream, then holds the socket open for 8 s without closing it.
    //       The transport must NOT return during that window: it has no way to
    //       know the body is over, so it blocks in readLine until EOF/timeout.
    //       This is the D3 device failure (SocketTimeoutException after a fully
    //       received stream) in miniature — the client was never at fault.
    //
    //   (b) `Connection: close` (the post-fix fixture): the server closes right
    //       after the final byte; the transport returns at EOF, fast, normally.
    //
    // Both servers send the IDENTICAL 592-byte stream. If (a) times out and
    // (b) passes, the D3 owner is the FIXTURE'S HTTP framing, not the Android
    // transport.
    // ---------------------------------------------------------------------------

    @Test
    fun keepAliveAfterDoneHoldsReadWhileConnectionCloseCompletes() = runBlocking {
        // (a) keep-alive after [DONE]: server holds the socket open 8 s.
        StreamServer(
            lines = 5,
            delayMillis = 20,
            holdAfterLines = true,
            clientGone = AtomicBoolean(false),
            emitDone = true,
        ).use { server ->
            server.start()
            val transport = jvmTransport()
            var completedBefore = false
            var clientError: Throwable? = null
            runCatching {
                withTimeout(4_000) {
                    transport.postStream(request(server.port)) { }
                    completedBefore = true
                }
            }.onFailure { clientError = it }
            assertTrue(
                !completedBefore && clientError is CancellationException,
                "with a keep-alive connection held open after [DONE], postStream must still " +
                    "be blocked in readLine (no EOF available); it completed=$completedBefore, " +
                    "clientError=$clientError — completing early would mean the transport " +
                    "guesses EOF from [DONE], which it does not (it waits for physical EOF)",
            )
        }

        // (b) Connection: close after [DONE]: server closes right after the last byte.
        StreamServer(
            lines = 5,
            delayMillis = 20,
            holdAfterLines = false,
            clientGone = AtomicBoolean(false),
            emitDone = true,
        ).use { server ->
            server.start()
            val transport = jvmTransport()
            var clientError: Throwable? = null
            val resp = runCatching {
                withTimeout(10_000) { transport.postStream(request(server.port)) { } }
            }.onFailure { clientError = it }.getOrNull()
            assertTrue(
                resp != null,
                "with Connection: close (the post-fix fixture framing), postStream must " +
                    "return normally at EOF; clientError=$clientError " +
                    "serverError=${server.serverError}",
            )
            assertEquals(200, resp!!.statusCode)
        }
    }
}
