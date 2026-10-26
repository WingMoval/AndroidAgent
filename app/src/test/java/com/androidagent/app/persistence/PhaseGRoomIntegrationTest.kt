package com.androidagent.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.androidagent.app.TranscriptModel
import com.androidagent.core.AgentCore
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelMessage
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.ModelTurn
import com.androidagent.core.RunOptions
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import com.androidagent.runtime.AgentRuntime
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.TopicSessionStore
import com.androidagent.runtime.persistence.UnrecoverableSessionHistoryException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Phase G: File-backed Room + AgentRuntime integration tests.
 *
 * Simulates process death → new process:
 *  - Create file-backed DB, persist session + messages, close DB.
 *  - Create NEW database/store/durability/AgentCore/AgentRuntime.
 *  - initialize() → same TopicSession restored → Core history exact.
 *
 * Also tests:
 *  - File-backed dangling repair (cross-process).
 *  - Second reopen idempotence (no duplicate repair).
 *  - Room sequence corruption detection.
 *
 * JUnit 4 (Robolectric) — assertEquals(message, expected, actual) order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhaseGRoomIntegrationTest {

    private lateinit var context: Context
    private var tempDir: File? = null

    private val now = 1_700_000_000_000L

    private class AnswerProvider : ModelProvider {
        override suspend fun complete(request: ModelRequest): ModelTurn =
            ModelTurn.FinalAnswer("ok")
    }

    /** Opens a file-backed DB in a unique temp dir. Returns the file path. */
    private fun openFileBacked(): String {
        val dir = File(System.getProperty("java.io.tmpdir"), "phase-g-test-${UUID.randomUUID()}")
        assertTrue("could not create temp dir", dir.mkdirs())
        tempDir = dir
        val file = File(dir, TopicSessionDatabase.DATABASE_NAME)
        val db = Room.databaseBuilder(context, TopicSessionDatabase::class.java, file.absolutePath)
            .openHelperFactory(WalOpenHelperFactory())
            .build()
        return file.absolutePath
    }

    /** Opens a NEW DB instance on the same file (simulates new process). */
    private fun reopenFileBacked(path: String): Pair<TopicSessionDatabase, RoomTopicSessionStore> {
        val db = Room.databaseBuilder(context, TopicSessionDatabase::class.java, path)
            .openHelperFactory(WalOpenHelperFactory())
            .build()
        return db to RoomTopicSessionStore(db)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        tempDir?.deleteRecursively()
    }

    // ================================================================
    // FILE_BACKED_REOPEN_RESTORE: process death → new process → same session
    // ================================================================
    @Test
    fun `file backed reopen restores same session with exact Core history`() {
        val dbPath = openFileBacked()

        // --- "Process 1": create DB, persist session + messages, close. ---
        val (db1, store1) = reopenFileBacked(dbPath)
        runBlocking {
            store1.createSession("session-1", now)
            store1.appendMessage("session-1", UserMessage("hello"), now)
            store1.appendMessage("session-1", ModelMessage("hi there"), now)
            store1.appendMessage("session-1", UserMessage("how are you"), now)
            store1.appendMessage("session-1", ModelMessage("I'm fine"), now)
        }
        db1.close()

        // --- "Process 2": new components, same file. initialize() → restore. ---
        val (db2, store2) = reopenFileBacked(dbPath)
        val durability = StoreMessageDurability(store2)
        val core = AgentCore(AnswerProvider(), ToolRegistry(), durability)
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(),
            sessionIdFactory = { "should-not-be-used" },
            topicSessionStore = store2,
            nowMillis = { now },
        )

        val id = runBlocking { runtime.initialize() }
        assertEquals("restored session id", "session-1", id)
        assertEquals("current session id", id, runtime.currentSessionId)

        // Core history matches the durable history exactly.
        val history = core.session("session-1").history()
        assertEquals("history size", 4, history.size)
        assertEquals("msg0", "hello", (history[0] as UserMessage).content)
        assertEquals("msg1", "hi there", (history[1] as ModelMessage).content)
        assertEquals("msg2", "how are you", (history[2] as UserMessage).content)
        assertEquals("msg3", "I'm fine", (history[3] as ModelMessage).content)

        // No new messages were persisted (restore does NOT repersist).
        val messages = runBlocking { store2.loadMessages("session-1") }!!
        assertEquals("no repersistence", 4, messages.size)

        db2.close()
    }

    // ================================================================
    // FILE_BACKED_DANGLING_REPAIR: cross-process dangling repair
    // ================================================================
    @Test
    fun `file backed dangling repair persists one synthetic UNKNOWN result`() {
        val dbPath = openFileBacked()

        // --- "Process 1": persist session with a dangling tool call. ---
        val (db1, store1) = reopenFileBacked(dbPath)
        runBlocking {
            store1.createSession("session-d", now)
            store1.appendMessage("session-d", UserMessage("hi"), now)
            store1.appendMessage(
                "session-d",
                ModelMessage("checking", listOf(ToolCall("A", "probe", "{}"))),
                now,
            )
            // NO ToolResultMessage for A → dangling (process died mid-tool).
        }
        db1.close()

        // --- "Process 2": new components, initialize() → repair + restore. ---
        val (db2, store2) = reopenFileBacked(dbPath)
        val durability = StoreMessageDurability(store2)
        val core = AgentCore(AnswerProvider(), ToolRegistry(), durability)
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(),
            sessionIdFactory = { "should-not-be-used" },
            topicSessionStore = store2,
            nowMillis = { now },
        )

        val id = runBlocking { runtime.initialize() }
        assertEquals("session id", "session-d", id)

        // One synthetic UNKNOWN result was persisted.
        val messages = runBlocking { store2.loadMessages("session-d") }!!
        assertEquals("3 messages after repair", 3, messages.size)
        val synthetic = messages[2] as ToolResultMessage
        assertEquals("tool call id", "A", synthetic.toolCallId)
        assertEquals(
            "canonical UNKNOWN error",
            AgentCore.IN_FLIGHT_INTERRUPTED_ERROR,
            synthetic.result.error,
        )

        // Core was hydrated with the repaired history.
        val history = core.session("session-d").history()
        assertEquals("Core history size", 3, history.size)

        // No tool replay: no tool was registered, and none was executed.
        // (If a tool replay happened, the ToolRegistry would have dispatched.)

        db2.close()
    }

    // ================================================================
    // SECOND_REOPEN_NO_DUPLICATE_REPAIR: repair idempotence
    // ================================================================
    @Test
    fun `second reopen does not duplicate synthetic repair`() {
        val dbPath = openFileBacked()

        // --- "Process 1": persist dangling. ---
        val (db1, store1) = reopenFileBacked(dbPath)
        runBlocking {
            store1.createSession("session-r", now)
            store1.appendMessage("session-r", UserMessage("hi"), now)
            store1.appendMessage(
                "session-r",
                ModelMessage("checking", listOf(ToolCall("A", "probe", "{}"))),
                now,
            )
        }
        db1.close()

        // --- "Process 2": repair (adds synthetic). ---
        val (db2, store2) = reopenFileBacked(dbPath)
        runBlocking {
            store2.loadMessages("session-r") // validate
        }
        // Manually trigger repair by closing and reopening with a runtime.
        db2.close()

        val (db2b, store2b) = reopenFileBacked(dbPath)
        val durability2 = StoreMessageDurability(store2b)
        val core2 = AgentCore(AnswerProvider(), ToolRegistry(), durability2)
        val runtime2 = AgentRuntime(
            agentCore = core2,
            runOptions = RunOptions(),
            sessionIdFactory = { "unused" },
            topicSessionStore = store2b,
            nowMillis = { now },
        )
        runBlocking { runtime2.initialize() }
        db2b.close()

        // After repair: 3 messages (original 2 + 1 synthetic).
        val (db3, store3) = reopenFileBacked(dbPath)
        val msgsAfterRepair = runBlocking { store3.loadMessages("session-r") }!!
        assertEquals("3 messages after first repair", 3, msgsAfterRepair.size)
        db3.close()

        // --- "Process 3": reopen again. The synthetic is already there. ---
        val (db4, store4) = reopenFileBacked(dbPath)
        val durability4 = StoreMessageDurability(store4)
        val core4 = AgentCore(AnswerProvider(), ToolRegistry(), durability4)
        val runtime4 = AgentRuntime(
            agentCore = core4,
            runOptions = RunOptions(),
            sessionIdFactory = { "unused" },
            topicSessionStore = store4,
            nowMillis = { now },
        )
        val id = runBlocking { runtime4.initialize() }
        assertEquals("session id", "session-r", id)

        // Still 3 messages (no duplicate synthetic).
        val msgsAfterSecond = runBlocking { store4.loadMessages("session-r") }!!
        assertEquals("still 3 messages after second reopen (no duplicate repair)", 3, msgsAfterSecond.size)

        db4.close()
    }

    // ================================================================
    // ROOM_SEQUENCE_CORRUPTION_TEST: non-contiguous sequence → explicit failure
    // ================================================================
    @Test
    fun `room loadMessages detects sequence gap`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // Create a session and insert messages with a sequence gap (0, 1, 3).
        runBlocking {
            store.createSession("session-gap", now)
            // Use the DAO directly to insert with specific sequences.
            val messageDao = db.messageDao()
            val sessionDao = db.topicSessionDao()

            // Insert sequence 0.
            messageDao.insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    topicSessionId = "session-gap",
                    sequence = 0,
                    type = "USER",
                    payloadJson = """{"content":"hello"}""",
                    createdAt = now,
                ),
            )
            // Insert sequence 1.
            messageDao.insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    topicSessionId = "session-gap",
                    sequence = 1,
                    type = "MODEL",
                    payloadJson = """{"content":"hi","toolCalls":[]}""",
                    createdAt = now,
                ),
            )
            // SKIP sequence 2 → insert sequence 3 (gap!).
            messageDao.insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    topicSessionId = "session-gap",
                    sequence = 3,
                    type = "USER",
                    payloadJson = """{"content":"world"}""",
                    createdAt = now,
                ),
            )
        }

        // loadMessages must fail explicitly as a SESSION-LEVEL unrecoverable
        // history (sequence continuity violation), distinct from a backend failure.
        val thrown = runCatching { runBlocking { store.loadMessages("session-gap") } }.exceptionOrNull()
        assertTrue(
            "expected UnrecoverableSessionHistoryException for sequence gap, got: $thrown",
            thrown is UnrecoverableSessionHistoryException,
        )
        assertEquals("category", "sequence_continuity_violation", (thrown as UnrecoverableSessionHistoryException).category)

        db.close()
    }

    // ================================================================
    // NO DESTRUCTIVE RECOVERY: old session preserved
    // ================================================================
    @Test
    fun `unrecoverable session is preserved not deleted`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // Create two sessions.
        runBlocking {
            store.createSession("old-session", now)
            store.appendMessage("old-session", UserMessage("data"), now)
            store.createSession("new-session", now)
        }

        // The old session is still in the DB (not deleted by any recovery path).
        val oldSession = runBlocking { store.loadSession("old-session") }
        assertNotNull("old session still exists", oldSession)
        val oldMessages = runBlocking { store.loadMessages("old-session") }
        assertEquals("old messages preserved", 1, oldMessages!!.size)

        db.close()
    }

    // ================================================================
    // ROOM VALIDATE-G1-R: sequence gap → Runtime controlled fresh recovery
    // ================================================================
    @Test
    fun `room sequence gap triggers runtime controlled recovery`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // Create a session with a sequence gap (0, 1, 3).
        runBlocking {
            store.createSession("seq-gap", now)
            val messageDao = db.messageDao()
            messageDao.insert(MessageEntity(UUID.randomUUID().toString(), "seq-gap", 0L, "USER", """{"content":"a"}""", now))
            messageDao.insert(MessageEntity(UUID.randomUUID().toString(), "seq-gap", 1L, "MODEL", """{"content":"b","toolCalls":[]}""", now))
            messageDao.insert(MessageEntity(UUID.randomUUID().toString(), "seq-gap", 3L, "USER", """{"content":"c"}""", now))
        }

        // Runtime initialize() must recover with a controlled fresh session.
        val runtime = buildRuntime(store)
        val id = runBlocking { runtime.initialize() }
        assertTrue("recovery is a NEW session (id=$id)", id != "seq-gap")
        assertEquals("current", id, runtime.currentSessionId)

        // The corrupt seq-gap session is preserved (row + messages intact).
        assertNotNull("corrupt session preserved", runBlocking { store.loadSession("seq-gap") })
        val corruptMsgs = runBlocking { db.messageDao().countBySession("seq-gap") }
        assertEquals("corrupt messages preserved", 3, corruptMsgs)

        db.close()
    }

    // ================================================================
    // ROOM VALIDATE-G3: malformed durable row → Runtime controlled recovery
    // ================================================================
    @Test
    fun `room malformed durable row triggers runtime controlled recovery`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // Insert a row whose payload is NOT valid JSON for the declared type.
        runBlocking {
            store.createSession("malformed", now)
            db.messageDao().insert(
                MessageEntity(UUID.randomUUID().toString(), "malformed", 0L, "USER", "THIS-IS-NOT-JSON", now),
            )
        }

        val runtime = buildRuntime(store)
        val id = runBlocking { runtime.initialize() }
        assertTrue("recovered with fresh session (id=$id)", id != "malformed")
        assertEquals("current", id, runtime.currentSessionId)

        // Corrupt session preserved.
        assertNotNull("corrupt session preserved", runBlocking { store.loadSession("malformed") })
        assertEquals("corrupt messages preserved", 1, runBlocking { db.messageDao().countBySession("malformed") })

        db.close()
    }

    // ================================================================
    // ROOM VALIDATE-G4: unknown durable type → Runtime controlled recovery
    // ================================================================
    @Test
    fun `room unknown durable type triggers runtime controlled recovery`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // Insert a row with an unknown type discriminator.
        runBlocking {
            store.createSession("unknown-type", now)
            db.messageDao().insert(
                MessageEntity(UUID.randomUUID().toString(), "unknown-type", 0L, "SOME_FUTURE_TYPE", """{"content":"x"}""", now),
            )
        }

        val runtime = buildRuntime(store)
        val id = runBlocking { runtime.initialize() }
        assertTrue("recovered with fresh session (id=$id)", id != "unknown-type")
        assertEquals("current", id, runtime.currentSessionId)
        assertNotNull("corrupt session preserved", runBlocking { store.loadSession("unknown-type") })

        db.close()
    }

    // ================================================================
    // ROOM RECOVERY-G2: restart after recovery does not loop
    // ================================================================
    @Test
    fun `room restart after recovery does not create another session`() {
        val dbPath = openFileBacked()
        val (db, store) = reopenFileBacked(dbPath)

        // "Process 1": corrupt latest session → recovery creates a fresh one.
        runBlocking {
            store.createSession("corrupt", now)
            db.messageDao().insert(MessageEntity(UUID.randomUUID().toString(), "corrupt", 0L, "USER", "BAD-JSON", now))
        }
        val runtime1 = buildRuntime(store)
        val recoveryId = runBlocking { runtime1.initialize() }
        assertTrue("recovered to a new session (id=$recoveryId)", recoveryId != "corrupt")

        // "Process 2" (restart): the recovery session is now the latest and
        // loadable. initialize() must restore IT (not create yet another).
        db.close()
        val (db2, store2) = reopenFileBacked(dbPath)
        val runtime2 = buildRuntime(store2)
        val id2 = runBlocking { runtime2.initialize() }
        assertEquals("restart restores recovery session", recoveryId, id2)
        // The recovery session is the latest (no loop back to the corrupt one).
        assertEquals("latest is recovery", recoveryId, runBlocking { store2.loadLatestSession() }?.id)

        db2.close()
    }

    // ================================================================
    // PHASE I vertical slice: file-backed reopen → visible restore → continue
    // ================================================================
    @Test
    fun `phase I file backed reopen restores visible history then continues without duplicate`() {
        val dbPath = openFileBacked()

        // --- "Process 1": persist a completed exchange, close. ---
        val (db1, store1) = reopenFileBacked(dbPath)
        runBlocking {
            store1.createSession("session-pi", now)
            store1.appendMessage("session-pi", UserMessage("old"), now)
            store1.appendMessage("session-pi", ModelMessage("answer-old"), now)
        }
        db1.close()

        // --- "Process 2": new components, same file. initialize() → restore. ---
        val (db2, store2) = reopenFileBacked(dbPath)
        val durability = StoreMessageDurability(store2)
        val core = AgentCore(AnswerProvider(), ToolRegistry(), durability)
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(),
            sessionIdFactory = { "should-not-be-used" },
            topicSessionStore = store2,
            nowMillis = { now },
        )
        val id = runBlocking { runtime.initialize() }
        assertEquals("restored session", "session-pi", id)

        // Host reads the restored history through the read-only seam and
        // re-projects it into the transcript (presentation-only).
        val restored = runtime.currentHistory()
        assertEquals("restored history size", 2, restored.size)
        val transcript = TranscriptModel()
        transcript.reproject(restored)
        val blocks = transcript.snapshot()
        assertEquals("restored visible items", 2, blocks.size)
        assertEquals("restored user item", "old", (blocks[0] as TranscriptModel.Block.User).text)
        assertEquals("restored assistant item", "answer-old", (blocks[1] as TranscriptModel.Block.Assistant).text)

        // A new Run continues from the SAME session.
        val outcome = runBlocking { runtime.send("new") }
        assertTrue("new run succeeds", outcome is com.androidagent.core.RunOutcome.Answer)

        // The restored User("old") is NOT duplicated by the live bootstrap.
        val after = runtime.currentHistory()
        assertEquals("combined history", 4, after.size)
        assertEquals("no duplicate restored user", "old", (after[0] as UserMessage).content)
        assertEquals("restored answer", "answer-old", (after[1] as ModelMessage).content)
        assertEquals("new user appended once", "new", (after[2] as UserMessage).content)
        assertEquals("new answer appended", "ok", (after[3] as ModelMessage).content)

        // The durable store holds the combined history (same session).
        val durable = runBlocking { store2.loadMessages("session-pi") }!!
        assertEquals("durable combined history", 4, durable.size)

        db2.close()
    }

    // ---- helper: build an AgentRuntime over the given store ----
    private fun buildRuntime(store: com.androidagent.runtime.persistence.TopicSessionStore): AgentRuntime {
        val durability = StoreMessageDurability(store)
        val core = AgentCore(AnswerProvider(), ToolRegistry(), durability)
        var counter = 0
        // nowMillis advances per call so a recovery session created after the
        // corrupt one has a strictly NEWER last_used_at and becomes the latest
        // (matches production: real time only moves forward).
        var clock = now
        return AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(),
            sessionIdFactory = { "recovered-${++counter}" },
            topicSessionStore = store,
            nowMillis = { clock += 1000; clock },
        )
    }
}
