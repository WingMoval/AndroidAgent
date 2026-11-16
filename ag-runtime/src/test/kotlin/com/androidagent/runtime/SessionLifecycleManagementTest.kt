package com.androidagent.runtime

import com.androidagent.core.Message
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOutcome
import com.androidagent.core.ToolRegistry
import com.androidagent.core.UserMessage
import com.androidagent.runtime.persistence.SessionNotFoundException
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import com.androidagent.runtime.persistence.TitleOrigin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AG.SESSION-MANAGEMENT-UX-0 — Session lifecycle contract tests.
 *
 * The fundamental product rule: NO USER MESSAGE = NO SESSION.
 *  - A transient blank draft is NOT durable, NOT listed, has NO title.
 *  - The durable TopicSession MATERIALIZES on the first non-blank User Message,
 *    title = first message (origin = AUTO_FIRST_MESSAGE), persisted before
 *    model success.
 *  - The automatic title runs ONCE (later messages never change it).
 *  - A manual rename (USER_RENAMED) wins permanently.
 *  - Delete is ownership-scoped; deleting the current session enters a blank
 *    draft with NO replacement durable row.
 *
 * Real [AgentRuntime] + real [com.androidagent.core.AgentCore] + scripted
 * provider + a recording in-memory store.
 */
class SessionLifecycleManagementTest {

    /** A store that records every durable operation (for lifecycle assertions). */
    private class RecordingStore : TopicSessionStore {
        private val sessions = linkedMapOf<String, TopicSessionRecord>()
        private val msgCounts = linkedMapOf<String, Int>()
        private val messagesBySession = linkedMapOf<String, MutableList<Message>>()

        val createdIds = mutableListOf<String>()
        val createdTitles = mutableListOf<String?>()
        val createdOrigins = mutableListOf<TitleOrigin?>()
        val appended = mutableListOf<Pair<String, Message>>()

        override suspend fun createSession(
            id: String, nowMillis: Long, model: String?, provider: String?,
            title: String?, titleOrigin: TitleOrigin?,
        ): TopicSessionRecord {
            val rec = TopicSessionRecord(id, nowMillis, nowMillis, model, provider, title, titleOrigin)
            sessions[id] = rec
            msgCounts[id] = 0
            messagesBySession[id] = mutableListOf()
            createdIds.add(id); createdTitles.add(title); createdOrigins.add(titleOrigin)
            return rec
        }

        override suspend fun appendMessage(
            sessionId: String, message: Message, nowMillis: Long,
        ): com.androidagent.runtime.persistence.MessageRecord {
            check(sessionId in sessions) { "append to missing session $sessionId" }
            msgCounts[sessionId] = (msgCounts[sessionId] ?: 0) + 1
            messagesBySession[sessionId]!!.add(message)
            appended.add(sessionId to message)
            return com.androidagent.runtime.persistence.MessageRecord(
                "rec-${sessionId}-${msgCounts[sessionId]}", sessionId,
                (msgCounts[sessionId] ?: 0).toLong(), "USER", "{}", nowMillis,
            )
        }

        override suspend fun loadSession(id: String): TopicSessionRecord? = sessions[id]
        override suspend fun loadLatestSession(): TopicSessionRecord? = sessions.values.lastOrNull()
        override suspend fun loadMessages(sessionId: String): List<Message>? =
            if (sessionId in sessions) messagesBySession[sessionId].orEmpty().toList() else null
        override suspend fun updateModelProviderMetadata(sessionId: String, model: String?, provider: String?) {}
        override suspend fun listSessions(): List<TopicSessionRecord> = sessions.values.toList()

        override suspend fun renameSession(sessionId: String, newTitle: String): TopicSessionRecord {
            val existing = sessions[sessionId] ?: throw SessionNotFoundException(sessionId)
            val updated = existing.copy(title = newTitle, titleOrigin = TitleOrigin.USER_RENAMED)
            sessions[sessionId] = updated
            return updated
        }

        override suspend fun deleteSession(sessionId: String): Boolean {
            if (sessions.remove(sessionId) == null) return false
            msgCounts.remove(sessionId); messagesBySession.remove(sessionId); return true
        }

        override suspend fun countMessages(sessionId: String): Int = msgCounts[sessionId] ?: 0

        // ---- inspection ----
        fun titleOf(id: String): String? = sessions[id]?.title
        fun originOf(id: String): TitleOrigin? = sessions[id]?.titleOrigin
        fun sessionCount(): Int = sessions.size
        fun messagesFor(id: String): List<Message> = messagesBySession[id].orEmpty().toList()
    }

    private var idCounter = 0
    private fun runtimeOf(store: RecordingStore, provider: ModelProvider): AgentRuntime {
        idCounter = 0
        // Use a store-backed durability gate (like the production
        // StoreMessageDurability) so Core's D1/D2 actually persist to the store —
        // proving materialization persists the first user message independent of
        // model success.
        val durability = StoreDurability(store)
        return AgentRuntime(
            agentCore = com.androidagent.core.AgentCore(provider, ToolRegistry(), durability),
            runOptions = com.androidagent.core.RunOptions(),
            sessionIdFactory = { "s-${++idCounter}" },
            topicSessionStore = store,
        )
    }

    private fun answerProvider(vararg turns: String) =
        ScriptedProvider(turns.map { ModelTurn.FinalAnswer(it) }.toList())

    private suspend fun holdUntilBusy(rt: AgentRuntime) {
        withTimeout(10_000) { while (!rt.isBusy) delay(10) }
    }

    // ==================================================================
    // BLANK DRAFT
    // ==================================================================

    @Test
    fun `enterBlankDraft does not create a durable session`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        assertEquals(1, store.sessionCount())
        val entered = rt.enterBlankDraft()
        assertTrue(entered)
        assertEquals(1, store.sessionCount(), "blank draft must NOT create a durable row")
        assertNull(rt.currentSessionId, "blank draft has no durable current id")
        assertTrue(rt.inBlankDraft)
    }

    @Test
    fun `repeated enterBlankDraft creates no rows`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        assertTrue(rt.enterBlankDraft())
        assertTrue(rt.enterBlankDraft())
        assertTrue(rt.enterBlankDraft())
        assertEquals(1, store.sessionCount(), "repeated blank drafts must create no rows")
        assertNull(rt.currentSessionId)
        assertTrue(rt.inBlankDraft)
    }

    @Test
    fun `enterBlankDraft is rejected while busy`() = runBlocking {
        val store = RecordingStore()
        val gate = CompletableDeferred<Unit>()
        val provider = answerProvider("done").apply { this.gate = gate }
        val rt = runtimeOf(store, provider)
        rt.initialize()
        val idBefore = rt.currentSessionId

        val job = GlobalScope.async(Dispatchers.Default) { rt.send("A") }
        holdUntilBusy(rt)
        assertFalse(rt.enterBlankDraft(), "must be rejected while busy")
        assertEquals(idBefore, rt.currentSessionId, "identity unchanged")
        gate.complete(Unit); job.join()
    }

    // ==================================================================
    // MATERIALIZATION ON FIRST MESSAGE
    // ==================================================================

    @Test
    fun `first message materializes a durable session with first-message title`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("ok"))
        rt.initialize()
        rt.enterBlankDraft()
        assertNull(rt.currentSessionId)

        val firstMsg = "帮我检查当前 Session 自动命名是否正常"
        val outcome = rt.send(firstMsg)
        assertTrue(outcome is RunOutcome.Answer, "run should succeed: $outcome")

        val materialized = rt.currentSessionId
        assertNotNull(materialized)
        assertEquals(2, store.sessionCount(), "original + materialized = 2 durable sessions")
        assertEquals(firstMsg, store.titleOf(materialized!!), "title must be the first message")
        assertEquals(TitleOrigin.AUTO_FIRST_MESSAGE, store.originOf(materialized))
        // The first User Message was durably committed to the NEW session (the
        // store's appendMessage is the real durability gate Core calls).
        assertTrue(
            store.appended.any { (sid, msg) -> sid == materialized && msg is UserMessage && msg.content == firstMsg },
            "first user message must be persisted to the materialized session",
        )
        // The user message was committed to the materialized session, not the
        // original draft-previous session.
        val userMsgsOnOriginal = store.appended.count { (sid, msg) -> sid != materialized && msg is UserMessage }
        assertTrue(userMsgsOnOriginal == 0, "no user message may land on a non-materialized session")
    }

    @Test
    fun `session is created even if the model call fails`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, ThrowingProvider())
        rt.initialize()
        rt.enterBlankDraft()
        val firstMsg = "帮我检查 ADB 状态"

        val outcome = rt.send(firstMsg)
        assertTrue(outcome is RunOutcome.Failure, "model failure expected: $outcome")

        val materialized = rt.currentSessionId
        assertNotNull(materialized, "session must exist despite model failure")
        assertEquals(firstMsg, store.titleOf(materialized!!))
        // The user message was durably committed BEFORE the (failed) model call.
        assertTrue(
            store.appended.any { (sid, msg) -> sid == materialized && msg is UserMessage && msg.content == firstMsg },
            "user message must be durable despite model failure",
        )
    }

    @Test
    fun `blank send in a draft does not create a session`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        rt.enterBlankDraft()
        val before = store.sessionCount()
        val outcome = rt.send("   ")
        assertTrue(outcome is RunOutcome.Failure, "blank send must be rejected")
        assertEquals(before, store.sessionCount(), "blank send must not create a session")
        assertNull(rt.currentSessionId)
        assertTrue(rt.inBlankDraft)
    }

    // ==================================================================
    // TITLE ONCE + MANUAL RENAME WINS
    // ==================================================================

    @Test
    fun `title is stable after the second message`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a", "b"))
        rt.initialize()
        rt.enterBlankDraft()
        val first = "帮我检查当前 Session 自动命名是否正常"
        rt.send(first)
        val id = rt.currentSessionId!!
        rt.send("再检查一下这个名称有没有变化")
        assertEquals(first, store.titleOf(id), "second message must NOT change the title")
        assertEquals(TitleOrigin.AUTO_FIRST_MESSAGE, store.originOf(id))
    }

    @Test
    fun `manual rename persists and wins permanently`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a", "b", "c"))
        rt.initialize()
        rt.enterBlankDraft()
        rt.send("帮我检查 ADB 配对状态")
        val id = rt.currentSessionId!!
        assertEquals("帮我检查 ADB 配对状态", store.titleOf(id))

        assertTrue(rt.renameCurrentSession("ADB Pairing 调试"))
        assertEquals("ADB Pairing 调试", store.titleOf(id))
        assertEquals(TitleOrigin.USER_RENAMED, store.originOf(id))

        rt.send("继续测试")
        assertEquals("ADB Pairing 调试", store.titleOf(id),
            "manual rename must NOT be overridden by a later message")
        assertEquals(TitleOrigin.USER_RENAMED, store.originOf(id))
    }

    @Test
    fun `rename is rejected in a blank draft`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        rt.enterBlankDraft()
        assertFalse(rt.renameCurrentSession("x"), "rename rejected in blank draft (no current session)")
    }

    @Test
    fun `rename is rejected while busy`() = runBlocking {
        val store = RecordingStore()
        val gate = CompletableDeferred<Unit>()
        val provider = answerProvider("done").apply { this.gate = gate }
        val rt = runtimeOf(store, provider)
        rt.initialize()

        val job = GlobalScope.async(Dispatchers.Default) { rt.send("A") }
        holdUntilBusy(rt)
        assertFalse(rt.renameCurrentSession("x"), "rename rejected while busy")
        gate.complete(Unit); job.join()
    }

    // ==================================================================
    // DELETE
    // ==================================================================

    @Test
    fun `delete non-current session leaves current unchanged`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a", "b", "c"))
        rt.initialize()
        val idA = rt.currentSessionId!!
        rt.enterBlankDraft()
        rt.send("session B message")
        val idB = rt.currentSessionId!!
        // Switch back to A so A is current and B is historical.
        assertTrue(rt.switchSession(idA))
        assertEquals(idA, rt.currentSessionId)

        val result = rt.deleteSession(idB)
        assertTrue(result is AgentRuntime.DeleteResult.Deleted)
        assertEquals(idA, (result as AgentRuntime.DeleteResult.Deleted).currentAfter, "current must be unchanged")
        assertNull(store.loadSession(idB), "B must be deleted")
        assertNotNull(store.loadSession(idA), "A must survive")
    }

    @Test
    fun `delete current session enters a blank draft with no replacement row`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a", "b"))
        rt.initialize()
        val idA = rt.currentSessionId!!
        val before = store.sessionCount()

        val result = rt.deleteSession(idA)
        assertTrue(result is AgentRuntime.DeleteResult.Deleted)
        assertEquals(null, (result as AgentRuntime.DeleteResult.Deleted).currentAfter)
        assertNull(rt.currentSessionId, "no durable current after deleting it")
        assertTrue(rt.inBlankDraft, "must be in a blank draft")
        assertEquals(0, store.sessionCount(), "no replacement durable row created (list empty)")
        assertEquals(store.createdIds.size - 1, store.sessionCount(), "only the deleted row is gone")
        assertNull(store.loadSession(idA), "A must be gone")
    }

    @Test
    fun `delete last session leaves an empty list and blank draft`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        val only = rt.currentSessionId!!
        val result = rt.deleteSession(only)
        assertTrue(result is AgentRuntime.DeleteResult.Deleted)
        assertEquals(0, store.sessionCount(), "session list must be empty")
        assertTrue(rt.inBlankDraft)
        assertNull(rt.currentSessionId)
    }

    @Test
    fun `delete is rejected while busy`() = runBlocking {
        val store = RecordingStore()
        val gate = CompletableDeferred<Unit>()
        val provider = answerProvider("done").apply { this.gate = gate }
        val rt = runtimeOf(store, provider)
        rt.initialize()
        val id = rt.currentSessionId!!

        val job = GlobalScope.async(Dispatchers.Default) { rt.send("A") }
        holdUntilBusy(rt)
        val result = rt.deleteSession(id)
        assertTrue(result is AgentRuntime.DeleteResult.Rejected, "delete must be rejected while busy")
        assertEquals("busy", (result as AgentRuntime.DeleteResult.Rejected).reason)
        assertNotNull(store.loadSession(id), "session must survive a rejected delete")
        gate.complete(Unit); job.join()
    }

    @Test
    fun `delete of a missing session is rejected`() = runBlocking {
        val store = RecordingStore()
        val rt = runtimeOf(store, answerProvider("a"))
        rt.initialize()
        val result = rt.deleteSession("does-not-exist")
        assertTrue(result is AgentRuntime.DeleteResult.Rejected)
        assertEquals("session not found", (result as AgentRuntime.DeleteResult.Rejected).reason)
    }
}
