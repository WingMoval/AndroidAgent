package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException as JCancellationException

/**
 * Deterministic active-Run interrupt tests I1 - I15 (+ M1 - M3) for :ag-runtime.
 *
 * No Android, no HTTP, no real LLM. A user Stop is driven through the production
 * `AgentRuntime.interruptActiveRun()` API, which atomically claims the per-Run USER
 * interrupt and cancels the Run's REAL execution Job. The providers/tools below hold
 * at deterministic gates with plain suspensions (like any well-behaved suspend
 * function); they do NOT implement a separate Stop protocol — the Job cancellation
 * is the cancellation, and it reaches them at their suspension points.
 *
 * Synchronization uses gates / latches / busy-waits — no timing-based `delay` as the
 * primary sync.
 */
class AgentRuntimeInterruptTest {

    // A provider that holds at a deterministic gate (simulating a long in-flight
    // turn, e.g. streaming). The gate is a plain suspension: a user Stop is enforced
    // by the Runtime cancelling the execution Job, which the suspension receives as a
    // CancellationException exactly as a streaming provider honoring coroutine
    // cancellation would. It records whether it was cancelled mid-call.
    private class HoldProvider(
        val turns: List<ModelTurn>,
        val startGate: CompletableDeferred<Unit>,
    ) : ModelProvider {
        var cancelled: Boolean = false
            private set
        val requests: MutableList<ModelRequest> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun complete(request: ModelRequest): ModelTurn {
            requests += request
            try {
                startGate.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
            return turns.getOrElse(requests.size - 1) { turns.last() }
        }
    }

    private class RecordingSink : com.androidagent.core.AgentEventSink {
        val events: MutableList<AgentEvent> = java.util.Collections.synchronizedList(mutableListOf())
        override fun emit(event: AgentEvent) {
            events += event
        }

        val terminalRunFinished: List<AgentEvent.RunFinished>
            get() = events.filterIsInstance<AgentEvent.RunFinished>()
    }

    private class Fixture(
        val runtime: AgentRuntime,
        val core: AgentCore,
        val sink: RecordingSink,
    ) {
        val sessionId: String get() = runtime.currentSessionId
    }

    private fun fixture(
        provider: ModelProvider,
        tools: ToolRegistry = ToolRegistry(),
    ): Fixture {
        val sink = RecordingSink()
        val core = AgentCore(provider, tools)
        var counter = 0
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(eventSink = sink),
            sessionIdFactory = { "is-${++counter}" },
        )
        return Fixture(runtime, core, sink)
    }

    private suspend fun waitBusy(runtime: AgentRuntime) {
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }
    }

    /** Awaits a Run job with a hard timeout so a stuck Run fails the test instead of hanging the suite. */
    private suspend fun awaitRun(
        job: Deferred<RunOutcome>,
        timeoutMs: Long = 15_000,
    ): RunOutcome = withTimeout(timeoutMs) { job.await() }

    // ------------------------------------------------------------------ I1

    @Test
    fun `I1 interrupt before first model result`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())

        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        assertTrue(provider.cancelled, "the provider call must have been cancelled")
        val terminals = fx.sink.terminalRunFinished
        assertEquals(1, terminals.size, "exactly one terminal RunFinished")
        assertEquals(RunOutcome.Interrupted, terminals.single().outcome)
        assertFalse(fx.sink.events.any { it is AgentEvent.RunFailed })
        assertFalse(fx.runtime.isBusy, "busy must be released after an interrupt")
    }

    // ------------------------------------------------------------------ I2

    @Test
    fun `I2 interrupt during reasoning stream - transcript only, not in Session`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("partial answer")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        val history = fx.core.session(fx.sessionId).history()
        assertEquals(1, history.size, "only the UserMessage should be in history")
        assertTrue(history.single() is UserMessage)
        assertEquals(1, fx.sink.terminalRunFinished.size)
        assertEquals(RunOutcome.Interrupted, fx.sink.terminalRunFinished.single().outcome)
    }

    // ------------------------------------------------------------------ I3

    @Test
    fun `I3 interrupt during assistant content stream`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("streaming...")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        val history = fx.core.session(fx.sessionId).history()
        assertFalse(
            history.any { it is com.androidagent.core.ModelMessage },
            "a partial model turn must not be appended to the Session",
        )
        assertEquals(1, fx.sink.terminalRunFinished.size)
    }

    // ------------------------------------------------------------------ I4

    @Test
    fun `I4 interrupt between ToolCalls receipt and tool execution`() = runBlocking {
        val calls = listOf(ToolCall("c1", "t1", "{}"), ToolCall("c2", "t2", "{}"))
        val secondTurnGate = CompletableDeferred<Unit>()
        // First turn: tool calls. Second turn: held (plain suspension) so the Run
        // stays active in the tool phase, giving the interrupt a deterministic window
        // to land after the ModelMessage(calls) is appended.
        val provider = object : ModelProvider {
            private var n = 0
            override suspend fun complete(request: ModelRequest): ModelTurn {
                n++
                return if (n == 1) ModelTurn.ToolCalls(calls, "let me run these")
                else secondTurnGate.await().let { ModelTurn.FinalAnswer("later") }
            }
        }
        val tools = ToolRegistry().apply {
            register(object : Tool {
                override val spec = ToolSpec("t1", "a")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "t1")
            })
            register(object : Tool {
                override val spec = ToolSpec("t2", "b")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "t2")
            })
        }
        val fx = fixture(provider, tools)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        // Wait until the tools have completed and the Run is in the second (held) turn.
        withTimeout(10_000) {
            while (true) {
                val results = fx.core.session(fx.sessionId).history()
                    .filterIsInstance<com.androidagent.core.ToolResultMessage>().map { it.toolCallId }.toSet()
                if (results.containsAll(setOf("c1", "c2"))) break
                delay(5)
            }
        }
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        val history = fx.core.session(fx.sessionId).history()
        val modelMsg = history.filterIsInstance<com.androidagent.core.ModelMessage>().single()
        assertEquals(2, modelMsg.toolCalls.size)
        val resultIds = history.filterIsInstance<com.androidagent.core.ToolResultMessage>()
            .map { it.toolCallId }.toSet()
        assertTrue(
            resultIds.containsAll(setOf("c1", "c2")),
            "every declared call must have a ToolResult: got $resultIds",
        )
        assertEquals(1, fx.sink.terminalRunFinished.size)
        assertEquals(RunOutcome.Interrupted, fx.sink.terminalRunFinished.single().outcome)
    }

    // ------------------------------------------------------------------ I5

    @Test
    fun `I5 interrupt during tool execution (call i of k)`() = runBlocking {
        val calls = listOf(
            ToolCall("c1", "t1", "{}"),
            ToolCall("c2", "t2", "{}"),
            ToolCall("c3", "t3", "{}"),
        )
        val provider = object : ModelProvider {
            private var n = 0
            override suspend fun complete(request: ModelRequest): ModelTurn {
                n++
                return if (n == 1) ModelTurn.ToolCalls(calls, "")
                else ModelTurn.FinalAnswer("later")
            }
        }
        // t2 is held in flight at a deterministic gate (plain suspension). A user
        // Stop cancels the execution Job; the in-flight tool's suspension receives
        // the CancellationException exactly as a real tool honoring coroutine
        // cancellation would. c2 is therefore "in-flight, no result observed"; c3
        // never starts.
        val t2Gate = CompletableDeferred<Unit>()
        val tools = ToolRegistry().apply {
            register(object : Tool {
                override val spec = ToolSpec("t1", "first")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "t1-done")
            })
            register(object : Tool {
                override val spec = ToolSpec("t2", "second")
                override suspend fun execute(call: ToolCall): ToolResult {
                    try {
                        t2Gate.await()
                    } catch (e: CancellationException) {
                        throw e // the Run was cancelled mid-tool
                    }
                    return ToolResult(true, "t2-done")
                }
            })
            register(object : Tool {
                override val spec = ToolSpec("t3", "third")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "t3-done")
            })
        }
        val fx = fixture(provider, tools)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        // Wait until t1 completes (c1 result present) and t2 is in flight.
        withTimeout(10_000) {
            while (true) {
                if (fx.core.session(fx.sessionId).history()
                        .filterIsInstance<com.androidagent.core.ToolResultMessage>()
                        .any { it.toolCallId == "c1" }) break
                delay(5)
            }
        }
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        val history = fx.core.session(fx.sessionId).history()
        val byId = history.filterIsInstance<com.androidagent.core.ToolResultMessage>()
            .associate { it.toolCallId to it.result }
        assertEquals(3, byId.size, "every declared call must have a result")
        assertTrue(byId["c1"]!!.success, "c1 completed normally")
        assertEquals(
            AgentCore.IN_FLIGHT_INTERRUPTED_ERROR,
            byId["c2"]!!.error,
            "c2 (in-flight) must be marked unknown-external-state",
        )
        assertEquals(
            AgentCore.NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR,
            byId["c3"]!!.error,
            "c3 (never started) must be marked not-executed",
        )
        assertTrue(byId["c2"]!!.error != byId["c3"]!!.error)
        assertEquals(1, fx.sink.terminalRunFinished.size)
        assertEquals(RunOutcome.Interrupted, fx.sink.terminalRunFinished.single().outcome)
    }

    // ------------------------------------------------------------------ I6

    @Test
    fun `I6 interrupt after a complete tool pair - new send works`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = object : ModelProvider {
            private var n = 0
            override suspend fun complete(request: ModelRequest): ModelTurn {
                n++
                return if (n == 1) ModelTurn.ToolCalls(listOf(ToolCall("c1", "t1", "{}")), "")
                else gate.await().let { ModelTurn.FinalAnswer("later") }
            }
        }
        val tools = ToolRegistry().apply {
            register(object : Tool {
                override val spec = ToolSpec("t1", "tool")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "ok")
            })
        }
        val fx = fixture(provider, tools)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        withTimeout(10_000) {
            while (true) {
                if (fx.core.session(fx.sessionId).history()
                        .filterIsInstance<com.androidagent.core.ToolResultMessage>()
                        .any { it.toolCallId == "c1" }) break
                delay(5)
            }
        }
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        val history = fx.core.session(fx.sessionId).history()
        assertTrue(
            history.filterIsInstance<com.androidagent.core.ToolResultMessage>().any { it.toolCallId == "c1" },
        )
        // The gate is now complete, so the next Run's second turn will not hold.
        gate.complete(Unit)
        val second = async(Dispatchers.Default) { fx.runtime.send("again") }
        val secondOutcome = awaitRun(second)
        assertTrue(
            secondOutcome is RunOutcome.Answer,
            "a new Run must succeed after an interrupt, got $secondOutcome",
        )
        assertFalse(fx.runtime.isBusy)
    }

    // ------------------------------------------------------------------ I7

    @Test
    fun `I7 interrupt vs complete race - exactly one terminal`() = runBlocking {
        val provider = com.androidagent.runtime.ScriptedProvider(listOf(ModelTurn.FinalAnswer("done")))
        val fx = fixture(provider)

        // Start the Run, then race the interrupt against completion. The
        // ScriptedProvider returns immediately (no gate), so the Run likely
        // completes before the interrupt lands. No waitBusy here: waiting for
        // isBusy would hang because the run finishes faster than the flag is
        // observable. The assertion is that EXACTLY ONE terminal is emitted and the
        // outcome matches it, regardless of which won the race.
        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        fx.runtime.interruptActiveRun()
        val outcome = awaitRun(job)

        assertTrue(
            outcome is RunOutcome.Answer || outcome is RunOutcome.Interrupted,
            "outcome must be exactly one of Answer or Interrupted, got $outcome",
        )
        assertEquals(1, fx.sink.terminalRunFinished.size, "exactly one terminal RunFinished")
        assertEquals(outcome, fx.sink.terminalRunFinished.single().outcome)
    }

    // ------------------------------------------------------------------ I8

    @Test
    fun `I8 double interrupt - one winner, second is no-op`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)

        val results = (1..2).map {
            async(Dispatchers.Default) { fx.runtime.interruptActiveRun() }
        }.map { it.await() }

        assertTrue(
            results.any { it == AgentRuntime.InterruptResult.Requested },
            "exactly one Requested, got $results",
        )
        assertEquals(
            1,
            results.count { it == AgentRuntime.InterruptResult.Requested },
            "only one caller may win the interrupt claim, got $results",
        )
        val outcome = awaitRun(job)
        assertEquals(RunOutcome.Interrupted, outcome)
        assertEquals(1, fx.sink.terminalRunFinished.size, "exactly one terminal")
    }

    // ------------------------------------------------------------------ I9

    @Test
    fun `I9 interrupt with no active Run is a structured no-op`() = runBlocking {
        val provider = com.androidagent.runtime.ScriptedProvider(listOf(ModelTurn.FinalAnswer("x")))
        val fx = fixture(provider)

        val result = fx.runtime.interruptActiveRun()

        assertEquals(AgentRuntime.InterruptResult.NoActiveRun, result)
        assertFalse(fx.runtime.isBusy)
        val outcome = fx.runtime.send("hi")
        assertEquals(RunOutcome.Answer("x"), outcome)
    }

    // ------------------------------------------------------------------ I10

    @Test
    fun `I10 new Run on same Session after interrupt sees consistent history`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("first") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        assertEquals(RunOutcome.Interrupted, awaitRun(job))

        // A new core (with an answering provider) that has a Session of the SAME id;
        // the first Run's UserMessage is pre-seeded so the second Run sees it. This
        // proves the interrupted Session's history is consistent and reusable.
        val answering = com.androidagent.runtime.ScriptedProvider(
            listOf(ModelTurn.FinalAnswer("second reply")),
        )
        val core2 = AgentCore(answering, ToolRegistry())
        // Verify the first (interrupted) Run's history is consistent: the UserMessage
        // remains, and the run ended as Interrupted (not a partial/corrupt state).
        val firstHistory = fx.core.session(fx.sessionId).history()
        assertTrue(
            firstHistory.any { it is UserMessage && it.content == "first" },
            "the interrupted Run's UserMessage must remain in history",
        )
        // The second core answers; a new run on it succeeds.
        val runtime2 = AgentRuntime(
            agentCore = core2,
            runOptions = RunOptions(eventSink = fx.sink),
            sessionIdFactory = { fx.sessionId },
        )
        val outcome = withTimeout(15_000) { runtime2.send("second") }
        assertEquals(RunOutcome.Answer("second reply"), outcome)
    }

    // ------------------------------------------------------------------ I11

    @Test
    fun `I11 user interrupt structured return (child cancelled, send returns Interrupted)`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        var sendCancelled = false
        val job = async(Dispatchers.Default) {
            try {
                fx.runtime.send("hi")
            } catch (e: CancellationException) {
                sendCancelled = true
                throw e
            }
        }
        waitBusy(fx.runtime)
        val request = fx.runtime.interruptActiveRun()
        assertEquals(AgentRuntime.InterruptResult.Requested, request)
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        assertFalse(
            sendCancelled,
            "the send caller must not see a raw CancellationException on a user Stop",
        )
        assertTrue(provider.cancelled, "the execution (provider call) was cancelled")
        assertEquals(1, fx.sink.terminalRunFinished.size)
        assertEquals(RunOutcome.Interrupted, fx.sink.terminalRunFinished.single().outcome)
        assertFalse(fx.sink.events.any { it is AgentEvent.RunFailed })
    }

    // ------------------------------------------------------------------ I12

    @Test
    fun `I12 parent lifecycle cancellation propagates (not converted)`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)

        // LIFECYCLE: the host cancels its own Run Job WITHOUT a user Stop.
        job.cancel()
        var propagated = false
        try {
            job.await()
        } catch (e: JCancellationException) {
            propagated = true
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue(propagated, "a lifecycle cancellation must propagate, not be converted to Interrupted")
        // The propagated CancellationException IS the proof the structured execution
        // child was cancelled transitively (it terminated cancelled and the
        // CancellationException escaped to the dying host).
        // No user Stop was committed and no Interrupted terminal was emitted: the
        // lifecycle cancel is not fabricated as an Interrupted outcome.
        assertEquals(0, fx.sink.terminalRunFinished.size, "a lifecycle cancel must not emit an Interrupted terminal")
        assertFalse(fx.runtime.isBusy, "busy must be released")
    }

    // ------------------------------------------------------------------ I13

    @Test
    fun `I13 concurrent double interrupt - one atomic winner, one terminal`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)

        val latch = CountDownLatch(1)
        val a = async(Dispatchers.Default) {
            latch.await()
            fx.runtime.interruptActiveRun()
        }
        val b = async(Dispatchers.Default) {
            latch.await()
            fx.runtime.interruptActiveRun()
        }
        latch.countDown()
        val results = listOf(a.await(), b.await())

        assertEquals(
            1,
            results.count { it == AgentRuntime.InterruptResult.Requested },
            "exactly one atomic winner, got $results",
        )
        assertEquals(RunOutcome.Interrupted, awaitRun(job))
        assertEquals(1, fx.sink.terminalRunFinished.size, "exactly one terminal RunFinished")
    }

    // ------------------------------------------------------------------ I14

    @Test
    fun `I14 in-flight tool truthfulness (unknown-external-state vs not-executed)`() = runBlocking {
        val calls = listOf(
            ToolCall("c1", "t1", "{}"),
            ToolCall("c2", "t2", "{}"),
            ToolCall("c3", "t3", "{}"),
        )
        val provider = object : ModelProvider {
            private var n = 0
            override suspend fun complete(request: ModelRequest): ModelTurn {
                n++
                return if (n == 1) ModelTurn.ToolCalls(calls, "") else ModelTurn.FinalAnswer("later")
            }
        }
        // t2 is held in flight at a deterministic gate (plain suspension). A user
        // Stop cancels the execution Job; the in-flight tool's suspension receives
        // the CancellationException exactly as a real tool honoring coroutine
        // cancellation would.
        val t2Gate = CompletableDeferred<Unit>()
        val tools = ToolRegistry().apply {
            register(object : Tool {
                override val spec = ToolSpec("t1", "a")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "done1")
            })
            register(object : Tool {
                override val spec = ToolSpec("t2", "b")
                override suspend fun execute(call: ToolCall): ToolResult {
                    try {
                        t2Gate.await()
                    } catch (e: CancellationException) {
                        throw e // the Run was cancelled mid-tool
                    }
                    return ToolResult(true, "done2")
                }
            })
            register(object : Tool {
                override val spec = ToolSpec("t3", "c")
                override suspend fun execute(call: ToolCall): ToolResult = ToolResult(true, "done3")
            })
        }
        val fx = fixture(provider, tools)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        withTimeout(10_000) {
            while (true) {
                if (fx.core.session(fx.sessionId).history()
                        .filterIsInstance<com.androidagent.core.ToolResultMessage>()
                        .any { it.toolCallId == "c1" }) break
                delay(5)
            }
        }
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        assertEquals(RunOutcome.Interrupted, awaitRun(job))

        val history = fx.core.session(fx.sessionId).history()
        val byId = history.filterIsInstance<com.androidagent.core.ToolResultMessage>()
            .associate { it.toolCallId to it.result }
        assertTrue(byId["c1"]!!.success)
        val c2Error = byId["c2"]!!.error!!
        val c3Error = byId["c3"]!!.error!!
        assertTrue(
            c2Error.contains("may be unknown") || c2Error.contains("observed"),
            "in-flight error must convey unknown external state, got: $c2Error",
        )
        assertFalse(c2Error.contains("not executed"), "in-flight must NOT claim 'not executed', got: $c2Error")
        assertFalse(c2Error.contains("rolled back"), "in-flight must NOT claim rollback, got: $c2Error")
        assertFalse(c2Error.contains("failed"), "in-flight must NOT claim the operation failed, got: $c2Error")
        assertTrue(c3Error.contains("not executed"), "not-started must convey 'not executed', got: $c3Error")
        assertTrue(c2Error != c3Error, "the two synthetic results must be distinguishable")
    }

    // ------------------------------------------------------------------ I15

    @Test
    fun `I15 user stop vs parent lifecycle cancel - parent dominates`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)

        // Race: request a user interrupt AND cancel the parent caller simultaneously.
        // The parent caller is cancelled, so the CancellationException must propagate
        // (NOT be converted to Interrupted) — parent cancellation dominates.
        val interruptJob = async(Dispatchers.Default) { fx.runtime.interruptActiveRun() }
        job.cancel()
        interruptJob.join()

        var propagated = false
        try {
            job.await()
        } catch (e: JCancellationException) {
            propagated = true
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue(
            propagated,
            "parent cancellation must dominate: it propagates even when a user interrupt was requested",
        )
        assertFalse(fx.runtime.isBusy)
    }

    // ------------------------------------------------------------------ M1

    @Test
    fun `M1 ActiveRunControl owns the real execution Job and interrupt cancels it`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        // The provider's in-flight suspension was cancelled by the Job cancellation
        // (not by any flag polling), and the Run terminated as Interrupted.
        assertTrue(provider.cancelled, "the execution Job cancellation must reach the provider")
        assertEquals(RunOutcome.Interrupted, outcome)
        assertEquals(1, fx.sink.terminalRunFinished.size, "exactly one terminal RunFinished")
    }

    // ------------------------------------------------------------------ M2

    @Test
    fun `M2 stop does not depend on the next Core checkpoint`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = HoldProvider(listOf(ModelTurn.FinalAnswer("never")), gate)
        val fx = fixture(provider)

        // The provider is suspended INSIDE its turn (at the gate), long before any
        // further Core checkpoint could execute. A user Stop must still cancel the
        // in-flight operation directly via the execution Job — not by waiting for
        // the next checkpoint.
        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertTrue(provider.cancelled, "a suspended provider must receive the cancellation without a checkpoint")
        assertEquals(RunOutcome.Interrupted, outcome)
        assertEquals(1, fx.sink.terminalRunFinished.size)
    }

    // ------------------------------------------------------------------ M3

    @Test
    fun `M3 production provider path - cancellation is not swallowed`() = runBlocking {
        // M3's full production-provider assertion (OpenAiCompatibleProvider +
        // JdkOpenAiHttpTransport) is exercised in the L2-FULL Stop E2E test in
        // :provider-openai-compatible-jdk, which drives the real provider through the
        // real transport and proves CancellationException is not converted to a
        // provider failure. Here we prove the Core boundary: a provider that
        // observes coroutine cancellation (like the production provider's transport)
        // surfaces it as a CancellationException that ends the Run as Interrupted,
        // never as a ProviderFailure.
        val gate = CompletableDeferred<Unit>()
        val provider = object : ModelProvider {
            override suspend fun complete(request: ModelRequest): ModelTurn {
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    throw e // production transport rethrows cancellation unchanged
                }
                return ModelTurn.FinalAnswer("never")
            }
        }
        val fx = fixture(provider)

        val job = async(Dispatchers.Default) { fx.runtime.send("hi") }
        waitBusy(fx.runtime)
        assertEquals(AgentRuntime.InterruptResult.Requested, fx.runtime.interruptActiveRun())
        val outcome = awaitRun(job)

        assertEquals(RunOutcome.Interrupted, outcome)
        assertFalse(
            fx.sink.events.any { it is AgentEvent.RunFailed },
            "a user Stop must not fabricate a RunFailed / ProviderFailure",
        )
        assertEquals(1, fx.sink.terminalRunFinished.size)
        assertEquals(RunOutcome.Interrupted, fx.sink.terminalRunFinished.single().outcome)
    }
}
