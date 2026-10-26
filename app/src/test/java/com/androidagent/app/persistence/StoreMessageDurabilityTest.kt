package com.androidagent.app.persistence

import com.androidagent.core.AgentCore
import com.androidagent.core.DurabilityCommitException
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.ToolSpec
import com.androidagent.core.UserMessage
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.SessionNotFoundException
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase F production-wiring tests: StoreMessageDurability + D1/D2/D3 durable-first
 * integration + cancellation semantics (CANCEL-F1..F6) + exactly-once.
 *
 * These run on the JVM (plain JUnit, no Android) against a deterministic
 * [ScriptedTopicSessionStore], so the cancellation/commit-outcome contract is
 * proven without a device or Room.
 */
class StoreMessageDurabilityTest {

    // ----------------------------------------------------------------------
    // Deterministic scripted store (test-only).
    // ----------------------------------------------------------------------
    private class ScriptedTopicSessionStore(
        val committed: MutableList<MessageRecord> = mutableListOf(),
        val createCalls: MutableList<String> = mutableListOf(),
        // When non-null and non-complete, appendMessage suspends on this deferred
        // BEFORE committing (simulates "transaction in flight").
        val gate: CompletableDeferred<Unit>? = null,
        val failAfterGate: AtomicBoolean = AtomicBoolean(false),
        val failAlways: AtomicBoolean = AtomicBoolean(false),
    ) : TopicSessionStore {
        val sessions = mutableSetOf<String>()
        private val seq = mutableMapOf<String, Long>()

        override suspend fun createSession(
            id: String,
            nowMillis: Long,
            model: String?,
            provider: String?,
            title: String?,
        ): TopicSessionRecord {
            createCalls.add(id)
            sessions.add(id)
            seq[id] = -1L
            return TopicSessionRecord(id, nowMillis, nowMillis)
        }

        override suspend fun appendMessage(
            sessionId: String,
            message: com.androidagent.core.Message,
            nowMillis: Long,
        ): MessageRecord {
            if (failAlways.get()) {
                throw PersistenceException(null, "storage_unavailable")
            }
            if (sessionId !in sessions) {
                throw SessionNotFoundException(sessionId)
            }
            // Simulate a slow in-flight transaction.
            gate?.await()
            if (failAfterGate.get()) {
                throw PersistenceException(null, "commit_failed_in_flight")
            }
            val next = (seq[sessionId] ?: -1L) + 1
            seq[sessionId] = next
            val rec = MessageRecord(
                id = "rec-${sessionId}-$next",
                topicSessionId = sessionId,
                sequence = next,
                type = typeOf(message),
                payloadJson = "json",
                createdAt = nowMillis,
            )
            committed.add(rec)
            return rec
        }

        override suspend fun loadSession(id: String): TopicSessionRecord? =
            if (id in sessions) TopicSessionRecord(id, 0, 0) else null

        override suspend fun loadLatestSession(): TopicSessionRecord? =
            sessions.firstOrNull()?.let { TopicSessionRecord(it, 0, 0) }

        override suspend fun loadMessages(sessionId: String): List<com.androidagent.core.Message>? =
            if (sessionId in sessions) emptyList() else null

        override suspend fun updateModelProviderMetadata(
            sessionId: String,
            model: String?,
            provider: String?,
        ) {
        }

        private fun typeOf(m: com.androidagent.core.Message): String = when (m) {
            is UserMessage -> "USER"
            is ModelMessage -> "MODEL"
            else -> "TOOL_RESULT"
        }
    }

    private class CountingTool(
        val execCount: AtomicInteger,
        private val result: ToolResult,
    ) : Tool {
        override val spec = ToolSpec("probe", "probe tool")
        override suspend fun execute(call: ToolCall): ToolResult {
            execCount.incrementAndGet()
            return result
        }
    }

    // =====================================================================
    // Gate M — D1: USER durability before ModelCall
    // =====================================================================
    @Test
    fun `D1 user message is durable before the model call`() {
        val store = ScriptedTopicSessionStore()
        val modelCalled = AtomicBoolean(false)
        val provider = ModelProvider { _: ModelRequest ->
            modelCalled.set(true)
            ModelTurn.FinalAnswer("ok")
        }
        val durability = StoreMessageDurability(store, { 1000L })
        val core = AgentCore(provider, ToolRegistry(), durability)
        runBlocking {
            store.createSession("s1", 999L)
            core.run("s1", "hello", RunOptions())
        }
        assertTrue("model must be called", modelCalled.get())
        assertTrue("user message must be durable", store.committed.any { it.type == "USER" })
        val userRec = store.committed.first { it.type == "USER" }
        val modelRec = store.committed.first { it.type == "MODEL" }
        assertTrue("USER must precede MODEL", userRec.sequence < modelRec.sequence)
    }

    @Test
    fun `D1 user persistence failure means no model call and no user row`() {
        val store = ScriptedTopicSessionStore(failAlways = AtomicBoolean(true))
        val callCount = AtomicInteger(0)
        val provider = ModelProvider { _: ModelRequest ->
            callCount.incrementAndGet()
            ModelTurn.FinalAnswer("ok")
        }
        val core = AgentCore(provider, ToolRegistry(), StoreMessageDurability(store, { 1000L }))
        val outcome = runBlocking {
            store.createSession("s1", 999L)
            core.run("s1", "hello", RunOptions())
        }
        assertTrue("expected Failure, got $outcome", outcome is RunOutcome.Failure)
        val failure = (outcome as RunOutcome.Failure).failure
        assertTrue("expected DurabilityFailure, got $failure", failure is RunFailure.DurabilityFailure)
        assertEquals("USER", (failure as RunFailure.DurabilityFailure).boundary)
        assertEquals("model must NOT be called when USER persistence fails", 0, callCount.get())
        assertTrue("no user row must be durable", store.committed.none { it.type == "USER" })
    }

    // =====================================================================
    // Gate N — D2: MODEL durability before tool dispatch; final answer before success
    // =====================================================================
    @Test
    fun `D2 model message is durable before tool dispatch`() {
        val store = ScriptedTopicSessionStore()
        val execCount = AtomicInteger(0)
        val tool = CountingTool(execCount, ToolResult(true, "tool-out", null))
        val provider = ModelProvider { _: ModelRequest ->
            if (store.committed.none { it.type == "MODEL" }) {
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "probe", "{}")))
            } else {
                ModelTurn.FinalAnswer("done")
            }
        }
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider, registry, StoreMessageDurability(store, { 1000L }))
        val outcome = runBlocking {
            store.createSession("s1", 999L)
            core.run("s1", "go", RunOptions())
        }
        assertTrue("run must succeed, got $outcome", outcome is RunOutcome.Answer)
        assertEquals(1, execCount.get())
        val modelRec = store.committed.first { it.type == "MODEL" }
        val toolResultRec = store.committed.first { it.type == "TOOL_RESULT" }
        assertTrue("MODEL must precede TOOL_RESULT", modelRec.sequence < toolResultRec.sequence)
    }

    @Test
    fun `D2 model persistence failure means no tool dispatch and DurabilityFailure MODEL`() {
        val base = ScriptedTopicSessionStore()
        val failingStore = object : TopicSessionStore by base {
            override suspend fun appendMessage(
                sessionId: String,
                message: com.androidagent.core.Message,
                nowMillis: Long,
            ): MessageRecord {
                if (message is ModelMessage) {
                    throw PersistenceException(null, "model_persist_failed")
                }
                return base.appendMessage(sessionId, message, nowMillis)
            }
        }
        val execCount = AtomicInteger(0)
        val tool = CountingTool(execCount, ToolResult(true, "out", null))
        val provider = ModelProvider { _: ModelRequest ->
            if (base.committed.none { it.type == "MODEL" }) {
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "probe", "{}")))
            } else {
                ModelTurn.FinalAnswer("done")
            }
        }
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider, registry, StoreMessageDurability(failingStore, { 1000L }))
        val outcome = runBlocking {
            base.createSession("s1", 999L)
            core.run("s1", "go", RunOptions())
        }
        assertTrue("expected Failure, got $outcome", outcome is RunOutcome.Failure)
        val failure = (outcome as RunOutcome.Failure).failure
        assertTrue("expected DurabilityFailure, got $failure", failure is RunFailure.DurabilityFailure)
        assertEquals("MODEL", (failure as RunFailure.DurabilityFailure).boundary)
        assertEquals("tool must NOT be dispatched when MODEL persistence fails", 0, execCount.get())
        assertTrue("USER was committed", base.committed.any { it.type == "USER" })
        assertTrue("no MODEL row must be durable", base.committed.none { it.type == "MODEL" })
    }

    @Test
    fun `D2 final answer must be durable before run success`() {
        val store = ScriptedTopicSessionStore()
        val provider = ModelProvider { _: ModelRequest -> ModelTurn.FinalAnswer("the answer") }
        val core = AgentCore(provider, ToolRegistry(), StoreMessageDurability(store, { 1000L }))
        val outcome = runBlocking {
            store.createSession("s1", 999L)
            core.run("s1", "q", RunOptions())
        }
        assertTrue("run must succeed, got $outcome", outcome is RunOutcome.Answer)
        assertTrue("final answer MODEL must be durable", store.committed.any { it.type == "MODEL" })
    }

    @Test
    fun `D2 final answer persistence failure means no successful answer`() {
        val base = ScriptedTopicSessionStore()
        val failingStore = object : TopicSessionStore by base {
            override suspend fun appendMessage(
                sessionId: String,
                message: com.androidagent.core.Message,
                nowMillis: Long,
            ): MessageRecord {
                if (message is ModelMessage) {
                    throw PersistenceException(null, "final_failed")
                }
                return base.appendMessage(sessionId, message, nowMillis)
            }
        }
        val provider = ModelProvider { _: ModelRequest -> ModelTurn.FinalAnswer("the answer") }
        val core = AgentCore(provider, ToolRegistry(), StoreMessageDurability(failingStore, { 1000L }))
        val outcome = runBlocking {
            base.createSession("s1", 999L)
            core.run("s1", "q", RunOptions())
        }
        assertTrue("run MUST NOT report success, got $outcome", outcome !is RunOutcome.Answer)
        assertTrue("expected a failure, got $outcome", outcome is RunOutcome.Failure)
        val failure = (outcome as RunOutcome.Failure).failure
        assertTrue("expected DurabilityFailure, got $failure", failure is RunFailure.DurabilityFailure)
    }

    // =====================================================================
    // Gate O — D3: TOOL_RESULT durability before next ModelCall
    // =====================================================================
    @Test
    fun `D3 tool result is durable before the next model call`() {
        val store = ScriptedTopicSessionStore()
        val execCount = AtomicInteger(0)
        val modelCalls = AtomicInteger(0)
        val tool = CountingTool(execCount, ToolResult(true, "out", null))
        val provider = ModelProvider { _: ModelRequest ->
            val n = modelCalls.incrementAndGet()
            if (n == 1) {
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "probe", "{}")))
            } else {
                ModelTurn.FinalAnswer("done")
            }
        }
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider, registry, StoreMessageDurability(store, { 1000L }))
        val outcome = runBlocking {
            store.createSession("s1", 999L)
            core.run("s1", "go", RunOptions())
        }
        assertTrue("run must succeed, got $outcome", outcome is RunOutcome.Answer)
        assertEquals(1, execCount.get())
        assertEquals(2, modelCalls.get())
        val toolResultRec = store.committed.first { it.type == "TOOL_RESULT" }
        val finalModelRec = store.committed.filter { it.type == "MODEL" }.last()
        assertTrue("TOOL_RESULT must precede next MODEL", toolResultRec.sequence < finalModelRec.sequence)
    }

    @Test
    fun `D3 tool result persistence failure means no next model call`() {
        val base = ScriptedTopicSessionStore()
        val failingStore = object : TopicSessionStore by base {
            override suspend fun appendMessage(
                sessionId: String,
                message: com.androidagent.core.Message,
                nowMillis: Long,
            ): MessageRecord {
                if (message is ToolResultMessage) {
                    throw PersistenceException(null, "toolresult_failed")
                }
                return base.appendMessage(sessionId, message, nowMillis)
            }
        }
        val execCount = AtomicInteger(0)
        val modelCalls = AtomicInteger(0)
        val tool = CountingTool(execCount, ToolResult(true, "out", null))
        val provider = ModelProvider { _: ModelRequest ->
            val n = modelCalls.incrementAndGet()
            if (n == 1) {
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "probe", "{}")))
            } else {
                ModelTurn.FinalAnswer("done")
            }
        }
        val registry = ToolRegistry().apply { register(tool) }
        val core = AgentCore(provider, registry, StoreMessageDurability(failingStore, { 1000L }))
        val outcome = runBlocking {
            base.createSession("s1", 999L)
            core.run("s1", "go", RunOptions())
        }
        assertTrue("expected Failure, got $outcome", outcome is RunOutcome.Failure)
        val failure = (outcome as RunOutcome.Failure).failure
        assertTrue("expected DurabilityFailure, got $failure", failure is RunFailure.DurabilityFailure)
        assertEquals("TOOL_RESULT", (failure as RunFailure.DurabilityFailure).boundary)
        assertEquals(
            "next model call must NOT occur when TOOL_RESULT persistence fails",
            1,
            modelCalls.get(),
        )
    }

    // =====================================================================
    // Gate Q / I — Cancellation tests (CANCEL-F1..F6)
    // =====================================================================

    // CANCEL-F1: cancellation before persistence starts -> NOT_COMMITTED, no row.
    @Test
    fun `CANCEL-F1 cancellation before commit means no durable row`() {
        val store = ScriptedTopicSessionStore()
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            val job = launch { durability.commit("s1", UserMessage("hi")) }
            job.cancel()
            job.join()
            assertTrue("no row must be durable", store.committed.isEmpty())
        }
    }

    // CANCEL-F2: cancellation while the append transaction is in flight ->
    // deterministic COMMIT (the shield completes the transaction).
    @Test
    fun `CANCEL-F2 cancellation during in-flight commit commits deterministically`() {
        val gate = CompletableDeferred<Unit>()
        val store = ScriptedTopicSessionStore(gate = gate)
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            val resultRef = AtomicReference<Throwable?>(null)
            val commitJob = async {
                try {
                    durability.commit("s1", UserMessage("hi"))
                } catch (e: Throwable) {
                    resultRef.set(e)
                }
            }
            delay(100) // let the commit reach the (blocked) transaction
            commitJob.cancel()
            gate.complete(Unit) // release the shielded transaction -> it COMMITS
            commitJob.join()
            val t = resultRef.get()
            assertTrue(
                "in-flight commit must commit, not fail with a durability error; got $t",
                t == null || t is CancellationException,
            )
            assertTrue("no DurabilityCommitException for a committed row", t !is DurabilityCommitException)
            assertEquals(1, store.committed.count { it.type == "USER" })
        }
    }

    // CANCEL-F3: commit succeeds while cancellation is pending -> COMMITTED; the
    // committed fact is NOT reported as a durability failure.
    @Test
    fun `CANCEL-F3 commit succeeds with pending cancellation and is not a durability failure`() {
        val gate = CompletableDeferred<Unit>()
        val store = ScriptedTopicSessionStore(gate = gate)
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            val resultRef = AtomicReference<Throwable?>(null)
            val commitJob = async {
                try {
                    durability.commit("s1", UserMessage("hi"))
                } catch (e: Throwable) {
                    resultRef.set(e)
                }
            }
            delay(100)
            commitJob.cancel() // pending cancellation
            gate.complete(Unit)
            commitJob.join()
            val t = resultRef.get()
            assertTrue(
                "a committed row must not surface as a durability failure; got $t",
                t !is DurabilityCommitException,
            )
            assertEquals(1, store.committed.count { it.type == "USER" })
        }
    }

    // CANCEL-F4: transaction fails while cancellation is pending -> NOT_COMMITTED,
    // no row.
    @Test
    fun `CANCEL-F4 transaction fails with pending cancellation and is not committed`() {
        val gate = CompletableDeferred<Unit>()
        val store = ScriptedTopicSessionStore(gate = gate, failAfterGate = AtomicBoolean(true))
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            val resultRef = AtomicReference<Throwable?>(null)
            val commitJob = async {
                try {
                    durability.commit("s1", UserMessage("hi"))
                } catch (e: Throwable) {
                    resultRef.set(e)
                }
            }
            delay(100)
            commitJob.cancel()
            gate.complete(Unit)
            commitJob.join()
            assertTrue("no row must be durable", store.committed.isEmpty())
            val t = resultRef.get()
            assertTrue(
                "failure or cancellation expected, got $t",
                t == null || t is DurabilityCommitException || t is CancellationException,
            )
        }
    }

    // CANCEL-F5: no duplicate append after cancellation (exactly-once).
    @Test
    fun `CANCEL-F5 no duplicate append after cancellation`() {
        val gate = CompletableDeferred<Unit>()
        val store = ScriptedTopicSessionStore(gate = gate)
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            val job = async {
                try {
                    durability.commit("s1", UserMessage("hi"))
                } catch (_: Throwable) {
                }
            }
            delay(100)
            job.cancel()
            gate.complete(Unit)
            job.join()
            assertEquals("exactly one durable row", 1, store.committed.count { it.type == "USER" })
        }
    }

    // CANCEL-F6: each committed message appears exactly once; sequences unique.
    @Test
    fun `CANCEL-F6 committed messages appear exactly once with unique sequences`() {
        val store = ScriptedTopicSessionStore()
        val durability = StoreMessageDurability(store, { 1000L })
        runBlocking {
            store.createSession("s1", 999L)
            durability.commit("s1", UserMessage("only-once"))
            durability.commit("s1", UserMessage("second"))
        }
        assertEquals("each message durable exactly once", 2, store.committed.count { it.type == "USER" })
        val seqs = store.committed.map { it.sequence }
        assertEquals("sequences unique", seqs.toSet().size, seqs.size)
    }

    // =====================================================================
    // Gate S/T — sequence ownership + clock through the adapter
    // =====================================================================
    @Test
    fun `adapter does not allocate sequence and uses the injected clock`() {
        val store = ScriptedTopicSessionStore()
        val durability = StoreMessageDurability(store, { 1234L })
        runBlocking {
            store.createSession("s1", 1000L)
            durability.commit("s1", UserMessage("a"))
            durability.commit("s1", UserMessage("b"))
        }
        assertEquals(listOf(0L, 1L), store.committed.map { it.sequence })
        assertTrue("createdAt must come from the injected clock", store.committed.all { it.createdAt == 1234L })
    }

    @Test
    fun `store is the sole sequence owner across sessions`() {
        val store = ScriptedTopicSessionStore()
        val durability = StoreMessageDurability(store, { 1L })
        runBlocking {
            store.createSession("a", 0L)
            store.createSession("b", 0L)
            durability.commit("a", UserMessage("a1"))
            durability.commit("b", UserMessage("b1"))
            durability.commit("a", UserMessage("a2"))
        }
        val aSeqs = store.committed.filter { it.topicSessionId == "a" }.map { it.sequence }
        val bSeqs = store.committed.filter { it.topicSessionId == "b" }.map { it.sequence }
        assertEquals(listOf(0L, 1L), aSeqs)
        assertEquals(listOf(0L), bSeqs)
    }

    // Gate G — append to a session row that does not exist -> DurabilityCommitException
    // (NOT_COMMITTED), never a silent create.
    @Test
    fun `commit to a missing session row means DurabilityCommitException not committed`() {
        val store = ScriptedTopicSessionStore()
        val durability = StoreMessageDurability(store, { 1000L })
        val thrown = runCatching {
            runBlocking { durability.commit("nope", UserMessage("hi")) }
        }.exceptionOrNull()
        assertTrue("expected DurabilityCommitException, got $thrown", thrown is DurabilityCommitException)
        // The SessionNotFoundException must be somewhere in the cause chain (the
        // adapter wraps it; Core extracts a safe category from it).
        var chain: Throwable? = thrown
        var foundSessionNotFound = false
        while (chain != null) {
            if (chain is SessionNotFoundException) {
                foundSessionNotFound = true
                break
            }
            chain = chain.cause
        }
        assertTrue("SessionNotFoundException must be in the cause chain, got: $thrown", foundSessionNotFound)
        assertTrue("no row must be durable", store.committed.isEmpty())
    }
}
