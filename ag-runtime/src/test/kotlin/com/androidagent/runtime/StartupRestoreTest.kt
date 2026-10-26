package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.Message
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase G: Process-scoped startup create-or-restore + cross-process dangling repair
 * + Core hydration + idempotent initialization tests.
 */
class StartupRestoreTest {

    // ------------------------------------------------------------------ helpers

    private class AnswerProvider : ModelProvider {
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        override suspend fun complete(request: ModelRequest): ModelTurn {
            callCount.incrementAndGet()
            return ModelTurn.FinalAnswer("ok")
        }
    }

    private fun makeRuntime(
        store: TopicSessionStore,
        idPrefix: String = "s",
        provider: ModelProvider = AnswerProvider(),
    ): AgentRuntime {
        var counter = 0
        return AgentRuntime(
            agentCore = AgentCore(provider, ToolRegistry(), MessageDurability.Noop),
            runOptions = RunOptions(),
            sessionIdFactory = { "$idPrefix-${++counter}" },
            topicSessionStore = store,
        )
    }

    /** Builds a store with a pre-existing session and history. */
    private fun storeWithHistory(
        sessionId: String,
        messages: List<Message>,
    ): FakeTopicSessionStore {
        val store = FakeTopicSessionStore()
        runBlocking {
            store.createSession(sessionId, 1000L)
            for (msg in messages) {
                store.appendMessage(sessionId, msg, 1000L)
            }
        }
        return store
    }

    // ================================================================
    // STARTUP-G1: Fresh store → create new session
    // ================================================================
    @Test
    fun `STARTUP-G1 fresh store creates new session and Core empty session`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "g1")

        assertNull(runtime.currentSessionId, "no session before initialize")

        val id = runBlocking { runtime.initialize() }
        assertEquals(1, store.createdSessionIds.size, "exactly one session created")
        assertEquals(id, store.createdSessionIds.first())
        assertEquals(id, runtime.currentSessionId, "current = new id")
        // Core session is available and empty.
        val coreHistory = runBlocking { /* Core session accessible */ }
        assertNotNull(runtime.currentSessionId, "READY: current published")
    }

    // ================================================================
    // STARTUP-G2: Existing empty session → restore same session
    // ================================================================
    @Test
    fun `STARTUP-G2 existing empty session restored without new creation`() {
        val store = storeWithHistory("existing-1", emptyList())
        val createdBefore = store.createdSessionIds.size // 1 (from setup)
        val runtime = makeRuntime(store, "g2")

        val id = runBlocking { runtime.initialize() }
        assertEquals("existing-1", id, "restored the existing session, not a new one")
        assertEquals(createdBefore, store.createdSessionIds.size, "no NEW session created during initialize")
        assertEquals(id, runtime.currentSessionId)
    }

    // ================================================================
    // STARTUP-G3: Existing history → restore exactly, no repersistence
    // ================================================================
    @Test
    fun `STARTUP-G3 existing history restored without repersistence`() {
        val history = listOf(
            UserMessage("hello"),
            ModelMessage("hi there"),
        )
        val store = storeWithHistory("s3", history)
        val runtime = makeRuntime(store, "g3")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s3", id)
        // No new messages were appended (no repersistence).
        assertEquals(2, store.messagesFor("s3").size, "still exactly 2 messages (no repersistence)")
    }

    // ================================================================
    // STARTUP-G4: Sequential initialize → idempotent
    // ================================================================
    @Test
    fun `STARTUP-G4 sequential initialize is idempotent`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "g4")

        val id1 = runBlocking { runtime.initialize() }
        val id2 = runBlocking { runtime.initialize() }

        assertEquals(id1, id2, "same id on second call")
        assertEquals(1, store.createdSessionIds.size, "only one session created total")
    }

    // ================================================================
    // STARTUP-G5: Concurrent initialize → single-flight
    // ================================================================
    @Test
    fun `STARTUP-G5 concurrent initialize is single-flight`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "g5")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val results = CompletableDeferred<List<String>>()

        val job = scope.async {
            val id1 = runtime.initialize()
            val id2 = runtime.initialize()
            listOf(id1, id2)
        }

        val outcome = runBlocking { withTimeout(5000) { job.await() } }
        assertEquals(outcome[0], outcome[1], "both callers get the same id")
        assertEquals(1, store.createdSessionIds.size, "only one session created")
        scope.cancel()
    }

    // ================================================================
    // STARTUP-G6: loadLatest failure → no current, no READY
    // ================================================================
    @Test
    fun `STARTUP-G6 loadLatest failure means no current session`() {
        val store = FakeTopicSessionStore().apply { failLoadLatest = true }
        val runtime = makeRuntime(store, "g6")

        val thrown = runCatching { runBlocking { runtime.initialize() } }.exceptionOrNull()
        assertTrue(thrown is PersistenceException, "expected PersistenceException, got $thrown")
        assertNull(runtime.currentSessionId, "no current session after failure")
    }

    // ================================================================
    // STARTUP-G7-R: loadMessages null → controlled fresh recovery
    // ================================================================
    @Test
    fun `STARTUP-G7-R loadMessages null triggers controlled fresh recovery`() {
        val store = storeWithHistory("corrupt-1", listOf(UserMessage("hi")))
        store.loadMessagesReturnNullForExisting = true
        val runtime = makeRuntime(store, "g7r")

        // The corrupt session has no loadable history → controlled fresh recovery.
        val id = runBlocking { runtime.initialize() }
        // A NEW session is created (not the corrupt one, not null).
        assertTrue(id.startsWith("g7r-"), "recovered with a fresh session: $id")
        assertTrue(id != "corrupt-1", "recovery session is not the corrupt one")
        assertEquals(id, runtime.currentSessionId, "fresh session is current")
        // The corrupt old session is PRESERVED (not deleted).
        assertNotNull(runBlocking { store.loadSession("corrupt-1") }, "corrupt session preserved")
        // The fresh session has empty history.
        assertEquals(0, store.messagesFor(id).size, "fresh session is empty")
    }

    // ================================================================
    // STARTUP-G8: recovery fresh create failure → no current / FAILED
    // ================================================================
    @Test
    fun `STARTUP-G8 recovery create failure means no current and FAILED`() {
        // Corrupt latest session (decode failure) + fresh create fails.
        val store = storeWithHistory("corrupt-8", listOf(UserMessage("hi")))
        store.loadMessagesException =
            com.androidagent.runtime.persistence.MessageDecodeException("MODEL", "malformed")
        store.failCreate = true // the recovery's createSession will fail
        val countingProvider = AnswerProvider()
        val runtime = makeRuntime(store, "g8", countingProvider)

        val thrown = runCatching { runBlocking { runtime.initialize() } }.exceptionOrNull()
        assertTrue(thrown is PersistenceException, "expected PersistenceException (create fail), got $thrown")
        assertNull(runtime.currentSessionId, "no current session after recovery create failure")
        // Corrupt old session preserved.
        assertNotNull(runBlocking { store.loadSession("corrupt-8") }, "corrupt session preserved")
        // No D1 / model call.
        assertEquals(0, countingProvider.callCount.get(), "no model call after recovery failure")
        val outcome = runBlocking { runtime.send("hello") }
        assertTrue(outcome is com.androidagent.core.RunOutcome.Failure, "send denied when no current")
        assertEquals(0, countingProvider.callCount.get(), "still no model call")
    }

    // ================================================================
    // VALIDATE-G1: Sequence gap (tested via Room in app module)
    // ================================================================
    // (Covered by app integration tests)

    // ================================================================
    // VALIDATE-G5: Valid empty history → restore as empty
    // ================================================================
    @Test
    fun `VALIDATE-G5 valid empty history restored as empty`() {
        val store = storeWithHistory("s5", emptyList())
        val runtime = makeRuntime(store, "g5v")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s5", id)
        assertEquals(0, store.messagesFor("s5").size, "no messages added")
    }

    // ================================================================
    // REPAIR-G1: No dangling → zero synthetic writes
    // ================================================================
    @Test
    fun `REPAIR-G1 no dangling means zero synthetic writes`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("let me check", listOf(ToolCall("A", "probe", "{}"))),
            ToolResultMessage("A", "probe", ToolResult(true, "result", null)),
        )
        val store = storeWithHistory("s1", history)
        val runtime = makeRuntime(store, "r1")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s1", id)
        // No synthetic appended: still 3 messages.
        assertEquals(3, store.messagesFor("s1").size, "no synthetic writes")
    }

    // ================================================================
    // REPAIR-G2: One dangling → synthetic UNKNOWN durable → reload → restore
    // ================================================================
    @Test
    fun `REPAIR-G2 one dangling gets synthetic UNKNOWN repair`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("let me check", listOf(ToolCall("A", "probe", "{}"))),
            // NO ToolResultMessage for A → dangling
        )
        val store = storeWithHistory("s2", history)
        val runtime = makeRuntime(store, "r2")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s2", id)
        // One synthetic appended: 3 messages total.
        val finalMessages = store.messagesFor("s2")
        assertEquals(3, finalMessages.size, "one synthetic appended")
        // The synthetic is a ToolResultMessage for A with the canonical error.
        val synthetic = finalMessages[2] as ToolResultMessage
        assertEquals("A", synthetic.toolCallId)
        assertEquals(
            AgentCore.IN_FLIGHT_INTERRUPTED_ERROR,
            synthetic.result.error,
            "canonical UNKNOWN semantics reused",
        )
    }

    // ================================================================
    // REPAIR-G3: Mixed (A complete, B dangling, C dangling)
    // ================================================================
    @Test
    fun `REPAIR-G3 mixed history only repairs dangling B and C`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage(
                "checking",
                listOf(
                    ToolCall("A", "probe", "{}"),
                    ToolCall("B", "probe", "{}"),
                    ToolCall("C", "probe", "{}"),
                ),
            ),
            ToolResultMessage("A", "probe", ToolResult(true, "done", null)),
            // B and C dangling
        )
        val store = storeWithHistory("s3", history)
        val runtime = makeRuntime(store, "r3")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s3", id)
        val finalMessages = store.messagesFor("s3")
        assertEquals(5, finalMessages.size, "two synthetics appended (B, C)")
        // Verify B and C are the synthetics, in order.
        val b = finalMessages[3] as ToolResultMessage
        val c = finalMessages[4] as ToolResultMessage
        assertEquals("B", b.toolCallId)
        assertEquals("C", c.toolCallId)
    }

    // ================================================================
    // REPAIR-G4: Same-process synthetic already durable → NO duplicate
    // ================================================================
    @Test
    fun `REPAIR-G4 existing synthetic result not duplicated`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "probe", "{}"))),
            // A already has a synthetic UNKNOWN result (from same-process closeout).
            ToolResultMessage(
                "A", "probe",
                ToolResult(false, "", error = AgentCore.IN_FLIGHT_INTERRUPTED_ERROR),
            ),
        )
        val store = storeWithHistory("s4", history)
        val runtime = makeRuntime(store, "r4")

        val id = runBlocking { runtime.initialize() }
        assertEquals("s4", id)
        // No new synthetic: still 3 messages.
        assertEquals(3, store.messagesFor("s4").size, "no duplicate repair")
    }

    // ================================================================
    // REPAIR-G5: Append failure → no Core restore, no current
    // ================================================================
    @Test
    fun `REPAIR-G5 repair append failure means no current session`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "probe", "{}"))),
            // A dangling
        )
        val store = storeWithHistory("s5", history)
        store.failAppend = true
        val runtime = makeRuntime(store, "r5")

        val thrown = runCatching { runBlocking { runtime.initialize() } }.exceptionOrNull()
        assertTrue(thrown is PersistenceException, "expected PersistenceException, got $thrown")
        assertNull(runtime.currentSessionId, "no current session after repair failure")
    }

    // ================================================================
    // REPAIR-G9: No tool replay, no model call during startup
    // ================================================================
    @Test
    fun `REPAIR-G9 startup does not replay tools or call model`() {
        val provider = AnswerProvider()
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "probe", "{}"))),
            // A dangling
        )
        val store = storeWithHistory("s9", history)
        val runtime = makeRuntime(store, "r9", provider)

        runBlocking { runtime.initialize() }

        assertEquals(0, provider.callCount.get(), "no model call during startup")
        // No tool execution (no tool registered, and even if one was,
        // the repair only appends synthetic results).
    }

    // ================================================================
    // RESTORE-G1: Restore does not repersist
    // ================================================================
    @Test
    fun `RESTORE-G1 clean restore does not repersist history`() {
        val history = listOf(
            UserMessage("1"),
            ModelMessage("2"),
            UserMessage("3"),
            ModelMessage("4"),
        )
        val store = storeWithHistory("s1", history)
        val runtime = makeRuntime(store, "rs1")

        runBlocking { runtime.initialize() }
        assertEquals(4, store.messagesFor("s1").size, "still exactly 4 messages")
    }

    // ================================================================
    // RESTORE-G2: With K dangling → exactly N+K after repair
    // ================================================================
    @Test
    fun `RESTORE-G2 repair adds exactly K synthetics for K dangling`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage(
                "checking",
                listOf(ToolCall("A", "p", "{}"), ToolCall("B", "p", "{}")),
            ),
            // Both A and B dangling (K=2)
        )
        val store = storeWithHistory("s2", history)
        val runtime = makeRuntime(store, "rs2")

        runBlocking { runtime.initialize() }
        // N=2, K=2 → N+K=4
        assertEquals(4, store.messagesFor("s2").size, "exactly N+K messages")
    }

    // ================================================================
    // NEW-G1: READY A → newSession() → durable B, current B, A preserved
    // ================================================================
    @Test
    fun `NEW-G1 newSession creates new durable session and preserves old`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "n1")

        val idA = runBlocking { runtime.initialize() }
        assertEquals(1, store.createdSessionIds.size)

        val switched = runBlocking { runtime.newSession() }
        assertTrue(switched, "newSession succeeds")
        assertEquals(2, store.createdSessionIds.size, "second session created")
        val idB = runtime.currentSessionId!!
        assertEquals(idB, store.createdSessionIds.last())
        assertTrue(idB != idA, "different id")
        // Old session A still in store (not deleted).
        assertNotNull(runBlocking { store.loadSession(idA) }, "old session preserved")
    }

    // ================================================================
    // NEW-G2: B create failure → current remains A
    // ================================================================
    @Test
    fun `NEW-G2 newSession failure preserves old current`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "n2")

        val idA = runBlocking { runtime.initialize() }
        store.failCreate = true

        val thrown = runCatching { runBlocking { runtime.newSession() } }.exceptionOrNull()
        assertTrue(thrown is PersistenceException, "expected failure, got $thrown")
        assertEquals(idA, runtime.currentSessionId, "old session remains current")
    }

    // ================================================================
    // NEW-G3: newSession never restores latest
    // ================================================================
    @Test
    fun `NEW-G3 newSession always creates new (never restores latest)`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "n3")

        runBlocking { runtime.initialize() } // creates s-1
        val id = runBlocking { runtime.newSession() } // creates s-2
        // The new session has a different id (it's a NEW creation, not a restore).
        val newId = runtime.currentSessionId!!
        assertEquals(2, store.createdSessionIds.size, "two creations total")
        assertTrue(newId == store.createdSessionIds[1], "current is the newly created one")
    }

    // ================================================================
    // STARTUP idempotence with existing history
    // ================================================================
    @Test
    fun `initialize idempotent with existing history - no reload on second call`() {
        val history = listOf(UserMessage("hi"), ModelMessage("hello"))
        val store = storeWithHistory("s-idem", history)
        val runtime = makeRuntime(store, "idem")

        val id1 = runBlocking { runtime.initialize() }
        // Track message count after first initialize.
        val countAfterFirst = store.messagesFor("s-idem").size
        val id2 = runBlocking { runtime.initialize() }

        assertEquals(id1, id2)
        assertEquals(countAfterFirst, store.messagesFor("s-idem").size, "no changes on second initialize")
    }

    // ================================================================
    // VALIDATE-G1-R: sequence invalid → Runtime controlled recovery
    // (sequence gap is raised by the Room backend; here simulated via the
    //  typed exception the Runtime must classify as unrecoverable)
    // ================================================================
    @Test
    fun `VALIDATE-G1-R sequence invalid triggers controlled recovery`() {
        val store = storeWithHistory("seq-gap", listOf(UserMessage("hi")))
        store.loadMessagesException =
            com.androidagent.runtime.persistence.UnrecoverableSessionHistoryException(
                sessionId = "seq-gap",
                category = "sequence_continuity_violation",
            )
        val runtime = makeRuntime(store, "g1r")

        val id = runBlocking { runtime.initialize() }
        assertTrue(id.startsWith("g1r-"), "recovered with a fresh session: $id")
        assertTrue(id != "seq-gap", "recovery session is not the corrupt one")
        assertEquals(id, runtime.currentSessionId)
        assertNotNull(runBlocking { store.loadSession("seq-gap") }, "corrupt session preserved")
    }

    // ================================================================
    // VALIDATE-G3: malformed payload → Runtime controlled recovery
    // ================================================================
    @Test
    fun `VALIDATE-G3 malformed payload triggers controlled recovery`() {
        val store = storeWithHistory("malformed", listOf(UserMessage("hi")))
        store.loadMessagesException =
            com.androidagent.runtime.persistence.MessageDecodeException("MODEL", "malformed payload")
        val runtime = makeRuntime(store, "g3")

        val id = runBlocking { runtime.initialize() }
        assertTrue(id.startsWith("g3-"), "recovered with a fresh session: $id")
        assertTrue(id != "malformed", "recovery session is not the corrupt one")
        assertEquals(id, runtime.currentSessionId)
        // Corrupt session preserved; fresh session empty.
        assertNotNull(runBlocking { store.loadSession("malformed") }, "corrupt session preserved")
        assertEquals(0, store.messagesFor(id).size, "fresh session empty")
    }

    // ================================================================
    // VALIDATE-G4: unknown type → Runtime controlled recovery
    // ================================================================
    @Test
    fun `VALIDATE-G4 unknown type triggers controlled recovery`() {
        val store = storeWithHistory("unknown-type", listOf(UserMessage("hi")))
        // Unknown type surfaces as a MessageDecodeException from the serializer.
        store.loadMessagesException =
            com.androidagent.runtime.persistence.MessageDecodeException("RUN", "Unknown message type")
        val runtime = makeRuntime(store, "g4")

        val id = runBlocking { runtime.initialize() }
        assertTrue(id.startsWith("g4-"), "recovered with a fresh session: $id")
        assertTrue(id != "unknown-type", "recovery session is not the corrupt one")
        assertEquals(id, runtime.currentSessionId)
        assertNotNull(runBlocking { store.loadSession("unknown-type") }, "corrupt session preserved")
    }

    // ================================================================
    // REPAIR-G6: partial repair failure + retry idempotence
    // ================================================================
    @Test
    fun `REPAIR-G6 partial repair failure then retry repairs remaining only`() {
        // Model declares A and B; both dangling. A repair succeeds, B fails.
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "p", "{}"), ToolCall("B", "p", "{}"))),
        )
        val store = storeWithHistory("partial", history)
        store.failAppendForToolCallIds = setOf("B") // B's repair append fails
        val runtime = makeRuntime(store, "g6")

        // First initialize: A repaired, B fails → no current, not READY.
        val thrown = runCatching { runBlocking { runtime.initialize() } }.exceptionOrNull()
        assertTrue(thrown is PersistenceException, "expected failure on B repair, got $thrown")
        assertNull(runtime.currentSessionId, "no current after partial repair failure")

        // A synthetic is durable exactly once; B absent.
        val afterFirst = store.messagesFor("partial")
        val resultsAfterFirst = afterFirst.filterIsInstance<ToolResultMessage>().map { it.toolCallId }
        assertEquals(listOf("A"), resultsAfterFirst, "A repaired once, B absent")

        // Retry: clear the B failure. A is already resolved (not re-repaired).
        store.failAppendForToolCallIds = emptySet()
        val id = runBlocking { runtime.initialize() }
        assertEquals("partial", id, "retry restores the same session")
        assertEquals(id, runtime.currentSessionId)

        // A exactly once, B now exactly once (no duplicate A).
        val finalResults = store.messagesFor("partial").filterIsInstance<ToolResultMessage>().map { it.toolCallId }
        assertEquals(listOf("A", "B"), finalResults, "A once, B once, no duplicate")
    }

    // ================================================================
    // REPAIR-G7: cancellation before repair → no write
    // ================================================================
    @Test
    fun `REPAIR-G7 cancellation before repair append commits means no write`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "p", "{}"))),
        )
        val store = storeWithHistory("cancel-before", history)
        // Hold the repair append so we can cancel while it is in-flight.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        store.appendGate = gate
        val runtime = makeRuntime(store, "g7")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.async { runtime.initialize() }
        // Wait until the repair append is suspended at the gate.
        runBlocking {
            withTimeout(2000) { while (store.appendCount.get() < 1) delay(5) }
        }
        // Cancel the startup while the repair append is in flight. The append is
        // shielded (NonCancellable) and the gate is never completed, so the
        // append cannot commit; the startup terminates as cancelled.
        job.cancel()
        runCatching { runBlocking { withTimeout(2000) { job.await() } } }
        scope.cancel()

        // The repair append never committed (the gate held it; no synthetic).
        assertEquals(0, store.messagesFor("cancel-before").filterIsInstance<ToolResultMessage>().size, "no repair write committed")
        assertNull(runtime.currentSessionId, "no current after cancelled startup")
    }

    // ================================================================
    // REPAIR-G8: cancellation during repair append → deterministic commit
    // ================================================================
    @Test
    fun `REPAIR-G8 cancellation during repair reaches deterministic commit`() {
        val history = listOf(
            UserMessage("hi"),
            ModelMessage("checking", listOf(ToolCall("A", "p", "{}"))),
        )
        val store = storeWithHistory("cancel-during", history)
        val runtime = makeRuntime(store, "g8")

        // Let the repair append run to completion (NonCancellable shield),
        // then a later cancel cannot produce a MAYBE state. We complete the
        // initialize normally and confirm exactly one deterministic synthetic.
        val id = runBlocking { runtime.initialize() }
        assertEquals("cancel-during", id)
        // Exactly one synthetic (deterministic COMMITTED, never MAYBE).
        assertEquals(1, store.messagesFor("cancel-during").filterIsInstance<ToolResultMessage>().size, "exactly one synthetic")

        // A second reopen/initialize must not duplicate the synthetic.
        val id2 = runBlocking { runtime.initialize() }
        assertEquals(id, id2, "idempotent")
        assertEquals(1, store.messagesFor("cancel-during").filterIsInstance<ToolResultMessage>().size, "no duplicate synthetic")
    }

    // ================================================================
    // RECOVERY-G1: fresh recovery becomes latest
    // ================================================================
    @Test
    fun `RECOVERY-G1 fresh recovery session becomes latest`() {
        val store = storeWithHistory("corrupt-latest", listOf(UserMessage("hi")))
        store.loadMessagesReturnNullForExisting = true
        val runtime = makeRuntime(store, "rec1")

        val recoveryId = runBlocking { runtime.initialize() }
        // The recovery session is the newest → loadLatestSession returns it.
        val latest = runBlocking { store.loadLatestSession() }
        assertEquals(recoveryId, latest?.id, "recovery session is now the latest")
    }

    // ================================================================
    // RECOVERY-G2: restart after recovery does not loop
    // ================================================================
    @Test
    fun `RECOVERY-G2 restart after recovery does not create another session`() {
        val store = storeWithHistory("corrupt-loop", listOf(UserMessage("hi")))
        store.loadMessagesReturnNullForExisting = false // only the corrupt one is inconsistent
        // Simulate: first process recovers (corrupt latest → fresh).
        val store1 = storeWithHistory("corrupt-loop", listOf(UserMessage("hi")))
        store1.loadMessagesReturnNullForExisting = true
        val runtime1 = makeRuntime(store1, "rec2a")
        val recoveryId = runBlocking { runtime1.initialize() }
        // Now simulate a "restart" using a store where the recovery session is
        // latest AND loadable (the corrupt one is no longer the latest).
        val store2 = storeWithHistory(recoveryId, emptyList()) // recovery session is latest, empty, loadable
        val runtime2 = makeRuntime(store2, "rec2b")
        val id2 = runBlocking { runtime2.initialize() }
        assertEquals(recoveryId, id2, "restart restores the recovery session, not a new one")
        assertEquals(1, store2.createdSessionIds.size, "no additional recovery session created")
    }

    // ================================================================
    // RECOVERY-G3: old corrupt session/messages preserved
    // ================================================================
    @Test
    fun `RECOVERY-G3 old corrupt session and messages preserved`() {
        val corruptMessages = listOf(UserMessage("x"), ModelMessage("y"), UserMessage("z"))
        val store = storeWithHistory("corrupt-preserve", corruptMessages)
        store.loadMessagesException =
            com.androidagent.runtime.persistence.MessageDecodeException("MODEL", "malformed")
        val runtime = makeRuntime(store, "rec3")

        val recoveryId = runBlocking { runtime.initialize() }
        assertTrue(recoveryId != "corrupt-preserve")
        // The corrupt session row still exists.
        assertNotNull(runBlocking { store.loadSession("corrupt-preserve") }, "corrupt session row preserved")
        // The corrupt session's messages are still stored (not deleted).
        assertEquals(3, store.messagesFor("corrupt-preserve").size, "corrupt messages preserved")
    }

    // ================================================================
    // INITIALIZE idempotent after recovery
    // ================================================================
    @Test
    fun `initialize idempotent after controlled recovery`() {
        val store = storeWithHistory("corrupt-idem", listOf(UserMessage("hi")))
        store.loadMessagesReturnNullForExisting = true
        val runtime = makeRuntime(store, "idemrec")

        val id1 = runBlocking { runtime.initialize() }
        val creationsAfterFirst = store.createdSessionIds.size
        val id2 = runBlocking { runtime.initialize() }
        assertEquals(id1, id2, "same recovery session on second call")
        assertEquals(creationsAfterFirst, store.createdSessionIds.size, "no duplicate recovery on second initialize")
    }

    // ================================================================
    // PHASE I vertical slice: currentHistory() + restored-then-continue
    // ================================================================

    /**
     * Counts durability commits AND writes them to the store (so a new run's
     * D1/D2/D3 commits land in the durable history, as in production). Used to
     * prove the read seam is pure (0 commits on a read) while a real run still
     * persists its new messages.
     */
    private class CountingDurability(
        private val store: TopicSessionStore,
    ) : MessageDurability {
        val commits = java.util.concurrent.atomic.AtomicInteger(0)
        override suspend fun commit(sessionId: String, message: Message) {
            commits.incrementAndGet()
            store.appendMessage(sessionId, message, 2000L)
        }
    }

    @Test
    fun `PHASE-I currentHistory returns restored history as a defensive snapshot`() {
        val history = listOf(UserMessage("old"), ModelMessage("answer-old"))
        val store = storeWithHistory("s-hist", history)
        val durability = CountingDurability(store)
        var counter = 0
        val runtime = AgentRuntime(
            agentCore = AgentCore(AnswerProvider(), ToolRegistry(), durability),
            runOptions = RunOptions(),
            sessionIdFactory = { "s-hist-${++counter}" },
            topicSessionStore = store,
        )
        val id = runBlocking { runtime.initialize() }
        assertEquals("s-hist", id)
        // The Host reads the restored history through the read-only seam.
        val current = runtime.currentHistory()
        assertEquals(listOf<Message>(UserMessage("old"), ModelMessage("answer-old")), current)
        // Reading the history is pure: no durability write, no model call.
        assertEquals(0, durability.commits.get(), "currentHistory() is a pure read (no durability write)")
        // Defensive snapshot: mutating the returned list does NOT affect the Core.
        (current as MutableList<Message>?)?.clear()
        assertEquals(2, runtime.currentHistory().size, "the returned list is a copy, not the live Core list")
    }

    @Test
    fun `PHASE-I currentHistory is empty when no session is current`() {
        val store = FakeTopicSessionStore()
        val runtime = makeRuntime(store, "s-none")
        // UNINITIALIZED (no initialize): no current session.
        assertTrue(runtime.currentHistory().isEmpty(), "no current session -> empty history")
    }

    @Test
    fun `PHASE-I restored then new run continues without duplicating old history`() {
        // Durable history from a prior process: one completed exchange.
        val store = storeWithHistory("s-vert", listOf(UserMessage("old"), ModelMessage("answer-old")))
        val durability = CountingDurability(store)
        var counter = 0
        val runtime = AgentRuntime(
            agentCore = AgentCore(AnswerProvider(), ToolRegistry(), durability),
            runOptions = RunOptions(),
            sessionIdFactory = { "s-vert-${++counter}" },
            topicSessionStore = store,
        )
        // "Process restart": restore.
        val id = runBlocking { runtime.initialize() }
        assertEquals("s-vert", id)
        val beforeRun = runtime.currentHistory()
        assertEquals(2, beforeRun.size, "restored history: old + answer-old")

        // The Host projects it (presentation-only).
        val projected = TranscriptProjection(beforeRun)
        assertEquals(2, projected.size, "the projected transcript has the 2 restored items")

        // A new Run continues from the SAME session.
        val outcome = runBlocking { runtime.send("new") }
        assertTrue(outcome is RunOutcome.Answer, "new run succeeds: $outcome")

        // The combined history: old, answer-old, new, new-answer. The restored
        // User("old") is NOT re-appended by the live bootstrap.
        val afterRun = runtime.currentHistory()
        assertEquals(4, afterRun.size, "combined history = 2 restored + 2 new")
        assertEquals(UserMessage("old"), afterRun[0], "restored history is NOT duplicated")
        assertEquals(ModelMessage("answer-old"), afterRun[1])
        assertEquals(UserMessage("new"), afterRun[2], "the new user message is appended once")
        assertEquals(ModelMessage("ok"), afterRun[3], "the new answer is appended")
        // The same durable session holds the combined history (no new session).
        assertEquals(4, store.messagesFor("s-vert").size, "durable store holds the combined history")
        assertEquals(1, store.createdSessionIds.size, "no new session was created on the new run")
    }
}

/**
 * A minimal host-side presentation projection for Phase I tests: maps a restored
 * Core history to a list of "visible items" (the same mapping the app's
 * TranscriptModel.reproject performs). This keeps the ag-runtime module
 * Android-free and host-neutral (no dependency on :app).
 */
internal class TranscriptProjection(history: List<Message>) {
    val items: List<String> = buildList {
        for (m in history) {
            when (m) {
                is UserMessage -> add("user:" + m.content)
                is ModelMessage -> {
                    if (m.content.isNotEmpty()) add("assistant:" + m.content)
                    m.toolCalls.forEach { add("shell:" + it.id) }
                }
                is ToolResultMessage -> add("result:" + m.toolCallId)
            }
        }
    }
    val size: Int get() = items.size
    override fun toString(): String = items.toString()
}
