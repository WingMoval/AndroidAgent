package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.Message
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Test doubles for :ag-runtime.
 *
 * Test source only. There is no Android, no HTTP, no real provider and no EMG
 * anywhere in this module's tests — which is the whole point of the extraction.
 */
internal class ScriptedProvider(
    private val turns: List<ModelTurn>,
) : ModelProvider {

    val requests: MutableList<ModelRequest> = java.util.Collections.synchronizedList(mutableListOf())

    val callCount: Int get() = requests.size

    /** When set, [complete] suspends on this before returning the next turn. */
    var gate: CompletableDeferred<Unit>? = null

    /**
     * When set, [complete] suspends on this BEFORE returning any turn. This lets a
     * test hold the Run in flight at a known point (e.g. right as the provider is
     * called) so it can interrupt the Run deterministically and observe that the
     * provider's call was cancelled (it never returned a turn).
     */
    var startGate: CompletableDeferred<Unit>? = null

    /** True once [complete] observed a cancellation (i.e. was interrupted mid-call). */
    var cancelled: Boolean = false
        private set

    override suspend fun complete(request: ModelRequest): ModelTurn {
        requests += request
        try {
            // Hold at each gate (if any) with a plain suspension. A user Stop is
            // enforced by the Runtime cancelling the Run's execution Job, so the
            // gate suspension receives the CancellationException exactly as any
            // well-behaved suspend function would (no separate Stop protocol).
            startGate?.await()
            gate?.await()
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        }
        return turns.getOrElse(requests.size - 1) { turns.last() }
    }
}

/** A provider that always throws, producing a real AG Core provider failure. */
internal class ThrowingProvider(
    private val cause: () -> Throwable = { IllegalStateException("provider offline") },
) : ModelProvider {
    override suspend fun complete(request: ModelRequest): ModelTurn = throw cause()
}

/** Builds a runtime over a scripted provider, with deterministic session ids. */
internal fun runtimeOf(
    provider: ModelProvider,
    idPrefix: String = "s",
): Pair<AgentRuntime, () -> String> {
    var counter = 0
    val nextId = { "$idPrefix-${++counter}" }
    val runtime = AgentRuntime(
        agentCore = AgentCore(provider, ToolRegistry()),
        runOptions = RunOptions(),
        sessionIdFactory = nextId,
    )
    return runtime to nextId
}

internal fun historyTexts(request: ModelRequest): List<String> =
    request.history.map { m ->
        when (m) {
            is com.androidagent.core.UserMessage -> "U:${m.content}"
            is com.androidagent.core.ModelMessage -> "M:${m.content}:${m.toolCalls.size}"
            is com.androidagent.core.ToolResultMessage -> "T:${m.toolCallId}"
        }
    }

internal fun assertTrue(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

internal fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
    if (expected != actual) {
        throw AssertionError("$message expected=<$expected> actual=<$actual>")
    }
}
