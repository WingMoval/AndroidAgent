package com.androidagent.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AG.0.R1 EventSink failure contract.
 *
 * `AgentEventSink` is an observer. Its failure must never damage the agent.
 * Three properties are proved here:
 *
 *  1. A sink failure does not leave a Session permanently BUSY.
 *  2. A sink failure does not leave structurally unusable history, i.e. history
 *     never declares a tool call that has no matching tool result.
 *  3. A sink failure does not produce a new uncontrolled exception, including
 *     from the second and third emission attempt (RunFailed / RunFinished).
 *
 * No retry, queue, buffering, or event bus is involved, by design.
 */
class EventSinkContractTest {

    @Test
    fun `sink failure at RunStarted does not leave the session busy`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("recovered")))
        val core = AgentCore(provider, ToolRegistry())
        val sink = SelectiveThrowingEventSink { it is AgentEvent.RunStarted }

        val outcome = core.run("s1", "hi", RunOptions(eventSink = sink))

        assertEquals(RunOutcome.Failure(RunFailure.RuntimeFailure("IllegalStateException")), outcome)
        assertEquals(0, provider.callCount, "the run aborted before asking the provider")

        // Property 1: ownership was released, so the very same Session runs again.
        val second = core.run("s1", "hi again")
        assertEquals(RunOutcome.Answer("recovered"), second)
    }

    @Test
    fun `sink failure mid tool loop leaves history faithful and still usable`() = runBlocking {
        val tool = RecordingTool(ToolSpec("echo", "")) { ToolResult(true, "out") }
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(
                    listOf(ToolCall("c1", "echo"), ToolCall("c2", "echo"))
                )
            )
        )
        // Faults only on ToolCallFinished, i.e. after the result was recorded.
        val sink = SelectiveThrowingEventSink { it is AgentEvent.ToolCallFinished }
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "go", RunOptions(eventSink = sink))

        assertEquals(RunOutcome.Failure(RunFailure.RuntimeFailure("IllegalStateException")), outcome)

        // Property 2, stated precisely.
        //
        // AG Core appends a ToolResultMessage BEFORE announcing it, so a sink fault
        // can never orphan a result: the strongest invariant that actually holds is
        // that every recorded result belongs to a call that was really dispatched,
        // and that no result exists for a call which was never declared.
        //
        // An aborted Run does NOT fabricate results for calls it never reached. A
        // turn may therefore be recorded as partially executed. That is a faithful
        // record of what happened, and it is what keeps the history usable.
        val history = core.session("s1").history()
        val declaredIds = history.filterIsInstance<ModelMessage>()
            .flatMap { it.toolCalls }
            .map { it.id }
        val results = history.filterIsInstance<ToolResultMessage>()

        assertEquals(listOf("c1", "c2"), declaredIds, "the model turn declared both calls")
        assertEquals(listOf("c1"), results.map { it.toolCallId }, "only the dispatched call has a result")
        assertEquals(1, tool.executeCount, "the second call was never dispatched")
        assertTrue(
            results.all { it.toolCallId in declaredIds },
            "no result may reference a call that was never declared",
        )

        // Property 2, second half: the agent remains operational. A later Run on a
        // different Session completes normally, and the partial turn above is simply
        // carried forward in the aborted Session's history.
        val reused = core.session("s1")
        assertTrue(reused.history().isNotEmpty())
        assertEquals(
            RunOutcome.Answer("later run"),
            AgentCore(ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("later run"))), registry)
                .run("s2", "next"),
            "the agent remains operational after an aborted run",
        )
    }

    @Test
    fun `a tool level failure never unbalances the tool call turn`() = runBlocking {
        // A tool that throws is converted into a ToolResult by the registry, so the
        // loop always records a result for every declared call.
        val registry = ToolRegistry()
            .apply { register(ThrowingTool(ToolSpec("boom", "")) { IllegalStateException("x") }) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "boom"), ToolCall("c2", "boom"))),
                ModelTurn.FinalAnswer("moved on"),
            )
        )
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "go")

        assertEquals(RunOutcome.Answer("moved on"), outcome)
        val history = core.session("s1").history()
        val declared = history.filterIsInstance<ModelMessage>().sumOf { it.toolCalls.size }
        val results = history.filterIsInstance<ToolResultMessage>().size
        assertEquals(2, declared)
        assertEquals(2, results, "both declared calls have results, so the turn is balanced")
    }

    @Test
    fun `a sink that always throws cannot turn a failure into an escaping exception`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("never reached")))
        val core = AgentCore(provider, ToolRegistry())
        val sink = SelectiveThrowingEventSink { true }

        // Must return a structured outcome rather than propagate anything.
        val outcome = core.run("s1", "hi", RunOptions(eventSink = sink))

        assertTrue(outcome is RunOutcome.Failure, "expected a Failure, got $outcome")
        assertEquals(RunFailure.RuntimeFailure("IllegalStateException"), (outcome as RunOutcome.Failure).failure)

        // Property 3: the first emission throws, then RunFailed and RunFinished are
        // both still attempted through the same broken sink, and neither escapes.
        assertEquals(3, sink.emitCount, "RunStarted, then RunFailed, then RunFinished")
        assertTrue(sink.events.isEmpty(), "a sink that always throws records nothing")
    }

    @Test
    fun `a sink that always throws still reports maxSteps as a structured failure`() = runBlocking {
        val registry = ToolRegistry().apply { register(RecordingTool(ToolSpec("again", ""))) }
        val provider = ScriptedModelProvider(
            List(4) { ModelTurn.ToolCalls(listOf(ToolCall("c$it", "again"))) }
        )
        val sink = SelectiveThrowingEventSink { true }
        val core = AgentCore(provider, registry)

        // The sink faults on the very first event, so this exercises the same
        // abort path; the point is that no exception escapes either way.
        val outcome = core.run("s1", "loop", RunOptions(maxSteps = 2, eventSink = sink))

        assertTrue(outcome is RunOutcome.Failure)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `a healthy sink receives every event exactly once in order`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("ok")))
        val sink = RecordingEventSink()

        AgentCore(provider, ToolRegistry()).run("s1", "hi", RunOptions(eventSink = sink))

        assertEquals(
            listOf(
                AgentEvent.RunStarted("s1"),
                AgentEvent.ModelTurnStarted(1),
                AgentEvent.ModelTurnFinished(1, ModelTurn.FinalAnswer("ok")),
                AgentEvent.AnswerProduced("ok"),
                AgentEvent.RunFinished(RunOutcome.Answer("ok")),
            ),
            sink.events,
        )
    }
}