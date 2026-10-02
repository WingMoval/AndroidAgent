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

    /** Snapshot of the conversation so far, oldest first. */
    fun history(): List<Message> = synchronized(monitor) { messages.toList() }

    internal fun append(message: Message) {
        synchronized(monitor) { messages.add(message) }
    }

    /**
     * Attempts to claim the single active-Run slot for this Session.
     *
     * @return true if claimed, false if another Run already holds it.
     */
    internal fun tryBeginRun(): Boolean = synchronized(monitor) {
        if (activeRun) false else { activeRun = true; true }
    }

    internal fun endRun() {
        synchronized(monitor) { activeRun = false }
    }
}