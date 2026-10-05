package com.androidagent.app.provider

import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiHttpResponse
import com.androidagent.provider.openai.OpenAiHttpTransport
import com.androidagent.provider.openai.OpenAiTransportNetworkException
import com.androidagent.provider.openai.OpenAiTransportTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * The Android implementation of [OpenAiHttpTransport], built on
 * [HttpsURLConnection].
 *
 * It lives in `:app` rather than its own module because there is exactly one
 * Android consumer today. A second real Android consumer is what would justify
 * extracting `:provider-openai-compatible-android` — not anticipation.
 *
 * `HttpsURLConnection` is part of the Android platform, works from minSdk 26, and
 * needs no third-party HTTP dependency. OkHttp / Retrofit are deliberately NOT
 * introduced; if this transport hits a real, unsolvable blocker that becomes a
 * separate proposal rather than an inline swap.
 *
 * This class interprets nothing about the model API: it returns the status and
 * body verbatim, including error bodies, so the Provider keeps ownership of
 * HTTP-status mapping.
 */
class AndroidOpenAiHttpTransport : OpenAiHttpTransport {

    override suspend fun post(request: OpenAiHttpRequest): OpenAiHttpResponse =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(request.endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = request.timeout.toMillis().toInt()
                    readTimeout = request.timeout.toMillis().toInt()
                    useCaches = false
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    // Never logged. Applied straight from the request.
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                }

                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                // getInputStream() throws for >= 400, so the error body must come
                // from getErrorStream(). Losing it would strip the Provider of the
                // safe summary it needs for HttpStatus.
                val stream: InputStream? =
                    if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                OpenAiHttpResponse(statusCode = status, body = body)
            } catch (e: SocketTimeoutException) {
                throw OpenAiTransportTimeoutException("request timed out", e)
            } catch (e: IOException) {
                throw OpenAiTransportNetworkException("could not reach the endpoint", e)
            } finally {
                // Also the cancellation path: when the coroutine is cancelled the
                // finally block still runs and tears the socket down.
                connection?.disconnect()
            }
        }
}