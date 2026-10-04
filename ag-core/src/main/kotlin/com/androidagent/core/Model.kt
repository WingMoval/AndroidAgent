package com.androidagent.core

/**
 * One model turn: either a final answer, or a request to run tools.
 *
 * Provider-neutral by design. No vendor chat-completion schema, no streaming
 * delta, no token accounting.
 */
sealed interface ModelTurn {

    /** The model is done; [content] is the answer for this Run. */
    data class FinalAnswer(val content: String) : ModelTurn

    /**
     * The model wants tools run before it can answer.
     *
     * AG.0 executes these strictly in list order.
     *
     * [content] carries any prose the model emitted in the SAME turn as the tool
     * calls. Real chat-completion APIs do return both together ("Let me check the
     * device" plus a tool call), and discarding either half would lose model
     * output. It defaults to empty so a pure tool-call turn needs no argument, and
     * so existing `ToolCalls(calls)` call sites keep working when recompiled.
     */
    data class ToolCalls(
        val calls: List<ToolCall>,
        val content: String = "",
    ) : ModelTurn
}

/**
 * Everything a [ModelProvider] needs to produce one turn.
 *
 * [history] is the full Session history including everything earlier Runs
 * appended, which is what gives a Session its continuity across Runs.
 * [tools] is the current registry specification set.
 */
data class ModelRequest(
    val sessionId: String,
    val instructions: String,
    val history: List<Message>,
    val tools: List<ToolSpec>,
)

/**
 * The model boundary AG Core depends on.
 *
 * Implementations live outside AG Core. AG Core never knows a concrete provider.
 *
 /**
 * Failure convention: throwing an ordinary exception signals provider failure and
 * ends the Run with [RunFailure.ProviderFailure]. A
 * [java.util.concurrent.CancellationException] is deliberately not treated as
 * provider failure: it is coroutine control flow and is allowed to propagate.
 */
 */
fun interface ModelProvider {
    suspend fun complete(request: ModelRequest): ModelTurn
}