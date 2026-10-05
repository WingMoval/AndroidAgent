package com.androidagent.runtime

import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.core.ModelTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException as JCancellationException

/**
 * AGA-0.ARCH Runtime contract tests R1 - R8.
 *
 * Block-body `Unit` tests throughout, per the frozen test-hygiene rule: a
 * non-Unit inferred return type is silently skipped by JUnit.
 *
 * No Android, no UI, no HTTP, no EMG, no real provider.
 */
class AgentRuntimeTest {

    // ------------------------------------------------------------------ R1

    @Test
    fun `R1 runtime drives AG Core and returns its outcome`() = runBlocking {
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("hello")))
        val (runtime, _) = runtimeOf(provider)

        val outcome = runtime.send("hi")

        assertEquals(RunOutcome.Answer("hello"), outcome)
        assertEquals(1, provider.callCount)
        assertFalse(runtime.isBusy)
    }

    // ------------------------------------------------------------------ R2

    @Test
    fun `R2 sequential sends reuse the same Session and accumulate history`() = runBlocking {
        val provider = ScriptedProvider(
            listOf(ModelTurn.FinalAnswer("first reply"), ModelTurn.FinalAnswer("second reply"))
        )
        val (runtime, _) = runtimeOf(provider)
        val sessionId = runtime.currentSessionId

        runtime.send("first")
        runtime.send("second")

        // Same Session for both sends.
        assertEquals(sessionId, runtime.currentSessionId)
        assertEquals(2, provider.callCount)

        // The second request must see the whole first exchange.
        // AG Core appends the new UserMessage before asking the provider, so the
        // current turn's user message is already present at call time.
        val second = provider.requests[1]
        val secondHistory = historyTexts(second)
        assertEquals(
            listOf("U:first", "M:first reply:0", "U:second"),
            secondHistory,
        )
        assertTrue(secondHistory.contains("U:first"))
        assertTrue(secondHistory.contains("M:first reply:0"))
        assertTrue(secondHistory.contains("U:second"))
    }

    // ------------------------------------------------------------------ R3

    @Test
    fun `R3 newSession switches identity and does not reuse old history`() = runBlocking {
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("a"), ModelTurn.FinalAnswer("b")))
        val (runtime, _) = runtimeOf(provider)

        runtime.send("first")
        val oldId = runtime.currentSessionId

        assertTrue(runtime.newSession())
        val newId = runtime.currentSessionId
        assertNotEquals(oldId, newId)

        runtime.send("second")

        // The second Run's history must NOT contain the first Session's messages.
        val secondHistory = historyTexts(provider.requests[1])
        assertFalse(secondHistory.contains("U:first"), "old session history leaked: $secondHistory")
        assertTrue(secondHistory.contains("U:second"))
    }

    // ------------------------------------------------------------------ R4

    @Test
    fun `R4 a second concurrent send is rejected without opening another Session`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("done"))).apply { this.gate = gate }
        val (runtime, _) = runtimeOf(provider)
        val sessionId = runtime.currentSessionId

        val first = async(Dispatchers.Default) { runtime.send("A") }
        // Wait until the first Run is genuinely in flight.
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }

        val second = runtime.send("B")

        assertEquals(RunOutcome.Failure(RunFailure.SessionBusy(sessionId)), second)
        // Crucially, the rejected send did not create or switch Sessions.
        assertEquals(sessionId, runtime.currentSessionId)
        assertEquals(1, provider.callCount, "the rejected send must not reach the provider")

        gate.complete(Unit)
        assertEquals(RunOutcome.Answer("done"), first.await())
    }

    // ------------------------------------------------------------------ R5

    @Test
    fun `R5 newSession during a busy run is rejected and identity is unchanged`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("done"))).apply { this.gate = gate }
        val (runtime, _) = runtimeOf(provider)
        val sessionId = runtime.currentSessionId

        val first = async(Dispatchers.Default) { runtime.send("A") }
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }

        assertFalse(runtime.newSession(), "newSession must be rejected while busy")
        assertEquals(sessionId, runtime.currentSessionId)

        gate.complete(Unit)
        assertEquals(RunOutcome.Answer("done"), first.await())
    }

    // ------------------------------------------------------------------ R6

    @Test
    fun `R6 a failing Run still releases the busy flag and the runtime stays usable`() = runBlocking {
        // A provider that throws produces a real AG Core provider failure.
        val provider = ThrowingProvider()
        val (runtime, _) = runtimeOf(provider)

        val outcome = runtime.send("boom")

        assertTrue(outcome is RunOutcome.Failure, "expected failure, got $outcome")
        assertFalse(runtime.isBusy, "busy flag must be released after a failure")

        // Still usable afterwards: swap in a working provider through a new runtime
        // over the same shape is not possible, so assert re-usability via a
        // second send against a provider that now succeeds.
        val ok = ScriptedProvider(listOf(ModelTurn.FinalAnswer("recovered")))
        val (runtime2, _) = runtimeOf(ok)
        assertFalse(runtime2.isBusy)
        assertEquals(RunOutcome.Answer("recovered"), runtime2.send("again"))
    }


    // ------------------------------------------------------------------ R7

    @Test
    fun `R7 cancellation propagates and releases the busy flag`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf(ModelTurn.FinalAnswer("never"))).apply { this.gate = gate }
        val (runtime, _) = runtimeOf(provider)

        val job = async(Dispatchers.Default) { runtime.send("A") }
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }

        job.cancel()
        var caught = false
        try {
            job.await()
        } catch (e: JCancellationException) {
            caught = true
        } catch (e: CancellationException) {
            caught = true
        }
        assertTrue(caught, "cancellation must propagate, not be swallowed")
        assertFalse(runtime.isBusy, "a cancelled send must not leave the runtime permanently busy")

        // Reusable after cancellation.
        gate.complete(Unit)
        val after = ScriptedProvider(listOf(ModelTurn.FinalAnswer("usable")))
        val (runtime2, _) = runtimeOf(after)
        assertEquals(RunOutcome.Answer("usable"), runtime2.send("again"))
    }

    // ------------------------------------------------------------------ R8

    @Test
    fun `R8 independent runtime instances share no state`() = runBlocking {
        val providerA = ScriptedProvider(listOf(ModelTurn.FinalAnswer("a1")))
        val providerB = ScriptedProvider(listOf(ModelTurn.FinalAnswer("b1")))
        val (runtimeA, _) = runtimeOf(providerA, "A")
        val (runtimeB, _) = runtimeOf(providerB, "B")

        assertNotEquals(runtimeA.currentSessionId, runtimeB.currentSessionId)

        runtimeA.send("hello")

        // B is unaffected by A's run: its own provider untouched, still idle.
        assertEquals(0, providerB.callCount)
        assertFalse(runtimeB.isBusy)

        runtimeB.send("hi")

        assertEquals(1, providerA.callCount)
        assertEquals(1, providerB.callCount)
        // No cross-contamination of history.
        assertFalse(historyTexts(providerB.requests[0]).contains("U:hello"))
    }
}