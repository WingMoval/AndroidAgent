package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiHttpResponse
import com.androidagent.provider.openai.OpenAiHttpTransport
import com.androidagent.provider.openai.OpenAiTransportNetworkException
import com.androidagent.provider.openai.OpenAiTransportTimeoutException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The JVM implementation of [OpenAiHttpTransport], built on `java.net.http`.
 *
 * Behaviour is a straight extraction of what the Provider used to do inline:
 * same headers, same timeout, same UTF-8 body, same status and body, same
 * cancellation semantics. The wire JSON is untouched.
 *
 * Lives in its own module because `java.net.http` does not exist on Android
 * below API 35. Android hosts must supply their own transport instead.
 */
class JdkOpenAiHttpTransport(
    private val httpClient: HttpClient = defaultHttpClient(),
) : OpenAiHttpTransport {

    override suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse {
        val httpRequest = buildRequest(request)
        val response = try {
            send(httpRequest)
        } catch (e: CancellationException) {
            // Coroutine control flow. Never converted into a transport failure.
            throw e
        } catch (e: HttpTimeoutException) {
            throw OpenAiTransportTimeoutException("request timed out after ${request.timeout}", e)
        } catch (e: IOException) {
            throw OpenAiTransportNetworkException("could not reach the endpoint", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OpenAiTransportNetworkException("interrupted while calling the endpoint", e)
        }

        return OpenAiHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    private fun buildRequest(request: OpenAiHttpRequest): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(request.endpoint))
            .timeout(request.timeout)
            .POST(HttpRequest.BodyPublishers.ofString(request.body, StandardCharsets.UTF_8))
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    /**
     * Bridges [HttpClient.sendAsync] into a cancellable suspension.
     *
     * Unwraps CompletionException so the real cause (HttpTimeoutException,
     * IOException) is what propagates and can be classified.
     */
    private suspend fun send(request: HttpRequest): HttpResponse<String> =
        suspendCancellableCoroutine { continuation ->
            val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())

            future.whenComplete { response, throwable ->
                when {
                    throwable == null -> continuation.resume(response)
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

    companion object {
        private fun defaultHttpClient(): HttpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    }
}