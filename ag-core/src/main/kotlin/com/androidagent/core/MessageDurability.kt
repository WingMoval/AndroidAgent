package com.androidagent.core

/**
 * Per-message durability boundary. AG Core awaits this before crossing
 * a safety boundary (model call, tool dispatch, next model call).
 *
 * This is NOT an observation/event. This is a CORRECTNESS GATE:
 * the Core must not proceed until the commit is confirmed.
 *
 * Implementation is storage-agnostic: could be Room, file, or in-memory.
 *
 * CANCELLATION CONTRACT (frozen, AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0):
 * - The implementation MUST shield the actual commit from coroutine cancellation.
 * - CancellationException may ONLY be thrown BEFORE the commit transaction starts.
 * - Once the commit has started, it MUST complete deterministically.
 * - The Core NEVER sees an ambiguous commit outcome.
 */
fun interface MessageDurability {

    /**
     * Durable-commit [message] for [sessionId].
     *
     * OUTCOMES (exactly two, never ambiguous):
     * - Normal return = COMMITTED (transaction committed; guaranteed)
     * - [DurabilityCommitException] thrown = NOT_COMMITTED (transaction did not commit)
     *
     * Cancellation:
     * - CancellationException may be thrown ONLY if cancellation was detected
     *   BEFORE the commit transaction was entered (NOT_COMMITTED).
     * - CancellationException MUST NOT be thrown after the transaction has started.
     *
     * Requirements:
     * - Must AWAIT actual commit (no fire-and-forget, no background queue)
     * - Must not expose backend exceptions (Room/SQLite) to Core
     * - Normal return guarantees: the message IS in the durable store
     */
    suspend fun commit(sessionId: String, message: Message)

    companion object {
        /**
         * No-op durability for explicit test / ephemeral use ONLY.
         * Production MUST inject a real persistence-backed implementation.
         * This is NOT a default — it must be passed explicitly.
         */
        val Noop: MessageDurability = MessageDurability { _, _ -> }
    }
}

/**
 * Thrown when a durability commit definitively FAILED (NOT_COMMITTED).
 *
 * The implementation must catch backend exceptions and wrap them here.
 * The Core maps this to [RunFailure.DurabilityFailure].
 *
 * [cause] may be the backend exception (for internal logging) but must
 * NEVER be exposed directly to the domain/UI layer.
 */
class DurabilityCommitException(
    cause: Throwable? = null,
) : Exception("Durability commit failed", cause)
