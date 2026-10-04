package com.androidagent.provider.openai

import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.ToolCall
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A concrete OpenAI-compatible `/chat/completions` ModelProvider.
 *
 * This is an integration boundary, not framework scaffolding: it is the first
 * real consumer of the frozen AG Core contract, and it lives outside AG Core.
 *
 * Responsibilities and non-responsibilities:
 *  - Owns the OpenAI wire format, and nothing else.
 *  - Never modifies, relaxes or reinterprets an AG Core type.
 *  - Never uses `runBlocking`. The frozen contract is `suspend`, so the HTTP call
 *    is bridged with `sendAsync` + `suspendCancellableCoroutine`, which keeps
 *    host cancellation working.
 *  - Never silently discards model output. When the wire carries prose and tool
 *    calls in the same turn, both are preserved, thanks to the
 *    `ModelTurn.ToolCalls.content` field added by AG.PROVIDER-0.R1.
 */
class OpenAiCompatibleProvider(
    private val config: OpenAiCompatibleConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
) : ModelProvider {

    override suspend fun complete(request: ModelRequest): ModelTurn {
        val payload = ChatCompletionWire.buildRequestBody(config.model, request)
        val response = post(payload)
        return toModelTurn(response)
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun post(payload: JsonElement): JsonElement {
        val httpRequest = buildRequest(payload)
        val httpResponse = try {
            send(httpRequest)
        } catch (e: CancellationException) {
            // Coroutine control flow, not a provider failure.
            throw e
        } catch (e: HttpTimeoutException) {
            throw OpenAiProviderException.Timeout(
                "request to ${safeEndpoint()} timed out after ${config.requestTimeout}",
                e,
            )
        } catch (e: IOException) {
            throw OpenAiProviderException.Network("could not reach ${safeEndpoint()}", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OpenAiProviderException.Network("interrupted while calling ${safeEndpoint()}", e)
        }

        val status = httpResponse.statusCode()
        val bodyText = httpResponse.body()

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

    private fun buildRequest(payload: JsonElement): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(config.chatCompletionsEndpoint))
            .timeout(config.requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))

        if (config.apiKey.isNotBlank()) {
            // Never logged, never echoed into an exception message.
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        return builder.build()
    }

    /** Bridges [HttpClient.sendAsync] into a cancellable suspension. */
    private suspend fun send(request: HttpRequest): HttpResponse<String> =
        suspendCancellableCoroutine { continuation ->
            val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())

            future.whenComplete { response, throwable ->
                when {
                    throwable == null -> continuation.resume(response)
                    // Unwrap CompletionException so the real cause (for example
                    // HttpTimeoutException or IOException) is what propagates and
                    // can be classified. Without this every failure would surface
                    // as an opaque CompletionException.
                    else -> {
                        val cause = unwrap(throwable)
                        if (cause is CancellationException) continuation.cancel(cause)
                        else continuation.resumeWithException(cause)
                    }
                }
            }

            continuation.invokeOnCancellation { future.cancel(true) }
        }

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is CompletionException && throwable.cause != null) throwable.cause!! else throwable

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
            hasToolCalls ->
                // A turn may legitimately carry prose AND tool calls. Since
                // AG.PROVIDER-0.R1 the frozen contract carries both, so nothing is
                // discarded. A pure tool-call turn yields content = "".
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

    companion object {
        private fun defaultHttpClient(): HttpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    }
}