package com.androidagent.app.persistence

import com.androidagent.core.Message
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.MessageSerializer
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.SessionNotFoundException
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import androidx.room.withTransaction
import java.util.UUID

/**
 * Room/SQLite production backend for the ag-runtime [TopicSessionStore]
 * contract (AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0E).
 *
 * Responsibilities (and ONLY these):
 *  - Room persistence
 *  - record/entity mapping ([TopicSessionRecord] <-> [TopicSessionEntity],
 *    [MessageRecord] <-> [MessageEntity])
 *  - transaction orchestration (the atomic append)
 *
 * It owns no agent loop, no run lifecycle, no tool dispatch, no UI
 * projection, and no TopicSession business decisions. The ag-runtime
 * contract type is the boundary; Room entities never leak out.
 *
 * Cancellation shielding (ensureActive + NonCancellable commit) is NOT here:
 * the frozen architecture assigns COMMIT_SHIELDING_OWNER = the backend
 * ADAPTER (Phase F: StoreMessageDurability). This store is responsible for
 * transaction correctness only.
 *
 * Error mapping (frozen): backend exceptions (Room/SQLite) are wrapped in
 * [PersistenceException] with a SAFE category — never a SQL statement, file
 * path, or raw backend message. The original exception is preserved as
 * cause for internal logging.
 */
class RoomTopicSessionStore(
    private val database: TopicSessionDatabase,
) : TopicSessionStore {

    private val sessionDao = database.topicSessionDao()
    private val messageDao = database.messageDao()

    override suspend fun createSession(
        id: String,
        nowMillis: Long,
        model: String?,
        provider: String?,
        title: String?,
    ): TopicSessionRecord {
        val entity = TopicSessionEntity(
            id = id,
            createdAt = nowMillis,
            lastUsedAt = nowMillis,
            model = model,
            provider = provider,
            title = title,
        )
        try {
            // ON CONFLICT ABORT: a duplicate id is an explicit failure, never
            // a silent overwrite.
            sessionDao.insert(entity)
        } catch (e: Exception) {
            throw PersistenceException(e, category = "session_create_failed")
        }
        return entity.toRecord()
    }

    override suspend fun appendMessage(
        sessionId: String,
        message: Message,
        nowMillis: Long,
    ): MessageRecord {
        val serialized = MessageSerializer.serialize(message)
        val recordId = UUID.randomUUID().toString()

        return try {
            // ONE SHORT TRANSACTION (frozen):
            //   1. verify session exists
            //   2. determine next sequence (MAX+1, first = 0)
            //   3. INSERT message
            //   4. UPDATE topic_sessions.last_used_at
            //   5. COMMIT
            //
            // The sequence read and the insert happen inside the SAME
            // transaction (SEQUENCE_ALLOCATION = ATOMIC_WITH_MESSAGE_INSERT).
            // UNIQUE(topic_session_id, sequence) is the final DB-level guard.
            val assignedSequence = database.withTransaction {
                if (sessionDao.findById(sessionId) == null) {
                    throw SessionNotFoundException(sessionId)
                }
                val next = messageDao.nextSequence(sessionId)
                messageDao.insert(
                    MessageEntity(
                        id = recordId,
                        topicSessionId = sessionId,
                        sequence = next,
                        type = serialized.wireName,
                        payloadJson = serialized.payloadJson,
                        createdAt = nowMillis,
                    ),
                )
                if (messageDao.touchLastUsed(sessionId, nowMillis) != 1) {
                    throw SessionNotFoundException(sessionId)
                }
                next
            }
            MessageRecord(
                id = recordId,
                topicSessionId = sessionId,
                sequence = assignedSequence,
                type = serialized.wireName,
                payloadJson = serialized.payloadJson,
                createdAt = nowMillis,
            )
        } catch (e: SessionNotFoundException) {
            throw e
        } catch (e: PersistenceException) {
            throw e
        } catch (e: Exception) {
            // Safe category: never a SQL statement / path / raw backend text.
            throw PersistenceException(e, category = "append_failed")
        }
    }

    override suspend fun loadSession(id: String): TopicSessionRecord? =
        try {
            sessionDao.findById(id)?.toRecord()
        } catch (e: Exception) {
            throw PersistenceException(e, category = "load_session_failed")
        }

    override suspend fun loadLatestSession(): TopicSessionRecord? =
        try {
            sessionDao.findLatest()?.toRecord()
        } catch (e: Exception) {
            throw PersistenceException(e, category = "load_latest_failed")
        }

    override suspend fun loadMessages(sessionId: String): List<Message>? =
        try {
            val entities = messageDao.findBySession(sessionId)
            if (entities.isEmpty() && sessionDao.findById(sessionId) == null) {
                null // missing session -> contract-defined missing result
            } else {
                // SEQUENCE CONTINUITY VALIDATION (Phase G):
                // The entities are ordered by sequence ASC (DB guarantee).
                // Validate that sequence is contiguous from 0 (0, 1, 2, ..., N-1).
                // A gap (e.g. 0, 1, 3) or offset (e.g. 1, 2) is an explicit
                // failure — the session is UNRECOVERABLE (never silently skip).
                for (i in entities.indices) {
                    if (entities[i].sequence != i.toLong()) {
                        // Session-level data validity: the durable sequence is not
                        // contiguous 0..N-1. The caller (Runtime) recovers with a
                        // controlled fresh session (the corrupt session is preserved).
                        throw com.androidagent.runtime.persistence.UnrecoverableSessionHistoryException(
                            sessionId = sessionId,
                            category = "sequence_continuity_violation",
                        )
                    }
                }
                entities.map {
                    MessageSerializer.deserialize(it.type, it.payloadJson)
                }
            }
        } catch (e: com.androidagent.runtime.persistence.MessageDecodeException) {
            // A durable row cannot be decoded: propagate the decode exception as
            // the UNRECOVERABLE signal. The Runtime classifies it (controlled
            // fresh recovery; the corrupt session is preserved). The Room layer
            // does NOT re-wrap it: MessageDecodeException is the frozen Phase D/E
            // decode-failure signal (never embeds the raw payload).
            throw e
        } catch (e: com.androidagent.runtime.persistence.UnrecoverableSessionHistoryException) {
            throw e // propagate session-level invalid history explicitly
        } catch (e: PersistenceException) {
            throw e // backend failure: fail closed (distinct from corruption)
        } catch (e: Exception) {
            throw PersistenceException(e, category = "load_messages_failed")
        }

    override suspend fun updateModelProviderMetadata(
        sessionId: String,
        model: String?,
        provider: String?,
    ) {
        try {
            val updated = sessionDao.updateModelProvider(sessionId, model, provider)
            if (updated == 0) throw SessionNotFoundException(sessionId)
        } catch (e: SessionNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw PersistenceException(e, category = "metadata_update_failed")
        }
    }
}

private fun TopicSessionEntity.toRecord(): TopicSessionRecord =
    TopicSessionRecord(
        id = id,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        model = model,
        provider = provider,
        title = title,
    )
