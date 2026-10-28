package com.androidagent.app

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT — Host-level model discovery.
 *
 * Queries `GET {baseUrl}/models` on an OpenAI-compatible endpoint using the
 * SAVED provider credentials. This is a HOST CONFIGURATION capability — it does
 * NOT modify AG Core, ModelProvider, or the transport layer.
 *
 * The API key is read ONLY from the saved [OpenAiCompatibleConfig] and is
 * NEVER logged, echoed, or exposed in any result.
 *
 * Failure modes (network error, 401, unsupported endpoint, empty list) are
 * all mapped to [ModelDiscoveryResult.Failure] with a safe reason string.
 * The current active model is NEVER disturbed by a failed discovery.
 */
sealed interface ModelDiscoveryResult {
    /** Successfully retrieved the model list. [models] is non-empty. */
    data class Success(val models: List<String>) : ModelDiscoveryResult

    /** Discovery failed. [safeReason] is a stable, non-secret category. */
    data class Failure(val safeReason: String) : ModelDiscoveryResult
}

/**
 * Host-level model discovery. Uses the platform [HttpURLConnection] directly
 * (same as [com.androidagent.app.provider.AndroidOpenAiHttpTransport]) — no
 * new dependency, no provider architecture change.
 */
object ModelDiscovery {

    private const val TIMEOUT_MS = 10_000

    /**
     * Queries the saved endpoint's `/models` endpoint for the available model list.
     *
     * @param config The SAVED provider config (endpoint + API key from file).
     * @return [ModelDiscoveryResult.Success] with model ids, or [ModelDiscoveryResult.Failure].
     *
     * Never throws. Never logs the API key. Never modifies the active model.
     */
    suspend fun discover(config: OpenAiCompatibleConfig): ModelDiscoveryResult =
        withContext(Dispatchers.IO) {
            try {
                // Build the models endpoint: baseUrl + /models
                // baseUrl may already end with /v1, so append /models
                val modelsUrl = if (config.baseUrl.endsWith("/")) {
                    config.baseUrl + "models"
                } else {
                    config.baseUrl + "/models"
                }

                val connection = URL(modelsUrl).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Accept", "application/json")
                    if (config.apiKey.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == 401) {
                        return@withContext ModelDiscoveryResult.Failure("UNAUTHORIZED")
                    }
                    if (responseCode == 404) {
                        // Endpoint doesn't support /models — fall back to saved model only
                        return@withContext ModelDiscoveryResult.Failure("ENDPOINT_UNSUPPORTED")
                    }
                    if (responseCode != 200) {
                        return@withContext ModelDiscoveryResult.Failure("HTTP_${responseCode}")
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val models = parseModelList(body)
                    if (models.isEmpty()) {
                        ModelDiscoveryResult.Failure("EMPTY_MODEL_LIST")
                    } else {
                        ModelDiscoveryResult.Success(models)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: java.net.ConnectException) {
                ModelDiscoveryResult.Failure("CONNECTION_FAILED")
            } catch (e: java.net.SocketTimeoutException) {
                ModelDiscoveryResult.Failure("TIMEOUT")
            } catch (e: java.net.MalformedURLException) {
                ModelDiscoveryResult.Failure("INVALID_URL")
            } catch (e: Exception) {
                // Catch-all: never expose exception details (could contain URL with key)
                ModelDiscoveryResult.Failure("DISCOVERY_FAILED")
            }
        }

    /**
     * Parses the OpenAI-compatible /models response JSON.
     * Expected shape: { "data": [ { "id": "model-name" }, ... ] }
     *
     * Uses a minimal regex-free parse to avoid adding a JSON dependency here.
     * The response is standard OpenAI-compatible; we extract "id" fields.
     */
    internal fun parseModelList(json: String): List<String> {
        // Simple extraction of "id" values from the data array.
        // This is intentionally minimal — the Host config layer does not
        // need a full JSON parser for this purpose.
        val models = mutableListOf<String>()
        // Find all "id":"..." or "id": "..." patterns within the data array
        val pattern = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        for (match in pattern.findAll(json)) {
            models.add(match.groupValues[1])
        }
        return models.distinct()
    }
}
