package com.androidagent.tool.network

import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException

/**
 * Concrete Network Tool — the second Tool Layer tool (sibling of AndroidShellTool).
 *
 * Responsibilities, frozen (AG.NETWORK-0):
 *
 * ```
 * ToolCall.arguments ({"action":"fetch","url":"https://..."})
 *   ↓ NetworkToolSchema.parse          (invalid -> success=false, 0 transport calls)
 *   ↓ UrlValidator.validate            (scheme https, host normalize, no creds)
 *   ↓ resolve + PublicIpGuard.allPublic (PUBLIC_NETWORK_ONLY; mixed -> block)
 *   ↓ Fetcher.fetch                    (single real impl: AppHttpFetcher)
 *   ↓ content-type policy + text bound
 *   ↓ NetworkResultPayload.encode      (JSON -> ToolResult.output)
 * ToolResult
 * ```
 *
 * ToolResult.success = mechanism success (a reached HTTP response, any status, or
 * a clean argument decision), NOT "HTTP 2xx". Only a failure of the fetch path
 * (transport / policy / SSRF / timeout / TLS) is success=false.
 *
 * The [Fetcher] seam is tool-local and test-only: JVM unit tests inject a fake
 * Fetcher (and a fake resolver) to verify argument / SSRF / result mapping
 * deterministically; production uses [AppHttpFetcher] (HttpsURLConnection).
 *
 * Cancellation: a [java.util.concurrent.CancellationException] from the Fetcher is
 * rethrown unchanged (frozen runtime semantics) — it is never converted to a
 * TransportError. The Fetcher tears down its socket in a finally.
 */
class NetworkToolImpl internal constructor(
    private val fetcher: Fetcher,
    private val resolver: (String) -> Array<InetAddress> = ::resolveDefault,
) : Tool {

    constructor() : this(AppHttpFetcher())

    override val spec: ToolSpec = ToolSpec(
        name = TOOL_NAME,
        description =
            "WHAT: Interact with network resources. The only action available now is " +
                "\"fetch\": read one known public HTTPS resource and return its bounded, " +
                "readable, attributed content.\n" +
                "WHEN: Use when the task needs the content of a specific, known public " +
                "https URL (a document, API JSON, or page) and the answer is not already " +
                "known from earlier observations. Do NOT use for shell commands, file " +
                "operations, search, download, authenticated requests, or anything that is " +
                "not a single public https URL.\n" +
                "INPUT: A single JSON object: {\"action\":\"fetch\",\"url\":\"https://...\"}. " +
                "\"url\" must be an absolute https:// URL to a public resource. No other " +
                "fields are supported (no method, headers, body, auth, cookies, or path " +
                "override).\n" +
                "OUTPUT: A JSON object with fields \"action\",\"requestedUrl\",\"finalUrl\"," +
                "\"httpStatus\",\"contentType\",\"content\",\"truncated\",\"bytesRead\"," +
                "\"redirectCount\". A reached HTTP response (any status, including 404/500) " +
                "reports success=true with the real httpStatus; only a transport/mechanism " +
                "failure reports success=false with an \"error\" field. \"content\" is " +
                "bounded; truncated=true when the source exceeded the limit. For HTML, " +
                "content is extracted readable text (title + body), not raw markup.",
    )

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        when (val parsed = NetworkToolSchema.parse(call.arguments)) {
            is NetworkToolSchema.ParseResult.Error ->
                ToolResult(success = false, output = "", error = "${NetworkError.INVALID_ARGUMENT}: ${parsed.reason}")

            is NetworkToolSchema.ParseResult.Ok -> doFetch(parsed.url)
        }
    }

    private suspend fun doFetch(url: String): ToolResult {
        // 1. Validate + normalize the URL (no network).
        val target = when (val v = UrlValidator.validate(url)) {
            is UrlValidator.ParseResult.Error ->
                return ToolResult(success = false, output = "", error = "${v.error}: invalid or unsupported URL")
            is UrlValidator.ParseResult.Ok -> v.target
        }

        // 2. Resolve + classify ALL addresses (PUBLIC_NETWORK_ONLY; mixed -> block).
        val addresses: Array<InetAddress> = try {
            if (target.isIpLiteral) {
                // IP literal: resolve is a no-op parse; still classify it.
                arrayOf(InetAddress.getByName(target.host))
            } else {
                resolver(target.host)
            }
        } catch (e: UnknownHostException) {
            return ToolResult(success = false, output = "", error = "${NetworkError.DNS_FAILURE}: host did not resolve")
        } catch (e: CancellationException) {
            throw e
        }
        if (!PublicIpGuard.allPublic(addresses)) {
            return ToolResult(
                success = false,
                output = "",
                error = "${NetworkError.DESTINATION_BLOCKED}: destination is not a public network address",
            )
        }

        // 3. Fetch (the Fetcher re-validates each redirect hop via the same guard).
        val outcome = try {
            fetcher.fetch(target, MAX_RESPONSE_BYTES, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransportError) {
            return ToolResult(success = false, output = "", error = "${e.code}: ${e.message}")
        }

        // 4. Content-type policy + decode + (HTML) extraction + text bound.
        return NetworkResultMapper.toToolResult(outcome, target.rawUrl)
    }

    companion object {
        const val TOOL_NAME: String = "network"

        // Frozen bounds (Preflight §14; initial constants, product-tunable).
        const val MAX_RESPONSE_BYTES: Int = 524_288   // 512 KiB decompressed
        const val MAX_CONTENT_CHARS: Int = 30_000     // extracted text chars
        const val MAX_ERROR_CHARS: Int = 2_000

        const val CONNECT_TIMEOUT_MS: Int = 15_000
        const val READ_TIMEOUT_MS: Int = 30_000
        const val MAX_REDIRECT_HOPS: Int = 5
    }
}

private fun resolveDefault(host: String): Array<InetAddress> = InetAddress.getAllByName(host)

// Re-export the decode/charset helpers used by the mapper (kept internal to the module).
internal fun decodeBody(bytes: ByteArray, contentType: String?): String {
    val charset = charsetOf(contentType)
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val s = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    // Strip a BOM if present.
    return if (s.isNotEmpty() && s[0] == '\uFEFF') s.substring(1) else s
}

private fun charsetOf(contentType: String?): Charset {
    if (contentType != null) {
        val m = CHARSET_REGEX.find(contentType)
        if (m != null) {
            val name = m.groupValues[1]
            try {
                return Charset.forName(name)
            } catch (_: Exception) {
                // fall through to UTF-8
            }
        }
    }
    return StandardCharsets.UTF_8
}

private val CHARSET_REGEX = Regex("""charset\s*=\s*"?([A-Za-z0-9_\-]+)"?""", RegexOption.IGNORE_CASE)
