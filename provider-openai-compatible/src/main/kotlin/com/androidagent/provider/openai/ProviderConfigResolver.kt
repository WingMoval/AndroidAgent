package com.androidagent.provider.openai

import java.nio.file.Path

/**
 * Where a resolved provider configuration came from.
 *
 * A single run uses exactly one source. Values are never mixed across sources,
 * so a run can never end up with a baseUrl from one place and a key from another.
 */
sealed interface ConfigSource {

    /** AG-owned local JSON, e.g. `<rootProject>/config/provider.local.json`. */
    data class LocalJson(val path: String) : ConfigSource

    /** Legacy `AG_OPENAI_*` process environment variables. Fallback only. */
    data object Environment : ConfigSource

    /** A description safe to print. Never contains a secret. */
    val description: String
        get() = when (this) {
            is LocalJson -> "provider.local.json"
            Environment -> "AG_OPENAI_* environment"
        }
}

/**
 * A configuration plus its origin.
 */
data class ResolvedProviderConfig(
    val config: OpenAiCompatibleConfig,
    val source: ConfigSource,
)

/**
 * Resolves the provider configuration for a live run.
 *
 * Precedence:
 *  1. AndroidAgent's own local JSON file.
 *  2. `AG_OPENAI_*` environment variables, as a fallback.
 *
 * Whichever source is chosen supplies *all* fields. There is no partial merge.
 */
object ProviderConfigResolver {

    /** Default AG-owned config location, relative to the root project. */
    const val LOCAL_CONFIG_RELATIVE_PATH: String = "config/provider.local.json"

    /**
     * @param localConfigPath AG-owned JSON location.
     * @param env environment lookup, injectable so tests do not touch real env.
     * @throws ProviderConfigException when neither source yields a usable config.
     */
    fun resolve(
        localConfigPath: Path,
        env: (String) -> String? = { System.getenv(it) },
    ): ResolvedProviderConfig {
        // Preferred: AG-owned local JSON.
        var localError: ProviderConfigException? = null
        try {
            val local = OpenAiCompatibleConfigLoader.load(localConfigPath)
            return ResolvedProviderConfig(local, ConfigSource.LocalJson(localConfigPath.toString()))
        } catch (e: ProviderConfigException) {
            localError = e
        }

        // Fallback: legacy environment. All fields come from this one source.
        val baseUrl = env("AG_OPENAI_BASE_URL")?.takeIf { it.isNotBlank() }
        val model = env("AG_OPENAI_MODEL")?.takeIf { it.isNotBlank() }
        if (baseUrl != null && model != null) {
            return ResolvedProviderConfig(
                config = OpenAiCompatibleConfig(
                    baseUrl = baseUrl,
                    apiKey = env("AG_OPENAI_API_KEY").orEmpty(),
                    model = model,
                ),
                source = ConfigSource.Environment,
            )
        }

        // Neither source is usable. Report the preferred source's reason, since
        // that is the one the user is expected to fix.
        throw localError ?: ProviderConfigException.Invalid(
            "no usable provider configuration: local JSON unusable and " +
                "AG_OPENAI_BASE_URL / AG_OPENAI_MODEL not both set",
        )
    }
}
