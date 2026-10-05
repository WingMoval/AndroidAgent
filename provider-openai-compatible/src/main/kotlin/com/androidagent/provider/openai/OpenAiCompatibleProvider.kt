package com.androidagent.provider.openai

import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
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
        val payload = ChatCompletionWire.buildRequestBody(config.model, request)
        return toModelTurn(post(payload))
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun post(payload: JsonElement): JsonElement {
        val httpResponse = try {
            transport.post(buildRequest(payload))
        } catch (e: CancellationException) {
            // Coroutine control flow, not a provider failure.
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
        val bodyText = httpResponse.body

        if (status !in 200..299) {
            throw OpenAiProviderException.HttpStatus(
                statusCode = status,
                endpoint = safeEndpoint(),
                providerSummary = ChatCompletionWire.errorSummary(bodyText).ifEmpty { null },
            )
        }

        return try {
            Json.parseToJsonElement(bodyText)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw OpenAiProviderException.Parse("response body was not valid JSON", e)
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
    private fun safeEndpoint(): String = runCatching {
        val uri = URI.create(config.chatCompletionsEndpoint)
        val authority = uri.rawAuthority?.substringAfter('@').orEmpty()
        "${uri.scheme}://$authority${uri.rawPath}"
    }.getOrElse { "endpoint" }

    // --------------------------------------------------------------- Response

    private fun toModelTurn(body: JsonElement): ModelTurn {
        val message = ChatCompletionWire.assistantMessageOf(body)
        val content = ChatCompletionWire.contentOf(message)
        val toolCalls = ChatCompletionWire.toolCallsOf(message)

        val hasContent = !content.isNullOrBlank()
        val hasToolCalls = toolCalls != null && toolCalls.isNotEmpty()

        return when {
            // A turn may legitimately carry prose AND tool calls. Since
            // AG.PROVIDER-0.R1 the frozen contract carries both, so nothing is
            // discarded. A pure tool-call turn yields content = "".
            hasToolCalls ->
                ModelTurn.ToolCalls(
                    calls = toolCalls(toolCalls!!),
                    content = content.orEmpty(),
                )

            hasContent -> ModelTurn.FinalAnswer(content!!)
            // Neither prose nor tool calls: a well-formed but empty turn cannot be
            // represented as a FinalAnswer with no text.
            else -> throw OpenAiProviderException.Protocol(
                "assistant message carried neither content nor tool_calls",
            )
        }
    }

    /**
     * Maps every tool call, preserving count, id, name, arguments and order.
     * Nothing is deduplicated, reordered, or truncated to the first element.
     */
    private fun toolCalls(array: kotlinx.serialization.json.JsonArray): List<ToolCall> =
        array.mapIndexed { index, element ->
            val wire = ChatCompletionWire.toolCallOf(element, index)
            ToolCall(id = wire.id, name = wire.name, arguments = wire.arguments)
        }
}