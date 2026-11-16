package com.androidagent.app.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO for topic_sessions.
 *
 * All queries are suspend (room-ktx) so the RoomTopicSessionStore can run
 * them off the main thread inside a coroutine. No allowMainThreadQueries()
 * anywhere in production.
 */
@Dao
interface TopicSessionDao {

    /**
     * ABORT on conflict: a duplicate session id is an explicit persistence
     * failure (the store must never silently overwrite an existing session).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TopicSessionEntity)

    @Query("SELECT * FROM topic_sessions WHERE id = :id")
    suspend fun findById(id: String): TopicSessionEntity?

    /**
     * Latest session: last_used_at DESC with a stable secondary sort
     * (created_at DESC, id) so equal last_used_at never yields an
     * arbitrary pick.
     */
    // Raw queries use the frozen SSOT snake_case column names (the entities
    // pin these via @ColumnInfo).
    @Query("SELECT * FROM topic_sessions ORDER BY last_used_at DESC, created_at DESC, id LIMIT 1")
    suspend fun findLatest(): TopicSessionEntity?

    /**
     * LAST_USED_MODEL semantic: snapshot update on provider/model switch.
     * Deliberately does NOT touch last_used_at (frozen rule: last_used_at
     * changes only on a successful durable message append).
     *
     * @return the number of updated rows (0 when the session is missing).
     */
    @Query("UPDATE topic_sessions SET model = :model, provider = :provider WHERE id = :id")
    suspend fun updateModelProvider(id: String, model: String?, provider: String?): Int

    /** Test/schema-level only: raw SQL access for integrity verification. */
    @Query("SELECT 1")
    suspend fun probe(): Int?

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: list all sessions for the Session Browser.
     * ADDITIVE query only — no schema change, no migration, no new column.
     * Ordered by last_used_at DESC (most recently used first).
     */
    @Query("SELECT * FROM topic_sessions ORDER BY last_used_at DESC, created_at DESC, id")
    suspend fun listAll(): List<TopicSessionEntity>

    // ---- AG.SESSION-MANAGEMENT-UX-0: rename / delete / count ----

    /**
     * Renames a session and marks the title as user-owned (AG.SESSION-MANAGEMENT-UX-0).
     *
     * METADATA-ONLY: updates `title` and `title_origin` (= USER_RENAMED ordinal).
     * Deliberately does NOT touch last_used_at (frozen rule: last_used_at changes
     * only on a successful durable message append).
     *
     * @param titleOrigin the [com.androidagent.runtime.persistence.TitleOrigin] ordinal
     *   (USER_RENAMED). Stored as a nullable Int column.
     * @return the number of updated rows (0 when the session is missing).
     */
    @Query("UPDATE topic_sessions SET title = :title, title_origin = :titleOrigin WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, titleOrigin: Int?): Int

    /**
     * Permanently deletes a session row. Its messages are removed by the
     * existing FK `ON DELETE CASCADE` contract (messages.topic_session_id ->
     * topic_sessions.id). Ownership-scoped: only this session + its messages.
     *
     * @return the number of deleted rows (0 when the session is missing).
     */
    @Query("DELETE FROM topic_sessions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Durable message count for one session (read-only; no last_used_at change). */
    @Query("SELECT COUNT(*) FROM messages WHERE topic_session_id = :id")
    suspend fun countMessagesFor(id: String): Int
}

/**
 * DAO for messages.
 *
 * Ordering is a DB responsibility: history reads are always
 * `ORDER BY sequence ASC` — the store never re-sorts in Kotlin.
 */
@Dao
interface MessageDao {

    @Insert
    suspend fun insert(entity: MessageEntity)

    /**
     * History of one session, sequence ASC (frozen contract: the store
     * returns ordered history; callers never reorder).
     */
    @Query("SELECT * FROM messages WHERE topic_session_id = :sessionId ORDER BY sequence ASC")
    suspend fun findBySession(sessionId: String): List<MessageEntity>

    /**
     * Next sequence = MAX(sequence)+1, first message = 0.
     *
     * MUST only be called INSIDE the same transaction as the subsequent
     * [insert] (SEQUENCE_ALLOCATION = ATOMIC_WITH_MESSAGE_INSERT). Calling
     * this outside a transaction is a contract violation by the caller.
     */
    @Query("SELECT COALESCE(MAX(sequence), -1) + 1 FROM messages WHERE topic_session_id = :sessionId")
    suspend fun nextSequence(sessionId: String): Long

    /**
     * Durable append side effect: last_used_at moves ONLY when a message
     * commit succeeds (same transaction as the message INSERT).
     *
     * @return the number of updated rows (0 when the session is missing).
     */
    @Query("UPDATE topic_sessions SET last_used_at = :nowMillis WHERE id = :sessionId")
    suspend fun touchLastUsed(sessionId: String, nowMillis: Long): Int

    /** Test/schema-level only: raw SQL access for FK/cascade verification. */
    @Query("SELECT COUNT(*) FROM messages WHERE topic_session_id = :sessionId")
    suspend fun countBySession(sessionId: String): Int
}
