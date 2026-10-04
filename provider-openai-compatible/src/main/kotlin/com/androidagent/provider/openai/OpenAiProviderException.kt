package com.androidagent.provider.openai

/**
 * Failure categories for [OpenAiCompatibleProvider].
 *
 * Deliberately a small sealed hierarchy rather than a single opaque failure, so
 * that callers and tests can distinguish "the network broke" from "the endpoint
 * refused us" from "the endpoint answered with something this contract cannot
 * represent". No logging framework is involved and no credential appears in any
 * message.
 *
 * Cancellation is NOT modelled here. [java.util.concurrent.CancellationException]
 * is rethrown unchanged so it stays coroutine control flow.
 */
sealed class OpenAiProviderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The request never produced an HTTP response. */
    class Network(message: String, cause: Throwable?) : OpenAiProviderException(message, cause)

    /** The endpoint answered with a non-2xx status. */
    class HttpStatus(
        val statusCode: Int,
        /** Endpoint host and path only, never query or credentials. */
        val endpoint: String,
        /** Truncated, redacted provider error summary. */
        val providerSummary: String?,
    ) : OpenAiProviderException(
        "HTTP $statusCode from $endpoint" +
            if (providerSummary.isNullOrBlank()) "" else ": $providerSummary",
    )

    /** The request exceeded [OpenAiCompatibleConfig.requestTimeout]. */
    class Timeout(message: String, cause: Throwable?) : OpenAiProviderException(message, cause)

    /** HTTP succeeded but the payload violates the expected protocol shape. */
    class Protocol(message: String) : OpenAiProviderException(message)

    /** The body was not valid JSON. */
    class Parse(message: String, cause: Throwable?) : OpenAiProviderException(message, cause)

    /** A tool call was present but lacked a required field such as id or name. */
    class InvalidToolCall(message: String) : OpenAiProviderException(message)
}