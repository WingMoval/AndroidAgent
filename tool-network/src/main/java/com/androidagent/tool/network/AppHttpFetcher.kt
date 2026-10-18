package com.androidagent.tool.network

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * The single production [Fetcher]: an App-Realm HTTPS GET over the Android
 * platform [HttpsURLConnection] (the same low-level class the provider transport
 * uses). NO new third-party dependency (Preflight §12, §16).
 *
 * Behavior, frozen:
 *  - HTTPS only, GET, no client headers beyond Accept / Accept-Encoding.
 *  - Follows up to [NetworkToolImpl.MAX_REDIRECT_HOPS] hops. EVERY hop re-runs the
 *    full gate: scheme https + host normalize + resolve + ALL-IPs public (via
 *    [PublicIpGuard]) — a redirect to a private/loopback/metadata target or an
 *    https->http downgrade is denied.
 *  - Streaming read with a running byte counter; the DECOMPRESSED stream is capped
 *    at [maxResponseBytes] (decompression-bomb guard). The cap is on decompressed
 *    bytes, read as they arrive — never "read all then truncate".
 *  - gzip/deflate decompressed transparently (Accept-Encoding requested).
 *  - connectTimeout / readTimeout; socket torn down in finally on every path
 *    (success, timeout, error, cancel).
 *  - Cancellation: a [java.util.concurrent.CancellationException] is rethrown
 *    unchanged; it is never converted to a TransportError.
 *
 * DNS-rebinding posture (Preflight §16, controller override): we do NOT hand-write
 * a raw-SSLSocket HTTP client (that would re-implement HTTP framing/chunked/gzip/
 * redirect/TLS/SNI/cert handling). We rely on [HttpsURLConnection]'s own
 * resolution + TLS. Consequence: the connect target is whatever the transport
 * re-resolves, so a narrow DNS-rebinding window REMAINS. This is the documented
 * PARTIAL defense + KNOWN_RESIDUAL_RISK (see step doc §13); it is NOT closed by
 * trust-all / hostname bypass (none is present — TLS validation + hostname
 * verification stay fully ON).
 */
internal class AppHttpFetcher(
    private val resolver: (String) -> Array<InetAddress> = ::defaultResolve,
) : Fetcher {

    override suspend fun fetch(
        target: UrlValidator.Target,
        maxResponseBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): FetchOutcome {
        var current: UrlValidator.Target = target
        var redirectCount = 0

        while (true) {
            coroutineContext.ensureActive() // cooperative cancellation point between hops

            val url = buildUrl(current)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false // we drive redirects (per-hop re-validation)
                useCaches = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
            }
            try {
                val status = connection.responseCode

                // Redirect handling (301/302/303/307/308).
                if (status in REDIRECT_STATUSES) {
                    val location = connection.getHeaderField("Location")
                    if (location == null) {
                        throw TransportError(NetworkError.REDIRECT_LIMIT, "redirect without Location")
                    }
                    redirectCount++
                    if (redirectCount > NetworkToolImpl.MAX_REDIRECT_HOPS) {
                        throw TransportError(NetworkError.REDIRECT_LIMIT, "too many redirects")
                    }
                    val next = resolveRedirect(location, current)
                    current = next
                    continue
                }

                // A reached response (any non-redirect status) -> read the body.
                val isErrorBody = status !in 200..299
                val rawStream: InputStream? =
                    if (isErrorBody) connection.errorStream else connection.inputStream

                val (bytes, bytesRead, truncated) = readBounded(
                    rawStream,
                    connection.getContentEncoding(),
                    maxResponseBytes,
                )
                // (readBounded is suspend; it checks cancellation while reading.)
                val contentType = connection.getHeaderField("Content-Type")
                return FetchOutcome(
                    finalUrl = connection.url.toString(),
                    httpStatus = status,
                    contentType = contentType,
                    bodyBytes = bytes,
                    bytesRead = bytesRead,
                    redirectCount = redirectCount,
                    truncatedBytes = truncated,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                // Distinguish connect vs read by what phase we were in; conservatively
                // report READ_TIMEOUT (the body-read phase is the long one). A connect
                // timeout also surfaces as SocketTimeoutException on some stacks.
                throw TransportError(timeoutCodeFor(e), "request timed out")
            } catch (e: java.security.cert.CertificateException) {
                throw TransportError(NetworkError.TLS_FAILURE, "certificate validation failed")
            } catch (e: javax.net.ssl.SSLException) {
                throw TransportError(NetworkError.TLS_FAILURE, "TLS handshake failed")
            } catch (e: IOException) {
                throw TransportError(NetworkError.TRANSPORT_FAILURE, "network I/O failure: ${e.message ?: "unknown"}", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    // --------------------------------------------------------------- redirect

    /** Re-validate a redirect target and return the next [UrlValidator.Target]. */
    private fun resolveRedirect(location: String, from: UrlValidator.Target): UrlValidator.Target {
        // Absolute or relative Location.
        val absolute = try {
            URL(location)
        } catch (_: Exception) {
            URL(URL(from.rawUrl), location)
        }
        // https -> http downgrade is denied.
        val scheme = absolute.protocol?.lowercase()
        if (scheme != "https") {
            throw TransportError(NetworkError.DESTINATION_BLOCKED, "redirect to non-https scheme is denied")
        }
        val next = when (val v = UrlValidator.validate(absolute.toString())) {
            is UrlValidator.ParseResult.Error ->
                throw TransportError(NetworkError.INVALID_URL, "redirect target is invalid")
            is UrlValidator.ParseResult.Ok -> v.target
        }
        // Re-resolve + re-validate ALL IPs for the redirect hop.
        val addrs = try {
            if (next.isIpLiteral) arrayOf(InetAddress.getByName(next.host)) else resolver(next.host)
        } catch (e: java.net.UnknownHostException) {
            throw TransportError(NetworkError.DNS_FAILURE, "redirect host did not resolve")
        }
        if (!PublicIpGuard.allPublic(addrs)) {
            throw TransportError(NetworkError.DESTINATION_BLOCKED, "redirect target is not a public network address")
        }
        return next
    }

    // --------------------------------------------------------------- read

    private suspend fun readBounded(
        stream: InputStream?,
        contentEncoding: String?,
        maxBytes: Int,
    ): Triple<ByteArray, Int, Boolean> {
        if (stream == null) return Triple(ByteArray(0), 0, false)
        return stream.use { src ->
            val wrapped: InputStream = when (contentEncoding?.lowercase()) {
                "gzip" -> GZIPInputStream(src)
                "deflate" -> InflaterInputStream(src)
                else -> src
            }
            wrapped.use { body ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                var truncated = false
                while (true) {
                    coroutineContext.ensureActive()
                    val n = body.read(buffer)
                    if (n == -1) break
                    val toWrite = minOf(n, maxBytes - total)
                    if (toWrite > 0) {
                        out.write(buffer, 0, toWrite)
                        total += toWrite
                    }
                    if (total >= maxBytes) {
                        truncated = true
                        // Consume/discard the rest so the stream closes cleanly.
                        while (body.read(buffer) != -1) {
                            if (total >= maxBytes + DRAIN_CAP) break
                        }
                        break
                    }
                }
                Triple(out.toByteArray(), total, truncated)
            }
        }
    }

    private fun buildUrl(t: UrlValidator.Target): URL =
        URL("https", t.host, t.port, if (t.pathAndQuery.startsWith("/")) t.pathAndQuery else "/${t.pathAndQuery}")

    private fun timeoutCodeFor(e: SocketTimeoutException): NetworkError {
        // The message on Android/OkHttp-backed stacks often says "timeout" without
        // phase. We map connect-phase (no response yet) to CONNECT_TIMEOUT and
        // read-phase to READ_TIMEOUT. Without a reliable phase signal, default to
        // READ_TIMEOUT (the body read is where long blocks happen).
        val m = (e.message ?: "").lowercase()
        return if (m.contains("connect")) NetworkError.CONNECT_TIMEOUT else NetworkError.READ_TIMEOUT
    }

    companion object {
        private const val DRAIN_CAP: Int = 1024 * 1024 // safety cap on post-truncation drain
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        private fun defaultResolve(host: String): Array<InetAddress> = InetAddress.getAllByName(host)
    }
}
