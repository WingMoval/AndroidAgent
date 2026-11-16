package com.androidagent.runtime

import com.androidagent.core.AgentCore
import com.androidagent.core.Message
import com.androidagent.core.ModelMessage
import com.androidagent.core.RunFailure
import com.androidagent.core.RunInterruptSignal
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResultMessage
import com.androidagent.runtime.persistence.MessageDecodeException
import com.androidagent.runtime.persistence.PersistenceException
import com.androidagent.runtime.persistence.TitleOrigin
import com.androidagent.runtime.persistence.TopicSessionStore
import com.androidagent.runtime.persistence.UnrecoverableSessionHistoryException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Host-neutral Agent lifecycle: which Session a host is currently talking to, and
 * how to drive one Run at a time — including the user-facing Stop.
 *
 * This is deliberately **not** a second intelligence core. It owns no history, no
 * Agent Loop, no tool registry and no provider. The Agent history SSOT remains the
 * AG Core Session; every Run is executed by AG Core's [AgentCore.run]. This type
 * decides *which* Session the host is using, serialises access, and owns the
 * mechanism for interrupting the active Run.
 *
 * Layer contract:
 *  - AG Core owns Session/Run *semantics* (including the interrupted outcome and the
 *    interrupted-history finalization).
 *  - [AgentRuntime] owns the host's *current Session selection*, the *cancellation
 *    mechanism* (the interrupt entrypoint + the active Run's execution Job), and the
 *    *process startup create-or-restore* lifecycle (Phase G).
 *  - The Composition Root owns provider/transport *construction*.
 *  - The frontend owns input, rendering and platform lifecycle.
 *
 * Process startup create-or-restore (Phase G):
 *  - [initialize] is the process-scoped startup entrypoint. It loads the latest
 *    durable session, validates its history, repairs any dangling tool calls
 *    (durable-first), reloads, revalidates, hydrates Core, and publishes the
 *    current session id. If no session exists, it creates a new one.
 *  - [initialize] is idempotent: after a successful startup, subsequent calls
 *    return the same session id without re-loading/re-creating/re-repairing.
 *  - Concurrent [initialize] calls are single-flight (Mutex): one startup
 *    lifecycle, one restore/create, one repair sequence.
 *
 * Transient blank draft (AG.SESSION-MANAGEMENT-UX-0):
 *  - [enterBlankDraft] does NOT create a durable row. It only clears the live
 *    Core Session to an empty, untitled draft. NO USER MESSAGE = NO SESSION:
 *    a blank draft is not durable, not listed, and has no title.
 *  - The durable TopicSession is MATERIALIZED lazily on the FIRST non-blank
 *    User Message: [send] creates the durable row (title = first message,
 *    origin = AUTO_FIRST_MESSAGE), persists the first message, then hydrates
 *    Core and runs. Materialization does NOT depend on model success.
 *  - The automatic title runs ONCE (at materialization). Later messages never
 *    change it. A manual rename (USER_RENAMED) wins permanently.
 */
class AgentRuntime(
    private val agentCore: AgentCore,
    private val runOptions: RunOptions = RunOptions(),
    private val sessionIdFactory: () -> String = { "session-" + UUID.randomUUID() },
    private val topicSessionStore: TopicSessionStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * The per-Run control handle: the actual execution [Job], the user-interrupt
     * claim, and the per-Run acknowledgement that AG Core committed the Interrupted
     * terminal finalization. This is the only active-Run state the Runtime holds.
     * It is a small state holder, not a subsystem.
     */
    private class ActiveRunControl(
        val interruptRequested: AtomicBoolean,
        @Volatile var executionJob: Job,
    ) {
        val interruptedFinalized = AtomicBoolean(false)
    }

    // The active Run's control handle (atomic ownership; never a bare @Volatile Job).
    private val activeRun = AtomicReference<ActiveRunControl?>(null)

    // Backward-compatible busy flag, driven in lock-step with [activeRun].
    private val runActive = AtomicBoolean(false)

    // ---- Phase G: process startup state ----

    /** Minimal startup state (frozen: 4 states). */
    private enum class StartupState { UNINITIALIZED, INITIALIZING, READY, FAILED }

    @Volatile
    private var startupState: StartupState = StartupState.UNINITIALIZED

    /** Single-flight mutex for startup (per-instance, coroutine-safe). */
    private val startupMutex = Mutex()

    /**
     * The current durable TopicSession id, or null when no durable session has
     * been created yet.
     *
     * A non-null value GUARANTEES that:
     *  1. A matching durable TopicSession row exists in the store.
     *  2. The Core Session has been hydrated (restoreSession called) with the
     *     final durable history (including any synthetic repairs).
     *
     * This is the SESSION_ROW_EXISTS_BEFORE_CURRENT + CORE_HYDRATED invariant.
     */
    @Volatile
    private var sessionId: String? = null

    /**
     * Transient blank-draft flag (AG.SESSION-MANAGEMENT-UX-0).
     *
     * When true, the live Core Session is an empty, untitled draft that has NOT
     * been durably committed. [sessionId] is null. The draft is NOT durable,
     * NOT listed, and has no title. The durable TopicSession is created lazily
     * by [send] on the first non-blank User Message (see [materializeDraft]).
     *
     * This is a pure runtime/UI state flag — it is never persisted. A process
     * restart discards it (RESTORE_EMPTY_DRAFT = NO).
     */
    @Volatile
    private var blankDraft: Boolean = false

    /** Identifier of the current Session, or null when no durable session exists yet. */
    val currentSessionId: String? get() = sessionId

    /**
     * True when the host is in a transient blank draft (no current durable
     * session). The durable TopicSession does not exist yet; it is created on
     * the first non-blank User Message. (AG.SESSION-MANAGEMENT-UX-0)
     */
    val inBlankDraft: Boolean get() = blankDraft

    /**
     * Read-only, host-neutral seam to the current Session's restored/ live
     * history (Phase I transcript re-projection).
     *
     *  - Returns a defensive snapshot of the Core [Session.history] for the
     *    CURRENT session (the one [initialize] restored or created).
     *  - Returns an empty list when no current session exists (UNINITIALIZED /
     *    FAILED / not yet READY).
     *  - Pure read: no persistence side effect, no DB access, no model/tool
     *    execution. The Core Session is the live materialization of the durable
     *    history (restore does not re-persist), so reading it is the single
     *    source of truth for the Host's transcript projection.
     *
     * The Host (e.g. SessionHolder) maps this history into its presentation
     * model (TranscriptModel). This seam deliberately does NOT expose Room/SQLite
     * or the store to the UI.
     */
    fun currentHistory(): List<Message> {
        val currentId = sessionId ?: return emptyList()
        return agentCore.session(currentId).history()
    }

    /**
     * Whether a Run is in flight.
     */
    val isBusy: Boolean get() = runActive.get()

    /**
     * The result of an interrupt *request*.
     */
    sealed interface InterruptResult {
        data object Requested : InterruptResult
        data object NoActiveRun : InterruptResult
        data object AlreadyInterrupting : InterruptResult
    }

    // ---- Phase G: startup create-or-restore ----

    /**
     * Process startup create-or-restore (Phase G).
     *
     * Semantics:
     *  - If already READY: return the current session id (idempotent, no I/O).
     *  - Otherwise: single-flight (Mutex) startup:
     *    1. [TopicSessionStore.loadLatestSession]
     *    2a. null → create new durable session → Core empty session → READY
     *    2b. found → load messages → validate → repair dangling → reload →
     *        revalidate → Core restoreSession → READY
     *  - On failure: state = FAILED, no current session published.
     *    A future [initialize] call retries the startup.
     *
     * @return the current session id (new or restored).
     * @throws PersistenceException / MessageDecodeException on unrecoverable failure.
     */
    suspend fun initialize(): String {
        // Idempotent: already READY → return immediately (no I/O).
        if (startupState == StartupState.READY) {
            return sessionId!!
        }
        // Single-flight: only one startup at a time.
        return startupMutex.withLock {
            // Re-check inside the lock (another caller may have completed startup).
            if (startupState == StartupState.READY) {
                return@withLock sessionId!!
            }
            startupState = StartupState.INITIALIZING
            try {
                val result = doStartup()
                startupState = StartupState.READY
                sessionId = result
                result
            } catch (e: Exception) {
                startupState = StartupState.FAILED
                sessionId = null
                throw e
            }
        }
    }

    /**
     * The actual startup logic (runs under the [startupMutex]).
     *
     * Recovery policy (frozen, Phase G NORMALIZE-1):
     *  - Branch A (no session): create a fresh durable session.
     *  - Branch B (session exists): restore it. If its durable HISTORY is
     *    session-level UNRECOVERABLE ([UnrecoverableSessionHistoryException]:
     *    missing history, sequence gap, malformed/unknown row), the corrupt
     *    session is PRESERVED and the Runtime recovers with a controlled fresh
     *    session. A backend failure ([PersistenceException] on load/repair)
     *    FAILS CLOSED — no recovery, no current published.
     */
    private suspend fun doStartup(): String {
        val latest = topicSessionStore.loadLatestSession()
        return if (latest == null) {
            // Branch A: fresh store → create new session.
            createNewDurableSession()
        } else {
            // Branch B: existing session → load, validate, repair, restore.
            try {
                restoreExistingSession(latest.id)
            } catch (e: UnrecoverableSessionHistoryException) {
                // Session-level invalid history: preserve the corrupt session
                // (never delete) and recover with a controlled fresh session.
                // If the fresh create itself fails, that failure propagates
                // (FAILED, no current published) — the corrupt session is intact.
                recoverWithFreshSession()
            }
            // PersistenceException (backend failure) is NOT caught here: it
            // propagates to initialize() → FAILED (fail closed, no recovery).
        }
    }

    /**
     * Controlled fresh recovery for a session whose durable history is
     * UNRECOVERABLE. The corrupt old session (and its messages) are preserved;
     * a brand-new durable TopicSession is created and becomes current.
     *
     * Ordering (frozen): new durable row committed → Core empty Session
     * restored → publish (done by [initialize] after this returns).
     *
     * @return the new session id.
     * @throws PersistenceException if the fresh createSession fails (no recovery;
     *   the corrupt old session remains preserved and no new current is published).
     */
    private suspend fun recoverWithFreshSession(): String = createNewDurableSession()

    /**
     * Creates a brand-new durable TopicSession and makes it current.
     *
     * Used by both [initialize] (Branch A: no existing session) and [newSession].
     *
     * Flow:
     *  1. Generate id via [sessionIdFactory] (pure).
     *  2. [TopicSessionStore.createSession] (durable commit).
     *  3. Core [AgentCore.restoreSession] with empty history.
     *  4. Publish [sessionId].
     *
     * @throws PersistenceException on store failure.
     */
    private suspend fun createNewDurableSession(): String {
        val id = sessionIdFactory()
        topicSessionStore.createSession(id, nowMillis())
        // Core empty session (restore with empty list = no history, no durability).
        agentCore.restoreSession(id, emptyList())
        return id
    }

    /**
     * Restores an existing durable session: load → validate → repair → reload →
     * revalidate → Core restore.
     *
     * @throws PersistenceException / MessageDecodeException on unrecoverable state.
     */
    private suspend fun restoreExistingSession(sessionId: String): String {
        // Load durable history. A session row that exists but has no loadable
        // history is a SESSION-LEVEL inconsistency (UNRECOVERABLE), not an empty
        // history. A decode failure (malformed/unknown row) is likewise
        // session-level UNRECOVERABLE. In both cases the Runtime recovers with a
        // controlled fresh session (the corrupt session is preserved).
        val history = loadHistoryOrUnrecoverable(sessionId)

        // Check for dangling tool calls.
        val dangling = findDanglingToolCalls(history)

        // Repair if necessary (durable-first).
        if (dangling.isNotEmpty()) {
            repairDanglingToolCalls(sessionId, dangling)
        }

        // Reload after repair (must materialize from FINAL durable history).
        val finalHistory = if (dangling.isNotEmpty()) {
            loadHistoryOrUnrecoverable(sessionId)
        } else {
            history
        }

        // Revalidate after repair (dangling must now be zero).
        val remainingDangling = findDanglingToolCalls(finalHistory)
        check(remainingDangling.isEmpty()) {
            "Dangling tool calls remain after repair: $remainingDangling"
        }

        // Core hydration (does NOT trigger MessageDurability).
        agentCore.restoreSession(sessionId, finalHistory)
        return sessionId
    }

    /**
     * Loads a session's durable history, classifying session-level invalid
     * history as [UnrecoverableSessionHistoryException]:
     *  - null (row exists, no loadable history) → "session_history_inconsistent"
     *  - [MessageDecodeException] (malformed / unknown row) → "malformed_payload"
     *  - [UnrecoverableSessionHistoryException] (e.g. sequence gap, raised by the
     *    Room backend) → propagated as-is.
     * A backend [PersistenceException] (IO/SQLite/operation failure) is NOT
     * converted: it propagates so startup FAILS CLOSED (no recovery).
     */
    private suspend fun loadHistoryOrUnrecoverable(sessionId: String): List<Message> {
        val history = try {
            topicSessionStore.loadMessages(sessionId)
        } catch (e: UnrecoverableSessionHistoryException) {
            throw e // already classified (e.g. Room sequence continuity violation)
        } catch (e: MessageDecodeException) {
            throw UnrecoverableSessionHistoryException(
                sessionId = sessionId,
                category = "malformed_payload",
                cause = e,
            )
        }
        return history ?: throw UnrecoverableSessionHistoryException(
            sessionId = sessionId,
            category = "session_history_inconsistent",
        )
    }

    /**
     * Detects dangling tool calls in a durable history: every ToolCall in a
     * ModelMessage that has no matching ToolResultMessage.
     */
    private fun findDanglingToolCalls(history: List<Message>): List<ToolCall> {
        val resultIds = history
            .filterIsInstance<ToolResultMessage>()
            .mapTo(HashSet()) { it.toolCallId }
        val dangling = mutableListOf<ToolCall>()
        for (msg in history) {
            if (msg !is ModelMessage || msg.toolCalls.isEmpty()) continue
            for (call in msg.toolCalls) {
                if (call.id !in resultIds) {
                    dangling.add(call)
                }
            }
        }
        return dangling
    }

    /**
     * Durably repairs dangling tool calls by appending canonical synthetic
     * ToolResults (durable-first: each append is a deterministic commit).
     *
     * Uses [AgentCore.syntheticInterruptedToolResult] for the canonical UNKNOWN
     * semantics (shared with same-process interrupt closeout).
     *
     * Each repair append is shielded (ensureActive + NonCancellable) so the
     * transaction reaches a deterministic COMMIT or ROLLBACK.
     *
     * @throws PersistenceException if any repair append fails (partial repair:
     *   successfully appended synthetics remain durable; retry will skip them).
     */
    private suspend fun repairDanglingToolCalls(
        sessionId: String,
        dangling: List<ToolCall>,
    ) {
        for (call in dangling) {
            // Honour cancellation BEFORE the repair append begins.
            coroutineContext.ensureActive()
            val synthetic = AgentCore.syntheticInterruptedToolResult(call, inFlight = true)
            withContext(NonCancellable) {
                try {
                    topicSessionStore.appendMessage(sessionId, synthetic, nowMillis())
                } catch (e: PersistenceException) {
                    throw e
                } catch (e: MessageDecodeException) {
                    throw e
                } catch (e: Exception) {
                    throw PersistenceException(e, category = "repair_append_failed")
                }
            }
        }
    }

    // ---- AG.SESSION-MANAGEMENT-UX-0: transient blank draft ----

    /**
     * Enters a transient BLANK DRAFT (user "New Session").
     *
     * NO USER MESSAGE = NO SESSION. This does NOT create a durable TopicSession
     * row. It only:
     *  1. Clears the live Core Session to an empty, untitled draft
     *     ([AgentCore.restoreSession] with an empty list — a hydration, not a
     *     durability write), and
     *  2. Marks the runtime in [blankDraft] (sessionId stays null).
     *
     * The previous durable session is PRESERVED (not deleted, only no longer
     * current). The draft is not durable, not listed, and has no title.
     * Repeated calls are idempotent and never create DB rows
     * (REPEATED_EMPTY_NEW_SESSION_CREATES_DB_ROWS = NO).
     *
     * The durable TopicSession is created LAZILY by [send] on the first
     * non-blank User Message ([materializeDraft]).
     *
     * Rejected while a Run is active (no half-switch mid-Run).
     *
     * @return true if the draft was entered, false if rejected because busy.
     */
    fun enterBlankDraft(): Boolean {
        if (isBusy) return false
        if (startupState != StartupState.READY) return false
        // Clear the live Core Session to an empty untitled draft (no DB write).
        // A DRAFT id is used for the Core Session; it is never committed as a
        // durable row. If the user sends a first message, [materializeDraft]
        // creates the real durable id and re-hydrates Core under it.
        agentCore.restoreSession(DRAFT_CORE_ID, emptyList())
        sessionId = null
        blankDraft = true
        return true
    }

    /**
     * The placeholder id used to hydrate the live Core Session while in a
     * transient blank draft. It is NEVER a durable TopicSession id and is never
     * written to the store.
     */
    private companion object {
        const val DRAFT_CORE_ID = "__blank_draft__"
    }

    /**
     * Lazily materializes the durable TopicSession on the FIRST non-blank User
     * Message (AG.SESSION-MANAGEMENT-UX-0).
     *
     * Ordering (frozen, two-step, model-success-independent):
     *  1. [TopicSessionStore.createSession] with title = first message,
     *     titleOrigin = AUTO_FIRST_MESSAGE (durable row committed).
     *  2. [AgentCore.restoreSession] under the REAL durable id with the empty
     *     history — this re-hydrates Core so the Run's durability gate persists
     *     the first User Message to the REAL session (the user message is
     *     committed by AG Core BEFORE the model call, so a model failure still
     *     leaves Session + UserMessage + title).
     *
     * The two steps are ordered so that Core can NEVER be hydrated with an
     * EMPTY history while the durable row has messages (that combination is
     * classified UNRECOVERABLE). At this point the row has zero messages, so
     * step 2 is always safe.
     *
     * @return the new durable session id.
     * @throws PersistenceException if the store fails to create the session
     *   (the draft is then cleared so the next send retries).
     */
    private suspend fun materializeDraft(firstUserMessage: String): String {
        val id = sessionIdFactory()
        val title = normalizeTitle(firstUserMessage)
        topicSessionStore.createSession(
            id = id,
            nowMillis = nowMillis(),
            title = title,
            titleOrigin = TitleOrigin.AUTO_FIRST_MESSAGE,
        )
        // Re-hydrate Core under the real durable id (empty history — the row
        // has no messages yet). The Run then appends the first User Message to
        // this session via the durability gate.
        agentCore.restoreSession(id, emptyList())
        sessionId = id
        blankDraft = false
        return id
    }

    /**
     * Deterministic title normalization (AG.SESSION-MANAGEMENT-UX-0): trim
     * leading/trailing whitespace and collapse internal line breaks to single
     * spaces. NO LLM, NO AI summary, NO regeneration. The UI ellipsizes long
     * titles visually; the durable title is never truncated by UI width.
     */
    private fun normalizeTitle(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    /**
     * Durably renames the CURRENT session (AG.SESSION-MANAGEMENT-UX-0).
     *
     * Metadata-only: updates the title + origin (USER_RENAMED) in the durable
     * store. It does NOT touch the message history, last_used_at, Core, or
     * trigger any model/tool call.
     *
     * Rejected when not in a durable session (blank draft / no session) or
     * while a Run is active (the Host also disables this in the UI).
     *
     * @return true if renamed, false if rejected (no current session / busy).
     * @throws SessionNotFoundException / PersistenceException on store failure.
     */
    suspend fun renameCurrentSession(newTitle: String): Boolean {
        if (isBusy) return false
        val currentId = sessionId ?: return false
        topicSessionStore.renameSession(currentId, newTitle)
        return true
    }

    /**
     * Permanently deletes a durable TopicSession and everything it owns
     * (AG.SESSION-MANAGEMENT-UX-0).
     *
     * Ownership-scoped: removes the session row + its messages via the FK
     * ON DELETE CASCADE contract. It does NOT touch other sessions, provider
     * config, API keys, AT identity, or the workspace.
     *
     * If the deleted session is the CURRENT one, the runtime enters a transient
     * blank draft (DELETE_CURRENT -> BLANK DRAFT). No replacement durable row is
     * created (DELETE_CURRENT_CREATES_DURABLE_REPLACEMENT = NO).
     *
     * Rejected while a Run is active (DELETE_DOES_NOT_IMPLICITLY_INTERRUPT_RUN
     * = YES; the Host also disables delete for busy sessions).
     *
     * @return [DeleteResult.Deleted] with the new current id (null if a blank
     *   draft was entered), or [DeleteResult.Rejected].
     */
    suspend fun deleteSession(sessionId: String): DeleteResult {
        if (isBusy) return DeleteResult.Rejected("busy")
        val existed = topicSessionStore.deleteSession(sessionId)
        if (!existed) return DeleteResult.Rejected("session not found")
        if (this.sessionId == sessionId) {
            // Deleted the current session: enter a transient blank draft.
            // Reuse the draft hydration (no durable row is created).
            agentCore.restoreSession(DRAFT_CORE_ID, emptyList())
            this.sessionId = null
            blankDraft = true
            return DeleteResult.Deleted(currentAfter = null)
        }
        // Deleted a non-current session: the current session is unchanged.
        return DeleteResult.Deleted(currentAfter = this.sessionId)
    }

    /** Outcome of [AgentRuntime.deleteSession]. */
    sealed interface DeleteResult {
        /** A session was deleted. [currentAfter] is the new current durable id (null = blank draft). */
        data class Deleted(val currentAfter: String?) : DeleteResult
        /** The delete was rejected (busy or session not found). */
        data class Rejected(val reason: String) : DeleteResult
    }

    // ---- AG.UI-TRANSCRIPT-CARDS-1: Session switching (additive) ----

    /**
     * Switches the current session to an EXISTING durable session.
     *
     * AG.UI-TRANSCRIPT-CARDS-1: ADDITIVE runtime seam for the Session Browser.
     * Reuses the existing `restoreExistingSession` pattern (load history,
     * repair dangling tool calls, hydrate Core).
     *
     * Rules:
     *  - Rejected if a Run is active (isBusy).
     *  - Rejected if the session id does not exist in the store.
     *  - On success: sessionId = targetId, Core is hydrated with target history.
     *  - The old session is preserved (not deleted, not modified).
     *
     * @param targetSessionId The durable session id to switch to.
     * @return true if switched, false if rejected (busy or not found).
     * @throws PersistenceException if the store fails to load the session.
     */
    suspend fun switchSession(targetSessionId: String): Boolean {
        if (isBusy) return false
        if (startupState != StartupState.READY) {
            initialize()
        }
        // Verify the target session exists in the store.
        val record = topicSessionStore.loadSession(targetSessionId)
            ?: return false
        // Reuse the existing restore pattern (load + repair + hydrate).
        restoreExistingSession(targetSessionId)
        sessionId = targetSessionId
        return true
    }

    // ---- Run execution (unchanged from Phase 0F) ----

    /**
     * Runs one user turn against the current Session in the caller's structured
     * coroutine and returns the AG Core outcome verbatim.
     *
     * If no durable session is current ([currentSessionId] is null), the send is
     * rejected with [RunFailure.SessionBusy] — no D1, no model call.
     */
    suspend fun send(userInput: String): RunOutcome {
        // AG.SESSION-MANAGEMENT-UX-0: materialize the transient blank draft on
        // the first non-blank User Message. The durable TopicSession (title =
        // first message, origin = AUTO_FIRST_MESSAGE) is created BEFORE the run
        // proceeds, and does NOT depend on model success.
        if (blankDraft) {
            if (userInput.isBlank()) {
                // A blank send in a draft is a no-op (no session is created).
                return RunOutcome.Failure(RunFailure.SessionBusy("no-session"))
            }
            materializeDraft(userInput)
        }
        val currentId = sessionId
            ?: return RunOutcome.Failure(RunFailure.SessionBusy("no-session"))
        if (!runActive.compareAndSet(false, true)) {
            return RunOutcome.Failure(RunFailure.SessionBusy(currentId))
        }
        var controlRef: ActiveRunControl? = null
        try {
            val control = ActiveRunControl(
                interruptRequested = AtomicBoolean(false),
                executionJob = Job(),
            )
            controlRef = control
            activeRun.set(control)
            val outcome = coroutineScope {
                val executionJob = async {
                    control.executionJob = coroutineContext[Job]!!
                    agentCore.run(
                        sessionId = currentId,
                        userInput = userInput,
                        options = runOptions,
                        interruptSignal = RunInterruptSignal.of(
                            isUserInterrupted = { control.interruptRequested.get() },
                            onInterruptedFinalized = { control.interruptedFinalized.set(true) },
                        ),
                    )
                }
                executionJob.await()
            }
            return outcome
        } catch (e: CancellationException) {
            val control = controlRef
            val callerActive = coroutineContext.isActive
            if (callerActive &&
                control != null &&
                control.interruptRequested.get() &&
                control.interruptedFinalized.get()
            ) {
                return RunOutcome.Interrupted
            }
            throw e
        } finally {
            activeRun.set(null)
            runActive.set(false)
        }
    }

    /**
     * Requests interruption of the active Run (the user Stop).
     */
    fun interruptActiveRun(): InterruptResult {
        val control = activeRun.get() ?: return InterruptResult.NoActiveRun
        if (!control.interruptRequested.compareAndSet(false, true)) {
            return InterruptResult.AlreadyInterrupting
        }
        control.executionJob.cancel(UserInterruptException())
        return InterruptResult.Requested
    }

    private fun controlFor(job: Job): ActiveRunControl? =
        activeRun.get()?.takeIf { it.executionJob === job }
}

/**
 * The cause attached to the execution Job when a user Stop is committed.
 */
private class UserInterruptException : CancellationException("Run interrupted by user Stop")
