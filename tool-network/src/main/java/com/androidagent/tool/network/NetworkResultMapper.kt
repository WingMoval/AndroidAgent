package com.androidagent.tool.network

import com.androidagent.core.ToolResult

/**
 * Maps a [FetchOutcome] to the frozen AG [ToolResult] contract.
 *
 * AG semantics: [ToolResult.success] = tool invocation MECHANISM success. A reached
 * HTTP response (any status) is a success; only a transport/policy failure (which
 * the Fetcher already surfaced as a [TransportError]) is a failure.
 *
 * This is where the content-type policy and the TEXT bound (MAX_CONTENT_CHARS) are
 * applied. The BYTE bound was already enforced by the Fetcher.
 */
internal object NetworkResultMapper {

    fun toToolResult(outcome: FetchOutcome, requestedUrl: String): ToolResult {
        val ct = outcome.contentType
        val supported = isSupportedTextType(ct)

        val rawText: String
        val truncated: Boolean
        val content: String

        if (!supported) {
            // A response WAS reached (success), but the body is a rejected binary
            // type. Report it without the body so the model can adapt.
            rawText = ""
            truncated = outcome.truncatedBytes
            content = ""
        } else {
            rawText = decodeBody(outcome.bodyBytes, ct)
            val extracted = if (isHtml(ct)) HtmlToText.extract(rawText) else rawText
            truncated = outcome.truncatedBytes || extracted.length > NetworkToolImpl.MAX_CONTENT_CHARS
            content = if (truncated && extracted.length > NetworkToolImpl.MAX_CONTENT_CHARS) {
                extracted.substring(0, NetworkToolImpl.MAX_CONTENT_CHARS)
            } else {
                extracted
            }
        }

        return ToolResult(
            success = true,
            output = NetworkResultPayload.encode(
                requestedUrl = requestedUrl,
                finalUrl = outcome.finalUrl,
                httpStatus = outcome.httpStatus,
                contentType = ct,
                content = content,
                truncated = truncated,
                bytesRead = outcome.bytesRead,
                redirectCount = outcome.redirectCount,
                supportedType = supported,
            ),
        )
    }

    /** text/plain, text/html, application/json (+json), and (optional) xml are supported. */
    fun isSupportedTextType(contentType: String?): Boolean {
        if (contentType == null) {
            // Missing Content-Type: the tool treats it as best-effort text. The
            // binary rejection is decided by the caller's binary sniff, which for V0
            // is conservative: missing type is allowed through as text.
            return true
        }
        val ct = contentType.lowercase().trim()
        val base = ct.substringBefore(';').trim()
        return base in SUPPORTED_TYPES || base.endsWith("+json") || base.endsWith("+xml")
    }

    fun isHtml(contentType: String?): Boolean {
        if (contentType == null) return false
        val base = contentType.lowercase().trim().substringBefore(';').trim()
        return base == "text/html" || base == "application/xhtml+xml"
    }

    private val SUPPORTED_TYPES = setOf(
        "text/plain",
        "text/html",
        "application/json",
        "application/xml",
        "text/xml",
    )
}
