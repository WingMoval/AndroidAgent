package com.androidagent.app.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the durable TopicSession row.
 *
 * This is a :app persistence detail. The ag-runtime contract type is
 * [com.androidagent.runtime.persistence.TopicSessionRecord]; the mapping
 * lives in [RoomTopicSessionStore]. The entity must NEVER leak into
 * ag-runtime.
 *
 * Schema (frozen, AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0): 2 tables only —
 * topic_sessions + messages. No runs / tool_calls / events / checkpoints.
 *
 * Column names are pinned to the frozen SSOT snake_case shape
 * (id / created_at / last_used_at / model / provider / title / title_origin)
 * via [androidx.room.ColumnInfo] so the physical schema matches the
 * architecture contract exactly.
 *
 * AG.SESSION-MANAGEMENT-UX-0: [titleOrigin] (column `title_origin`) is the
 * durable title-ownership seam. It stores the [com.androidagent.runtime.persistence.TitleOrigin]
 * ordinal (null = no title yet). A Room [Migration] 1→2 adds the column for
 * existing databases; the enum is serialized by ordinal so it is stable
 * across the migration boundary.
 */
@Entity(
    tableName = "topic_sessions",
    indices = [Index("last_used_at")],
)
data class TopicSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "provider") val provider: String? = null,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "title_origin") val titleOrigin: Int? = null,
)

/**
 * Room entity for one durable message row.
 *
 * Invariants (frozen):
 *  - FK topic_session_id -> topic_sessions.id ON DELETE CASCADE
 *  - UNIQUE(topic_session_id, sequence) — the FINAL guard against duplicate
 *    sequence, independent of the application-level transaction logic.
 *  - INDEX(topic_session_id, sequence) — ordered history reads.
 *
 * [sequence] is assigned atomically by the persistence implementation inside
 * the append transaction (see [RoomTopicSessionStore.appendMessage]).
 * [payloadJson] is produced by
 * [com.androidagent.runtime.persistence.MessageSerializer] — the Room layer
 * does not (re)serialize the domain Message.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = TopicSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("topic_session_id", "sequence", unique = true)],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "topic_session_id") val topicSessionId: String,
    val sequence: Long,
    val type: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
