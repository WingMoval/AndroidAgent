package com.androidagent.provider.openai

import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.ModelTurn
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CancellationException

/**
 * An OpenAI-compatible `/chat/completions` ModelProvider.
 *
 * Responsibilities and non-responsibilities:
 *  - Owns the OpenAI wire format, and nothing else.
 *  - Never modifies, relaxes or reinterprets an AG Core type.
 *  - Never uses `runBlocking`. The frozen contract is `suspend`, so the HTTP call
 *    is delegated to an injected [OpenAiHttpTransport] that keeps host
 *    cancellation working.
 *  - Never silently discards model output. When the wire carries prose and tool
 *    calls together, which the frozen contract now carries, both are preserved.
 *
 * The transport is always supplied explicitly by the composition root. This
 * class deliberately has no default: a hidden JDK default would silently
 * re-break Android, which is exactly the failure this boundary exists to fix.
 */
class OpenAiCompatibleProvider(
    private val config: OpenAiCompatibleConfig,
    private val transport: OpenAiHttpTransport,
) : ModelProvider {

    override suspend fun complete(request: ModelRequest): ModelTurn {
        val payload = ChatCompletionWire.buildRequestBody(config.model, request, stream = true)
        val acc = ChatCompletionWire.StreamAccumulator()
        val observer = request.streamObserver
        val httpResponse = postStream(payload, acc, observer)
        // The streamed chunks are the only body: the final turn is assembled
        // from what actually streamed, never from a second non-streaming call.
        return ChatCompletionWire.toModelTurn(acc)
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * Streams one request body and feeds every SSE line into [acc] via
     * [ChatCompletionWire.parseStreamLine]. Non-2xx responses raise
     * [OpenAiProviderException.HttpStatus] exactly like the buffered path.
     */
    private suspend fun postStream(
        payload: JsonElement,
        acc: ChatCompletionWire.StreamAccumulator,
        observer: ModelStreamObserver,
    ) {
        val httpResponse = try {
            transport.postStream(buildRequest(payload)) { line ->
                ChatCompletionWire.parseStreamLine(line, acc, observer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAiTransportTimeoutException) {
            throw OpenAiProviderException.Timeout(
                "request to ${safeEndpoint()} timed out after ${config.requestTimeout}",
                e,
            )
        } catch (e: OpenAiTransportNetworkException) {
            throw OpenAiProviderException.Network("could not reach ${safeEndpoint()}", e)
        }

        val status = httpResponse.statusCode
        if (status !in 200..299) {
            throw OpenAiProviderException.HttpStatus(
                statusCode = status,
                endpoint = safeEndpoint(),
                providerSummary = ChatCompletionWire.errorSummary(httpResponse.body).ifEmpty { null },
            )
        }
    }

    private fun buildRequest(payload: JsonElement): OpenAiHttpRequest {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
        )
        if (config.apiKey.isNotBlank()) {
            // Never logged, never echoed into an exception message.
            headers["Authorization"] = "Bearer ${config.apiKey}"
        }
        return OpenAiHttpRequest(
            endpoint = config.chatCompletionsEndpoint,
            headers = headers,
            body = payload.toString(),
            timeout = config.requestTimeout,
        )
    }

    /** Host, port and path only. No query string, no credential. */
    private fun safeEndpoint(): String = config.safeChatCompletionsEndpoint
}