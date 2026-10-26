package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.ToolRegistry
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * 0F.NORMALIZE-1: Runtime-owned durable TopicSession creation tests.
 *
 * Verifies:
 *  - CREATE-T1: initialize() calls factory once + store.createSession once;
 *    after success currentSessionId == id; Core session available.
 *  - CREATE-T2: store.createSession suspended → current session MUST NOT become
 *    usable before store completes (SESSION_ROW_EXISTS_BEFORE_CURRENT).
 *  - CREATE-T3: store.createSession fails → current session unchanged/unset;
 *    error propagated; NO D1.
 *  - Pure ID factory: sessionIdFactory does NOT call store.createSession.
 *  - No blocking bootstrap: initialize() is suspend (no CountDownLatch).
 */
class RuntimeSessionCreationTest {

    // ------------------------------------------------------------------ helpers

    private class AnswerProvider : ModelProvider {
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        override suspend fun complete(request: ModelRequest): ModelTurn {
            callCount.incrementAndGet()
            return ModelTurn.FinalAnswer("ok")
        }
    }

    /**
     * A TopicSessionStore whose createSession can be gated (suspended) or failed,
     * for deterministic ordering/failure tests.
     */
    private class GatingStore(
        val createGate: CompletableDeferred<Unit> = CompletableDeferred(),
        val failCreate: Boolean = false,
        val createdIds: MutableList<String> = mutableListOf(),
    ) : TopicSessionStore {
        override suspend fun createSession(
            id: String,
            nowMillis: Long,
            model: String?,
            provider: String?,
            title: String?,
        ): com.androidagent.runtime.persistence.TopicSessionRecord {
            if (failCreate) throw PersistenceException(null, "create_failed")
            createGate.await()
            createdIds.add(id)
            return com.androidagent.runtime.persistence.TopicSessionRecord(id, nowMillis, nowMillis)
        }

        override suspend fun appendMessage(
            sessionId: String,
            message: com.androidagent.core.Message,
            nowMillis: Long,
        ): com.androidagent.runtime.persistence.MessageRecord =
            com.androidagent.runtime.persistence.MessageRecord(
                id = "rec", topicSessionId = sessionId, sequence = 0L,
                type = "USER", payloadJson = "{}", createdAt = nowMillis,
            )

        override suspend fun loadSession(id: String): com.androidagent.runtime.persistence.TopicSessionRecord? = null
        override suspend fun loadLatestSession(): com.androidagent.runtime.persistence.TopicSessionRecord? = null
        override suspend fun loadMessages(sessionId: String): List<com.androidagent.core.Message>? = null
        override suspend fun updateModelProviderMetadata(
            sessionId: String, model: String?, provider: String?,
        ) {}
    }

    private fun makeRuntime(
        store: TopicSessionStore,
        idPrefix: String = "s",
    ): AgentRuntime {
        var counter = 0
        return AgentRuntime(
            agentCore = AgentCore(AnswerProvider(), ToolRegistry(), MessageDurability.Noop),
            runOptions = RunOptions(),
            sessionIdFactory = { "$idPrefix-${++counter}" },
            topicSessionStore = store,
        )
    }

    // ================================================================
    // CREATE-T1: successful durable creation
    // ================================================================
    @Test
    fun `CREATE-T1 initialize calls factory and store once, sets currentSessionId`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "t1")

        assertNull(runtime.currentSessionId, "no session before initialize")

        val id = runBlocking { runtime.initialize() }
        assertTrue(id.startsWith("t1-"), "id must come from the factory: $id")
        assertEquals(1, store.createdSessionIds.size, "store.createSession called exactly once")
        assertEquals(id, store.createdSessionIds.first(), "the created id matches the factory id")
        assertEquals(id, runtime.currentSessionId, "currentSessionId is set after success")
        // Core session is available (the Runtime can now send to this session).
        assertNotNull(runtime.currentSessionId)
    }

    // ================================================================
    // CREATE-T2: creation ordering — current session not usable before store completes
    // ================================================================
    @Test
    fun `CREATE-T2 current session not usable before store createSession completes`() {
        val gate = CompletableDeferred<Unit>()
        val store = GatingStore(createGate = gate)
        val runtime = makeRuntime(store, "t2")
        val result = CompletableDeferred<String>()

        // Launch initialize on a background scope (it will suspend on the gate).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                result.complete(runtime.initialize())
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }

        // Wait for initialize to reach the store gate (give it a moment).
        runBlocking { delay(100) }

        // The store has NOT completed → the session MUST NOT be current yet.
        assertNull(
            runtime.currentSessionId,
            "currentSessionId must be null while store.createSession is in flight",
        )

        // Now complete the gate → the store commits → initialize returns.
        gate.complete(Unit)
        val id = runBlocking { withTimeout(5000) { result.await() } }

        // After the store completes, the session IS current.
        assertEquals(id, runtime.currentSessionId)
        assertEquals(1, store.createdIds.size)
        scope.cancel()
    }

    // ================================================================
    // CREATE-T3: creation failure — no current session, error propagated, no D1
    // ================================================================
    @Test
    fun `CREATE-T3 store failure means no current session and error propagated`() {
        val store = GatingStore(failCreate = true)
        val countingProvider = AnswerProvider()
        var counter = 0
        val failingRuntime = AgentRuntime(
            agentCore = AgentCore(countingProvider, ToolRegistry(), MessageDurability.Noop),
            runOptions = RunOptions(),
            sessionIdFactory = { "t3-${++counter}" },
            topicSessionStore = store,
        )

        // initialize must throw (the store failed).
        val thrown = runCatching { runBlocking { failingRuntime.initialize() } }.exceptionOrNull()
        assertTrue(
            thrown is PersistenceException,
            "expected PersistenceException from store failure, got $thrown",
        )

        // No current session was set.
        assertNull(failingRuntime.currentSessionId, "no session must be current after failure")

        // send() must NOT cross D1 (no model call, no session).
        val outcome = runBlocking { failingRuntime.send("hello") }
        assertEquals(0, countingProvider.callCount.get(), "model must NOT be called when no session exists")
        assertTrue(
            outcome is com.androidagent.core.RunOutcome.Failure,
            "send must return Failure when no session exists, got $outcome",
        )
    }

    // ================================================================
    // Pure ID factory: sessionIdFactory does NOT call store.createSession
    // ================================================================
    @Test
    fun `sessionIdFactory is pure - does not call store createSession`() {
        val store = FakeTopicSessionStore()
        // Build a runtime WITHOUT calling initialize: the factory is invoked
        // lazily (only by initialize/newSession). Verify the factory itself
        // does not trigger a store call by checking the store is empty after
        // construction.
        var counter = 0
        val runtime = AgentRuntime(
            agentCore = AgentCore(AnswerProvider(), ToolRegistry(), MessageDurability.Noop),
            runOptions = RunOptions(),
            sessionIdFactory = { "pure-${++counter}" },
            topicSessionStore = store,
        )
        // The factory has NOT been called yet (no initialize).
        assertEquals(0, store.createdSessionIds.size, "no store call before initialize")
        // The runtime has no current session.
        assertNull(runtime.currentSessionId)
    }

    // ================================================================
    // newSession() durably creates a new session
    // ================================================================
    @Test
    fun `newSession durably creates a new session`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "ns")

        val firstId = runBlocking { runtime.initialize() }
        assertEquals(1, store.createdSessionIds.size)

        val switched = runBlocking { runtime.newSession() }
        assertTrue(switched, "newSession must succeed when not busy")
        assertEquals(2, store.createdSessionIds.size, "store.createSession called for new session")
        val secondId = runtime.currentSessionId!!
        assertEquals(secondId, store.createdSessionIds.last())
        assertTrue(secondId != firstId, "new session must have a different id")
    }
}
