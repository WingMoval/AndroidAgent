package com.androidagent.app.provider

import android.util.Log
import com.androidagent.app.BuildConfig
import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiHttpResponse
import com.androidagent.provider.openai.OpenAiHttpTransport
import com.androidagent.provider.openai.OpenAiTransportNetworkException
import com.androidagent.provider.openai.OpenAiTransportTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.concurrent.thread

/**
 * The Android implementation of [OpenAiHttpTransport], built on
 * [HttpsURLConnection].
 *
 * It lives in `:app` rather than its own module because there is exactly one
 * Android consumer today. A second real Android consumer is what would justify
 * extracting `:provider-openai-compatible-android` — not anticipation.
 *
 * `HttpsURLConnection` is part of the Android platform, works from minSdk 26, and
 * needs no third-party HTTP dependency. OkHttp / Retrofit are deliberately NOT
 * introduced; if this transport hits a real, unsolvable blocker that becomes a
 * separate proposal rather than an inline swap.
 *
 * This class interprets nothing about the model API: it returns the status and
 * body verbatim, including error bodies, so the Provider keeps ownership of
 * HTTP-status mapping.
 */
/**
 * `open` (and with a couple of `open` seams) so the JVM cancellation regression
 * test can subclass it to disable the `android.util.Log` debug logging, which is
 * unavailable on the JVM test runtime. No production behaviour changes: the
 * production instance is still constructed exactly as before.
 */
open class AndroidOpenAiHttpTransport : OpenAiHttpTransport {

    override suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = open(request)
                val status = connection.responseCode
                // getInputStream() throws for >= 400, so the error body must come
                // from getErrorStream(). Losing it would strip the Provider of the
                // safe summary it needs for HttpStatus.
                val stream: InputStream? =
                    if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                OpenAiHttpResponse(statusCode = status, body = body)
            } catch (e: SocketTimeoutException) {
                throw OpenAiTransportTimeoutException("request timed out", e)
            } catch (e: IOException) {
                throw OpenAiTransportNetworkException("could not reach the endpoint", e)
            } finally {
                // Also the cancellation path: when the coroutine is cancelled the
                // finally block still runs and tears the socket down.
                connection?.disconnect()
            }
        }

    override suspend fun postStream(
        request: OpenAiHttpRequest,
        onLine: (String) -> Unit,
    ): OpenAiHttpResponse {
        // Open the connection and read the status line on the IO dispatcher (this
        // is a one-shot blocking connect and is cancellable at its suspension
        // point). The streaming body read is handled separately so it can be
        // actively torn down on coroutine cancellation (see [readStreamBody]).
        val connection = withContext(Dispatchers.IO) {
            try {
                val c = open(request)
                // Force the connect + response-headers exchange to happen here so a
                // non-2xx status is surfaced before the stream phase begins.
                @Suppress("UNUSED_EXPRESSION") c.responseCode
                c
            } catch (e: SocketTimeoutException) {
                throw OpenAiTransportTimeoutException("request timed out", e)
            } catch (e: IOException) {
                throw OpenAiTransportNetworkException("could not reach the endpoint", e)
            }
        }
        return try {
            val status = connection.responseCode
            if (status in 200..299) {
                // Deliver every body line as it arrives; the returned body is empty.
                // The Provider owns SSE framing. [readStreamBody] is the cancellable
                // bridge: on coroutine cancellation it actively tears the connection
                // down so the blocked socket read is interrupted, and the suspension
                // resumes as CANCELLED (it never turns a Stop into a Provider
                // Failure). A genuine network error inside the read resumes the
                // suspension with that IOException, which the caller classifies.
                //
                // The streaming suspension — and therefore its resume, in particular
                // the CANCELLED resume a Stop produces — is bound to the IO
                // dispatcher, never to the caller's context. Bound to a same-thread
                // context (the Host's Dispatchers.Main.immediate), a Stop on that
                // thread would resume the cancelled continuation INLINE and run the
                // whole downstream terminal pipeline inside the caller's Stop
                // handler, reordering the Host's own state writes (observed on a
                // real device: the final status stayed "Stopping..." after a
                // successful Stop). This keeps the unwind asynchronous, matching
                // the connect phase and the pre-fix blocking read.
                withContext(Dispatchers.IO) { readStreamBody(connection, onLine) }
                OpenAiHttpResponse(statusCode = status, body = "")
            } else {
                val body = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                OpenAiHttpResponse(statusCode = status, body = body)
            }
        } finally {
            // Always release the connection (normal EOF, error, or cancellation).
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Cancellable bridge for the streaming body read.
     *
     * `HttpURLConnection`'s blocking `readLine()` cannot observe coroutine
     * cancellation by itself: a thread blocked in a socket read does not wake when
     * its coroutine is cancelled. So the read is run on the IO dispatcher and the
     * suspension is made CANCELLABLE — on cancellation we actively tear down the
     * connection (close the input stream + `disconnect()`), which interrupts the
     * blocked read by surfacing a `SocketException`/`IOException` on the reader
     * thread. That is the production Stop path:
     *
     * ```text
     * AgentRuntime execution-Job cancel
     *   -> this suspension's invokeOnCancellation
     *   -> connection.disconnect() (closes the socket under the blocked read)
     *   -> readLine() throws -> the reader thread exits
     *   -> the cancelled suspension resumes as cancelled
     *   -> cancellation propagates -> exactly one RunFinished(Interrupted)
     * ```
     *
     * A successful read resumes the continuation exactly once (normal EOF); the
     * teardown only ever runs on the cancellation path, so there is no double
     * resume. The reader thread is a plain worker that delivers lines via
     * [onLine] (invoked on that thread, as before) and never resumes the
     * continuation itself — resumption is owned here.
     */
    private suspend fun readStreamBody(
        connection: HttpURLConnection,
        onLine: (String) -> Unit,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val readerThread = thread(name = "aga-stream-reader") {
                var stream: InputStream? = null
                try {
                    stream = openStream(connection)
                    stream!!.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (continuation.isCancelled) break
                            onLine(line)
                        }
                    }
                    // Normal EOF: the stream finished. Resume exactly once.
                    if (!continuation.isCancelled) continuation.resume(Unit)
                } catch (e: IOException) {
                    // Either a genuine network error, or our own cancellation
                    // teardown (disconnect closed the socket under the read).
                    // If the coroutine is already cancelled this is a cancellation,
                    // not a network failure — do NOT resume-with-exception, which
                    // would turn a user Stop into a Provider Failure. The
                    // cancellation resumes the suspension.
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                } catch (e: InterruptedException) {
                    // Cancellation teardown interrupted the thread while it was
                    // blocked in the read. This is a cancellation, not a failure:
                    // do NOT resume; the cancelled suspension resumes itself.
                    Thread.currentThread().interrupt()
                } finally {
                    closeStream(stream)
                }
            }
            // The cancellation bridge: actively close the streams and disconnect the
            // connection so the blocked readLine() is interrupted immediately. This
            // runs on the cancelling thread and is safe to call while the reader
            // thread is inside the read.
            continuation.invokeOnCancellation {
                runCatching { connection.disconnect() }
                readerThread.interrupt()
            }
        }
    }

    /**
     * Opens the response body for the streaming read. A seam so the JVM
     * cancellation regression test can exercise the REAL `HttpURLConnection`
     * blocking read (against a local loopback socket server) without needing the
     * Android framework. Production returns `connection.inputStream`.
     */
    protected open fun openStream(connection: HttpURLConnection): InputStream =
        connection.inputStream

    /** Releases the body stream. Production is a no-op (the stream is closed by its
     *  `use` block and the connection by the caller's `finally`). */
    protected open fun closeStream(stream: InputStream?) {
        stream?.close()
    }

    /**
     * Debug-only request logging. Routed through a seam so the JVM cancellation
     * regression test (which runs without the Android framework, where
     * `android.util.Log` is not available) can exercise the real transport; the
     * production transport logs via `android.util.Log` exactly as before.
     */
    protected open fun debugLog(message: String) {
        Log.d(TAG, message)
    }

    /** Opens and configures one POST connection. Never logs headers or body. */
    private fun open(request: OpenAiHttpRequest): HttpURLConnection =
        (URL(request.endpoint).openConnection() as HttpURLConnection).apply {
            // AGA.OBSERVABILITY-0: debug-only evidence of the ACTUAL request
            // endpoint (scheme + host + path, no query, no credential). This is
            // how the provider-truth gate proves the request route on device.
            if (BuildConfig.DEBUG) {
                debugLog("request endpoint: ${request.endpoint}")
            }
            requestMethod = "POST"
            connectTimeout = request.timeout.toMillis().toInt()
            readTimeout = request.timeout.toMillis().toInt()
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            // Never logged. Applied straight from the request.
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
        }

    private companion object {
        const val TAG: String = "AG.OpenAiTransport"
    }
}