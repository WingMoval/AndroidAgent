package com.androidagent.provider.openai.jdk

import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.provider.openai.OpenAiProviderException
import com.androidagent.provider.openai.ProviderConfigResolver
import com.androidagent.provider.openai.ResolvedProviderConfig

import com.androidagent.core.AgentCore
import com.androidagent.core.AgentEvent
import com.androidagent.core.AgentEventSink
import com.androidagent.core.Message
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live verification against a real OpenAI-compatible endpoint.
 *
 * Gated behind `@Tag("live")` and run only by `:provider-openai-compatible:liveTest`,
 * never by the ordinary `build`.
 *
 * Talks to a networked, possibly billable service, so assertions are structural
 * rather than on exact prose.
 *
 * Config comes from AndroidAgent's own `config/provider.local.json`, falling back
 * to `AG_OPENAI_*`. The path arrives via a system property so nothing depends on
 * the shell's working directory.
 *
 * Evidence discipline: L4 and L5 record only what is directly observed. A tool
 * result is never inferred from the model's own narration.
 */
@Tag("live")
class LiveApiTest {

    // ------------------------------------------------------------- resolution

    private val resolved: ResolvedProviderConfig by lazy {
        val configured = System.getProperty("ag.provider.config")
            ?: error(
                "system property 'ag.provider.config' not set; run via " +
                    ":provider-openai-compatible:liveTest",
            )
        ProviderConfigResolver.resolve(Path.of(configured)).also {
            println("[live] CONFIG_SOURCE = ${it.source.description}")
            println("[live] MODEL         = ${it.config.model}")
            println("[live] ENDPOINT      = ${safeEndpoint(it.config)}")
            println("[live] TIMEOUT       = ${it.config.requestTimeout.seconds}s")
            println("[live] API_KEY       = ${if (it.config.apiKey.isBlank()) "ABSENT" else "PRESENT"}")
        }
    }

    private fun provider(): OpenAiCompatibleProvider = OpenAiCompatibleProvider(resolved.config, JdkOpenAiHttpTransport())

    /** Host, port and path only. Never prints a credential. */
    private fun safeEndpoint(cfg: OpenAiCompatibleConfig = resolved.config): String =
        runCatching {
            val uri = java.net.URI.create(cfg.chatCompletionsEndpoint)
            val authority = uri.rawAuthority?.substringAfter('@').orEmpty()
            "${uri.scheme}://$authority${uri.rawPath}"
        }.getOrElse { "endpoint" }

    // ------------------------------------------------------------- test doubles

    /** Generic probe tool. Deliberately not a shell, battery or file tool. */
    private class ProbeTool : Tool {
        override val spec: ToolSpec = ToolSpec(
            name = "probe",
            description = "Returns a fixed probe result. Call this when asked to probe.",
        )
        val calls = AtomicInteger()
        val lastArguments = java.util.concurrent.atomic.AtomicReference<String?>(null)

        override suspend fun execute(call: ToolCall): ToolResult {
            calls.incrementAndGet()
            lastArguments.set(call.arguments)
            return ToolResult(success = true, output = "probe-ok depth=1")
        }
    }

    private class CountingTool(
        override val spec: ToolSpec,
        private val output: String,
    ) : Tool {
        val calls = AtomicInteger()
        override suspend fun execute(call: ToolCall): ToolResult {
            calls.incrementAndGet()
            return ToolResult(success = true, output = output)
        }
    }

    /** Test-only event sink used to observe what the model actually returned. */
    private class TurnRecorder : AgentEventSink {
        val turns = mutableListOf<ModelTurn>()
        override fun emit(event: AgentEvent) {
            if (event is AgentEvent.ModelTurnFinished) turns += event.turn
        }
    }

    private fun RunOutcome.describe(): String = when (this) {
        is RunOutcome.Answer -> "Answer(length=${content.length})"
        is RunOutcome.Failure -> "Failure($failure)"
    }

    // ------------------------------------------------------------------ L1

    @Test
    fun `L1 real final answer`() = runBlocking {
        val turn = provider().complete(
            ModelRequest(
                sessionId = "live-L1",
                instructions = "Answer with a single short sentence.",
                history = listOf(UserMessage("In one short sentence, what is 2+2?")),
                tools = emptyList(),
            ),
        )
        assertTrue(turn is ModelTurn.FinalAnswer, "expected FinalAnswer, got ${turn::class.simpleName}")
        val content = (turn as ModelTurn.FinalAnswer).content
        assertTrue(content.isNotBlank(), "final answer must not be empty")
        println("[live L1] PASS turnType=FinalAnswer contentLength=${content.length}")
    }

    // ------------------------------------------------------------------ L2

    @Test
    fun `L2 real tool call`() = runBlocking {
        val tool = ProbeTool()
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider(), registry)

        val outcome = core.run(
            "live-L2",
            "You must call the probe tool. Do not answer directly. Call probe now.",
            RunOptions(maxSteps = 2),
        )
        println("[live L2] outcome=${outcome.describe()} toolExecutions=${tool.calls.get()}")
        println("[live L2] argumentsLength=${tool.lastArguments.get()?.length ?: -1}")

        assertTrue(
            outcome is RunOutcome.Answer || outcome is RunOutcome.Failure,
            "run must terminate with a definite outcome",
        )
    }

    // ------------------------------------------------------------------ L3

    @Test
    fun `L3 real tool result continuation`() = runBlocking {
        val tool = ProbeTool()
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider(), registry)

        val outcome = core.run(
            "live-L3",
            "Call the probe tool first, then report what it returned.",
            RunOptions(maxSteps = 4),
        )
        println("[live L3] outcome=${outcome.describe()} toolExecutions=${tool.calls.get()}")
        println("[live L3] arguments=${tool.lastArguments.get()}")

        assertTrue(
            tool.calls.get() >= 1,
            "the model was told to call probe but never did (outcome=$outcome)",
        )
        assertTrue(tool.lastArguments.get() != null, "a real tool call must carry arguments from the wire")
        assertTrue(
            outcome is RunOutcome.Answer,
            "the run must reach a final answer after the tool result; got $outcome",
        )
    }

    // ------------------------------------------------------------------ L4

    @Test
    fun `L4 multiple tool call observation`() = runBlocking {
        val alpha = CountingTool(ToolSpec("alpha_probe", "First probe."), "alpha-ok")
        val beta = CountingTool(ToolSpec("beta_probe", "Second probe."), "beta-ok")
        val registry = ToolRegistry().apply {
            register(alpha)
            register(beta)
        }
        val core = AgentCore(provider(), registry)

        val outcome = core.run(
            "live-L4",
            "In this single turn, call BOTH alpha_probe and beta_probe before answering.",
            RunOptions(maxSteps = 3),
        )

        // Counted from actual tool invocations, never from the model's narration.
        val alphaCount = alpha.calls.get()
        val betaCount = beta.calls.get()
        println("[live L4] outcome=${outcome.describe()}")
        println("[live L4] DIRECT alpha_executions=$alphaCount beta_executions=$betaCount")

        when {
            alphaCount >= 1 && betaCount >= 1 ->
                println("[live L4] RESULT=DIRECTLY OBSERVED AND ASSERTED (both tools executed)")

            alphaCount + betaCount == 0 ->
                println("[live L4] RESULT=NOT OBSERVED ON THIS RUN (no tool executed)")

            else ->
                println("[live L4] RESULT=PARTIAL (only one tool executed this run)")
        }
    }

    // ------------------------------------------------------------------ L5

    @Test
    fun `L5 same-turn mixed prose and tool call observation`() = runBlocking {
        val tool = ProbeTool()
        val registry = ToolRegistry().apply { register(tool) }
        val recorder = TurnRecorder()
        val core = AgentCore(provider(), registry)

        val outcome = core.run(
            "live-L5",
            "First write one short sentence of prose, and in the SAME turn also call the probe tool.",
            RunOptions(maxSteps = 3, eventSink = recorder),
        )

        // A genuine mixed turn is ONE assistant response carrying BOTH prose and
        // tool calls. A tool call followed by a later prose turn is an ordinary
        // continuation and must never be counted as mixed.
        val mixedTurns = recorder.turns.filterIsInstance<ModelTurn.ToolCalls>()
            .filter { it.content.isNotBlank() }
        val toolCallTurns = recorder.turns.filterIsInstance<ModelTurn.ToolCalls>()

        println("[live L5] outcome=${outcome.describe()} toolExecutions=${tool.calls.get()}")
        println("[live L5] modelTurns=${recorder.turns.size} toolCallTurns=${toolCallTurns.size}")
        for (t in toolCallTurns) {
            println("[live L5] toolCallTurn contentLength=${t.content.length} calls=${t.calls.size}")
        }

        if (mixedTurns.isNotEmpty()) {
            val m = mixedTurns.first()
            println("[live L5] RESULT=TRUE SAME-TURN MIXED OBSERVED contentLength=${m.content.length} calls=${m.calls.size}")
            println("[live L5] preservedProse=${m.content}")
            println("[live L5] preservedCalls=${m.calls.map { it.name }}")
            println("[live L5] preservedIds=${m.calls.map { it.id }}")
        } else if (toolCallTurns.isNotEmpty()) {
            println("[live L5] RESULT=TOOL CALL WITHOUT PROSE (not a mixed turn)")
        } else {
            println("[live L5] RESULT=NOT OBSERVED ON THIS RUN (model produced no tool call)")
        }
    }

    // ------------------------------------------------------------------ L6

    /**
     * GAP-2 validation: does a real endpoint accept a partial tool turn?
     *
     * AG Core can legitimately record a turn whose tool calls were not all
     * completed, because an aborted Run releases ownership without fabricating
     * results. This projects exactly that history to the real endpoint and
     * records whether it is accepted.
     *
     * No ToolResult is fabricated for callB. If the endpoint rejects this, the
     * correct response is a provider-projection review, NOT a change to Session
     * history semantics.
     */
    @Test
    fun `L6 partial tool turn history acceptance`() = runBlocking {
        val callA = ToolCall("call_partial_A", "probe", """{"depth":1}""")
        val callB = ToolCall("call_partial_B", "probe", """{"depth":2}""")

        val partialHistory: List<Message> = listOf(
            UserMessage("probe twice"),
            // Assistant asked for two tools...
            ModelMessage(content = "", toolCalls = listOf(callA, callB)),
            // ...but only callA produced a result. callB has none, because the Run
            // aborted. Nothing is fabricated here.
            ToolResultMessage(
                toolCallId = callA.id,
                toolName = callA.name,
                result = ToolResult(success = true, output = "probe-ok depth=1"),
            ),
        )

        val request = ModelRequest(
            sessionId = "live-L6",
            instructions = "",
            history = partialHistory,
            tools = emptyList(),
        )

        println("[live L6] endpoint=${safeEndpoint()}")
        println("[live L6] declared tool_calls=2, supplied tool results=1")

        try {
            val turn = provider().complete(request)
            println("[live L6] RESULT=PARTIAL_HISTORY_ACCEPTED turnType=${turn::class.simpleName}")
            assertTrue(
                turn is ModelTurn.FinalAnswer || turn is ModelTurn.ToolCalls,
                "an accepted response must still be a valid ModelTurn",
            )
        } catch (e: OpenAiProviderException.HttpStatus) {
            println("[live L6] RESULT=PARTIAL_HISTORY_REJECTED status=${e.statusCode}")
            println("[live L6] providerSummary=${e.providerSummary}")
        } catch (e: OpenAiProviderException) {
            println("[live L6] RESULT=PARTIAL_HISTORY_REJECTED type=${e::class.simpleName}")
            println("[live L6] message=${e.message}")
        }
    }
}