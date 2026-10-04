package com.androidagent.provider.openai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * C1 - C9: the AG-owned local provider configuration loader and resolver.
 *
 * Deterministic, offline, and secret-safe: no test may leak an api key value into
 * a failure message.
 */
class ProviderConfigTest {

    private fun write(dir: Path, name: String, body: String): Path =
        Files.writeString(dir.resolve(name), body)

    private fun validJson(
        type: String = "openai-compatible",
        baseUrl: String = "https://example.test/v1",
        apiKey: String = "test-secret",
        model: String = "model-a",
    ) = """
        {"provider":{"type":"$type","baseUrl":"$baseUrl",
        "apiKey":"$apiKey","model":"$model"}}
    """.trimIndent()

    // ------------------------------------------------------------------ C1

    @Test
    fun `C1 valid config maps to OpenAiCompatibleConfig`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson())
        val config = OpenAiCompatibleConfigLoader.load(path)

        assertEquals("https://example.test/v1", config.baseUrl)
        assertEquals("test-secret", config.apiKey)
        assertEquals("model-a", config.model)
        assertEquals(
            "https://example.test/v1/chat/completions",
            config.chatCompletionsEndpoint,
        )
    }

    // ------------------------------------------------------------------ C2

    @Test
    fun `C2 missing file fails explicitly`(@TempDir dir: Path) {
        val missing = dir.resolve("nope.json")
        val error = assertThrows(ProviderConfigException.NotFound::class.java) {
            OpenAiCompatibleConfigLoader.load(missing)
        }
        assertTrue(error.message!!.contains("not found"))
        assertFalse(OpenAiCompatibleConfigLoader.isUsable(missing))
    }

    // ------------------------------------------------------------------ C3

    @Test
    fun `C3 malformed JSON fails without leaking content`(@TempDir dir: Path) {
        val path = write(dir, "bad.json", """{"provider": {"apiKey": "test-secret", """)
        val error = assertThrows(ProviderConfigException.Malformed::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertFalse(error.message!!.contains("test-secret"), "raw content must not be echoed")
    }

    // ------------------------------------------------------------------ C4

    @Test
    fun `C4 missing baseUrl fails`(@TempDir dir: Path) {
        val path = write(
            dir,
            "provider.local.json",
            """{"provider":{"type":"openai-compatible","model":"model-a"}}""",
        )
        val error = assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("baseUrl"))
    }

    // ------------------------------------------------------------------ C5

    @Test
    fun `C5 missing model fails`(@TempDir dir: Path) {
        val path = write(
            dir,
            "provider.local.json",
            """{"provider":{"type":"openai-compatible","baseUrl":"https://example.test/v1"}}""",
        )
        val error = assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("model"))
    }

    // ------------------------------------------------------------------ C6

    @Test
    fun `C6 unsupported type fails instead of guessing`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(type = "anthropic-native"))
        val error = assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("unsupported provider type"))
        assertTrue(error.message!!.contains("openai-compatible"))
    }

    // ------------------------------------------------------------------ C7

    @Test
    fun `C7 REPLACE_ME placeholder is configuration not ready`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(apiKey = "REPLACE_ME"))
        val error = assertThrows(ProviderConfigException.PlaceholderNotReplaced::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("not ready"))
        assertFalse(
            OpenAiCompatibleConfigLoader.isUsable(path),
            "a placeholder config must never be treated as usable",
        )
    }

    // ------------------------------------------------------------------ C8

    @Test
    fun `C8 empty api key is allowed for unauthenticated endpoints`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(apiKey = ""))
        val config = OpenAiCompatibleConfigLoader.load(path)
        assertEquals("", config.apiKey)
        assertTrue(config.toString().contains("apiKey=<redacted>"))
    }

    // ------------------------------------------------------------------ C9

    @Test
    fun `C9 secret never appears in diagnostics`(@TempDir dir: Path) {
        val secret = "sk-do-not-leak-value-4242"

        val scenarios = listOf(
            validJson(type = "wrong-type", apiKey = secret),
            """{"provider":{"type":"openai-compatible","model":"m"}}""",
            """{"provider":{"type":"openai-compatible","baseUrl":"https://x.test/v1"}}""",
            """{"provider": "not-an-object"}""",
            validJson(apiKey = "REPLACE_ME"),
        )

        for ((i, body) in scenarios.withIndex()) {
            val path = write(dir, "s$i.json", body)
            val thrown = runCatching { OpenAiCompatibleConfigLoader.load(path) }.exceptionOrNull()
            assertTrue(thrown is ProviderConfigException, "scenario $i should fail")
            val rendered = thrown!!.toString()
            assertFalse(rendered.contains(secret), "scenario $i leaked the secret")
            // The message must stay useful, i.e. name what went wrong.
            assertTrue(rendered.isNotBlank(), "scenario $i gave no diagnostic")
        }

        // And a valid config must not print the key either.
        val ok = write(dir, "ok.json", validJson(apiKey = secret))
        assertFalse(OpenAiCompatibleConfigLoader.load(ok).toString().contains(secret))
    }

    // ------------------------------------------------------------- Resolution

    @Test
    fun `local json is preferred over environment`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(model = "from-json"))
        val resolved = ProviderConfigResolver.resolve(path) {
            when (it) {
                "AG_OPENAI_BASE_URL" -> "https://env.test/v1"
                "AG_OPENAI_MODEL" -> "from-env"
                else -> ""
            }
        }
        assertTrue(resolved.source is ConfigSource.LocalJson)
        assertEquals("from-json", resolved.config.model, "no field may come from the fallback")
    }

    @Test
    fun `environment is used only when local json is unusable`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(apiKey = "REPLACE_ME"))
        val resolved = ProviderConfigResolver.resolve(path) {
            when (it) {
                "AG_OPENAI_BASE_URL" -> "https://env.test/v1"
                "AG_OPENAI_MODEL" -> "from-env"
                "AG_OPENAI_API_KEY" -> "env-key"
                else -> null
            }
        }
        assertEquals(ConfigSource.Environment, resolved.source)
        assertEquals("from-env", resolved.config.model)
        assertEquals("env-key", resolved.config.apiKey)
    }

    @Test
    fun `resolution fails when neither source is usable`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson(apiKey = "REPLACE_ME"))
        val error = assertThrows(ProviderConfigException.PlaceholderNotReplaced::class.java) {
            ProviderConfigResolver.resolve(path) { null }
        }
        assertTrue(error.message!!.contains("not ready"))
    }

    @Test
    fun `repository example config is loadable once a key is supplied`(@TempDir dir: Path) {
        // Proves the shipped example has the right shape: only the placeholder
        // stands between it and a usable configuration.
        val example = Path.of("..", "config", "provider.example.json").toAbsolutePath().normalize()
        val error = assertThrows(ProviderConfigException.PlaceholderNotReplaced::class.java) {
            OpenAiCompatibleConfigLoader.load(example)
        }
        assertTrue(error.message!!.contains("not ready"))
    }

    // ------------------------------------------- requestTimeoutSeconds (C10-C14)

    private fun timeoutJson(value: String) =
        """
        {"provider":{"type":"openai-compatible","baseUrl":"https://example.test/v1",
        "apiKey":"k","model":"m","requestTimeoutSeconds":$value}}
        """.trimIndent()

    @Test
    fun `C10 absent requestTimeoutSeconds uses the generic default`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", validJson())
        val config = OpenAiCompatibleConfigLoader.load(path)
        assertEquals(
            OpenAiCompatibleConfig.DEFAULT_REQUEST_TIMEOUT,
            config.requestTimeout,
            "an omitted timeout must fall back to the single generic default",
        )
        assertEquals(60L, config.requestTimeout.seconds, "the generic default must stay 60s")
    }

    @Test
    fun `C11 requestTimeoutSeconds maps onto the existing config field`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", timeoutJson("120"))
        val config = OpenAiCompatibleConfigLoader.load(path)
        assertEquals(java.time.Duration.ofSeconds(120), config.requestTimeout)
    }

    @Test
    fun `C12 requestTimeoutSeconds zero is rejected`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", timeoutJson("0"))
        val error = assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("greater than 0"))
    }

    @Test
    fun `C13 negative requestTimeoutSeconds is rejected`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", timeoutJson("-1"))
        assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
    }

    @Test
    fun `C14 non-numeric requestTimeoutSeconds is rejected`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", timeoutJson("\"soon\""))
        val error = assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
        assertTrue(error.message!!.contains("whole number"))
    }

    @Test
    fun `C14b fractional requestTimeoutSeconds is rejected`(@TempDir dir: Path) {
        val path = write(dir, "provider.local.json", timeoutJson("1.5"))
        assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
    }

    @Test
    fun `C14c object typed requestTimeoutSeconds is rejected`(@TempDir dir: Path) {
        val path = write(
            dir,
            "provider.local.json",
            """{"provider":{"type":"openai-compatible","baseUrl":"https://example.test/v1",
               "apiKey":"k","model":"m","requestTimeoutSeconds":{"seconds":10}}}""",
        )
        assertThrows(ProviderConfigException.Invalid::class.java) {
            OpenAiCompatibleConfigLoader.load(path)
        }
    }

    @Test
    fun `C14d no arbitrary upper bound is imposed`(@TempDir dir: Path) {
        // A very large timeout is legal: the value is the deployment's call.
        val path = write(dir, "provider.local.json", timeoutJson("86400"))
        assertEquals(
            java.time.Duration.ofSeconds(86400),
            OpenAiCompatibleConfigLoader.load(path).requestTimeout,
        )
    }

    @Test
    fun `C14e config rejects a non-positive timeout directly`(@TempDir dir: Path) {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleConfig(
                baseUrl = "https://example.test/v1",
                model = "m",
                requestTimeout = java.time.Duration.ZERO,
            )
        }
    }
}
