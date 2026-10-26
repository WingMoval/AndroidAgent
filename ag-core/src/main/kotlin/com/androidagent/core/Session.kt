package com.androidagent.core

/**
 * A long-lived multi-turn conversation.
 *
 * A Session owns two things: its message history, and ownership of at most one
 * active Run at a time.
 *
 * Concurrency contract, frozen:
 *
 *  - One Session admits at most one active Run. A second concurrent Run on the
 *    same Session is rejected with [RunFailure.SessionBusy].
 *  - Sessions are independent. Session A holding an active Run does not make
 *    Session B busy.
 *
 * The ownership flag therefore lives on the Session, guarded by that Session's
 * own monitor. There is intentionally no lock on the owning AgentCore, because a
 * single global flag would incorrectly serialize unrelated Sessions.
 *
 * Ownership is always released in a finally block, so a Run that ends by
 * success, by failure, or by exception leaves the Session reusable.
 *
 * History after an aborted Run: a Run that aborts part-way through a tool-call
 * turn may leave that turn recorded as partially executed. AG Core does not
 * fabricate results for calls it never reached, so the recorded history stays a
 * faithful account of what actually ran. Two guarantees always hold:
 *
 *  - a recorded tool result never refers to a call that was not declared, and
 *  - a tool result is recorded before it is announced, so a failing event sink
 *    can never orphan one.
 *
 * Session is responsible for storage and ownership only. It performs no
 * validation and no repair of recorded history.
 */
class Session internal constructor(val id: String) {

    private val monitor = Any()
    private val messages = mutableListOf<Message>()
    private var activeRun = false

    /**
     * Set to true when an interrupt closeout durability failure left the durable
     * history in a state that requires reconciliation before the Session can
     * safely accept another Run. While this flag is set, [tryBeginRun] rejects
     * all Run admission (fail-closed: no D1, no ModelCall, no ToolCall).
     *
     * Cleared by [restoreHistory] (a fresh Session instance is created by
     * [AgentCore.restoreSession], so the flag is naturally false on the new
     * instance). Never set by normal Run paths.
     */
    private var reconciliationRequired = false

    /** Snapshot of the conversation so far, oldest first. */
    fun history(): List<Message> = synchronized(monitor) { messages.toList() }

    internal fun append(message: Message) {
        synchronized(monitor) { messages.add(message) }
    }

    /**
     * Marks this Session as requiring reconciliation (durable history is
     * inconsistent after an interrupt closeout failure). Subsequent
     * [tryBeginRun] calls are rejected until [restoreHistory] is called.
     */
    internal fun markReconciliationRequired() {
        synchronized(monitor) { reconciliationRequired = true }
    }

    /** True if the Session requires reconciliation before the next Run. */
    internal fun isReconciliationRequired(): Boolean =
        synchronized(monitor) { reconciliationRequired }

    /**
     * Restores the full message history from a durable source (e.g., Room/SQLite).
     *
     * This is the Core restore seam (AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0 §12).
     * It replaces the in-memory history with [history] and clears the
     * reconciliation flag. Used at startup to hydrate a Session from durable
     * storage WITHOUT re-triggering durability.
     *
     * Must only be called when no Run is active (the Session is fresh).
     */
    internal fun restoreHistory(history: List<Message>) {
        synchronized(monitor) {
            check(!activeRun) { "Cannot restore history while a Run is active" }
            messages.clear()
            messages.addAll(history)
            reconciliationRequired = false
        }
    }

    /**
     * Attempts to claim the single active-Run slot for this Session.
     *
     * Rejected if [activeRun] is already held OR if [reconciliationRequired]
     * is set (fail-closed: the durable history needs repair first).
     *
     * @return true if claimed, false if another Run already holds it or the
     *   Session requires reconciliation.
     */
    internal fun tryBeginRun(): Boolean = synchronized(monitor) {
        if (activeRun || reconciliationRequired) false
        else { activeRun = true; true }
    }

    internal fun endRun() {
        synchronized(monitor) { activeRun = false }
    }
}