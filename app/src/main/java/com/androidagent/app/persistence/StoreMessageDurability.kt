package com.androidagent.app.persistence

import com.androidagent.core.DurabilityCommitException
import com.androidagent.core.Message
import com.androidagent.core.MessageDurability
import com.androidagent.runtime.persistence.MessageDecodeException
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.MessageSerializationException
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.SessionNotFoundException
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Production [MessageDurability] backed by a [TopicSessionStore]
 * (AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F).
 *
 * This is the CORE DURABILITY SEAM's persistence-backed implementation:
 *
 * ```
 * AgentCore
 *   -> MessageDurability.commit(sessionId, message)
 *   -> StoreMessageDurability
 *   -> TopicSessionStore.appendMessage(sessionId, message, now)
 *   -> RoomTopicSessionStore (ONE transaction: nextSequence + INSERT + touchLastUsed)
 *   -> Room -> SQLite -> topic_sessions.db (WAL)
 * ```
 *
 * ## Cancellation contract (frozen, Phase F)
 *
 * A single `commit` resolves to EXACTLY one of two outcomes — never ambiguous:
 *
 *  - **COMMITTED** — `commit` returns normally. The message IS in the durable
 *    store. This holds EVEN IF the caller coroutine was cancelled while the
 *    transaction was running: the commit is shielded, completes, and the
 *    committed fact is returned to the Core so the Core and the DB agree.
 *  - **NOT_COMMITTED** — [DurabilityCommitException] is thrown. The message is
 *    NOT in the durable store (the transaction did not commit).
 *
 * The shield ([NonCancellable] around `appendMessage`) is the SMALLEST possible:
 * it covers only the persistence transaction / outcome determination. It does
 * NOT wrap the model call, tool execution, or the whole Run — those remain
 * fully cancellation-responsive. Cancellation is honoured:
 *
 *  - BEFORE the transaction starts ([ensureActive] at entry) -> NOT_COMMITTED
 *    (the caller's [CancellationException] propagates; the Core treats a
 *    cancelled commit as "not durable" and stops, which is correct because the
 *    row was never written).
 *  - DURING the transaction -> the transaction runs to a deterministic
 *    COMMIT or ROLLBACK; the outcome is then returned/propagated.
 *
 * ## Ownership boundaries (frozen)
 *
 *  - Does NOT allocate sequence (the store transaction owns it).
 *  - Does NOT serialize (reuses the store's [MessageSerializer] path).
 *  - Does NOT touch last_used_at (the store transaction owns it).
 *  - Does NOT create the TopicSession row (session lifecycle is NOT this
 *    adapter's job — see the composition root's session bootstrap).
 *
 * @param store the durable message store (production: RoomTopicSessionStore).
 * @param nowMillis clock supplier. Production = system time; tests inject a
 *   deterministic fake. Kept as a minimal `() -> Long` per the frozen Phase F
 *   clock-ownership rule (no complex time framework).
 */
class StoreMessageDurability(
    private val store: TopicSessionStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : MessageDurability {

    override suspend fun commit(sessionId: String, message: Message) {
        // Honour cancellation BEFORE the commit transaction begins (Case I1):
        // if the caller is already cancelled, do not start the write. The
        // CancellationException propagates to the Core, which treats the commit
        // as NOT_COMMITTED (correct: nothing was written).
        coroutineContext.ensureActive()

        // Shield ONLY the persistence transaction / outcome determination
        // (Cases I2-I5). The transaction must reach a deterministic COMMIT or
        // ROLLBACK regardless of a concurrent caller cancellation, so the Core
        // never sees "DB committed but Core thinks not committed".
        // The store-assigned sequence is diagnostic-only; the commit is COMMITTED
        // on normal return. A caller cancellation that arrived during the
        // transaction is deliberately NOT converted into a failure here — the row
        // IS committed, so reporting a durability failure would corrupt the
        // Core/DB invariant. The Run's normal cancellation path still applies at
        // the Core's next checkpoint (after the append).
        withContext(NonCancellable) {
            try {
                store.appendMessage(sessionId, message, nowMillis())
            } catch (e: MessageSerializationException) {
                // Programming error serializing a domain message. NOT a durable
                // commit; surface as a durability failure with a safe cause.
                throw DurabilityCommitException(e)
            } catch (e: MessageDecodeException) {
                // Defensive: append does not decode, but keep the mapping total.
                throw DurabilityCommitException(e)
            } catch (e: SessionNotFoundException) {
                // The durable TopicSession row is missing (creation is the
                // composition root's job, not this adapter's). The append did not
                // commit; surface as a durability failure with a safe cause.
                throw DurabilityCommitException(e)
            } catch (e: PersistenceException) {
                // Storage failure (the transaction did not commit). NOT_COMMITTED.
                throw DurabilityCommitException(e)
            }
        }
    }
}
