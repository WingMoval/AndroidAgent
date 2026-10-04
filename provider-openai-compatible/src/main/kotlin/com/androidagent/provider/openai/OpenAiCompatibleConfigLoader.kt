package com.androidagent.provider.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Why a provider configuration could not be used.
 *
 * No subtype or message ever embeds an api key value. The loader reports the
 * *shape* of the problem, never the secret it was protecting.
 */
sealed class ProviderConfigException(message: String) : Exception(message) {

    /** The file does not exist. */
    class NotFound(path: String) : ProviderConfigException("provider config not found: $path")

    /** The file is not valid JSON, or not a JSON object. */
    class Malformed(path: String, reason: String) :
        ProviderConfigException("provider config is not valid JSON ($path): $reason")

    /** The JSON parsed but a required field is absent or blank. */
    class Invalid(message: String) : ProviderConfigException(message)

    /** The apiKey is still the shipped placeholder, so the config is not ready. */
    class PlaceholderNotReplaced :
        ProviderConfigException(
            "provider config apiKey is still the REPLACE_ME placeholder; " +
                "configuration not ready",
        )
}

/**
 * Loads an AG-owned OpenAI-compatible provider configuration from a local JSON file.
 *
 * This is AndroidAgent's own development configuration. It deliberately does not
 * read, import or depend on any other tool's configuration, and it lives in the
 * provider module so that AG Core never learns about JSON, config files, or API keys.
 *
 * Responsibilities are exactly: parse, validate, map. It does not start a
 * provider, touch an AgentCore, register a tool, or know about Android.
 */
object OpenAiCompatibleConfigLoader {

    /** The only provider type this loader accepts. */
    const val SUPPORTED_TYPE: String = "openai-compatible"

    /** Shipped placeholder that must be replaced before any real request. */
    const val PLACEHOLDER: String = "REPLACE_ME"

    /**
     * Parses [path] into an [OpenAiCompatibleConfig].
     *
     * @throws ProviderConfigException for every failure mode. Messages never
     *   contain the api key value.
     */
    fun load(path: Path): OpenAiCompatibleConfig {
        if (!Files.isRegularFile(path)) {
            throw ProviderConfigException.NotFound(path.toString())
        }

        val text = Files.readString(path)
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ProviderConfigException.Malformed(path.fileName.toString(), e::class.simpleName ?: "parse error")
        }

        val provider = root["provider"] as? JsonObject
            ?: throw ProviderConfigException.Invalid("provider config has no 'provider' object")

        val type = (provider["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        if (type.isNullOrBlank()) {
            throw ProviderConfigException.Invalid("provider config has no 'type'")
        }
        if (type != SUPPORTED_TYPE) {
            // Never guess. An unknown type must fail loudly.
            throw ProviderConfigException.Invalid(
                "unsupported provider type '$type'; expected '$SUPPORTED_TYPE'",
            )
        }

        val baseUrl = (provider["baseUrl"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        if (baseUrl.isNullOrBlank()) {
            throw ProviderConfigException.Invalid("provider config has no 'baseUrl'")
        }

        val model = (provider["model"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        if (model.isNullOrBlank()) {
            throw ProviderConfigException.Invalid("provider config has no 'model'")
        }

        // An absent or empty apiKey is legitimate: some endpoints need no auth.
        val apiKey = (provider["apiKey"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
        if (apiKey == PLACEHOLDER) {
            throw ProviderConfigException.PlaceholderNotReplaced()
        }

        return OpenAiCompatibleConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            requestTimeout = readRequestTimeout(provider),
        )
    }

    /**
     * Optional per-deployment request timeout.
     *
     * Exists because a locally hosted model can take far longer than a hosted API
     * to cold start, and that is a property of one deployment, not of
     * OpenAI-compatible providers in general. When absent, the generic default in
     * [OpenAiCompatibleConfig] applies unchanged; there is deliberately no second
     * default value here.
     */
    private fun readRequestTimeout(provider: JsonObject): Duration {
        val element = provider["requestTimeoutSeconds"] ?: return OpenAiCompatibleConfig.DEFAULT_REQUEST_TIMEOUT
        if (element is kotlinx.serialization.json.JsonNull) {
            return OpenAiCompatibleConfig.DEFAULT_REQUEST_TIMEOUT
        }

        val primitive = element as? kotlinx.serialization.json.JsonPrimitive
            ?: throw ProviderConfigException.Invalid(
                "provider config requestTimeoutSeconds must be a number",
            )

        val seconds = primitive.contentOrNull?.trim()?.toLongOrNull()
            ?: throw ProviderConfigException.Invalid(
                "provider config requestTimeoutSeconds must be a whole number of seconds",
            )
        if (seconds <= 0) {
            throw ProviderConfigException.Invalid(
                "provider config requestTimeoutSeconds must be greater than 0, was $seconds",
            )
        }
        return Duration.ofSeconds(seconds)
    }

    /** True when [path] holds a loadable, fully specified configuration. */
    fun isUsable(path: Path): Boolean = try {
        load(path)
        true
    } catch (_: ProviderConfigException) {
        false
    }
}
