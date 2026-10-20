package com.androidagent.app

import android.content.Context
import android.os.Bundle
import com.androidagent.app.debug.DebugBridge
import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelStreamObserver
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.runtime.AgentRuntime

/**
 * Process-scoped holder for the AGA Session state.
 *
 * AG.HOST-SESSION-CONTINUITY-0: the Session (AgentRuntime + ChatCompositionRoot +
 * TranscriptModel) must survive Activity recreation (rotation, config change,
 * window size class change). The holder is a process-level singleton (like the
 * existing `sharedDebugBridge` pattern) so that a new Activity instance can
 * rebind to the SAME Session without creating a replacement.
 *
 * This is NOT Session Persistence (STATE-003): the state lives in process memory
 * only. If the process dies, the state is gone. That is by design.
 */
object SessionHolder {

    @Volatile
    var root: ChatCompositionRoot? = null
        private set

    @Volatile
    var transcriptModel: TranscriptModel = TranscriptModel()
        private set

    @Volatile
    var providerNode: ProviderNode = ProviderNode.OPENROUTER
        private set

    @Volatile
    var providerStatusText: String? = null
        private set

    @Volatile
    var atStatusText: String? = null
        private set

    @Volatile
    var runStatusText: String? = null
        private set

    @Volatile
    var isBusy: Boolean = false
        private set

    /** The active node's safe endpoint (for UI rebind). */
    @Volatile
    var endpointLabel: String = ""
        private set

    /**
     * Create or return the existing Session. Called from ChatActivity.onCreate
     * or on provider node switch.
     */
    fun createRoot(
        cfg: OpenAiCompatibleConfig,
        context: Context,
        node: ProviderNode,
        onRunEvent: (AgentEvent) -> Unit,
        onDebugEvent: (AgentEvent) -> Unit,
        streamObserver: ModelStreamObserver,
    ) {
        val newRoot = ChatCompositionRoot(
            cfg,
            context.applicationContext,
            onRunEvent = onRunEvent,
            onDebugEvent = onDebugEvent,
            streamObserver = streamObserver,
        )
        root = newRoot
        providerNode = node
        transcriptModel = TranscriptModel()
        runStatusText = null
        atStatusText = null
        isBusy = false
    }

    /** The process-scoped AgentRuntime (survives Activity recreation). */
    val runtime: AgentRuntime?
        get() = root?.runtime

    /** Reset to a fresh Session (user tapped "New Session"). */
    fun newSession(): Boolean {
        val r = root?.runtime ?: return false
        val switched = r.newSession()
        if (switched) {
            transcriptModel = TranscriptModel()
            runStatusText = "New session started."
        }
        return switched
    }

    /**
     * Save Session state into [outState] for Activity recreation.
     * Only the identity is persisted — the actual state lives in this holder.
     */
    fun saveState(outState: Bundle) {
        outState.putString(KEY_SESSION_ID, root?.runtime?.currentSessionId)
        outState.putString(KEY_TRANSCRIPT, transcriptModel.render())
        outState.putString(KEY_PROVIDER_STATUS, providerStatusText)
        outState.putString(KEY_AT_STATUS, atStatusText)
        outState.putString(KEY_RUN_STATUS, runStatusText)
        outState.putString(KEY_ENDPOINT, endpointLabel)
        outState.putBoolean(KEY_BUSY, isBusy)
    }

    /**
     * Restore Session state after Activity recreation. Rebinds the UI to the
     * existing process-scoped holder. If the process was killed (holder empty),
     * returns false so the Activity can re-initialize.
     *
     * Returns true if a live Session was found and rebindable.
     */
    fun restoreState(savedState: Bundle?): Boolean {
        if (savedState == null) return false
        if (root == null) return false // process died; no Session to rebind

        val transcript = savedState.getString(KEY_TRANSCRIPT)
        if (transcript != null) {
            // Restore transcript from the saved rendered text.
            // The TranscriptModel is already in the holder (process-scoped);
            // if it has diverged (e.g. events arrived during recreation),
            // the holder's model is authoritative. We only restore if the
            // holder's model is empty (fresh) — otherwise keep the live one.
            if (transcriptModel.render().isEmpty() && transcript.isNotEmpty()) {
                transcriptModel = TranscriptModel.fromRendered(transcript)
            }
        }
        providerStatusText = savedState.getString(KEY_PROVIDER_STATUS)
        atStatusText = savedState.getString(KEY_AT_STATUS)
        runStatusText = savedState.getString(KEY_RUN_STATUS)
        endpointLabel = savedState.getString(KEY_ENDPOINT) ?: ""
        isBusy = savedState.getBoolean(KEY_BUSY)
        return true
    }

    fun setBusy(busy: Boolean) {
        isBusy = busy
    }

    fun setProviderStatus(text: String?) {
        providerStatusText = text
    }

    fun setAtStatus(text: String?) {
        atStatusText = text
    }

    fun setRunStatus(text: String?) {
        runStatusText = text
    }

    fun setEndpoint(text: String) {
        endpointLabel = text
    }

    // ---- UI update notification (Activity rebinds via this callback) --------

    @Volatile
    var onUiUpdate: (() -> Unit)? = null

    /** Called whenever transcript/status changes; the bound Activity re-renders. */
    fun notifyUiUpdate() {
        onUiUpdate?.invoke()
    }

    /**
     * The process-scoped event handler for AgentEvents. This is what gets wired
     * into ChatCompositionRoot — it operates on SessionHolder's transcriptModel
     * directly, NOT on any Activity instance.
     */
    fun onRunEvent(event: com.androidagent.core.AgentEvent) {
        runStatusText = com.androidagent.app.HostStatusModel.runPhaseText(event)
        when (event) {
            is com.androidagent.core.AgentEvent.ModelTurnStarted ->
                transcriptModel.onModelTurnStarted(event.step)
            is com.androidagent.core.AgentEvent.ToolCallStarted ->
                transcriptModel.onToolCallStarted(event.call)
            is com.androidagent.core.AgentEvent.ToolCallFinished ->
                transcriptModel.onToolCallFinished(event.call, event.result)
            is com.androidagent.core.AgentEvent.AnswerProduced ->
                transcriptModel.onAnswerProduced(event.content)
            is com.androidagent.core.AgentEvent.RunFailed ->
                transcriptModel.onRunFailed(event.failure)
            is com.androidagent.core.AgentEvent.RunFinished -> {
                if (event.outcome is com.androidagent.core.RunOutcome.Interrupted) {
                    transcriptModel.onInterrupted()
                }
                isBusy = false
            }
            else -> {}
        }
        notifyUiUpdate()
    }

    /** Process-scoped stream observer (reasoning/content deltas). */
    fun onStreamEvent(kind: String, text: String?) {
        when (kind) {
            "reasoning_started" -> transcriptModel.onReasoningStarted()
            "reasoning_delta" -> transcriptModel.onReasoningDelta(text ?: "")
            "reasoning_finished" -> transcriptModel.onReasoningFinished()
            "content_started" -> transcriptModel.onContentStarted()
            "content_delta" -> transcriptModel.onContentDelta(text ?: "")
            "content_finished" -> {}
        }
        notifyUiUpdate()
    }

    private const val KEY_SESSION_ID = "ag.session.id"
    private const val KEY_TRANSCRIPT = "ag.session.transcript"
    private const val KEY_PROVIDER_STATUS = "ag.session.provider_status"
    private const val KEY_AT_STATUS = "ag.session.at_status"
    private const val KEY_RUN_STATUS = "ag.session.run_status"
    private const val KEY_ENDPOINT = "ag.session.endpoint"
    private const val KEY_BUSY = "ag.session.busy"
}
