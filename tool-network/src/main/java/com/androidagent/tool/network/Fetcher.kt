package com.androidagent.tool.network

import java.net.InetAddress

/**
 * Tool-local transport seam (Preflight §25 / §19).
 *
 * This is a TOOL-LOCAL INTERNAL IMPLEMENTATION DETAIL of :tool-network — the same
 * role as `AndroidShellToolImpl.Executor`. It exists so the tool's argument
 * handling, SSRF guard, and result mapping are deterministically testable on the
 * JVM (a fake Fetcher returns crafted addresses / bodies / statuses) while
 * production uses the single real App-Realm implementation ([AppHttpFetcher]).
 *
 * It is NOT a NetworkBackend / AppNetworkBackend / AtNetworkBackend / provider.
 * There is exactly one production implementation today. A future AT adapter (if
 * AT ever forms an independent Network Capability) would be a second Fetcher; the
 * model-side Tool contract is unaffected.
 *
 * Contract:
 *  - [fetch] performs the validated HTTP GET for one [UrlValidator.Target],
 *    following redirects (each hop re-validated by the caller via the guard),
 *    enforcing the byte bound, and returning a [FetchOutcome].
 *  - It performs NO approval and NO final SSRF decision on its own: the caller
 *    (NetworkToolImpl) resolves + classifies addresses via [PublicIpGuard] and
 *    passes the validated target in. The fetcher must not connect to a
 *    non-public address it was not given.
 *  - It throws [java.util.concurrent.CancellationException] unchanged on
 *    cancellation (frozen runtime semantics); it must not convert it to a
 *    TransportError.
 *  - It runs blocking I/O; the caller wraps it in Dispatchers.IO.
 */
internal fun interface Fetcher {
    suspend fun fetch(
        target: UrlValidator.Target,
        maxResponseBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): FetchOutcome
}

/**
 * The raw result of one transport fetch (before content-type policy and text
 * bound are applied by the tool). [bodyBytes] is the DECOMPRESSED body, capped at
 * [maxResponseBytes] by the fetcher. [httpStatus] is the final status after
 * redirects. [contentType] is the response Content-Type (may be null).
 * [truncatedBytes] is true if the decompressed stream exceeded the byte cap.
 */
internal data class FetchOutcome(
    val finalUrl: String,
    val httpStatus: Int,
    val contentType: String?,
    val bodyBytes: ByteArray,
    val bytesRead: Int,
    val redirectCount: Int,
    val truncatedBytes: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is FetchOutcome &&
        other.finalUrl == finalUrl &&
        other.httpStatus == httpStatus &&
        other.contentType == contentType &&
        other.bodyBytes.contentEquals(bodyBytes) &&
        other.bytesRead == bytesRead &&
        other.redirectCount == redirectCount &&
        other.truncatedBytes == truncatedBytes

    override fun hashCode(): Int = bodyBytes.contentHashCode()
}

/** A transport-level failure (no HTTP response was obtained, or one was invalid). */
internal class TransportError(
    val code: NetworkError,
    val detail: String,
    cause: Throwable? = null,
) : Exception(detail, cause)
