package com.androidagent.tool.network

import java.net.IDN
import java.net.URI

/**
 * §7 URL normalization + validation pipeline. Pure (no network I/O except the
 * optional caller-supplied resolver is NOT called here — resolution happens in the
 * Fetcher, which then hands every address to [PublicIpGuard]).
 *
 * Output is a normalized [Target] the Fetcher can connect to. Every failure is a
 * specific [UrlError] so the tool can map it to the frozen error code.
 */
internal object UrlValidator {

    enum class UrlError { INVALID_URL, UNSUPPORTED_SCHEME }

    /** A validated, normalized fetch target. */
    data class Target(
        val rawUrl: String,
        val host: String,        // normalized ASCII host (lowercase, no trailing dot, punycode)
        val port: Int,           // resolved port (443 default for https)
        val pathAndQuery: String, // path + "?" + query, or "" / "?" as appropriate
        val isIpLiteral: Boolean, // host is a numeric IP literal (v4/v6)
    )

    sealed interface ParseResult {
        data class Ok(val target: Target) : ParseResult
        data class Error(val error: UrlError) : ParseResult
    }

    /**
     * Parses + validates one fetch URL.
     *
     * Order: parse -> scheme (https) -> host normalize (lowercase, trailing dot,
     * IDN->ASCII, reject credentials) -> port -> IP-literal detection.
     *
     * [url] must be an absolute https URL. Any other scheme, missing host,
     * embedded credentials, or unparseable input is rejected here (no network).
     */
    fun validate(url: String): ParseResult {
        if (url.isBlank()) return ParseResult.Error(UrlError.INVALID_URL)

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return ParseResult.Error(UrlError.INVALID_URL)
        }

        // Scheme must be exactly https (case-insensitive).
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            // http is explicitly UNSUPPORTED_SCHEME; file/ftp/data/etc. too.
            return ParseResult.Error(UrlError.UNSUPPORTED_SCHEME)
        }

        // No embedded credentials ("https://user:pass@host/").
        if (uri.userInfo != null) return ParseResult.Error(UrlError.INVALID_URL)

        var host = uri.host
        if (host == null || host.isBlank()) return ParseResult.Error(UrlError.INVALID_URL)

        // Normalize: lowercase, strip a single trailing dot (FQDN form).
        host = host.lowercase()
        if (host.endsWith(".")) host = host.dropLast(1)
        if (host.isBlank()) return ParseResult.Error(UrlError.INVALID_URL)

        // IDN -> ASCII (punycode). Non-ASCII hostnames go through IDN.toASCII.
        if (host.any { it.code > 0x7F }) {
            host = try {
                IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
            } catch (_: Exception) {
                return ParseResult.Error(UrlError.INVALID_URL)
            }
            if (host.isBlank()) return ParseResult.Error(UrlError.INVALID_URL)
        }

        // Port: default 443 for https; explicit must be 1..65535.
        val port = if (uri.port == -1) 443 else uri.port
        if (port !in 1..65535) return ParseResult.Error(UrlError.INVALID_URL)

        // Path + query (may be empty).
        val path = uri.path?.ifEmpty { null } ?: ""
        val query = uri.query
        val pathAndQuery = when {
            query != null -> "$path?$query"
            else -> path
        }

        // IP-literal detection: a v4 literal has 3 dots of digits; a v6 literal
        // is already bracket-stripped by URI.host (it returns the bare address).
        val isIpLiteral = isIpLiteral(host)

        return ParseResult.Ok(
            Target(
                rawUrl = url,
                host = host,
                port = port,
                pathAndQuery = pathAndQuery,
                isIpLiteral = isIpLiteral,
            )
        )
    }

    /**
     * Syntactic (NO DNS, NO network) check: is [host] an IP literal?
     *  - IPv4 literal: exactly 4 dot-separated parts, each all-ASCII-digit
     *    (covers decimal dot-quads; 0x/short-form are rare and the Fetcher's
     *    InetAddress.parse is the final authority, but they are still "literals").
     *  - IPv6 literal: contains ':'.
     * This must never resolve, because URL validation is a pure pre-network step.
     */
    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(':')) return true // IPv6 literal
        if (!host.contains('.')) return false
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.all { c -> c in '0'..'9' } }
    }
}
