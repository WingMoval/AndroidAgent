package com.androidagent.runtime.persistence

import com.androidagent.core.Message

/**
 * Storage-agnostic persistence port for TopicSession durable storage.
 *
 * Owner: ag-runtime (the Agent Runtime owns the persistence contract; the
 * Composition Root injects the concrete implementation, e.g. a Room-backed
 * store in :app).
 *
 * Hard boundary:
 *  - Pure Kotlin/JVM. No android.*, no androidx.room.*, no SQLite, no DAO,
 *    no Entity, no Context.
 *  - AG Core never sees this type: Core knows only [MessageDurability]
 *    (a per-message commit gate). The Runtime adapts a [TopicSessionStore]
 *    into that gate (Phase F: StoreMessageDurability).
 *
 * Sequence ownership (frozen):
 *  - The caller NEVER preallocates a message sequence. [appendMessage]
 *    receives a domain [Message]; the implementation assigns the next
 *    sequence atomically inside ONE transaction (next sequence + INSERT
 *    message + UPDATE last_used_at). The returned [MessageRecord] carries
 *    the assigned sequence.
 *
 * Ordering guarantee:
 *  - [loadMessages] always returns history ordered by sequence ASC. Callers
 *    never reorder.
 */
interface TopicSessionStore {

    /**
     * Creates a new durable TopicSession row.
     *
     * At creation: createdAt == lastUsedAt (both = [nowMillis]).
     *
     * @return the persisted session record.
     * @throws PersistenceException on storage failure.
     */
    suspend fun createSession(
        id: String,
        nowMillis: Long,
        model: String? = null,
        provider: String? = null,
        title: String? = null,
    ): TopicSessionRecord

    /**
     * Durable appends one message to a TopicSession.
     *
     * Contract (frozen for the Phase E Room implementation):
     *  - ONE short transaction: determine next sequence + INSERT message +
     *    UPDATE last_used_at + COMMIT.
     *  - The implementation assigns the sequence (caller provides none).
     *  - last_used_at is updated ONLY here (successful durable append).
     *    Loads, restores, and UI reads never touch it.
     *
     * @return the persisted [MessageRecord] with the assigned [MessageRecord.sequence].
     * @throws SessionNotFoundException if [sessionId] does not exist.
     * @throws PersistenceException on storage failure (transaction did NOT commit;
     *       the caller may treat the outcome as NOT_COMMITTED).
     */
    suspend fun appendMessage(
        sessionId: String,
        message: Message,
        nowMillis: Long,
    ): MessageRecord

    /**
     * Loads a session by id.
     *
     * @return the session record, or null if it does not exist.
     */
    suspend fun loadSession(id: String): TopicSessionRecord?

    /**
     * Loads the most recently used session.
     *
     * Implementation semantics: ORDER BY last_used_at DESC LIMIT 1.
     * Callers must not need to know the physical sort.
     *
     * @return the latest session record, or null if the store is empty.
     */
    suspend fun loadLatestSession(): TopicSessionRecord?

    /**
     * Loads the full durable history of a session, ordered by sequence ASC.
     *
     * @return ordered messages, or null if the session does not exist.
     * @throws MessageDecodeException if a row's payload cannot be decoded
     *       (caller may treat the session as UNRECOVERABLE).
     */
    suspend fun loadMessages(sessionId: String): List<Message>?

    /**
     * Updates the last-used model/provider metadata (LAST_USED_MODEL semantic).
     *
     * This is NOT session identity. It snapshots which model/provider the
     * session last ran against, updated on provider/model switch.
     */
    suspend fun updateModelProviderMetadata(
        sessionId: String,
        model: String?,
        provider: String?,
    )
}
