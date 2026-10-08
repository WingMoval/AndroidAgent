package com.androidagent.app

import com.androidagent.provider.openai.OpenAiCompatibleConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AGA.HOST-CONTROL-0 — P1-P6: Provider node selection maps to each node's
 * device-local config and fails safely. Uses the File-based loader entry so the
 * tests run on the JVM (no Context / no Android).
 */
class ProviderNodeTest {

    private fun nodeFile(dir: File, node: ProviderNode): File =
        File(dir, node.configFileName)

    private fun writeConfig(file: File, body: String) {
        file.writeText(body)
    }

    private fun validJson(
        type: String = "openai-compatible",
        baseUrl: String = "https://example.test/v1",
        apiKey: String = "node-key-not-real",
        model: String = "model-a",
        timeoutSeconds: String? = null,
    ): String {
        val timeout = timeoutSeconds?.let { ",\"requestTimeoutSeconds\":$it" } ?: ""
        return "{\"provider\":{\"type\":\"$type\",\"baseUrl\":\"$baseUrl\"," +
            "\"apiKey\":\"$apiKey\",\"model\":\"$model\"$timeout}}"
    }

    @Test
    fun P1_emg_node_maps_to_emg_device_config() {
        val dir = newTempDir()
        val file = nodeFile(dir, ProviderNode.EMG)
        writeConfig(file, validJson(baseUrl = "https://api.emg.dpdns.org/v1", model = "qwen3.8-local"))
        val result = DeviceLocalProviderConfigLoader.load(file)
        assertTrue(result is DeviceLocalProviderConfig.Loaded)
        val cfg = (result as DeviceLocalProviderConfig.Loaded).config
        assertEquals("https://api.emg.dpdns.org/v1", cfg.baseUrl)
        assertEquals("qwen3.8-local", cfg.model)
        // EMG uses provider.local.json
        assertEquals("provider.local.json", ProviderNode.EMG.configFileName)
    }

    @Test
    fun P2_openrouter_node_maps_to_openrouter_device_config() {
        val dir = newTempDir()
        val file = nodeFile(dir, ProviderNode.OPENROUTER)
        writeConfig(file, validJson(baseUrl = "https://openrouter.ai/api/v1", model = "deepseek/deepseek-v4-flash-0731"))
        val result = DeviceLocalProviderConfigLoader.load(file)
        assertTrue(result is DeviceLocalProviderConfig.Loaded)
        val cfg = (result as DeviceLocalProviderConfig.Loaded).config
        assertEquals("https://openrouter.ai/api/v1", cfg.baseUrl)
        assertEquals("deepseek/deepseek-v4-flash-0731", cfg.model)
        // OpenRouter uses provider.openrouter.json
        assertEquals("provider.openrouter.json", ProviderNode.OPENROUTER.configFileName)
    }

    @Test
    fun P3_node_selection_applies_valid_config() {
        val dir = newTempDir()
        // Both nodes provisioned; the loader contract returns Loaded for either.
        writeConfig(nodeFile(dir, ProviderNode.EMG), validJson(model = "emg-model"))
        writeConfig(nodeFile(dir, ProviderNode.OPENROUTER), validJson(model = "or-model"))
        val emg = DeviceLocalProviderConfigLoader.load(nodeFile(dir, ProviderNode.EMG))
        val or = DeviceLocalProviderConfigLoader.load(nodeFile(dir, ProviderNode.OPENROUTER))
        assertTrue(emg is DeviceLocalProviderConfig.Loaded)
        assertTrue(or is DeviceLocalProviderConfig.Loaded)
        assertEquals("emg-model", (emg as DeviceLocalProviderConfig.Loaded).config.model)
        assertEquals("or-model", (or as DeviceLocalProviderConfig.Loaded).config.model)
    }

    @Test
    fun P4_missing_node_config_fails_safely() {
        val dir = newTempDir() // empty -> no config files
        val missing = DeviceLocalProviderConfigLoader.load(nodeFile(dir, ProviderNode.OPENROUTER))
        assertTrue(missing is DeviceLocalProviderConfig.Missing)
        // No throw, no secret.
        assertFalse(missing.toString().contains("apiKey"))
    }

    @Test
    fun P5_invalid_node_config_fails_safely() {
        val dir = newTempDir()
        writeConfig(nodeFile(dir, ProviderNode.EMG), "not json at all")
        val invalid = DeviceLocalProviderConfigLoader.load(nodeFile(dir, ProviderNode.EMG))
        assertTrue(invalid is DeviceLocalProviderConfig.Invalid)
        assertFalse(invalid.toString().contains("apiKey"))
    }

    @Test
    fun P6_no_secret_in_status() {
        val dir = newTempDir()
        val secret = "P6_SENTINEL_SECRET"
        writeConfig(nodeFile(dir, ProviderNode.EMG), validJson(apiKey = secret))
        val loaded = DeviceLocalProviderConfigLoader.load(nodeFile(dir, ProviderNode.EMG))
        assertTrue(loaded is DeviceLocalProviderConfig.Loaded)
        // A Loaded config's toString (OpenAiCompatibleConfig) is redacted.
        assertFalse(loaded.toString().contains(secret))
        assertFalse(loaded.toString().contains("P6_SENTINEL_SECRET"))
        // The key is still usable in memory.
        assertEquals(secret, (loaded as DeviceLocalProviderConfig.Loaded).config.apiKey)
    }

    private fun newTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "agnode-" + java.util.UUID.randomUUID())
        if (!dir.mkdirs() && !dir.exists()) {
            throw IllegalStateException("could not create temp dir " + dir)
        }
        return dir
    }
}