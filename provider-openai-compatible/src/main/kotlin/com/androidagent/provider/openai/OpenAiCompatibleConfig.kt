package com.androidagent.provider.openai

import java.time.Duration

/**
 * Configuration for [OpenAiCompatibleProvider].
 *
 * No credential is ever stored in a tracked file. [fromEnvironment] is the only
 * supported way to obtain an api key for live verification, and it reads the
 * process environment exclusively.
 *
 * @property baseUrl OpenAI-compatible root, for example `https://api.openai.com/v1`.
 *   The provider appends `/chat/completions`.
 * @property apiKey Bearer credential. May be blank when the endpoint needs no auth.
 * @property model model identifier passed through unchanged.
 * @property requestTimeout per-request timeout.
 */
data class OpenAiCompatibleConfig(
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(!requestTimeout.isNegative && !requestTimeout.isZero) {
            "requestTimeout must be positive, was $requestTimeout"
        }
    }

    /**
     * Chat completions endpoint, derived from [baseUrl].
     *
     * A base that already ends in `/chat/completions` is used as-is, so both a
     * `/v1` root and a full endpoint are accepted.
     */
    val chatCompletionsEndpoint: String
        get() = if (baseUrl.endsWith(ENDPOINT_SUFFIX)) baseUrl else baseUrl.trimEnd('/') + ENDPOINT_SUFFIX

    /**
     * Redacted on purpose. The generated data-class toString would embed the raw
     * api key, which could then reach a log, a test report, or an exception
     * message. The key is only ever readable through the [apiKey] property.
     */
    override fun toString(): String =
        "OpenAiCompatibleConfig(baseUrl=$baseUrl, apiKey=<redacted>, model=$model, " +
            "requestTimeout=$requestTimeout)"

    companion object {
        private const val ENDPOINT_SUFFIX = "/chat/completions"

        /**
         * Generic default for any OpenAI-compatible endpoint.
         *
         * Intentionally NOT raised because of one slow local deployment: a hosted
         * API that takes longer than this is a caller problem, and changing the
         * shared default would penalise every other endpoint. Deployments that
         * need longer set `requestTimeoutSeconds` in their own local JSON.
         */
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}

/**
 * Reads configuration from the environment. Used only by live verification.
 *
 * Recognised variables: `AG_OPENAI_BASE_URL`, `AG_OPENAI_API_KEY`,
 * `AG_OPENAI_MODEL`. `AG_OPENAI_API_KEY` is optional because some
 * OpenAI-compatible endpoints need no auth.
 *
 * @throws IllegalStateException when a required variable is absent.
 */
fun openAiConfigFromEnvironment(): OpenAiCompatibleConfig {
    fun required(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("missing required environment variable $name")

    return OpenAiCompatibleConfig(
        baseUrl = required("AG_OPENAI_BASE_URL"),
        apiKey = System.getenv("AG_OPENAI_API_KEY").orEmpty(),
        model = required("AG_OPENAI_MODEL"),
    )
}