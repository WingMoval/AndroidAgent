package com.androidagent.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * T11 and T12: run ownership belongs to the Session, not to the AgentCore.
 *
 * These tests exist to catch a specific misimplementation: a global single-Run
 * flag or a lock on AgentCore, which would make unrelated Sessions falsely
 * report BUSY.
 *
 * `AgentCore.run` is a suspending function (AG.0.R1), so each call is bridged
 * with `runBlocking` at the point of use. That bridge lives in the test, not in
 * AG Core: choosing a thread belongs to the host, and these tests are acting as
 * the host.
 *
 * Determinism comes from latches, never from sleeps. A blocking tool holds the
 * first Run inside its execution while the main thread makes assertions.
 *
 * Every competing `run` call is submitted to a two-thread pool and awaited with a
 * timeout. Against the global-lock bug those calls would block forever, so
 * leaving them unbounded would wedge the test JVM instead of failing the build.
 * The pool needs two threads because the first Run already occupies one.
 */
class SessionConcurrencyTest {

    /**
     * A tool that parks inside execute until the test releases it, so a Run can be
     * observed while it is genuinely still active.
     */
    private class BlockingTool(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : Tool {
        override val spec: ToolSpec = ToolSpec("block", "blocks until released")

        override suspend fun execute(call: ToolCall): ToolResult {
            entered.countDown()
            release.await()
            return ToolResult(success = true, output = "released")
        }
    }

    // ---------------------------------------------------------------- T11

    @Test
    fun `T11 a second run on the same session is rejected while the first is active`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val registry = ToolRegistry().apply { register(BlockingTool(entered, release)) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "block"))),
                ModelTurn.FinalAnswer("first run finished"),
                ModelTurn.FinalAnswer("second run finished"),
            )
        )
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first = pool.submit<RunOutcome> { runBlocking { core.run("A", "start") } }
            assertTrue(entered.await(10, TimeUnit.SECONDS), "first run should be inside the tool")

            val rejected = pool.submit<RunOutcome> { runBlocking { core.run("A", "again") } }
                .get(10, TimeUnit.SECONDS)
            assertEquals(RunOutcome.Failure(RunFailure.SessionBusy("A")), rejected)

            release.countDown()
            assertEquals(RunOutcome.Answer("first run finished"), first.get(10, TimeUnit.SECONDS))

            // The rejected attempt appended nothing: still exactly one user
            // message, plus the two model turns of the first run (the tool-call
            // turn and the final answer).
            assertEquals(
                listOf(
                    UserMessage("start"),
                    ModelMessage("", listOf(ToolCall("c1", "block"))),
                    ToolResultMessage("c1", "block", ToolResult(true, "released")),
                    ModelMessage("first run finished"),
                ),
                core.session("A").history(),
            )

            // Ownership released: the same session accepts a new run.
            val third = runBlocking { core.run("A", "third") }
            assertEquals(RunOutcome.Answer("second run finished"), third)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `T11b a rejected run emits no lifecycle events`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val registry = ToolRegistry().apply { register(BlockingTool(entered, release)) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "block"))),
                ModelTurn.FinalAnswer("first run finished"),
            )
        )
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first = pool.submit<RunOutcome> { runBlocking { core.run("A", "start") } }
            assertTrue(entered.await(10, TimeUnit.SECONDS))

            val sink = RecordingEventSink()
            val rejected = pool.submit<RunOutcome> {
                runBlocking { core.run("A", "again", RunOptions(eventSink = sink)) }
            }.get(10, TimeUnit.SECONDS)

            assertEquals(RunOutcome.Failure(RunFailure.SessionBusy("A")), rejected)
            assertTrue(sink.events.isEmpty(), "a rejected run never started, so it emits nothing")

            release.countDown()
            assertEquals(RunOutcome.Answer("first run finished"), first.get(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    // ---------------------------------------------------------------- T12

    @Test
    fun `T12 an active run on one session does not block a different session`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val registry = ToolRegistry().apply { register(BlockingTool(entered, release)) }
        val provider = ScriptedModelProvider(
            // Both sessions share one provider, so the script is consumed in the
            // order the turns actually happen: session A takes script[0] and then
            // parks inside the tool, session B takes script[1] while A is parked,
            // and A resumes onto script[2]. The latch makes that order fixed.
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "block"))),
                ModelTurn.FinalAnswer("second scripted answer"),
                ModelTurn.FinalAnswer("third scripted answer"),
            )
        )
        val core = AgentCore(provider, registry, MessageDurability.Noop)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val runA = pool.submit<RunOutcome> { runBlocking { core.run("A", "start") } }
            assertTrue(entered.await(10, TimeUnit.SECONDS), "session A should be inside the tool")

            // Session B must complete normally even though A is active. If run
            // ownership were global, this call would block and the timeout below
            // would fail the test instead of hanging it.
            val runB = pool.submit<RunOutcome> { runBlocking { core.run("B", "hello") } }
                .get(10, TimeUnit.SECONDS)
            assertEquals(RunOutcome.Answer("second scripted answer"), runB)

            release.countDown()
            assertEquals(
                RunOutcome.Answer("third scripted answer"),
                runA.get(10, TimeUnit.SECONDS),
            )

            // Both sessions exist independently with their own histories.
            assertEquals(
                listOf(UserMessage("hello"), ModelMessage("second scripted answer")),
                core.session("B").history(),
            )
            assertEquals(
                listOf(
                    UserMessage("start"),
                    ModelMessage("", listOf(ToolCall("c1", "block"))),
                    ToolResultMessage("c1", "block", ToolResult(true, "released")),
                    ModelMessage("third scripted answer"),
                ),
                core.session("A").history(),
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `T12b three sessions coexist and each keeps its own history`() = runBlocking {
        val provider = ScriptedModelProvider(List(6) { ModelTurn.FinalAnswer("answer $it") })
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop)

        core.run("A", "a1")
        core.run("B", "b1")
        core.run("C", "c1")
        core.run("A", "a2")

        assertEquals(
            listOf(
                UserMessage("a1"),
                ModelMessage("answer 0"),
                UserMessage("a2"),
                ModelMessage("answer 3"),
            ),
            core.session("A").history(),
        )
        assertEquals(
            listOf(UserMessage("b1"), ModelMessage("answer 1")),
            core.session("B").history(),
        )
        assertEquals(
            listOf(UserMessage("c1"), ModelMessage("answer 2")),
            core.session("C").history(),
        )
    }
}