package com.androidagent.app

import android.content.Context
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleConfigLoader
import com.androidagent.provider.openai.ProviderConfigException
import java.io.File

/**
 * The outcome of looking for a development provider config in the app's private
 * directory.
 *
 * This is the AGA Host's configuration boundary. It deliberately reuses the
 * existing [OpenAiCompatibleConfigLoader.parse] (the single source of config
 * validation) rather than re-implementing JSON parsing / validation / placeholder
 * handling. The AGA only adds: where the file lives, how it is read with normal
 * Android File IO, and a structured, secret-safe result for the UI.
 *
 * The device-local file is READ-ONLY from the AGA's perspective: the app never
 * writes it. Provisioning (copying repo/config/provider.local.json into
 * filesDir) is a future ADB step, not part of the app.
 */
sealed interface DeviceLocalProviderConfig {

    /** The file existed and parsed into a usable [config]. */
    data class Loaded(val config: OpenAiCompatibleConfig) : DeviceLocalProviderConfig

    /** No file at the device-local location. The manual UI flow applies. */
    data object Missing : DeviceLocalProviderConfig

    /**
     * The file existed but could not be used. [safeReason] is a stable,
     * non-secret category so it is safe to show in the UI and never to log as raw
     * JSON. It never contains the apiKey or the file's raw content.
     */
    data class Invalid(val safeReason: String) : DeviceLocalProviderConfig
}

/**
 * Resolves the AGA development provider config from
 * `File(context.filesDir, "provider.local.json")`.
 *
 * Responsibilities, exactly:
 *  - locate the file with `context.filesDir` (never a hardcoded absolute path)
 *  - read it with normal Android File IO
 *  - delegate validation to [OpenAiCompatibleConfigLoader.parse]
 *  - return a structured, secret-safe result
 *
 * It does not start a provider, build a Composition Root, touch the network, or
 * write the file. No ADB. No external storage.
 */
object DeviceLocalProviderConfigLoader {

    /** Fixed device-local file name. Matches the repo config file exactly. */
    const val FILE_NAME: String = "provider.local.json"

    /** Stable, UI-safe reason for a missing file. */
    const val REASON_MISSING: String = "DEVICE_LOCAL_CONFIG_MISSING"

    /** Stable, UI-safe reason for a present-but-unusable file. */
    const val REASON_INVALID: String = "DEVICE_LOCAL_CONFIG_INVALID"

    /**
     * Looks up the device-local provider config from the app's private directory.
     *
     * Never throws: every failure mode is mapped to a structured [Result]. The
     * file is opened read-only and is never created or modified by this call.
     */
    fun load(context: Context): DeviceLocalProviderConfig =
        load(File(context.filesDir, FILE_NAME))

    /**
     * Core lookup against an explicit [file]. Split out from [load] so the JVM
     * unit tests can drive the exact same read/parse/validation path with a temp
     * file, without a real Android [Context]. The production entry point is
     * [load(Context)], which always targets `filesDir/provider.local.json`.
     *
     * The file is opened read-only and is never created or modified.
     */
    fun load(file: File): DeviceLocalProviderConfig {
        if (!file.exists() || !file.isFile) {
            return DeviceLocalProviderConfig.Missing
        }

        val raw = try {
            // Normal private-file read. Read-only.
            file.readText()
        } catch (e: Exception) {
            // An unreadable private file is treated as invalid, not a crash, and
            // never exposes the exception's raw content.
            return DeviceLocalProviderConfig.Invalid(REASON_INVALID)
        }

        return try {
            DeviceLocalProviderConfig.Loaded(
                OpenAiCompatibleConfigLoader.parse(raw, label = FILE_NAME),
            )
        } catch (e: ProviderConfigException) {
            // Map every provider-config failure to the single stable, non-secret
            // category. The parser's message is intentionally not surfaced because
            // it names the file and could echo a JSON shape; the UI only needs to
            // know "device config is unusable, use manual setup".
            DeviceLocalProviderConfig.Invalid(REASON_INVALID)
        }
    }

    /**
     * Builds the device-local file from the app's [Context]. Exposed for tests so
     * they can assert the exact location without hardcoding an absolute path.
     */
    fun configFile(context: Context): File = File(context.filesDir, FILE_NAME)
}

/**
 * A selectable provider node (endpoint + credentials), each backed by its own
 * device-local config file.
 *
 * The node concept is Host/UI-level: it lets AGA switch between providers
 * without re-typing credentials. It does NOT add a provider abstraction to AG
 * Core — the selected node still produces a plain [OpenAiCompatibleConfig].
 */
enum class ProviderNode(val displayName: String, val configFileName: String) {

    /**
     * OpenRouter endpoint (StreamLake-routed model). First in the enum so it is
     * the DEFAULT provider at startup; the EMG endpoint is the fallback.
     */
    OPENROUTER("OpenRouter", "provider.openrouter.json"),

    /** Current development endpoint (EMG, cloudflare-backed). */
    EMG("EMG", "provider.local.json"),
}

/**
 * Loads the device-local config for a specific [ProviderNode].
 *
 * Reuses [DeviceLocalProviderConfigLoader] semantics (same parser, same
 * Loaded/Missing/Invalid contract) against the node's own file name, so a node
 * that has no provisioned config simply falls back to the manual UI flow.
 */
object DeviceLocalProviderNodeLoader {

    /** Device-local config file for [node], e.g. `filesDir/provider.openrouter.json`. */
    fun configFile(context: Context, node: ProviderNode): File =
        File(context.filesDir, node.configFileName)

    /** Loads [node]'s device-local config. Never throws. */
    fun load(context: Context, node: ProviderNode): DeviceLocalProviderConfig =
        DeviceLocalProviderConfigLoader.load(configFile(context, node))

    /**
     * Saves [config] to [node]'s device-local file.
     *
     * AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT: minimal Host-side save path
     * for the Provider Settings Dialog. Writes the same JSON format that
     * [DeviceLocalProviderConfigLoader.load] reads — no second storage system.
     *
     * Secret semantics: [config.apiKey] is written to the file (it IS the
     * stored credential). The file is in the app's private filesDir with
     * default 0700 permissions. This is NOT a new storage location — it
     * reuses the EXISTING device-local config file.
     *
     * @return true if the file was written successfully.
     */
    fun save(context: Context, node: ProviderNode, config: OpenAiCompatibleConfig): Boolean {
        val file = configFile(context, node)
        return try {
            val json = buildConfigJson(config)
            file.writeText(json)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds the device-local config JSON from [config].
     * Matches the format that [OpenAiCompatibleConfigLoader.parse] expects.
     */
    internal fun buildConfigJson(config: OpenAiCompatibleConfig): String {
        val timeoutSeconds = config.requestTimeout.toSeconds()
        return buildString {
            append("{\n")
            append("  \"provider\": {\n")
            append("    \"type\": \"openai-compatible\",\n")
            append("    \"baseUrl\": \"").append(jsonEscape(config.baseUrl)).append("\",\n")
            append("    \"apiKey\": \"").append(jsonEscape(config.apiKey)).append("\",\n")
            append("    \"model\": \"").append(jsonEscape(config.model)).append("\",\n")
            append("    \"requestTimeoutSeconds\": ").append(timeoutSeconds).append("\n")
            append("  }\n")
            append("}\n")
        }
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
