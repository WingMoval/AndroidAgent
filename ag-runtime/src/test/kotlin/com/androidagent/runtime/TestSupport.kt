package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.Message
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.ToolRegistry
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/**
 * Test doubles for :ag-runtime.
 *
 * Test source only. There is no Android, no HTTP, no real provider and no EMG
 * anywhere in this module's tests 鈥?which is the whole point of the extraction.
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

/**
 * A functional in-memory TopicSessionStore for ag-runtime tests.
 *
 * Supports:
 *  - Session creation (records ids in [createdSessionIds]).
 *  - Message append (records messages per session, assigns sequences).
 *  - loadLatestSession (returns the last-created session).
 *  - loadMessages (returns the stored messages for a session; null if session missing).
 *  - Optional failure injection for deterministic failure tests.
 */
internal class FakeTopicSessionStore(
    val createdSessionIds: MutableList<String> = mutableListOf(),
) : TopicSessionStore {
    private val sessionOrder = mutableListOf<String>()
    private val sessionMessages = mutableMapOf<String, MutableList<Message>>()
    private val sessionRecords = mutableMapOf<String, TopicSessionRecord>()

    // Failure injection
    var failCreate: Boolean = false
    var failAppend: Boolean = false
    var failLoadLatest: Boolean = false
    var failLoadMessages: Boolean = false
    var loadMessagesReturnNullForExisting: Boolean = false
    /** When set, loadMessages throws this (to simulate decode / sequence failures). */
    var loadMessagesException: Exception? = null
    /** When set, appendMessage fails for the specified tool call ids (partial repair). */
    var failAppendForToolCallIds: Set<String> = emptySet()
    /** When set, appendMessage suspends here (to hold a repair append for cancellation tests). */
    var appendGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    /** Counts appendMessage calls (for cancellation / partial-repair assertions). */
    val appendCount = java.util.concurrent.atomic.AtomicInteger(0)

    override suspend fun createSession(
        id: String,
        nowMillis: Long,
        model: String?,
        provider: String?,
        title: String?,
    ): TopicSessionRecord {
        if (failCreate) throw com.androidagent.runtime.persistence.PersistenceException(null, "create_failed")
        createdSessionIds.add(id)
        sessionOrder.add(id)
        sessionMessages[id] = mutableListOf()
        val record = TopicSessionRecord(id, nowMillis, nowMillis)
        sessionRecords[id] = record
        return record
    }

    override suspend fun appendMessage(
        sessionId: String,
        message: Message,
        nowMillis: Long,
    ): MessageRecord {
        appendCount.incrementAndGet()
        appendGate?.await()
        if (failAppend) throw com.androidagent.runtime.persistence.PersistenceException(null, "append_failed")
        if (message is com.androidagent.core.ToolResultMessage && message.toolCallId in failAppendForToolCallIds) {
            throw com.androidagent.runtime.persistence.PersistenceException(null, "append_failed_for_$${message.toolCallId}")
        }
        val messages = sessionMessages[sessionId] ?: throw com.androidagent.runtime.persistence.SessionNotFoundException(sessionId)
        val seq = messages.size.toLong()
        messages.add(message)
        return MessageRecord(
            id = "rec-${sessionId}-$seq",
            topicSessionId = sessionId,
            sequence = seq,
            type = "USER",
            payloadJson = "{}",
            createdAt = nowMillis,
        )
    }

    override suspend fun loadSession(id: String): TopicSessionRecord? =
        sessionRecords[id]

    override suspend fun loadLatestSession(): TopicSessionRecord? {
        if (failLoadLatest) throw com.androidagent.runtime.persistence.PersistenceException(null, "load_latest_failed")
        return sessionOrder.lastOrNull()?.let { sessionRecords[it] }
    }

    override suspend fun loadMessages(sessionId: String): List<Message>? {
        loadMessagesException?.let { throw it }
        if (failLoadMessages) throw com.androidagent.runtime.persistence.PersistenceException(null, "load_messages_failed")
        val messages = sessionMessages[sessionId]
        if (messages == null) return null
        if (loadMessagesReturnNullForExisting) return null
        return messages.toList()
    }

    override suspend fun updateModelProviderMetadata(
        sessionId: String,
        model: String?,
        provider: String?,
    ) {
    }

    override suspend fun listSessions(): List<TopicSessionRecord> =
        sessionMessages.keys.map { TopicSessionRecord(it, 0L, 0L) }

    /** Test helper: get the stored messages for a session. */
    fun messagesFor(sessionId: String): List<Message> =
        sessionMessages[sessionId]?.toList() ?: emptyList()
}

/** Builds a runtime over a scripted provider, with an explicit [FakeTopicSessionStore]. */
internal fun runtimeOf(
    provider: ModelProvider,
    idPrefix: String = "s",
): Pair<AgentRuntime, FakeTopicSessionStore> {
    var counter = 0
    val store = FakeTopicSessionStore()
    val runtime = AgentRuntime(
        agentCore = AgentCore(provider, ToolRegistry(), MessageDurability.Noop),
        runOptions = RunOptions(),
        sessionIdFactory = { "$idPrefix-${++counter}" },
        topicSessionStore = store,
    )
    return runtime to store
}

/**
 * Builds a runtime with a [FakeTopicSessionStore] and immediately initializes a
 * durable session (so the runtime is ready to accept [AgentRuntime.send] calls).
 * Returns the runtime and the fake store (for assertions).
 */
internal fun runtimeWithStore(
    provider: ModelProvider,
    idPrefix: String = "s",
): Pair<AgentRuntime, FakeTopicSessionStore> {
    var counter = 0
    val store = FakeTopicSessionStore()
    val runtime = AgentRuntime(
        agentCore = AgentCore(provider, ToolRegistry(), MessageDurability.Noop),
        runOptions = RunOptions(),
        sessionIdFactory = { "$idPrefix-${++counter}" },
        topicSessionStore = store,
    )
    // Initialize the first durable session (blocking bridge for test setup).
    runBlocking { runtime.initialize() }
    return runtime to store
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
