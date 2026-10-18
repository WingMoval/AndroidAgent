package com.androidagent.tool.network

/**
 * Frozen minimal error code set for the network tool (Preflight §15).
 *
 * Distinctions that must hold:
 *  - A REACHED HTTP response (any status: 200/404/500/...) is NOT one of these —
 *    it is a mechanism success (ToolResult.success = true) carrying httpStatus.
 *  - DESTINATION_BLOCKED is a safety DENY by the SSRF guard (success = false); it
 *    is deliberately NOT dressed up as an HTTP status.
 *  - CANCELLED is never a code here: a cancelled fetch propagates
 *    CancellationException (frozen runtime semantics) and never becomes a result.
 */
enum class NetworkError {
    INVALID_ARGUMENT,
    UNSUPPORTED_ACTION,
    UNSUPPORTED_SCHEME,
    INVALID_URL,
    DESTINATION_BLOCKED,
    DNS_FAILURE,
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    TLS_FAILURE,
    REDIRECT_LIMIT,
    UNSUPPORTED_CONTENT_TYPE,
    RESPONSE_TOO_LARGE,
    TRANSPORT_FAILURE,
}
