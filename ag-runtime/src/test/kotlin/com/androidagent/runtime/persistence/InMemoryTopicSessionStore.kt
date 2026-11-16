package com.androidagent.runtime.persistence

import com.androidagent.core.Message
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [TopicSessionStore] for contract tests.
 *
 * Test source set ONLY. NOT a production fallback (a production Noop/fake
 * store is forbidden — PRODUCTION_NOOP = NO).
 *
 * Models the frozen persistence semantics:
 *  - sequence is assigned atomically per session (monotonic, starting at 0)
 *  - last_used_at is updated ONLY on successful durable append
 *  - loadMessages returns sequence ASC order
 *  - messages never cross sessions
 */
class InMemoryTopicSessionStore : TopicSessionStore {

    private val sessions = ConcurrentHashMap<String, TopicSessionRecord>()
    private val nextSequence = ConcurrentHashMap<String, AtomicLong>()
    private val messages = ConcurrentHashMap<String, MutableList<MessageRecord>>()

    private fun messageList(sessionId: String): MutableList<MessageRecord> =
        messages.computeIfAbsent(sessionId) { mutableListOf() }

    override suspend fun createSession(
        id: String,
        nowMillis: Long,
        model: String?,
        provider: String?,
        title: String?,
        titleOrigin: TitleOrigin?,
    ): TopicSessionRecord {
        val record = TopicSessionRecord(
            id = id,
            createdAt = nowMillis,
            lastUsedAt = nowMillis,
            model = model,
            provider = provider,
            title = title,
            titleOrigin = titleOrigin,
        )
        sessions[id] = record
        nextSequence[id] = AtomicLong(0)
        messages[id] = mutableListOf()
        return record
    }

    override suspend fun appendMessage(
        sessionId: String,
        message: Message,
        nowMillis: Long,
    ): MessageRecord {
        val session = sessions[sessionId]
            ?: throw SessionNotFoundException(sessionId)
        val serialized = MessageSerializer.serialize(message)
        // Atomic sequence assignment (models the single-transaction contract).
        val sequence = nextSequence[sessionId]!!.getAndIncrement()
        val record = MessageRecord(
            id = UUID.randomUUID().toString(),
            topicSessionId = sessionId,
            sequence = sequence,
            type = serialized.wireName,
            payloadJson = serialized.payloadJson,
            createdAt = nowMillis,
        )
        // Single synchronized block models the one-transaction boundary:
        // sequence + insert + last_used update commit together.
        synchronized(this) {
            messageList(sessionId).add(record)
            sessions[sessionId] = session.copy(lastUsedAt = nowMillis)
        }
        return record
    }

    override suspend fun loadSession(id: String): TopicSessionRecord? =
        sessions[id]

    override suspend fun loadLatestSession(): TopicSessionRecord? =
        sessions.values.maxByOrNull { it.lastUsedAt }

    override suspend fun loadMessages(sessionId: String): List<Message>? {
        if (!sessions.containsKey(sessionId)) return null
        return messageList(sessionId)
            .sortedBy { it.sequence }
            .map { MessageSerializer.deserialize(it.type, it.payloadJson) }
    }

    override suspend fun updateModelProviderMetadata(
        sessionId: String,
        model: String?,
        provider: String?,
    ) {
        val session = sessions[sessionId]
            ?: throw SessionNotFoundException(sessionId)
        sessions[sessionId] = session.copy(model = model, provider = provider)
    }

    override suspend fun listSessions(): List<TopicSessionRecord> =
        sessions.values.toList().sortedByDescending { it.lastUsedAt }

    override suspend fun renameSession(sessionId: String, newTitle: String): TopicSessionRecord {
        val session = sessions[sessionId]
            ?: throw SessionNotFoundException(sessionId)
        val updated = session.copy(title = newTitle, titleOrigin = TitleOrigin.USER_RENAMED)
        sessions[sessionId] = updated
        return updated
    }

    override suspend fun deleteSession(sessionId: String): Boolean {
        val removed = sessions.remove(sessionId)
        if (removed == null) return false
        // FK ON DELETE CASCADE model: the session's messages are removed with it.
        messages.remove(sessionId)
        nextSequence.remove(sessionId)
        return true
    }

    override suspend fun countMessages(sessionId: String): Int =
        if (sessions.containsKey(sessionId)) messageList(sessionId).size else 0

    // ---- test-only inspection (NOT part of the contract) ----

    fun records(sessionId: String): List<MessageRecord> =
        messageList(sessionId).sortedBy { it.sequence }

    fun sessionCount(): Int = sessions.size
}
