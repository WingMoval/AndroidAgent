package com.androidagent.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.androidagent.core.ModelMessage
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolResultMessage
import com.androidagent.core.UserMessage
import com.androidagent.runtime.persistence.MessageDecodeException
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.SessionNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Phase E Room integration tests (JVM via Robolectric 4.13 + JUnit 4).
 *
 * Robolectric 4.13 ships a JUnit 4 runner only; these run on the project's
 * JUnit Platform via the Vintage engine.
 *
 * These exercise the REAL Room schema + DAO + transaction code path against
 * real SQLite: schema shape, FK/cascade, unique constraint, atomic append
 * (sequence + insert + last_used in ONE transaction), explicit WAL at
 * runtime, file-backed reopen durability, serializer integration, and
 * concurrent-append sequence integrity.
 *
 * Test databases are isolated per test (unique in-memory name / temp file)
 * and closed in @After. No production wiring is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTopicSessionStoreTest {

    private lateinit var context: Context
    private lateinit var db: TopicSessionDatabase
    private lateinit var store: RoomTopicSessionStore
    private var tempDir: File? = null

    private val now = 1_700_000_000_000L

    /** Opens an isolated in-memory database (unique name per test). */
    private fun openInMemory() {
        db = Room.inMemoryDatabaseBuilder(context, TopicSessionDatabase::class.java)
            .openHelperFactory(WalOpenHelperFactory())
            .build()
        store = RoomTopicSessionStore(db)
    }

    /** Opens an isolated FILE-backed database in a unique temp dir. */
    private fun openFileBacked(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "topic-session-test-${UUID.randomUUID()}")
        assertTrue("could not create temp dir", dir.mkdirs())
        tempDir = dir
        val file = File(dir, TopicSessionDatabase.DATABASE_NAME)
        db = Room.databaseBuilder(context, TopicSessionDatabase::class.java, file.absolutePath)
            .openHelperFactory(WalOpenHelperFactory())
            .build()
        store = RoomTopicSessionStore(db)
        return file
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } catch (_: Exception) {
        }
        tempDir?.deleteRecursively()
        tempDir = null
    }

    private fun rawQuery(sql: String): List<List<String?>> = runBlocking {
        val database = db.openHelper.writableDatabase
        database.query(sql).use { cursor ->
            val rows = mutableListOf<List<String?>>()
            while (cursor.moveToNext()) {
                val row = mutableListOf<String?>()
                for (i in 0 until cursor.columnCount) row.add(cursor.getString(i))
                rows.add(row)
            }
            rows
        }
    }

    // ------------------------------------------------------------------
    // ROOM-T1: createSession -> loadSession equivalent
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T1 createSession then loadSession returns equivalent record`() {
        openInMemory()
        val created = runBlocking {
            store.createSession("s1", now, model = "m1", provider = "p1", title = "t1")
        }
        val loaded = runBlocking { store.loadSession("s1") }
        assertNotNull(loaded)
        assertEquals(created, loaded)
        assertEquals(created.createdAt, created.lastUsedAt)
    }

    // ------------------------------------------------------------------
    // ROOM-T2: multiple sessions -> loadLatestSession picks latest last_used
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T2 loadLatestSession picks the most recently used session`() {
        openInMemory()
        runBlocking {
            store.createSession("old", now)
            store.createSession("newer", now + 1000)
            // Touch "old" later -> it becomes the latest.
            store.appendMessage("old", UserMessage("hi"), now + 5000)
        }
        val latest = runBlocking { store.loadLatestSession() }
        assertNotNull(latest)
        assertEquals("old", latest!!.id)
        assertEquals(now + 5000, latest.lastUsedAt)
    }

    // ------------------------------------------------------------------
    // ROOM-T3: first append -> sequence = 0
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T3 first append assigns sequence 0`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        val record = runBlocking { store.appendMessage("s1", UserMessage("first"), now) }
        assertEquals(0L, record.sequence)
    }

    // ------------------------------------------------------------------
    // ROOM-T4: multiple appends -> contiguous 0..N-1
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T4 multiple appends are contiguous 0 through N-1`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        val n = 5
        val sequences = runBlocking {
            (0 until n).map { i ->
                store.appendMessage("s1", UserMessage("msg $i"), now + i).sequence
            }
        }
        assertEquals((0L until n).toList(), sequences)
    }

    // ------------------------------------------------------------------
    // ROOM-T5: loadMessages -> sequence ASC (DB ordering, not Kotlin sort)
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T5 loadMessages returns sequence ASC`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        runBlocking {
            repeat(4) { i -> store.appendMessage("s1", UserMessage("m$i"), now + i) }
        }
        val loaded = runBlocking { store.loadMessages("s1") }
        assertNotNull(loaded)
        assertEquals(4, loaded!!.size)
        assertEquals((0 until 4).map { UserMessage("m$it") }, loaded)
    }

    // ------------------------------------------------------------------
    // ROOM-T6: successful append -> last_used updated
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T6 successful append updates last_used_at`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        runBlocking { store.appendMessage("s1", UserMessage("x"), now + 42) }
        val s = runBlocking { store.loadSession("s1") }
        assertNotNull(s)
        assertEquals(now + 42, s!!.lastUsedAt)
    }

    // ------------------------------------------------------------------
    // ROOM-T7: failed append -> last_used unchanged (atomicity)
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T7 failed append does not change last_used_at`() {
        // Atomicity of the append transaction: if the transaction does NOT
        // commit (rollback), the message INSERT and the last_used_at UPDATE
        // must BOTH be undone.
        //
        // We verify this by driving the SAME two operations the store uses
        // (message INSERT + last_used UPDATE) inside a withTransaction block
        // and forcing a ROLLBACK. If either operation leaks past the rollback,
        // the append transaction is NOT atomic.
        openFileBacked()
        runBlocking { store.createSession("s1", now) }
        runBlocking { store.appendMessage("s1", UserMessage("ok"), now + 1) }
        val before = runBlocking { store.loadSession("s1") }!!.lastUsedAt
        val beforeCount = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }

        val dao = db.messageDao()
        var rolledBack: Throwable? = null
        try {
            runBlocking {
                db.withTransaction {
                    // Same operations the store performs, in the same order.
                    val seq = dao.nextSequence("s1")
                    dao.insert(
                        MessageEntity(
                            id = "rollback-probe",
                            topicSessionId = "s1",
                            sequence = seq,
                            type = "USER",
                            payloadJson = "{}",
                            createdAt = now + 99,
                        ),
                    )
                    dao.touchLastUsed("s1", now + 99)
                    // Force the transaction to roll back.
                    throw RuntimeException("forced rollback")
                }
            }
        } catch (e: Throwable) {
            rolledBack = e
        }
        assertTrue("transaction should have thrown", rolledBack != null)

        // After rollback: last_used_at is unchanged AND the message row is absent.
        val after = runBlocking { store.loadSession("s1") }!!.lastUsedAt
        assertEquals("last_used_at must not change on rollback", before, after)
        val afterCount = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }
        assertEquals("message row must not leak past rollback", beforeCount, afterCount)
        val probePresent = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE id = 'rollback-probe'").first().first()
        }
        assertEquals("rolled-back message must not be durable", "0", probePresent)

        // The store remains usable and the next real append works.
        runBlocking { store.appendMessage("s1", UserMessage("after"), now + 100) }
        val final = runBlocking { store.loadSession("s1") }!!.lastUsedAt
        assertEquals(now + 100, final)
    }

    // ------------------------------------------------------------------
    // ROOM-T8: missing session append -> SessionNotFoundException
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T8 append to missing session throws SessionNotFoundException`() {
        openInMemory()
        var thrown: Throwable? = null
        try {
            runBlocking { store.appendMessage("missing", UserMessage("x"), now) }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(
            "expected SessionNotFoundException, got $thrown",
            thrown is SessionNotFoundException,
        )
    }

    // ------------------------------------------------------------------
    // ROOM-T9: model/provider update persisted (last_used untouched)
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T9 updateModelProviderMetadata persists the snapshot`() {
        openInMemory()
        runBlocking { store.createSession("s1", now, model = "m1", provider = "p1") }
        runBlocking { store.updateModelProviderMetadata("s1", "m2", "p2") }
        val s = runBlocking { store.loadSession("s1") }
        assertNotNull(s)
        assertEquals("m2", s!!.model)
        assertEquals("p2", s.provider)
        assertEquals(now, s.lastUsedAt)
    }

    // ------------------------------------------------------------------
    // ROOM-T10: FK enforced at the database level
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T10 foreign key is enforced at the database level`() {
        openFileBacked()
        val fkOn = runBlocking { rawQuery("PRAGMA foreign_keys;").first().first() }
        assertEquals("1", fkOn)

        var thrown: Throwable? = null
        try {
            runBlocking {
                rawQuery(
                    "INSERT INTO messages (id, topic_session_id, sequence, type, payload_json, created_at) " +
                        "VALUES ('orphan', 'no-such-session', 0, 'USER', '{}', $now)",
                )
            }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("FK violation must fail the raw insert", thrown != null)

        var storeThrown: Throwable? = null
        try {
            runBlocking { store.appendMessage("no-such-session", UserMessage("x"), now) }
        } catch (e: Throwable) {
            storeThrown = e
        }
        assertTrue(
            "store must surface missing session as SessionNotFoundException, got $storeThrown",
            storeThrown is SessionNotFoundException,
        )
    }

    // ------------------------------------------------------------------
    // ROOM-T11: unique(session, sequence) enforced at the database level
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T11 unique session+sequence is enforced at the database level`() {
        openFileBacked()
        runBlocking { store.createSession("s1", now) }
        runBlocking { store.appendMessage("s1", UserMessage("a"), now) }
        var thrown: Throwable? = null
        try {
            runBlocking {
                rawQuery(
                    "INSERT INTO messages (id, topic_session_id, sequence, type, payload_json, created_at) " +
                        "VALUES ('dup', 's1', 0, 'USER', '{}', $now)",
                )
            }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("duplicate (session, sequence) must fail", thrown != null)
    }

    // ------------------------------------------------------------------
    // ROOM-T12: WAL explicitly active at RUNTIME (file-backed)
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T12 journal mode is wal at runtime`() {
        openFileBacked()
        runBlocking { store.createSession("s1", now) }
        val mode = runBlocking { journalModeOf(db) }
        assertTrue("journal_mode must be wal, got '$mode'", mode.equals("wal", ignoreCase = true))
    }

    // ------------------------------------------------------------------
    // ROOM-T13: read operations do NOT update last_used_at
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T13 reads do not touch last_used_at`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        runBlocking {
            store.loadSession("s1")
            store.loadLatestSession()
            store.loadMessages("s1")
        }
        val s = runBlocking { store.loadSession("s1") }
        assertNotNull(s)
        assertEquals(now, s!!.lastUsedAt)
    }

    // ------------------------------------------------------------------
    // ROOM-T14: cascade delete (schema-level FK ON DELETE CASCADE)
    // ------------------------------------------------------------------
    @Test
    fun `ROOM-T14 deleting a session cascades to its messages`() {
        openFileBacked()
        runBlocking { store.createSession("s1", now) }
        runBlocking { repeat(3) { i -> store.appendMessage("s1", UserMessage("m$i"), now + i) } }
        val before = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }
        assertEquals("3", before)

        runBlocking { rawQuery("DELETE FROM topic_sessions WHERE id = 's1'") }
        val after = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }
        assertEquals("0", after)
    }

    // ------------------------------------------------------------------
    // Serializer <-> Room integration round-trips
    // ------------------------------------------------------------------
    @Test
    fun `serializer and room round-trip all message types`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        val originals = listOf(
            UserMessage("user text 涓枃 馃殌"),
            ModelMessage("prose", listOf(ToolCall("c1", "t1", "{}"), ToolCall("c2", "t2", ""))),
            ToolResultMessage("c1", "t1", ToolResult(true, "output", null)),
            ToolResultMessage(
                "c3", "shell",
                ToolResult(false, "", "SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT: may or may not have taken effect"),
            ),
        )
        runBlocking { originals.forEach { store.appendMessage("s1", it, now) } }
        val loaded = runBlocking { store.loadMessages("s1") }
        assertNotNull(loaded)
        assertEquals(originals, loaded)
    }

    @Test
    fun `large canonical tool result is not truncated by persistence`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        val big = "x".repeat(200_000)
        val original = ToolResultMessage("big", "tool", ToolResult(true, big, null))
        runBlocking { store.appendMessage("s1", original, now) }
        val loaded = runBlocking { store.loadMessages("s1") }!!.single() as ToolResultMessage
        assertEquals(big.length, loaded.result.output.length)
        assertEquals(original, loaded)
    }

    // ------------------------------------------------------------------
    // Corrupt payload -> explicit decode failure (UNRECOVERABLE signal)
    // ------------------------------------------------------------------
    @Test
    fun `corrupt payload surfaces as MessageDecodeException`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        runBlocking {
            rawQuery(
                "INSERT INTO messages (id, topic_session_id, sequence, type, payload_json, created_at) " +
                    "VALUES ('bad', 's1', 0, 'USER', '{not json', $now)",
            )
        }
        var thrown: Throwable? = null
        try {
            runBlocking { store.loadMessages("s1") }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(
            "expected MessageDecodeException, got $thrown",
            thrown is MessageDecodeException,
        )
        // The exception must not embed the raw payload (secret hygiene).
        assertTrue("exception must not embed raw payload", !thrown!!.message.orEmpty().contains("not json"))
    }

    // ------------------------------------------------------------------
    // File-backed reopen durability: close DB, reopen, data survives
    // ------------------------------------------------------------------
    @Test
    fun `file-backed database survives close and reopen`() {
        val file = openFileBacked()
        runBlocking {
            store.createSession("s1", now)
            repeat(3) { i -> store.appendMessage("s1", UserMessage("m$i"), now + i) }
        }
        db.close()

        // Reopen the SAME file.
        db = Room.databaseBuilder(context, TopicSessionDatabase::class.java, file.absolutePath)
            .openHelperFactory(WalOpenHelperFactory())
            .build()
        store = RoomTopicSessionStore(db)

        val session = runBlocking { store.loadSession("s1") }
        assertNotNull(session)
        val loaded = runBlocking { store.loadMessages("s1") }
        assertNotNull(loaded)
        assertEquals(listOf(UserMessage("m0"), UserMessage("m1"), UserMessage("m2")), loaded)
    }

    // ------------------------------------------------------------------
    // Duplicate createSession id -> explicit failure (no silent overwrite)
    // ------------------------------------------------------------------
    @Test
    fun `duplicate createSession id is an explicit failure`() {
        openInMemory()
        runBlocking { store.createSession("s1", now) }
        var thrown: Throwable? = null
        try {
            runBlocking { store.createSession("s1", now + 1) }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(
            "expected a safe PersistenceException, got $thrown",
            thrown is PersistenceException &&
                thrown.message.orEmpty().contains("session_create_failed"),
        )
    }

    // ------------------------------------------------------------------
    // ROOM-TX-ROLLBACK: an uncommitted transaction leaves no durable row
    // ------------------------------------------------------------------
    @Test
    fun `uncommitted transaction leaves no durable row`() {
        openFileBacked()
        runBlocking { store.createSession("s1", now) }
        runBlocking {
            val wdb = db.openHelper.writableDatabase
            wdb.execSQL("BEGIN")
            wdb.execSQL(
                "INSERT INTO messages (id, topic_session_id, sequence, type, payload_json, created_at) " +
                    "VALUES ('ghost', 's1', 0, 'USER', '{}', $now)",
            )
            wdb.execSQL("ROLLBACK")
        }
        val count = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }
        assertEquals("0", count)
    }

    // ------------------------------------------------------------------
    // Atomic sequence concurrency: N concurrent appends to ONE session must
    // yield exactly N rows with unique, contiguous sequences 0..N-1.
    //
    // Relies on the DB transaction + UNIQUE constraint, NOT an
    // application-level global lock. SQLite serializes writers at the
    // database level; the unique index is the final guard.
    // ------------------------------------------------------------------
    @Test
    fun `concurrent appends produce unique contiguous sequences`() {
        openFileBacked()
        runBlocking { store.createSession("s1", now) }

        val n = 16
        val scope = CoroutineScope(Dispatchers.IO)
        val results = runBlocking {
            val deferred = (0 until n).map { i ->
                scope.async { store.appendMessage("s1", UserMessage("c$i"), now + i) }
            }
            deferred.map { it.await() }
        }

        assertEquals(n, results.size)
        val sequences = results.map { it.sequence }.sorted()
        assertEquals((0L until n).toList(), sequences)
        val rows = runBlocking {
            rawQuery("SELECT COUNT(*) FROM messages WHERE topic_session_id = 's1'").first().first()
        }
        assertEquals(n.toString(), rows)
    }
}
