package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-neutral Agent lifecycle: which Session a host is currently talking to, and
 * how to drive one Run at a time.
 *
 * This is deliberately **not** a second intelligence core. It owns no history, no
 * Agent Loop, no tool registry and no provider. The Agent history SSOT remains
 * the AG Core Session; every Run is executed by AG Core's [AgentCore.run]. This
 * type only decides *which* Session the host is using and serialises access.
 *
 * Layer contract:
 *  - AG Core owns Session/Run *semantics*.
 *  - [AgentRuntime] owns the host's *current Session selection*.
 *  - The Composition Root owns provider/transport *construction*.
 *  - The frontend owns input, rendering and platform lifecycle.
 *
 * Pure Kotlin/JVM: no Android, no UI, no HTTP, no provider implementation.
 */
class AgentRuntime(
    private val agentCore: AgentCore,
    private val runOptions: RunOptions = RunOptions(),
    private val sessionIdFactory: () -> String = { "session-" + UUID.randomUUID() },
) {

    private val runActive = AtomicBoolean(false)

    @Volatile
    private var sessionId: String = sessionIdFactory()

    /** Identifier of the Session this runtime is currently using. */
    val currentSessionId: String get() = sessionId

    /**
     * Whether a Run is in flight.
     *
     * A host-level guard so AGA and AGC both get "one send at a time" for free.
     * It does **not** replace the frozen AG Core rule (one active Run per
     * Session); that remains the final protection.
     */
    val isBusy: Boolean get() = runActive.get()

    /**
     * Runs one user turn against the current Session.
     *
     * @return the AG Core outcome verbatim. A rejected send returns
     *   [RunOutcome.Failure] with [RunFailure.SessionBusy] rather than inventing
     *   a Runtime-specific result type. Cancellation propagates unchanged and the
     *   busy flag is released either way.
     */
    suspend fun send(userInput: String): RunOutcome {
        if (!runActive.compareAndSet(false, true)) {
            return RunOutcome.Failure(RunFailure.SessionBusy(sessionId))
        }
        return try {
            agentCore.run(sessionId, userInput, runOptions)
        } finally {
            runActive.set(false)
        }
    }

    /**
     * Switches to a brand-new Session. The previous one stays intact inside AG
     * Core — it is never deleted, only no longer current.
     *
     * Rejected while a Run is active: switching Sessions mid-Run would be a way
     * to quietly escape the single-active-run guarantee.
     *
     * @return true if switched, false if rejected because busy.
     */
    fun newSession(): Boolean {
        if (isBusy) return false
        sessionId = sessionIdFactory()
        return true
    }
}