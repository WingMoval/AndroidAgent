package com.androidagent.app

import android.content.Context
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import java.io.File
import java.util.UUID

/**
 * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE — Track B: Generalized Provider Profiles.
 *
 * Replaces the fixed `ProviderNode` enum as the SOLE source of provider identity
 * for the UI. The enum remains for backward compatibility (built-in nodes), but
 * the UI now operates on [ProviderProfile] which can be built-in OR custom.
 *
 * Storage:
 *  - Built-in profiles: same files as before (`filesDir/provider.local.json`,
 *    `filesDir/provider.openrouter.json`)
 *  - Custom profiles: `filesDir/provider-profiles/<profileId>.json`
 *
 * Each profile file has the SAME format as the existing device-local config:
 * ```json
 * { "profileId": "...", "name": "...", "provider": { "type": "openai-compatible", ... } }
 * ```
 *
 * This is NOT a second provider system. It reuses [OpenAiCompatibleConfig]
 * and the same canonical apply path. The profile is just a named container
 * for credentials + a default model.
 */

/**
 * A provider profile: named identity + connection parameters.
 *
 * @param profileId Stable unique id (built-in: "emg"/"openrouter"; custom: UUID)
 * @param displayName User-visible name
 * @param isBuiltIn True for EMG/OpenRouter (legacy files), false for custom
 * @param config The OpenAI-compatible connection parameters
 */
data class ProviderProfile(
    val profileId: String,
    val displayName: String,
    val isBuiltIn: Boolean,
    val config: OpenAiCompatibleConfig,
    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: the models the user EXPLICITLY configured
     * on this profile (the only models switchable in the main model picker).
     *
     * `config.model` remains the currently SELECTED model id and MUST be a member
     * of this list. A legacy profile file (no `configuredModels` field) is
     * normalized to a single entry `[ConfiguredModel(config.model)]` on read.
     */
    val configuredModels: List<ConfiguredModel> = listOf(
        ConfiguredModel(config.model),
    ),
) {
    init {
        require(configuredModels.isNotEmpty()) { "configuredModels must not be empty" }
        require(configuredModels.any { it.modelId == config.model }) {
            "selected model '${config.model}' must be one of the configured models"
        }
        require(configuredModels.map { it.modelId }.toSet().size == configuredModels.size) {
            "configured modelIds must be unique within a profile"
        }
    }

    /** The [ConfiguredModel] currently selected (by [config.model]), or null. */
    val selectedConfiguredModel: ConfiguredModel?
        get() = configuredModels.firstOrNull { it.modelId == config.model }
}

/**
 * Loads/saves provider profiles from the app's filesDir.
 *
 * Built-in profiles reuse the EXISTING file locations and format.
 * Custom profiles use `filesDir/provider-profiles/<profileId>.json`.
 */
object ProviderProfileStore {

    private const val PROFILES_DIR = "provider-profiles"

    /** Built-in profile IDs (map to existing ProviderNode enum). */
    const val BUILTIN_EMG = "emg"
    const val BUILTIN_OPENROUTER = "openrouter"

    /**
     * Loads ALL available profiles (built-in + custom).
     * Never throws; missing/invalid files are skipped.
     */
    fun loadAll(context: Context): List<ProviderProfile> {
        val profiles = mutableListOf<ProviderProfile>()

        // Built-in: reuse existing node files
        for (node in ProviderNode.entries) {
            val loaded = DeviceLocalProviderNodeLoader.loadWithConfigured(context, node)
            if (loaded is DeviceLocalProviderNodeLoader.NodeLoadResult.Loaded) {
                val builtinId = if (node == ProviderNode.EMG) BUILTIN_EMG else BUILTIN_OPENROUTER
                profiles.add(
                    ProviderProfile(
                        profileId = builtinId,
                        displayName = node.displayName,
                        isBuiltIn = true,
                        config = loaded.config,
                        configuredModels = loaded.configuredModels,
                    )
                )
            }
        }

        // Custom: scan provider-profiles/ directory
        val dir = File(context.filesDir, PROFILES_DIR)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { file ->
                try {
                    val raw = file.readText()
                    val profile = parseProfileJson(raw, file.nameWithoutExtension)
                    if (profile != null) profiles.add(profile)
                } catch (e: Exception) {
                    // Skip malformed custom profiles
                }
            }
        }

        return profiles.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Loads a single profile by ID.
     */
    fun load(context: Context, profileId: String): ProviderProfile? {
        return loadAll(context).find { it.profileId == profileId }
    }

    /**
     * Saves a custom profile. Built-in profiles are NOT writable through this path
     * (they use the existing DeviceLocalProviderNodeLoader.save).
     */
    fun saveCustom(context: Context, profile: ProviderProfile): Boolean {
        if (profile.isBuiltIn) return false
        val dir = File(context.filesDir, PROFILES_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${profile.profileId}.json")
        return try {
            file.writeText(buildProfileJson(profile))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a new custom profile with a generated ID.
     */
    fun createCustom(
        context: Context,
        name: String,
        config: OpenAiCompatibleConfig,
        configuredModels: List<ConfiguredModel> = listOf(ConfiguredModel(config.model)),
    ): ProviderProfile? {
        val id = "custom-${UUID.randomUUID().toString().take(8)}"
        val profile = ProviderProfile(
            profileId = id,
            displayName = name,
            isBuiltIn = false,
            config = config,
            configuredModels = configuredModels,
        )
        return if (saveCustom(context, profile)) profile else null
    }

    /**
     * Resolves a profile to the canonical [ProviderNode] for built-in profiles,
     * or returns null for custom (custom profiles use their own config directly).
     */
    fun toProviderNode(profileId: String): ProviderNode? = when (profileId) {
        BUILTIN_EMG -> ProviderNode.EMG
        BUILTIN_OPENROUTER -> ProviderNode.OPENROUTER
        else -> null
    }

    // ------------------------------------------------------------------ JSON

    internal fun buildProfileJson(profile: ProviderProfile): String {
        val timeoutSeconds = profile.config.requestTimeout.toSeconds()
        return buildString {
            append("{\n")
            append("  \"profileId\": \"").append(esc(profile.profileId)).append("\",\n")
            append("  \"name\": \"").append(esc(profile.displayName)).append("\",\n")
            append("  \"provider\": {\n")
            append("    \"type\": \"openai-compatible\",\n")
            append("    \"baseUrl\": \"").append(esc(profile.config.baseUrl)).append("\",\n")
            append("    \"apiKey\": \"").append(esc(profile.config.apiKey)).append("\",\n")
            append("    \"model\": \"").append(esc(profile.config.model)).append("\",\n")
            append("    \"requestTimeoutSeconds\": ").append(timeoutSeconds).append(",\n")
            append("    \"configuredModels\": [\n")
            profile.configuredModels.forEachIndexed { i, cm ->
                append("      { \"modelId\": \"").append(esc(cm.modelId)).append("\"")
                if (cm.normalizedAlias != null) {
                    append(", \"alias\": \"").append(esc(cm.normalizedAlias!!)).append("\"")
                }
                append(" }").append(if (i < profile.configuredModels.lastIndex) "," else "").append("\n")
            }
            append("    ]\n")
            append("  }\n")
            append("}\n")
        }
    }

    internal fun parseProfileJson(raw: String, fallbackId: String): ProviderProfile? {
        return try {
            val profileId = extractString(raw, "profileId") ?: fallbackId
            val name = extractString(raw, "name") ?: "Custom API"
            val baseUrl = extractString(raw, "baseUrl") ?: return null
            val apiKey = extractString(raw, "apiKey") ?: ""
            val model = extractString(raw, "model") ?: return null
            val timeoutStr = extractString(raw, "requestTimeoutSeconds")
            val timeout = if (timeoutStr != null) {
                java.time.Duration.ofSeconds(timeoutStr.toLongOrNull() ?: 60L)
            } else {
                java.time.Duration.ofSeconds(60)
            }

            if (baseUrl.isBlank() || model.isBlank()) return null

            val config = OpenAiCompatibleConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                requestTimeout = timeout,
            )
            val configured = parseConfiguredModels(raw, fallbackSelected = model)
                ?: listOf(ConfiguredModel(model))
            ProviderProfile(
                profileId = profileId,
                displayName = name,
                isBuiltIn = false,
                config = config,
                configuredModels = configured,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: parse the optional `configuredModels`
     * array from the profile JSON. Returns null when the field is absent (legacy
     * file) so the caller normalizes to `[ConfiguredModel(fallbackSelected)]`.
     *
     * Tolerant, minimal parse (mirrors [extractString]): each entry is an object
     * with a required `modelId` and an optional `alias`. Entries with a blank
     * modelId are skipped; duplicates by modelId are dropped (first wins). If the
     * field is present but yields no usable entry, returns null (normalize).
     */
    internal fun parseConfiguredModels(raw: String, fallbackSelected: String): List<ConfiguredModel>? {
        // Isolate the "configuredModels": [ ... ] region (bracket-balanced scan).
        val keyIdx = raw.indexOf("\"configuredModels\"")
        if (keyIdx < 0) return null
        val open = raw.indexOf('[', keyIdx)
        if (open < 0) return null
        var depth = 0
        var end = -1
        for (i in open until raw.length) {
            when (raw[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end < 0) return null
        val body = raw.substring(open + 1, end)

        val models = mutableListOf<ConfiguredModel>()
        val seen = mutableSetOf<String>()
        // Each entry is { ...modelId... ...alias... }; split on '}' to bound entries.
        val entryPattern = Regex("\\{([^{}]*)\\}")
        for (entryMatch in entryPattern.findAll(body)) {
            val entry = entryMatch.groupValues[1]
            val modelId = extractString(entry, "modelId")?.trim() ?: continue
            if (modelId.isEmpty()) continue
            val alias = extractString(entry, "alias")?.trim()?.takeIf { it.isNotEmpty() }
            if (seen.add(modelId)) {
                models.add(ConfiguredModel(modelId, alias))
            }
        }
        if (models.isEmpty()) return null
        // The selected model must be representable; if the file's selected model is
        // not in the configured list, prepend it so the profile stays consistent.
        if (!models.any { it.modelId == fallbackSelected }) {
            models.add(0, ConfiguredModel(fallbackSelected))
        }
        return models
    }

    private fun extractString(json: String, key: String): String? {
        // Match "key": "value" or "key": number
        val pattern = Regex("\"$key\"\\s*:\\s*\"?([^\",}\\n\\]]+)\"?")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].trim()
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
