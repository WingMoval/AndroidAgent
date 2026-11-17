package com.androidagent.app

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * AG.PROVIDER-CONFIGURED-MODELS-0 — JVM tests for the configured/discovered
 * model separation and the model alias / short-display semantics.
 *
 * These are pure (no Android Context / no network / no device), so they run on
 * the JVM. They pin the frozen product semantics:
 *
 *  - DISCOVERED models (provider /models) are NOT configured models.
 *  - A profile stores an explicit CONFIGURED model list (with optional alias).
 *  - config.model is the SELECTED model and must be a member of the configured set.
 *  - A legacy file (no configuredModels) normalizes to [ConfiguredModel(model)].
 *  - The alias affects ONLY display; the modelId is what is sent to the API.
 *  - The home short display = alias, else the modelId provider prefix, else the id.
 *
 * @author test
 */
class ConfiguredModelsSemanticsTest {

    private fun cfg(model: String) = OpenAiCompatibleConfig(
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "test-key-not-real",
        model = model,
    )

    // ------------------------------------------------------------- ConfiguredModel

    @Test
    fun configuredModelRejectsBlankModelId() {
        assertThrows<IllegalArgumentException> { ConfiguredModel("  ") }
    }

    @Test
    fun aliasBlankNormalizesToNull() {
        assertEquals(null, ConfiguredModel("a/b", "   ").normalizedAlias)
        assertEquals(null, ConfiguredModel("a/b", null).normalizedAlias)
    }

    @Test
    fun aliasTrimmed() {
        assertEquals("V4 Flash", ConfiguredModel("a/b", "  V4 Flash  ").normalizedAlias)
    }

    // ------------------------------------------------------------- short display

    @Test
    fun shortDisplayUsesAliasWhenPresent() {
        assertEquals("V4 Flash", ConfiguredModel("deepseek/deepseek-v4-flash-0731", "V4 Flash").shortDisplay)
    }

    @Test
    fun shortDisplayFallsBackToProviderPrefix() {
        assertEquals("deepseek", ConfiguredModel("deepseek/deepseek-v4-flash-0731").shortDisplay)
        assertEquals("google", ConfiguredModel("google/gemini-2.5-flash").shortDisplay)
        assertEquals("qwen", ConfiguredModel("qwen/qwen3-coder").shortDisplay)
    }

    @Test
    fun shortDisplayUsesWholeIdWhenNoSlash() {
        assertEquals("local-qwen", ConfiguredModel("local-qwen").shortDisplay)
    }

    @Test
    fun shortDisplayBlankAliasFallsBackToPrefix() {
        assertEquals("deepseek", ConfiguredModel("deepseek/deepseek-v4-flash-0731", "   ").shortDisplay)
    }

    @Test
    fun modelShortDisplayTopLevelMatchesRule() {
        assertEquals("deepseek", modelShortDisplay("deepseek/deepseek-v4-flash-0731"))
        assertEquals("V4 Flash", modelShortDisplay("deepseek/deepseek-v4-flash-0731", "V4 Flash"))
        assertEquals("local-qwen", modelShortDisplay("local-qwen"))
        assertEquals("Model", modelShortDisplay(null))
        assertEquals("Model", modelShortDisplay("   "))
    }

    // ------------------------------------------------------------- profile invariants

    @Test
    fun profileDefaultsToOneConfiguredModel() {
        val p = ProviderProfile("openrouter", "OpenRouter", true, cfg("deepseek/x"))
        assertEquals(listOf(ConfiguredModel("deepseek/x")), p.configuredModels)
        assertEquals("deepseek/x", p.selectedConfiguredModel?.modelId)
    }

    @Test
    fun profileRequiresSelectedModelInConfiguredSet() {
        // The init invariant: the selected model (config.model) must be one of the
        // configured models. Constructing an inconsistent profile is rejected.
        assertThrows<IllegalArgumentException> {
            ProviderProfile(
                "openrouter", "OpenRouter", true, cfg("deepseek/x"),
                configuredModels = listOf(ConfiguredModel("other/y")),
            )
        }
    }

    @Test
    fun profileRequiresUniqueConfiguredModelIds() {
        assertThrows<IllegalArgumentException> {
            ProviderProfile(
                "openrouter", "OpenRouter", true, cfg("deepseek/x"),
                configuredModels = listOf(ConfiguredModel("deepseek/x"), ConfiguredModel("deepseek/x")),
            )
        }
    }

    @Test
    fun profileRejectsEmptyConfiguredModels() {
        assertThrows<IllegalArgumentException> {
            ProviderProfile("openrouter", "OpenRouter", true, cfg("deepseek/x"), configuredModels = emptyList())
        }
    }

    @Test
    fun defaultConfiguredSetAlwaysContainsSelectedModel() {
        // The default (and every store-built) profile keeps selected ∈ configured.
        val p = ProviderProfile("openrouter", "OpenRouter", true, cfg("deepseek/x"))
        assertNotEquals(null, p.selectedConfiguredModel)
        assertEquals("deepseek/x", p.selectedConfiguredModel?.modelId)
    }

    // ------------------------------------------------------------- persistence JSON

    @Test
    fun buildProfileJsonIncludesConfiguredModelsAndAlias() {
        val p = ProviderProfile(
            "custom-1", "My API", false, cfg("deepseek/deepseek-v4-flash-0731"),
            configuredModels = listOf(
                ConfiguredModel("deepseek/deepseek-v4-flash-0731", "V4 Flash"),
                ConfiguredModel("google/gemini-2.5-flash"),
            ),
        )
        val json = ProviderProfileStore.buildProfileJson(p)
        assertTrue(json.contains("\"configuredModels\""), "json must contain configuredModels: $json")
        assertTrue(json.contains("\"modelId\": \"deepseek/deepseek-v4-flash-0731\""))
        assertTrue(json.contains("\"alias\": \"V4 Flash\""))
        assertTrue(json.contains("\"modelId\": \"google/gemini-2.5-flash\""))
        // The selected model is still the single `model` field.
        assertTrue(json.contains("\"model\": \"deepseek/deepseek-v4-flash-0731\""))
    }

    @Test
    fun parseProfileJsonLegacySingleModelNormalizes() {
        // Legacy file: no configuredModels field.
        val json = """
            {
              "profileId": "openrouter",
              "name": "OpenRouter",
              "provider": {
                "type": "openai-compatible",
                "baseUrl": "https://openrouter.ai/api/v1",
                "apiKey": "k",
                "model": "deepseek/deepseek-v4-flash-0731",
                "requestTimeoutSeconds": 180
              }
            }
        """.trimIndent()
        val p = ProviderProfileStore.parseProfileJson(json, "openrouter")
        assertNotEquals(null, p)
        assertEquals(listOf(ConfiguredModel("deepseek/deepseek-v4-flash-0731", null)), p!!.configuredModels)
        assertEquals("deepseek/deepseek-v4-flash-0731", p.config.model)
    }

    @Test
    fun parseProfileJsonRoundTripsConfiguredModelsAndAliases() {
        val original = ProviderProfile(
            "custom-1", "My API", false, cfg("deepseek/deepseek-v4-flash-0731"),
            configuredModels = listOf(
                ConfiguredModel("deepseek/deepseek-v4-flash-0731", "V4 Flash"),
                ConfiguredModel("google/gemini-2.5-flash"),
            ),
        )
        val json = ProviderProfileStore.buildProfileJson(original)
        val parsed = ProviderProfileStore.parseProfileJson(json, "custom-1")
        assertNotEquals(null, parsed)
        assertEquals(2, parsed!!.configuredModels.size)
        assertEquals("V4 Flash", parsed.configuredModels[0].normalizedAlias)
        assertNull(parsed.configuredModels[1].normalizedAlias)
        assertEquals("deepseek/deepseek-v4-flash-0731", parsed.config.model)
    }

    @Test
    fun parseConfiguredModelsReturnsNullWhenAbsent() {
        val json = """{ "provider": { "model": "a/b" } }"""
        assertEquals(null, ProviderProfileStore.parseConfiguredModels(json, "a/b"))
    }

    @Test
    fun parseConfiguredModelsDedupsAndSkipsBlank() {
        val json = """
            { "provider": { "model": "a/b", "configuredModels": [
                { "modelId": "a/b", "alias": "AB" },
                { "modelId": "a/b" },
                { "modelId": "  " },
                { "modelId": "c/d" }
            ] } }
        """.trimIndent()
        val list = ProviderProfileStore.parseConfiguredModels(json, "a/b")
        assertNotEquals(null, list)
        assertEquals(listOf("a/b", "c/d"), list!!.map { it.modelId })
        assertEquals("AB", list[0].normalizedAlias)
    }

    @Test
    fun parseConfiguredModelsPrependsSelectedIfMissing() {
        val json = """
            { "provider": { "model": "sel/x", "configuredModels": [ { "modelId": "other/y" } ] } }
        """.trimIndent()
        val list = ProviderProfileStore.parseConfiguredModels(json, "sel/x")
        assertNotEquals(null, list)
        assertTrue(list!!.any { it.modelId == "sel/x" })
        assertTrue(list.any { it.modelId == "other/y" })
    }

    // ------------------------------------------------------------- node loader (built-in files)

    @Test
    fun nodeBuildConfigJsonIncludesConfiguredModels() {
        val json = DeviceLocalProviderNodeLoader.buildConfigJson(
            cfg("deepseek/deepseek-v4-flash-0731"),
            listOf(ConfiguredModel("deepseek/deepseek-v4-flash-0731", "V4 Flash"), ConfiguredModel("google/gemini-2.5-flash")),
        )
        assertTrue(json.contains("\"configuredModels\""))
        assertTrue(json.contains("\"modelId\": \"deepseek/deepseek-v4-flash-0731\""))
        assertTrue(json.contains("\"alias\": \"V4 Flash\""))
    }

    @Test
    fun nodeBuildConfigJsonLegacyWhenNoConfigured() {
        val json = DeviceLocalProviderNodeLoader.buildConfigJson(cfg("a/b"))
        assertFalse(json.contains("configuredModels"), "legacy shape must not include configuredModels: $json")
        assertTrue(json.contains("\"model\": \"a/b\""))
    }

    // ------------------------------------------------------------- alias isolation

    @Test
    fun aliasNeverBecomesTheModelId() {
        val cm = ConfiguredModel("deepseek/deepseek-v4-flash-0731", "V4 Flash")
        // The value sent to the API is always modelId, never the alias.
        assertEquals("deepseek/deepseek-v4-flash-0731", cm.modelId)
        assertNotEquals("V4 Flash", cm.modelId)
        // The display uses the alias.
        assertEquals("V4 Flash", cm.shortDisplay)
    }

    // ------------------------------------------------------------- catalog is configured-only

    @Test
    fun modelOptionCarriesProfileAndId() {
        val opt = ModelOption("deepseek/x", "openrouter", "deepseek/x")
        assertEquals("deepseek/x", opt.modelId)
        assertEquals("openrouter", opt.profileId)
        assertEquals("deepseek/x", opt.displayLabel)
    }
}
