package com.androidagent.core

/**
 * Test doubles for AG.0.
 *
 * Everything in this file is test scaffolding and lives only in the test source
 * set on purpose. AG.0 ships no concrete ModelProvider and no concrete Tool in
 * production, because doing so would make AG Core depend on its own test needs.
 */

/**
 * A [ModelProvider] that replays a fixed script of turns and records every
 * request it received.
 *
 * Fully deterministic and offline: no network, no clock, no randomness.
 *
 * [complete] is synchronized because the concurrency tests deliberately drive two
 * Sessions of one AgentCore from two threads.
 */
class ScriptedModelProvider(turns: List<ModelTurn>) : ModelProvider {

    private val lock = Any()
    private val script = ArrayDeque(turns)

    /** Every request the provider was asked to complete, in order. */
    val requests: MutableList<ModelRequest> = mutableListOf()

    val callCount: Int get() = synchronized(lock) { requests.size }

    override suspend fun complete(request: ModelRequest): ModelTurn = synchronized(lock) {
        requests += request
        script.removeFirstOrNull()
            ?: error("ScriptedModelProvider exhausted after ${requests.size} call(s)")
    }
}

/**
 * A [ModelProvider] that fails its first call with [failure], then behaves like
 * a scripted provider returning [then].
 *
 * Used to prove a failed Run still releases its Session.
 */
class FailsOnceModelProvider(
    private val failure: Throwable,
    private val then: ModelTurn,
) : ModelProvider {

    val requests: MutableList<ModelRequest> = mutableListOf()

    override suspend fun complete(request: ModelRequest): ModelTurn {
        requests += request
        return if (requests.size == 1) throw failure else then
    }
}

/**
 * A [Tool] that records its invocations and returns a caller-supplied result.
 *
 * When [order] is supplied, the tool name is appended on entry and [completed]
 * on exit, which lets a test observe both start order and completion order and
 * therefore argue about seriality rather than only ordering.
 */
class RecordingTool(
    override val spec: ToolSpec,
    private val order: MutableList<String>? = null,
    private val completed: MutableList<String>? = null,
    private val result: (ToolCall) -> ToolResult = { ToolResult(success = true, output = "ok") },
) : Tool {

    val calls: MutableList<ToolCall> = mutableListOf()

    val executeCount: Int get() = calls.size

    override suspend fun execute(call: ToolCall): ToolResult {
        calls += call
        order?.add(call.name)
        val produced = result(call)
        completed?.add(call.name)
        return produced
    }
}

/** A [Tool] that always throws, to exercise the registry's error boundary. */
class ThrowingTool(
    override val spec: ToolSpec,
    private val failure: () -> Throwable,
) : Tool {

    var executeCount: Int = 0
        private set

    override suspend fun execute(call: ToolCall): ToolResult {
        executeCount++
        throw failure()
    }
}

/** An [AgentEventSink] that records every event in emission order. */
class RecordingEventSink : AgentEventSink {

    val events: MutableList<AgentEvent> = mutableListOf()

    override fun emit(event: AgentEvent) {
        events += event
    }
}

/** An [AgentEventSink] that always throws, to exercise the runtime-failure path. */
class ThrowingEventSink : AgentEventSink {
    override fun emit(event: AgentEvent): Unit = throw IllegalStateException("sink is broken")
}

/**
 * An [AgentEventSink] that records every event and throws for the events matched
 * by [shouldThrow].
 *
 * [emitCount] counts attempted emissions including the ones that threw, which is
 * how a test proves a terminal event was attempted at all.
 */
class SelectiveThrowingEventSink(
    private val shouldThrow: (AgentEvent) -> Boolean,
) : AgentEventSink {

    val events: MutableList<AgentEvent> = mutableListOf()

    var emitCount: Int = 0
        private set

    override fun emit(event: AgentEvent) {
        emitCount++
        if (shouldThrow(event)) {
            throw IllegalStateException("sink refused ${event::class.simpleName}")
        }
        events += event
    }
}