package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleConfigLoader
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.provider.openai.OpenAiHttpTransport
import com.androidagent.provider.openai.OpenAiHttpRequest
import com.androidagent.provider.openai.OpenAiHttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

/**
 * TEMP PC-side sanity check for AGA.PROVIDER-AB-HEADLESS-0.
 *
 * Sends a minimal request to OpenRouter via the real JDK transport + the real
 * Provider, exactly like LiveApiTest.L1 but config is read from the OpenRouter
 * local config so EMG config is left untouched. NOT part of production design;
 * kept as a diagnostic fixture for this step.
 */
@Tag("live")
class OpenRouterPcProbe {

    private val config: OpenAiCompatibleConfig by lazy {
        // Test working dir is the module dir; the repo config lives one level up.
        val path = Path.of("..", "config", "provider.openrouter.local.json").toAbsolutePath().normalize()
        OpenAiCompatibleConfigLoader.load(path)
    }

    private fun safeEndpoint(): String = runCatching {
        val uri = java.net.URI.create(config.chatCompletionsEndpoint)
        val authority = uri.rawAuthority?.substringAfter('@').orEmpty()
        "${uri.scheme}://$authority${uri.rawPath}"
    }.getOrElse { "endpoint" }

    @Test
    fun pc_openrouter_live() = runBlocking {
        val start = System.currentTimeMillis()
        println("[PC] CONFIG_SOURCE = OPENROUTER_LOCAL")
        println("[PC] MODEL = ${config.model}")
        println("[PC] SAFE_ENDPOINT = ${safeEndpoint()}")
        println("[PC] API_KEY = ${if (config.apiKey.isBlank()) "ABSENT" else "PRESENT"}")
        println("[PC] TIMEOUT = ${config.requestTimeout.seconds}s")

        val provider = OpenAiCompatibleProvider(config, JdkOpenAiHttpTransport())
        val turn = provider.complete(
            com.androidagent.core.ModelRequest(
                sessionId = "pc-openrouter-probe",
                instructions = "",
                history = listOf(com.androidagent.core.UserMessage("Reply with exactly: OK")),
                tools = emptyList(),
            ),
        )
        val elapsed = System.currentTimeMillis() - start
        println("[PC] RESULT = ${turn::class.simpleName}")
        println("[PC] ELAPSED_MS = $elapsed")
        when (turn) {
            is com.androidagent.core.ModelTurn.FinalAnswer ->
                println("[PC] CONTENT_LENGTH = ${turn.content.length}")
            is com.androidagent.core.ModelTurn.ToolCalls ->
                println("[PC] TOOL_CALLS = ${turn.calls.size}")
        }
        assertTrue(turn is com.androidagent.core.ModelTurn.FinalAnswer, "expected FinalAnswer")
        assertTrue((turn as com.androidagent.core.ModelTurn.FinalAnswer).content.isNotBlank())
        println("[PC] OPENROUTER_PC_LIVE = PASS")
    }
}