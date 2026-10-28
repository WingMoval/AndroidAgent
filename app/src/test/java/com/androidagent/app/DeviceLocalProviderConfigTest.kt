package com.androidagent.app

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Duration

/**
 * AGA.DEV-CONFIG-0 — T1, T2, T3, T4, T5, T6, T10, T11, T12.
 *
 * Proves the device-local provider config path on the JVM. The real
 * [DeviceLocalProviderConfigLoader.load(File)] runs (normal File IO + the reused
 * OpenAiCompatibleConfigLoader.parse) against a temp file standing in for
 * `filesDir/provider.local.json`. No Android, no ADB, no network.
 *
 * T7 (startup auto-config plumbing) and T8/T9 (manual fallback/override) are the
 * ChatActivity UI flow; they are covered here at the contract level (a Loaded
 * config can drive a ChatCompositionRoot, and Missing/Invalid leave the manual
 * flow intact) because constructing the full Activity requires an instrumented
 * device, which this Step does not run.
 */
class DeviceLocalProviderConfigTest {

    private fun configFileIn(dir: File): File = File(dir, DeviceLocalProviderConfigLoader.FILE_NAME)

    private fun writeConfig(dir: File, body: String) {
        configFileIn(dir).writeText(body)
    }

    private fun validJson(
        type: String = "openai-compatible",
        baseUrl: String = "https://example.test/v1",
        apiKey: String = "dev-key-not-real",
        model: String = "model-a",
        timeoutSeconds: String? = null,
    ): String {
        val timeout = timeoutSeconds?.let { ",\"requestTimeoutSeconds\":$it" } ?: ""
        return "{\"provider\":{\"type\":\"$type\",\"baseUrl\":\"$baseUrl\"," +
            "\"apiKey\":\"$apiKey\",\"model\":\"model-a\"$timeout}}"
    }

    // ------------------------------------------------------------------ T1 missing

    @Test
    fun T1_missing_file_is_Missing() {
        val dir = newTempDir()
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Missing)
    }

    // ------------------------------------------------------------------ T2 valid

    @Test
    fun T2_valid_file_loads_expected_config() {
        val dir = newTempDir()
        writeConfig(dir, validJson(apiKey = "dev-key-not-real", model = "model-a", timeoutSeconds = "90"))
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Loaded)
        val cfg = (result as DeviceLocalProviderConfig.Loaded).config
        assertEquals("https://example.test/v1", cfg.baseUrl)
        assertEquals("dev-key-not-real", cfg.apiKey)
        assertEquals("model-a", cfg.model)
        assertEquals(Duration.ofSeconds(90), cfg.requestTimeout)
    }

    // ------------------------------------------------------------------ T3 same schema

    @Test
    fun T3_device_local_uses_same_parser_and_semantics_as_repo_local() {
        // Same JSON, parsed two ways: the repo-side loader (load(Path)) and the
        // device-side loader (load(File) -> parse). They must agree exactly, which
        // proves the device file uses the identical schema + validation.
        val body = validJson(apiKey = "shared-key", model = "shared-model", timeoutSeconds = "120")
        val repoDir = newTempDir()
        val repoFile = File(repoDir, "provider.local.json")
        repoFile.writeText(body)
        val viaRepo = OpenAiCompatibleConfigLoader.load(repoFile.toPath())

        val deviceDir = newTempDir()
        writeConfig(deviceDir, body)
        val viaDevice = (DeviceLocalProviderConfigLoader.load(configFileIn(deviceDir))
            as DeviceLocalProviderConfig.Loaded).config

        assertEquals(viaRepo, viaDevice, "device-local and repo-local must be identical")
    }

    // ------------------------------------------------------------------ T4 invalid JSON

    @Test
    fun T4_malformed_json_is_Invalid_without_crash_or_secret_leak() {
        val dir = newTempDir()
        val secret = "DEV_CONFIG_SECRET_SENTINEL"
        writeConfig(dir, "{\"provider\": {\"apiKey\": \"$secret\", \"model\":")
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Invalid)
        val reason = (result as DeviceLocalProviderConfig.Invalid).safeReason
        assertEquals(DeviceLocalProviderConfigLoader.REASON_INVALID, reason)
        assertFalse(reason.contains(secret), "the reason must not echo the secret")
        assertFalse(reason.contains("DEV_CONFIG_SECRET_SENTINEL"))
    }

    // ------------------------------------------------------------------ T5 invalid semantics

    @Test
    fun T5_invalid_semantic_config_is_Invalid() {
        val dir = newTempDir()
        // REPLACE_ME placeholder -> ProviderConfigException.PlaceholderNotReplaced
        writeConfig(dir, validJson(apiKey = "REPLACE_ME"))
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Invalid)
    }

    @Test
    fun T5b_unsupported_type_is_Invalid() {
        val dir = newTempDir()
        writeConfig(dir, validJson(type = "anthropic-native"))
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Invalid)
    }

    @Test
    fun T5c_missing_provider_object_is_Invalid() {
        val dir = newTempDir()
        writeConfig(dir, "{\"other\":1}")
        val result = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(result is DeviceLocalProviderConfig.Invalid)
    }

    // ------------------------------------------------------------------ T6 secret safety

    @Test
    fun T6_sentinel_never_appears_in_any_result_or_reason() {
        val sentinel = "DEV_CONFIG_SECRET_SENTINEL"
        // Present-but-invalid: the file holds the sentinel; the reason must not.
        val dir = newTempDir()
        writeConfig(dir, "{\"provider\": {\"apiKey\": \"$sentinel\", \"model\":")
        val invalid = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(invalid is DeviceLocalProviderConfig.Invalid)
        val rendered = invalid.toString()
        assertFalse(rendered.contains(sentinel), "Invalid result leaked the sentinel: $rendered")

        // The Missing result must not carry it either.
        val missing = DeviceLocalProviderConfigLoader.load(configFileIn(newTempDir()))
        assertFalse(missing.toString().contains(sentinel))

        // A valid load parses the key into the config; the config's own toString
        // (OpenAiCompatibleConfig) is already redacted and must not show the key.
        val okDir = newTempDir()
        writeConfig(okDir, validJson(apiKey = sentinel))
        val loaded = DeviceLocalProviderConfigLoader.load(configFileIn(okDir)) as DeviceLocalProviderConfig.Loaded
        assertFalse(loaded.config.toString().contains(sentinel))
        assertTrue(loaded.config.toString().contains("apiKey=<redacted>"))
        // The key is still present in memory for the provider to use.
        assertEquals(sentinel, loaded.config.apiKey)
    }

    // ------------------------------------------------------------ T7 auto-config plumbing

    @Test
    fun T7_loaded_config_can_drive_a_composition_root_shape() {
        // The Loaded config is a fully specified OpenAiCompatibleConfig, which is
        // exactly the type ChatCompositionRoot accepts. This proves the device-local
        // path produces a ready-to-use config with no manual key entry.
        val dir = newTempDir()
        writeConfig(dir, validJson(apiKey = "dev-key-not-real", model = "model-a", timeoutSeconds = "120"))
        val loaded = DeviceLocalProviderConfigLoader.load(configFileIn(dir)) as DeviceLocalProviderConfig.Loaded
        val cfg: OpenAiCompatibleConfig = loaded.config
        // A Composition Root is built from (config, applicationContext). We verify
        // the config is complete and usable (non-blank baseUrl/model, positive
        // timeout) — the exact preconditions ChatCompositionRoot requires.
        assertTrue(cfg.baseUrl.isNotBlank())
        assertTrue(cfg.model.isNotBlank())
        assertTrue(cfg.requestTimeout.seconds > 0)
        // The key is held in memory only; the device-local file is never written by
        // the app (T10). Here the file still exists and is unchanged.
        assertTrue(File(dir, DeviceLocalProviderConfigLoader.FILE_NAME).exists())
    }

    // ------------------------------------------------------------ T8 manual fallback

    @Test
    fun T8_missing_and_invalid_keep_manual_flow_available() {
        // Missing: the manual UI flow (Apply) is the path; the loader simply reports
        // Missing, so ChatActivity falls back to "enter key + Apply".
        val missing = DeviceLocalProviderConfigLoader.load(configFileIn(newTempDir()))
        assertTrue(missing is DeviceLocalProviderConfig.Missing)

        // Invalid: same fallback, with a safe category, and no crash.
        val dir = newTempDir()
        writeConfig(dir, "not json at all")
        val invalid = DeviceLocalProviderConfigLoader.load(configFileIn(dir))
        assertTrue(invalid is DeviceLocalProviderConfig.Invalid)
        assertEquals(DeviceLocalProviderConfigLoader.REASON_INVALID, (invalid as DeviceLocalProviderConfig.Invalid).safeReason)
    }

    // ------------------------------------------------------------ T10 read-only loader

    @Test
    fun T10_loader_is_read_only() {
        // AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT: The core DeviceLocalProviderConfigLoader
        // remains read-only (File.readText). The NodeLoader now has a save() method for the
        // Provider Settings Dialog, but the core loader itself never writes.
        // We verify the core loader section (before the NodeLoader) has no write operations.
        val src = File("src/main/java/com/androidagent/app/DeviceLocalProviderConfig.kt")
        val text = src.readText()
        // The core loader object ends before the NodeLoader. Extract just that section.
        val loaderStart = text.indexOf("object DeviceLocalProviderConfigLoader")
        val nodeLoaderStart = text.indexOf("object DeviceLocalProviderNodeLoader")
        val coreLoaderText = text.substring(loaderStart, nodeLoaderStart)
        assertFalse(coreLoaderText.contains("FileOutputStream"), "core loader must not open a file output stream")
        assertFalse(coreLoaderText.contains("FileWriter"), "core loader must not open a file writer")
        assertFalse(coreLoaderText.contains("writeText"), "core loader must not write text")
        // It does read via readText (normal private-file read).
        assertTrue(coreLoaderText.contains("readText"))
    }

    // ------------------------------------------------------------ T10b save round-trip

    @Test
    fun T10b_save_and_load_roundtrip() {
        // AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT: The NodeLoader.save() writes
        // the same format that load() reads. Verify round-trip integrity.
        val dir = newTempDir()
        val node = ProviderNode.EMG
        val file = File(dir, node.configFileName)

        val original = OpenAiCompatibleConfig(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key-123",
            model = "test-model",
            requestTimeout = java.time.Duration.ofSeconds(30),
        )

        // Write via buildConfigJson (the same path save() uses)
        file.writeText(DeviceLocalProviderNodeLoader.buildConfigJson(original))

        // Read back
        val loaded = DeviceLocalProviderConfigLoader.load(file)
        assertTrue(loaded is DeviceLocalProviderConfig.Loaded, "round-trip should load")
        val cfg = (loaded as DeviceLocalProviderConfig.Loaded).config
        assertEquals(original.baseUrl, cfg.baseUrl)
        assertEquals(original.apiKey, cfg.apiKey)
        assertEquals(original.model, cfg.model)
        assertEquals(30L, cfg.requestTimeout.toSeconds())
    }

    // ------------------------------------------------------------ T10c secret-safe save

    @Test
    fun T10c_save_does_not_expose_key_in_json_structure() {
        // The saved JSON must not contain the key in any loggable/echoable form.
        // The key IS in the file (it's the stored credential) but the JSON structure
        // is standard and the key is under the expected "apiKey" field.
        val cfg = OpenAiCompatibleConfig(
            baseUrl = "https://api.example.com/v1",
            apiKey = "super-secret-key",
            model = "m1",
        )
        val json = DeviceLocalProviderNodeLoader.buildConfigJson(cfg)
        // The key appears exactly once (in the apiKey field)
        assertEquals(1, json.split("super-secret-key").size - 1, "key must appear exactly once")
        // No raw exception-style exposure
        assertFalse(json.contains("Exception"))
        assertFalse(json.contains("stacktrace"))
    }

    // ------------------------------------------------------------ T11 not packaged

    @Test
    fun T11_provider_local_json_is_not_a_tracked_or_packaged_asset() {
        // The repo config is gitignored; only the example is tracked. The app must
        // not ship a provider.local.json in assets/res/raw (it would embed the key).
        val assets = File("src/main/assets")
        val resRaw = File("src/main/res/raw")
        if (assets.exists()) {
            assertFalse(assets.walkTopDown().any { it.name == DeviceLocalProviderConfigLoader.FILE_NAME })
        }
        if (resRaw.exists()) {
            assertFalse(resRaw.walkTopDown().any { it.name == DeviceLocalProviderConfigLoader.FILE_NAME })
        }
        // The repo's real local config is not tracked (gitignore covers it).
        val gitIgnore = File("..", ".gitignore").readText()
        assertTrue(gitIgnore.contains("/config/provider.local.json"))
    }

    // ------------------------------------------------------------ T12 boundary

    @Test
    fun T12_no_at_imports_and_no_adb_in_app_main() {
        // The unit test working dir is the :app module dir, so the app sources are
        // under src/main/java (no app/ prefix).
        val appMain = File("src/main/java")
        val files = appMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val text = f.readText()
            assertFalse(text.contains("import com.androidterminal"), "AT import in ${f.path}")
            assertFalse(text.contains("com.android.commands.adb"), "ADB invocation in ${f.path}")
            assertFalse(text.contains("Runtime.getRuntime().exec"), "process exec (ADB-style) in ${f.path}")
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun newTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "agadevcfg-" + java.util.UUID.randomUUID())
        if (!dir.mkdirs() && !dir.exists()) {
            throw IllegalStateException("could not create temp dir " + dir)
        }
        return dir
    }
}
