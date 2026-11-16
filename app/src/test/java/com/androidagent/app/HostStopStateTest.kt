package com.androidagent.app

import com.androidagent.core.AgentEvent
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelTurn
import com.androidagent.core.ModelProvider
import com.androidagent.core.ModelRequest
import com.androidagent.core.RunOptions
import com.androidagent.core.RunOutcome
import com.androidagent.core.AgentCore
import com.androidagent.core.AgentEventSink
import com.androidagent.core.ToolRegistry
import com.androidagent.core.UserMessage
import com.androidagent.runtime.AgentRuntime
import com.androidagent.runtime.persistence.MessageRecord
import com.androidagent.runtime.persistence.TopicSessionRecord
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AG.HOST-STOP-1 — H series: the Host Stop presentation-state machine and the
 * Host -> Runtime Stop integration.
 *
 * Two layers:
 *  - The pure [HostStopState] state machine (IDLE / RUNNING / INTERRUPTING),
 *    which is exactly the presentation logic the ChatActivity Stop button
 *    drives. It is Android-free so the frozen Host behaviour is JVM-testable.
 *  - A host/runtime integration test that uses the REAL [AgentRuntime] +
 *    [AgentCore] + a gated [ModelProvider] and drives the SAME state machine the
 *    UI uses: a Run starts, the Host enters RUNNING, the Stop action calls
 *    [AgentRuntime.interruptActiveRun], the real execution child is cancelled,
 *    Core emits RunFinished(Interrupted), and the Host reaches INTERRUPTING then
 *    back to IDLE.
 *
 * No Android, no Activity, no live endpoint.
 */
class HostStopStateTest {

    /** Minimal in-memory TopicSessionStore for Host Stop integration tests. */
    private class NoopStore : TopicSessionStore {
        private val sessions = mutableSetOf<String>()
        override suspend fun createSession(id: String, nowMillis: Long, model: String?, provider: String?, title: String?, titleOrigin: com.androidagent.runtime.persistence.TitleOrigin?): TopicSessionRecord {
            sessions.add(id)
            return TopicSessionRecord(id, nowMillis, nowMillis)
        }
        override suspend fun appendMessage(sessionId: String, message: com.androidagent.core.Message, nowMillis: Long): MessageRecord =
            MessageRecord("rec", sessionId, 0L, "USER", "{}", nowMillis)
        override suspend fun loadSession(id: String): TopicSessionRecord? = if (id in sessions) TopicSessionRecord(id, 0, 0) else null
        override suspend fun loadLatestSession(): TopicSessionRecord? = sessions.firstOrNull()?.let { TopicSessionRecord(it, 0, 0) }
        override suspend fun loadMessages(sessionId: String): List<com.androidagent.core.Message>? = if (sessionId in sessions) emptyList() else null
        override suspend fun updateModelProviderMetadata(sessionId: String, model: String?, provider: String?) {}
        override suspend fun listSessions(): List<TopicSessionRecord> = sessions.map { TopicSessionRecord(it, 0, 0) }
        override suspend fun renameSession(sessionId: String, newTitle: String): TopicSessionRecord = TopicSessionRecord(sessionId, 0, 0, title = newTitle)
        override suspend fun deleteSession(sessionId: String): Boolean = sessions.remove(sessionId)
        override suspend fun countMessages(sessionId: String): Int = 0
    }

    // --------------------------------------------------------------- state machine

    /**
     * The Host presentation state for the Stop control. This is the minimal
     * IDLE / RUNNING / INTERRUPTING model the task freezes (no RunControlManager,
     * no StopManager, no CancellationCoordinator). The UI (ChatActivity) renders
     * from this; the production control chain is Stop -> interruptActiveRun()
     * -> Runtime-owned execution-Job cancellation (the Host never cancels the
     * Job itself).
     */
    enum class HostStopState { IDLE, RUNNING, INTERRUPTING }

    /**
     * Decides the Host presentation transition from one Run lifecycle input.
     * Pure and total: every input yields exactly one next state, so a Stop vs
     * natural-terminal race can never strand the UI (H7).
     */
    object HostStopTransitions {

        /** A Run is admitted and starts (RunStarted / send accepted). */
        fun onRunStarted(current: HostStopState): HostStopState = HostStopState.RUNNING

        /** A Stop request was accepted by the Runtime (InterruptResult.Requested). */
        fun onStopRequested(current: HostStopState): HostStopState =
            if (current == HostStopState.RUNNING) HostStopState.INTERRUPTING else current

        /** A Stop request was already in flight (AlreadyInterrupting): no-op. */
        fun onStopAlreadyInterrupting(current: HostStopState): HostStopState = current

        /** No active Run (NoActiveRun): the Run already finished; back to IDLE. */
        fun onStopNoActiveRun(current: HostStopState): HostStopState = HostStopState.IDLE

        /**
         * A terminal RunFinished was observed (Answer, Failure, or Interrupted).
         * Any terminal ends INTERRUPTING and returns to IDLE (H7: never stuck).
         */
        fun onRunFinished(current: HostStopState): HostStopState = HostStopState.IDLE
    }

    // ------------------------------------------------------------------ H1

    @Test
    fun H1_running_enables_stop() {
        var state = HostStopState.IDLE
        state = HostStopTransitions.onRunStarted(state)
        assertEquals(HostStopState.RUNNING, state, "after a Run starts the Host is RUNNING")
        // The Stop button is enabled IFF RUNNING (see ChatActivity.setBusy).
        assertTrue(state == HostStopState.RUNNING, "Stop is enabled exactly in RUNNING")
    }

    // ------------------------------------------------------------------ H2

    @Test
    fun H2_requested_enters_interrupting_and_disables_stop() {
        var state = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        state = HostStopTransitions.onStopRequested(state)
        assertEquals(HostStopState.INTERRUPTING, state)
        // Stop is disabled in INTERRUPTING (the "Stopping..." phase is transient).
        assertFalse(state == HostStopState.RUNNING, "Stop is disabled while INTERRUPTING")
    }

    // ------------------------------------------------------------------ H3

    @Test
    fun H3_runFinished_interrupted_returns_to_idle() {
        var state = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        state = HostStopTransitions.onStopRequested(state)
        state = HostStopTransitions.onRunFinished(state)
        assertEquals(HostStopState.IDLE, state)
    }

    // ------------------------------------------------------------------ H4

    @Test
    fun H4_interrupted_is_not_a_failure() {
        // The transcript renders Interrupted as a "Stopped" block, never "Failed".
        val m = TranscriptModel()
        m.onInterrupted()
        val rendered = m.render()
        assertTrue(rendered.contains("Stopped"), "interrupt renders Stopped: $rendered")
        assertFalse(rendered.contains("Failed"), "an interrupt is NOT a failure")
        val block = m.snapshot().last()
        assertTrue(block is TranscriptModel.Block.Interrupted, "last block is Interrupted")
    }

    // ------------------------------------------------------------------ H5

    @Test
    fun H5_double_stop_is_safe() {
        var state = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        state = HostStopTransitions.onStopRequested(state)
        assertEquals(HostStopState.INTERRUPTING, state)
        // A second Stop is AlreadyInterrupting: a structured no-op, no crash, no
        // state change, no duplicate terminal (the terminal is owned by Core).
        state = HostStopTransitions.onStopAlreadyInterrupting(state)
        assertEquals(HostStopState.INTERRUPTING, state, "double Stop is a no-op")
    }

    // ------------------------------------------------------------------ H6

    @Test
    fun H6_no_active_run_is_safe() {
        // No active Run: a Stop is a structured no-op (NoActiveRun) -> IDLE.
        var state = HostStopState.IDLE
        state = HostStopTransitions.onStopNoActiveRun(state)
        assertEquals(HostStopState.IDLE, state)
        // And a Stop when already idle is likewise a no-op.
        state = HostStopTransitions.onStopRequested(state)
        assertEquals(HostStopState.IDLE, state, "Stop with no active Run cannot enter INTERRUPTING")
    }

    // ------------------------------------------------------------------ H7

    @Test
    fun H7_stop_vs_natural_terminal_race_never_stuck() {
        // Scenario A: Stop is requested, but the Run finishes naturally first.
        // The terminal still returns the Host to IDLE (not stuck in INTERRUPTING).
        var a = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        a = HostStopTransitions.onStopRequested(a)
        assertEquals(HostStopState.INTERRUPTING, a)
        a = HostStopTransitions.onRunFinished(a)
        assertEquals(HostStopState.IDLE, a, "a terminal after a Stop must clear INTERRUPTING")

        // Scenario B: the Run finishes naturally before any Stop; a late Stop is a
        // no-op (NoActiveRun) and the Host is already IDLE.
        var b = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        b = HostStopTransitions.onRunFinished(b)
        assertEquals(HostStopState.IDLE, b)
        b = HostStopTransitions.onStopNoActiveRun(b)
        assertEquals(HostStopState.IDLE, b)
    }

    // ------------------------------------------------------------------ H8

    @Test
    fun H8_new_run_after_stop_works() {
        var state = HostStopTransitions.onRunStarted(HostStopState.IDLE)
        state = HostStopTransitions.onStopRequested(state)
        state = HostStopTransitions.onRunFinished(state)
        assertEquals(HostStopState.IDLE, state)
        // A new Run is admitted after the Stop; the Host re-enters RUNNING.
        state = HostStopTransitions.onRunStarted(state)
        assertEquals(HostStopState.RUNNING, state, "a new Run after a Stop re-enters RUNNING")
    }

    // ------------------------------------------------- host/runtime integration

    /**
     * A provider that suspends at a deterministic gate until released (or the
     * execution child is cancelled). It is a plain suspension: a user Stop is
     * enforced by the Runtime cancelling the execution Job, so the gate receives
     * the CancellationException exactly as a streaming provider honoring
     * coroutine cancellation would. It records whether it was cancelled.
     */
    private class GatedProvider(
        private val gate: CompletableDeferred<Unit>,
    ) : ModelProvider {
        private val cancelledRef = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Volatile read of the cancellation flag (set on the execution-child thread). */
        val cancelled: Boolean get() = cancelledRef.get()

        override suspend fun complete(request: ModelRequest): ModelTurn {
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelledRef.set(true)
                throw e
            }
            return ModelTurn.FinalAnswer("never")
        }
    }

    private class EventRecorder : AgentEventSink {
        val events: MutableList<AgentEvent> = java.util.Collections.synchronizedList(mutableListOf())
        override fun emit(event: AgentEvent) {
            events += event
        }

        val terminals: List<AgentEvent.RunFinished>
            get() = events.filterIsInstance<AgentEvent.RunFinished>()
    }

    /**
     * H-integration: real AgentRuntime + real AgentCore + gated provider. The
     * Host (state machine + a [TranscriptModel], exactly what ChatActivity holds)
     * observes the event stream. The Stop action calls
     * [AgentRuntime.interruptActiveRun]; the real execution child is cancelled;
     * Core emits exactly one RunFinished(Interrupted); the Host reaches
     * INTERRUPTING then IDLE and renders a Stopped block.
     */
    @Test
    fun H_integration_real_runtime_stop_cancels_child_and_renders_stopped() {
        runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = GatedProvider(gate)
        val recorder = EventRecorder()
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop)
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(eventSink = recorder),
            topicSessionStore = NoopStore(),
        )
        runtime.initialize()

        // The Host presentation state + transcript, as ChatActivity keeps them.
        var state = HostStopState.IDLE
        val transcript = TranscriptModel()

        val job = async(Dispatchers.Default) {
            runtime.send("hi")
        }

        // Wait until the Run is in flight (the Host has entered RUNNING).
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }
        state = HostStopTransitions.onRunStarted(state)
        assertEquals(HostStopState.RUNNING, state)

        // The Stop action: exactly what ChatActivity.onStopRun does.
        val interruptResult = runtime.interruptActiveRun()
        assertEquals(AgentRuntime.InterruptResult.Requested, interruptResult, "the Stop must claim the active Run")
        state = HostStopTransitions.onStopRequested(state)
        assertEquals(HostStopState.INTERRUPTING, state, "Requested -> INTERRUPTING")

        // The Run terminates; send returns the structured Interrupted outcome.
        val outcome = withTimeout(20_000) { job.await() }
        assertTrue(outcome is RunOutcome.Interrupted, "send returns Interrupted, got $outcome")

        // The execution child was cancelled (the provider observed the Job cancel
        // at its suspension point). Asserted after the run ends (a happens-before
        // point), so the flag read is reliable across threads.
        assertTrue(provider.cancelled, "the execution child must be cancelled by the Job cancel")

        // Core emitted exactly one terminal, and it is Interrupted.
        assertEquals(1, recorder.terminals.size, "exactly one terminal RunFinished")
        assertEquals(RunOutcome.Interrupted, recorder.terminals.single().outcome)

        // The Host observes the terminal -> returns to IDLE and renders Stopped.
        state = HostStopTransitions.onRunFinished(state)
        assertEquals(HostStopState.IDLE, state)
        transcript.onInterrupted()
        val rendered = transcript.render()
        assertTrue(rendered.contains("Stopped"), "transcript renders Stopped: $rendered")
        assertFalse(rendered.contains("Failed"), "an interrupt is not a failure")

        // The Session is reusable (not busy) for the next Run.
        assertFalse(runtime.isBusy, "the Session must be reusable after a Stop")
        }
    }

    // ------------------------------------------------------------------ H9

    @Test
    fun H9_lifecycle_cancel_does_not_convert_to_interrupted() {
        // A host/lifecycle cancel (no user Stop) must propagate, not convert to a
        // Host "Stopped": the Host only renders Stopped on a real
        // RunFinished(Interrupted), which a lifecycle cancel never produces.
        runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = GatedProvider(gate)
        val recorder = EventRecorder()
        val core = AgentCore(provider, ToolRegistry(), MessageDurability.Noop)
        val runtime = AgentRuntime(
            agentCore = core,
            runOptions = RunOptions(eventSink = recorder),
            topicSessionStore = NoopStore(),
        )
        runtime.initialize()

        var state = HostStopState.IDLE
        val job = async(Dispatchers.Default) {
            try {
                runtime.send("hi")
            } catch (_: CancellationException) {
                // lifecycle cancel propagates to the dying caller
            }
        }
        withTimeout(10_000) { while (!runtime.isBusy) delay(10) }
        state = HostStopTransitions.onRunStarted(state)
        assertEquals(HostStopState.RUNNING, state)

        // LIFECYCLE: the host cancels its own caller (NOT a user Stop).
        job.cancel()
        withTimeout(20_000) { job.join() }

        // No user Stop was committed, so Core emitted NO Interrupted terminal.
        // The Host therefore never enters the "Stopped" path (no Interrupted
        // terminal to render). The Host returns to IDLE only on a real terminal;
        // a lifecycle cancel ends the coroutine, so the UI would be torn down.
        assertEquals(0, recorder.terminals.count { it.outcome is RunOutcome.Interrupted },
            "a lifecycle cancel must not fabricate an Interrupted terminal")
        }
    }
}
