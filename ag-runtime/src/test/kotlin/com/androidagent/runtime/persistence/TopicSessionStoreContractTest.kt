package com.androidagent.runtime.persistence

import com.androidagent.core.ModelMessage
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Phase C contract tests: TopicSessionStore semantics, verified against the
 * in-memory reference implementation.
 */
class TopicSessionStoreContractTest {

    private val now = 1_700_000_000_000L

    // STORE-T1: create session -> loadSession returns equivalent metadata.
    @Test
    fun `STORE-T1 create session returns equivalent metadata`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        val created = store.createSession("s1", now, model = "m1", provider = "p1", title = "t1")

        assertEquals("s1", created.id)
        assertEquals(now, created.createdAt)
        assertEquals(now, created.lastUsedAt)
        assertEquals("m1", created.model)
        assertEquals("p1", created.provider)
        assertEquals("t1", created.title)

        val loaded = store.loadSession("s1")
        assertNotNull(loaded)
        assertEquals(created, loaded)
    }

    // STORE-T2: multiple sessions -> latest by lastUsed.
    @Test
    fun `STORE-T2 loadLatestSession returns latest by lastUsed`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("old", now)
        store.createSession("newer", now + 1000)
        // Touch "old" later -> it becomes the latest.
        store.appendMessage("old", UserMessage("hi"), now + 5000)

        val latest = store.loadLatestSession()
        assertNotNull(latest)
        assertEquals("old", latest.id)
        assertEquals(now + 5000, latest.lastUsedAt)
    }

    // STORE-T3: append multiple messages -> loadMessages ordered.
    @Test
    fun `STORE-T3 loadMessages returns sequence ASC order`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("s1", now)
        store.appendMessage("s1", UserMessage("first"), now)
        store.appendMessage("s1", ModelMessage("second"), now + 1)
        store.appendMessage(
            "s1",
            ToolResultMessage("call-1", "tool", ToolResult(true, "out", null)),
            now + 2,
        )

        val loaded = store.loadMessages("s1")
        assertNotNull(loaded)
        assertContentEquals(
            listOf(
                UserMessage("first"),
                ModelMessage("second"),
                ToolResultMessage("call-1", "tool", ToolResult(true, "out", null)),
            ),
            loaded,
        )

        // Sequence assigned atomically by the store: 0, 1, 2.
        val records = store.records("s1")
        assertContentEquals(listOf(0L, 1L, 2L), records.map { it.sequence })
    }

    // STORE-T4: successful append -> lastUsed updated.
    @Test
    fun `STORE-T4 lastUsed updated only on successful append`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("s1", now)

        // Load alone does NOT update lastUsed.
        store.loadSession("s1")
        store.loadLatestSession()
        store.loadMessages("s1")
        assertEquals(now, store.loadSession("s1")!!.lastUsedAt)

        store.appendMessage("s1", UserMessage("hi"), now + 100)
        assertEquals(now + 100, store.loadSession("s1")!!.lastUsedAt)
    }

    // STORE-T5: independent sessions — messages do not cross.
    @Test
    fun `STORE-T5 messages do not cross sessions`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("a", now)
        store.createSession("b", now)
        store.appendMessage("a", UserMessage("in a"), now)
        store.appendMessage("b", UserMessage("in b"), now)

        val a = store.loadMessages("a")
        val b = store.loadMessages("b")
        assertContentEquals(listOf(UserMessage("in a")), a)
        assertContentEquals(listOf(UserMessage("in b")), b)
    }

    // STORE-T6: missing session -> well-defined result (null for loads,
    // exception for append).
    @Test
    fun `STORE-T6 missing session has well-defined results`() = runBlocking {
        val store = InMemoryTopicSessionStore()

        assertNull(store.loadSession("missing"))
        assertNull(store.loadMessages("missing"))

        assertFailsWith<SessionNotFoundException> {
            store.appendMessage("missing", UserMessage("hi"), now)
        }
        assertFailsWith<SessionNotFoundException> {
            store.updateModelProviderMetadata("missing", "m", "p")
        }
    }

    // appendMessage returns the committed representation with the assigned
    // sequence (APPEND_MESSAGE_RETURN_TYPE rationale: the caller needs the
    // store-assigned sequence for observability; the contract forbids
    // preallocation).
    @Test
    fun `appendMessage returns committed record with assigned sequence`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("s1", now)

        val r0 = store.appendMessage("s1", UserMessage("a"), now)
        val r1 = store.appendMessage("s1", UserMessage("b"), now + 1)

        assertEquals("s1", r0.topicSessionId)
        assertEquals(0L, r0.sequence)
        assertEquals(1L, r1.sequence)
        assertEquals("USER", r0.type)
        assertEquals(now, r0.createdAt)
        assertTrue(r0.id.isNotEmpty())
    }

    // updateModelProviderMetadata updates LAST_USED_MODEL without touching
    // lastUsedAt.
    @Test
    fun `updateModelProviderMetadata updates snapshot only`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("s1", now, model = "m1", provider = "p1")

        store.updateModelProviderMetadata("s1", "m2", "p2")
        val s = store.loadSession("s1")!!
        assertEquals("m2", s.model)
        assertEquals("p2", s.provider)
        assertEquals(now, s.lastUsedAt) // unchanged
    }

    // A ModelMessage with tool calls round-trips through the store (ordering
    // of multiple tool calls preserved).
    @Test
    fun `model message with multiple tool calls survives the store`() = runBlocking {
        val store = InMemoryTopicSessionStore()
        store.createSession("s1", now)
        val original = ModelMessage(
            content = "prose",
            toolCalls = listOf(
                ToolCall("c1", "t1", "{}"),
                ToolCall("c2", "t2", ""),
            ),
        )
        store.appendMessage("s1", original, now)

        val loaded = store.loadMessages("s1")
        assertContentEquals(listOf(original), loaded)
    }
}
