package com.androidagent.provider.openai

import java.time.Duration

/**
 * The minimal HTTP boundary the OpenAI-compatible Provider actually needs.
 *
 * This exists because the Provider now has two real consumers on two real
 * runtimes: a JVM host/tests and an Android host. The JDK 11 HTTP client that
 * the Provider originally used does not exist on Android below API 35, so a
 * single JDK-specific transport cannot serve both.
 *
 * Scope is deliberately one method. There is no GET/PUT/DELETE, no multipart,
 * no streaming, no cookies, no interceptor chain, no retry and no proxy
 * configuration — none of those has a consumer today.
 *
 * This is a **Provider integration boundary**, not an AG Core contract.
 * AG Core has no knowledge of HTTP.
 */
interface OpenAiHttpTransport {

    /**
     * Performs one POST and returns the response body.
     *
     * Implementations must not interpret HTTP status codes: a non-2xx status is
     * returned as-is so the Provider can map it to
     * [OpenAiProviderException.HttpStatus]. They must also never print headers,
     * the Authorization value, or the request body.
     *
     * @throws OpenAiTransportNetworkException if no response was obtained.
     * @throws OpenAiTransportTimeoutException if the request timed out.
     * @throws java.util.concurrent.CancellationException propagated unchanged.
     */
    suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse
}

/** One POST request. */
data class OpenAiHttpRequest(
    val endpoint: String,
    val headers: Map<String, String>,
    val body: String,
    val timeout: Duration,
)

/** One POST response. The body is whatever the endpoint returned. */
data class OpenAiHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** No HTTP response could be obtained (DNS, connect, reset, TLS...). */
class OpenAiTransportNetworkException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The request exceeded [OpenAiHttpRequest.timeout]. */
class OpenAiTransportTimeoutException(message: String, cause: Throwable? = null) :
    Exception(message, cause)