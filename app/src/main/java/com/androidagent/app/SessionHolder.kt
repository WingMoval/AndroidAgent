package com.androidagent.app

import android.content.Context
import android.os.Bundle
import com.androidagent.app.debug.DebugBridge
import com.androidagent.app.persistence.RoomTopicSessionStore
import com.androidagent.app.persistence.StoreMessageDurability
import com.androidagent.app.persistence.TopicSessionDatabase
import com.androidagent.core.AgentEvent
import com.androidagent.core.MessageDurability
import com.androidagent.core.ModelStreamObserver
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.runtime.AgentRuntime
import com.androidagent.runtime.persistence.TopicSessionStore
import kotlinx.coroutines.runBlocking

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

    /**
     * AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION: the REAL active model id from the
     * applied provider config. Set by [setActiveModel]; read by the snapshot.
     * No fake effort — only the real model id is stored.
     */
    @Volatile
    var activeModel: String? = null
        private set

    fun setActiveModel(model: String?) {
        activeModel = model
    }

    /**
     * AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION: the process-scoped application
     * context (the existing [rootAppContext], captured on first
     * [persistenceChain]), exposed so HostActions can resolve the provider node
     * loader context without an Activity.
     */
    val appContext: Context?
        get() = rootAppContext

    /**
     * AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION: capture the application context
     * synchronously (idempotent) so [HostActions] can resolve the provider node
     * loader context even before the async initialize()/createRoot path runs.
     */
    fun ensureAppContext(context: Context) {
        val appCtx = context.applicationContext
        if (rootAppContext == null) {
            rootAppContext = appCtx
        }
    }

    /**
     * AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F: the process-scoped durable
     * persistence chain. One app-process Room database -> one RoomTopicSessionStore
     * -> one StoreMessageDurability, injected into every ChatCompositionRoot.
     *
     * Created lazily on first use (the first Activity onCreate) with the
     * application context, so the DB instance is process-scoped (Gate L) — NOT
     * per-Activity or per-Run.
     */
    @Volatile
    private var topicSessionStore: TopicSessionStore? = null

    /** AG.UI-TRANSCRIPT-CARDS-1: public accessor for the Session Browser. */
    val topicSessionStoreRef: TopicSessionStore? get() = topicSessionStore

    @Volatile
    private var messageDurability: MessageDurability? = null

    @Volatile
    private var rootAppContext: Context? = null

    /** Builds (once) and returns the process-scoped durable persistence chain. */
    private fun persistenceChain(context: Context): Pair<TopicSessionStore, MessageDurability> {
        val appCtx = context.applicationContext
        rootAppContext = appCtx
        val existingStore = topicSessionStore
        val existingDurability = messageDurability
        if (existingStore != null && existingDurability != null) {
            return existingStore to existingDurability
        }
        // Room caches the database instance per (class, name, config); repeated
        // opens are cheap and thread-safe. WAL is explicit in TopicSessionDatabase.
        val db = TopicSessionDatabase.open(appCtx)
        val store = RoomTopicSessionStore(db)
        val durability = StoreMessageDurability(store)
        topicSessionStore = store
        messageDurability = durability
        return store to durability
    }

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
        // Capture the application context up front (synchronously) so HostActions
        // can resolve the provider node loader context even before the async
        // initialize() runs. (persistenceChain also captures it, but createRoot is
        // the synchronous entry point on the provider-switch / startup path.)
        rootAppContext = context.applicationContext
        // Production durability chain (Phase F): REAL Room-backed persistence,
        // NOT MessageDurability.Noop. Process-scoped (built once, reused).
        val (store, durability) = persistenceChain(context)
        val newRoot = ChatCompositionRoot(
            cfg,
            context.applicationContext,
            onRunEvent = onRunEvent,
            onDebugEvent = onDebugEvent,
            streamObserver = streamObserver,
            messageDurability = durability,
            topicSessionStore = store,
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

    /**
     * Process startup create-or-restore (Phase G) + transcript re-projection
     * (Phase I).
     *
     * AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F.NORMALIZE-1: the durable
     * session lifecycle is owned by [AgentRuntime]. This delegates to
     * [AgentRuntime.initialize] (idempotent; create-or-restore + controlled
     * recovery), and on success re-projects the restored Core history into the
     * process-scoped [TranscriptModel] so a process restart shows the previous
     * conversation (FULL_USER_VISIBLE_RESUME).
     *
     * Ordering (frozen Phase I): Runtime READY + Core history restored
     * → transcript projected → UI interactive. Projection happens ONLY after a
     * successful initialize; a FAILED startup leaves the transcript untouched
     * (cold process: empty; warm process: prior state preserved).
     *
     * @return true if a current session is ready and the transcript is projected.
     */
    suspend fun initialize(): Boolean {
        val r = root?.runtime ?: return false
        try {
            r.initialize()
        } catch (e: Exception) {
            // Startup FAILED (backend failure / unrecoverable recovery-create
            // failure). Do NOT clear the visible transcript: cold process stays
            // empty, a warm process keeps its prior projection. Return false so
            // the Host can surface a startup-error state without interaction.
            return false
        }
        // Runtime READY + Core history restored: project it into the transcript.
        projectCurrentHistory()
        return true
    }

    /**
     * Re-projects the current Session's restored/ live Core history into the
     * process-scoped [TranscriptModel] (Phase I). Called after a successful
     * [initialize] and after a successful [newSession].
     *
     * Pure presentation: reads [AgentRuntime.currentHistory] (a read-only Core
     * snapshot) and replaces the transcript projection. No durability write, no
     * model call, no tool execution.
     */
    fun projectCurrentHistory() {
        val r = root?.runtime ?: return
        val history = r.currentHistory()
        // Replace (not append) the current projection. A fresh empty session
        // yields an empty transcript; a recovered fresh session yields empty
        // (the corrupt old history is never shown).
        transcriptModel.reproject(history)
        notifyUiUpdate()
    }

    /**
     * Reset to a fresh durable Session (user tapped "New Session").
     *
     * Suspend: the Runtime durably creates the new TopicSession row before
     * making it current. If the store fails, the exception propagates and the
     * old session remains current — and the old transcript is PRESERVED (no
     * premature clear). On success, the (empty) new session is projected.
     */
    suspend fun newSession(): Boolean {
        val r = root?.runtime ?: return false
        val switched = r.newSession()
        if (switched) {
            // New session is current: project its (empty) history. This clears
            // the old projection ONLY after the durable switch succeeded.
            projectCurrentHistory()
            runStatusText = "New session started."
        }
        // On failure the old session remains current and the old transcript is
        // unchanged (no premature clear).
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
