package com.androidagent.runtime.persistence

/**
 * Storage-neutral persistence records for TopicSession durable storage.
 *
 * These are the wire shape between the [TopicSessionStore] contract and any
 * concrete backend (Room, file, in-memory test store). They are NOT Room
 * Entities — a Room implementation maps these records to its own @Entity
 * classes in :app.
 *
 * Pure Kotlin/JVM. No android.*, no androidx.room.*.
 */

/**
 * A durable TopicSession row.
 *
 * [model] / [provider] carry the LAST_USED_MODEL semantic: a snapshot of the
 * model/provider the session last ran against (creation snapshot, updated on
 * switch). They are NOT session identity.
 *
 * [title] is optional display metadata.
 */
data class TopicSessionRecord(
    val id: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val model: String? = null,
    val provider: String? = null,
    val title: String? = null,
)

/**
 * A durable message row.
 *
 * [sequence] is assigned ATOMICALLY by the persistence implementation inside
 * the append transaction (UNIQUE(topic_session_id, sequence) is the final
 * guard). The Runtime caller NEVER preallocates it.
 *
 * [type] is the frozen message-type discriminator: "USER" | "MODEL" |
 * "TOOL_RESULT". See [MessageSerializer].
 *
 * [payloadJson] is the domain message payload produced by [MessageSerializer]
 * (AG domain Message, never provider wire JSON, never a transcript block).
 */
data class MessageRecord(
    val id: String,
    val topicSessionId: String,
    val sequence: Long,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
)

/**
 * Thrown when a requested session does not exist.
 *
 * Distinct from [PersistenceException]: a missing session is an expected
 * query outcome for [TopicSessionStore.loadSession] / [loadMessages] (null),
 * but an append to a missing session is an error.
 */
class SessionNotFoundException(
    val sessionId: String,
) : Exception("Session not found: $sessionId")

/**
 * Thrown when a storage operation fails (backend error: SQLite/Room/IO).
 *
 * The exception message is a SAFE category, never a SQL statement, file
 * path, or backend exception message (secret/payload hygiene).
 *
 * [cause] may carry the backend exception for internal logging, but must
 * NEVER be exposed to the domain/UI layer.
 */
class PersistenceException(
    cause: Throwable? = null,
    val category: String = "storage_error",
) : Exception("Persistence error: $category", cause)

/**
 * Thrown when a single TopicSession's durable HISTORY is structurally invalid
 * and therefore UNRECOVERABLE: the session row may exist, but its message
 * history cannot be safely restored into AG Core.
 *
 * This is a SESSION-LEVEL data-validity condition, deliberately distinct from
 * [PersistenceException] (a backend/IO/operation failure). The distinction
 * drives the startup recovery policy (frozen, Phase G NORMALIZE-1):
 *
 *  - [UnrecoverableSessionHistoryException]  -> the corrupt session is
 *    PRESERVED (never deleted), and the Runtime recovers by creating a brand-new
 *    durable TopicSession (controlled fresh recovery).
 *  - [PersistenceException] (backend failure) -> FAIL CLOSED (no recovery, no
 *    current session published; a later [initialize] retries).
 *
 * Categories (safe, never a raw payload / SQL / path):
 *  - "session_history_inconsistent"         : loadMessages returned null for an
 *    existing session row.
 *  - "sequence_continuity_violation"        : durable sequence not 0..N-1 contiguous.
 *  - "malformed_payload" / "unknown_type"   : a durable row cannot be decoded into
 *    a domain Message (wraps [MessageDecodeException]).
 *
 * [cause] preserves the originating exception (e.g. a [MessageDecodeException])
 * for internal logging; it is never exposed to the domain/UI layer.
 */
class UnrecoverableSessionHistoryException(
    val sessionId: String,
    val category: String,
    cause: Throwable? = null,
) : Exception("Unrecoverable session history [$sessionId]: $category", cause)
