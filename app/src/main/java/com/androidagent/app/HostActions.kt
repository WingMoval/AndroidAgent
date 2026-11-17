package com.androidagent.app

import com.androidagent.core.RunOutcome
import com.androidagent.runtime.AgentRuntime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 * AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION — Shared Host Action seam.
 *
 * A THIN shared entry point between UI callbacks and DebugBridge commands.
 * NOT a second AgentRuntime / SessionHolder / Domain Service. It only:
 *
 *   - checks parameters,
 *   - calls the EXISTING Host / Runtime capability,
 *   - performs the minimal Host presentation side effect,
 *   - returns a result the automation can assert on.
 *
 * Structure (frozen for this Step):
 *
 *   UI callback  ─┐
 *                  ├→ HostActions → existing Runtime / Host capability
 *   Debug command ─┘
 *
 * Pure-UI behaviors (open/close sidebar, click, scroll, expand, copy) are NOT
 * Host Actions — they stay in ChatActivity and are validated by uiautomator.
 *
 * Credential policy: [HostSnapshot] never carries an API key, Authorization
 * header, pairing code, or raw secret config.
 */
object HostActions {

    // ------------------------------------------------------------------ Send

    /**
     * Send user input through the SAME [AgentRuntime.send] the UI uses.
     *
     * This is the SINGLE shared Host entry for Send. Both the UI onSend and
     * DebugBridge.handleSend delegate here. It owns the common semantics:
     *
     *   - blank check
     *   - busy check
     *   - User transcript block insertion (exactly once)
     *   - busy=true before, busy=false after
     *   - exactly one AgentRuntime.send
     *   - Interrupted terminal ("Stopped.")
     *
     * Pure-UI side effects (clearing the input, auto-follow, IME) are NOT
     * here — they stay in ChatActivity.onSend. The wire response
     * (accepted/busy/error) is NOT here either — it stays in DebugBridge.
     *
     * @return [SendResult.Rejected] for blank text / no runtime / busy (no
     *         run started); [SendResult.Sent] with the [RunOutcome] once the
     *         run reaches terminal.
     */
    suspend fun send(text: String): SendResult {
        if (text.isBlank()) return SendResult.Rejected("blank send rejected")
        val r = SessionHolder.runtime ?: return SendResult.Rejected("runtime not ready")
        if (r.isBusy) return SendResult.Rejected("busy")

        SessionHolder.transcriptModel.onUser(text)
        SessionHolder.setBusy(true)
        SessionHolder.setRunStatus("Running...")
        SessionHolder.notifyUiUpdate()

        val outcome = try {
            r.send(text)
        } finally {
            SessionHolder.setBusy(false)
        }
        if (outcome is RunOutcome.Interrupted) {
            SessionHolder.setRunStatus("Stopped.")
        }
        SessionHolder.notifyUiUpdate()
        return SendResult.Sent(outcome)
    }

    // ------------------------------------------------------------------ Stop

    /**
     * Interrupt the active Run through the SAME
     * [AgentRuntime.interruptActiveRun] the UI uses. No second cancel path.
     *
     * Faithfully maps [AgentRuntime.InterruptResult]:
     *   Requested          -> [StopResult.Requested]
     *   AlreadyInterrupting-> [StopResult.AlreadyInterrupting]
     *   NoActiveRun        -> [StopResult.NoActiveRun]
     */
    fun stop(): StopResult {
        val r = SessionHolder.runtime
            ?: return StopResult.NoActiveRun
        if (!r.isBusy) return StopResult.NoActiveRun
        return when (r.interruptActiveRun()) {
            AgentRuntime.InterruptResult.Requested -> {
                SessionHolder.setRunStatus("Stopping...")
                SessionHolder.notifyUiUpdate()
                StopResult.Requested
            }
            AgentRuntime.InterruptResult.AlreadyInterrupting -> {
                SessionHolder.notifyUiUpdate()
                StopResult.AlreadyInterrupting
            }
            AgentRuntime.InterruptResult.NoActiveRun -> {
                SessionHolder.setBusy(false)
                SessionHolder.notifyUiUpdate()
                StopResult.NoActiveRun
            }
        }
    }

    // ------------------------------------------------------------- New Session

    /**
     * Enter a transient BLANK DRAFT through the SAME [SessionHolder.newSession]
     * the UI uses (AG.SESSION-MANAGEMENT-UX-0). NO USER MESSAGE = NO SESSION:
     * this does NOT create a durable row. The one-active-Run-per-Session guard
     * is preserved (rejected while busy).
     */
    fun newSession(): Boolean {
        val switched = SessionHolder.newSession()
        if (switched) {
            SessionHolder.notifyUiUpdate()
        } else {
            SessionHolder.setRunStatus("Cannot start a new session while a run is in progress.")
            SessionHolder.notifyUiUpdate()
        }
        return switched
    }

    // ------------------------------------------------- Session Rename / Delete

    /**
     * Durably rename the CURRENT session (AG.SESSION-MANAGEMENT-UX-0).
     *
     * Shared entry for the Session Management UI. Metadata-only (no
     * model/tool call). The UI disables rename for busy sessions; this also
     * rejects blank/whitespace titles.
     *
     * @return [SessionRenameResult].
     */
    suspend fun renameSession(newTitle: String): SessionRenameResult {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return SessionRenameResult.Rejected("title must not be blank")
        val ok = SessionHolder.renameSession(trimmed)
        SessionHolder.notifyUiUpdate()
        if (!ok) {
            SessionHolder.setRunStatus("Cannot rename the session right now.")
            SessionHolder.notifyUiUpdate()
        }
        return if (ok) SessionRenameResult.Ok(trimmed) else SessionRenameResult.Rejected("rename failed")
    }

    /**
     * Permanently delete a durable session (AG.SESSION-MANAGEMENT-UX-0).
     *
     * Shared entry for the Session Management UI. Delegates to
     * [SessionHolder.deleteSession]. If the current session is deleted, the
     * runtime enters a blank draft (no replacement row).
     *
     * @return [SessionDeleteResult].
     */
    suspend fun deleteSession(sessionId: String): SessionDeleteResult {
        val result = SessionHolder.deleteSession(sessionId)
        SessionHolder.notifyUiUpdate()
        return when (result) {
            is com.androidagent.runtime.AgentRuntime.DeleteResult.Deleted ->
                SessionDeleteResult.Deleted(currentAfter = result.currentAfter)
            is com.androidagent.runtime.AgentRuntime.DeleteResult.Rejected ->
                SessionDeleteResult.Rejected(result.reason)
        }
    }

    // ------------------------------------------------------------ ADB Reconnect

    /**
     * Re-run the ADB connect flow through the SAME connect path the UI uses
     * (ShellConnection.connect -> AtExecutionAdapter -> integration-at).
     * Updates the AT status exactly as the UI does.
     */
    suspend fun adbReconnect(): Boolean {
        val shell = SessionHolder.root?.shell ?: return false
        SessionHolder.setAtStatus("Connecting...")
        SessionHolder.notifyUiUpdate()
        val result = shell.connect()
        SessionHolder.setAtStatus(
            if (result == null) "Not connected"
            else if (result.ready) "Connected"
            else HostStatusModel.safeAtMessage(result.message)
        )
        SessionHolder.notifyUiUpdate()
        return result?.ready == true
    }

    // ---------------------------------------------------------- Provider Switch

    /**
     * Switch the active provider node through the SINGLE canonical apply path.
     *
     * Both the UI switchNode and DebugBridge.handleProviderSwitch delegate here.
     * This is the ONLY place where createRoot + SessionHolder state update +
     * ADB reconnect + initialize happen for a provider switch
     * (CANONICAL_APPLY_CONFIG_IMPLEMENTATION_COUNT = 1).
     *
     * Only an existing, valid [ProviderNode] identity is accepted — a debug
     * client can NOT inject an apiKey / raw config / Authorization.
     */
    suspend fun switchProvider(node: ProviderNode): ProviderSwitchResult {
        val ctx = SessionHolder.appContext
            ?: return ProviderSwitchResult.Rejected(node, "host context unavailable")
        val outcome = resolveProviderSwitch(node, DeviceLocalProviderNodeLoader.load(ctx, node))
        return when (outcome) {
            is ProviderSwitchOutcome.Applied -> {
                applyConfigCanonical(outcome.config, outcome.node)
                ProviderSwitchResult.Applied(outcome.node, outcome.config.safeChatCompletionsEndpoint)
            }
            is ProviderSwitchOutcome.Rejected -> {
                ProviderSwitchResult.Rejected(node, outcome.reason)
            }
        }
    }

    // ------------------------------------------------------------- Model Switch

    /**
     * AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT — Switch the active model
     * through the SINGLE canonical apply path.
     *
     * Both the UI LLM selector and (future) DebugBridge model_switch delegate
     * here. This reuses [switchProvider]'s canonical apply path — it does NOT
     * create a second createRoot/initialize flow.
     *
     * Semantics:
     *  - [node] is the provider that owns [model] (from the saved config).
     *  - [model] is the model id to activate.
     *  - The saved config for [node] is loaded, the model is replaced with
     *    [model], and the canonical apply path runs.
     *
     * If [model] equals the current active model, this is a no-op (returns
     * Applied with the same state).
     */
    suspend fun switchModel(node: ProviderNode, model: String): ProviderSwitchResult {
        val ctx = SessionHolder.appContext
            ?: return ProviderSwitchResult.Rejected(node, "host context unavailable")

        // If same model is already active on this node, no-op
        if (SessionHolder.providerNode == node && SessionHolder.activeModel == model) {
            val cfg = when (val loaded = DeviceLocalProviderNodeLoader.load(ctx, node)) {
                is DeviceLocalProviderConfig.Loaded -> loaded.config
                else -> return ProviderSwitchResult.Rejected(node, "config not available")
            }
            return ProviderSwitchResult.Applied(node, cfg.safeChatCompletionsEndpoint)
        }

        val loaded = DeviceLocalProviderNodeLoader.load(ctx, node)
        val baseCfg = when (loaded) {
            is DeviceLocalProviderConfig.Loaded -> loaded.config
            is DeviceLocalProviderConfig.Missing ->
                return ProviderSwitchResult.Rejected(node, "${node.displayName} config not provisioned")
            is DeviceLocalProviderConfig.Invalid ->
                return ProviderSwitchResult.Rejected(node, "${node.displayName} config invalid")
        }

        // Replace the model in the config, keep all other credentials
        val newCfg = baseCfg.copy(model = model)
        applyConfigCanonical(newCfg, node)
        return ProviderSwitchResult.Applied(node, newCfg.safeChatCompletionsEndpoint)
    }

    // ---------------------------------------------------- Session List/Switch

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: List all durable sessions for the Session Browser.
     * Shared entry for both the UI sidebar and (future) Debug topic_session_list.
     */
    suspend fun listSessions(): List<SessionSummary> {
        val store = SessionHolder.topicSessionStoreRef ?: return emptyList()
        return try {
            store.listSessions().map { rec ->
                SessionSummary(
                    id = rec.id,
                    title = rec.title,
                    titleOrigin = rec.titleOrigin,
                    model = rec.model,
                    provider = rec.provider,
                    createdAt = rec.createdAt,
                    lastUsedAt = rec.lastUsedAt,
                    messageCount = store.countMessages(rec.id),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: Switch to a historical session.
     * Shared entry for both the UI sidebar click and (future) Debug topic_session_switch.
     */
    suspend fun switchSession(sessionId: String): SessionSwitchResult {
        val r = SessionHolder.runtime
            ?: return SessionSwitchResult.Rejected("runtime not ready")
        if (r.isBusy) return SessionSwitchResult.Rejected("busy")
        return try {
            val switched = r.switchSession(sessionId)
            if (switched) {
                SessionHolder.projectCurrentHistory()
                SessionHolder.setRunStatus("Switched to session.")
                SessionHolder.notifyUiUpdate()
                SessionSwitchResult.Ok(sessionId)
            } else {
                SessionSwitchResult.Rejected("session not found")
            }
        } catch (e: Exception) {
            SessionSwitchResult.Rejected("switch failed")
        }
    }

    // ---------------------------------------------------- Profile-based Switch

    /**
     * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE — Switch to a model from a
     * specific provider profile through the SINGLE canonical apply path.
     *
     * Works with BOTH built-in profiles (EMG/OpenRouter) and custom profiles.
     * For built-in profiles, this delegates to the existing [switchProvider]
     * path. For custom profiles, it loads the profile's config, replaces the
     * model, and runs [applyConfigCanonical].
     *
     * This is the entry point for the Composer LLM selector.
     */
    suspend fun switchProfile(profileId: String, model: String): ProfileSwitchResult {
        val ctx = SessionHolder.appContext
            ?: return ProfileSwitchResult.Rejected(profileId, "host context unavailable")

        val profile = ProviderProfileStore.load(ctx, profileId)
            ?: return ProfileSwitchResult.Rejected(profileId, "profile not found: $profileId")

        // No-op if same model already active
        if (SessionHolder.activeModel == model &&
            (profile.isBuiltIn && SessionHolder.providerNode.name.lowercase() == profileId)
        ) {
            return ProfileSwitchResult.Applied(profileId, profile.displayName, model,
                profile.config.safeChatCompletionsEndpoint)
        }

        val newCfg = profile.config.copy(model = model)

        if (profile.isBuiltIn) {
            // Built-in: use the existing node-based canonical path
            val node = ProviderProfileStore.toProviderNode(profileId)
                ?: return ProfileSwitchResult.Rejected(profileId, "cannot resolve built-in node")
            val outcome = resolveProviderSwitch(node, DeviceLocalProviderConfigLoader.load(
                File(ctx.filesDir, node.configFileName)
            ))
            return when (outcome) {
                is ProviderSwitchOutcome.Applied -> {
                    // Override model if different from file default
                    val finalCfg = if (outcome.config.model != model) outcome.config.copy(model = model) else outcome.config
                    applyConfigCanonical(finalCfg, outcome.node)
                    ProfileSwitchResult.Applied(profileId, profile.displayName, model,
                        finalCfg.safeChatCompletionsEndpoint)
                }
                is ProviderSwitchOutcome.Rejected ->
                    ProfileSwitchResult.Rejected(profileId, outcome.reason)
            }
        } else {
            // Custom: apply the profile's config directly through canonical path
            // Use EMG as the "node" carrier (the node is just an identity tag;
            // the actual config is what matters)
            applyConfigCanonical(newCfg, ProviderNode.EMG)
            // Update SessionHolder to reflect the custom profile
            SessionHolder.setActiveModel(model)
            SessionHolder.setProviderStatus("${profile.displayName} (custom)")
            SessionHolder.setEndpoint(newCfg.safeChatCompletionsEndpoint)
            SessionHolder.notifyUiUpdate()
            return ProfileSwitchResult.Applied(profileId, profile.displayName, model,
                newCfg.safeChatCompletionsEndpoint)
        }
    }

    // --------------------------------------------------------------- Snapshot

    /**
     * Read-only automation state. Reads the CURRENT real state on every call —
     * no cached automation state, no second Session / Runtime / Transcript.
     *
     * SECRET-FREE: never returns an API key, Authorization header, pairing
     * code, or raw secret config.
     */
    fun snapshot(): HostSnapshot {
        val r = SessionHolder.runtime
        val blocks = SessionHolder.transcriptModel.snapshot()
        return HostSnapshot(
            runtimeReady = r != null,
            busy = r?.isBusy == true,
            currentSessionId = r?.currentSessionId,
            activeProvider = SessionHolder.providerNode.displayName,
            activeModel = SessionHolder.activeModel,
            adbState = SessionHolder.root?.shell?.readiness()?.code,
            transcriptBlockCount = blocks.size,
            lastBlockType = blocks.lastOrNull()?.let { blockTypeOf(it) },
        )
    }

    // ---------------------------------------------------------- internal helpers

    /** Maps a [TranscriptModel.Block] to its stable type name (for automation). */
    private fun blockTypeOf(block: TranscriptModel.Block): String = when (block) {
        is TranscriptModel.Block.User -> "User"
        is TranscriptModel.Block.Thinking ->
            if (block.phase == TranscriptModel.Block.Thinking.Phase.THINKING) "Thinking" else "Thought"
        is TranscriptModel.Block.Assistant -> "Assistant"
        is TranscriptModel.Block.Shell -> "Shell"
        is TranscriptModel.Block.Result -> "Result"
        is TranscriptModel.Block.Failure -> "Failure"
        is TranscriptModel.Block.Interrupted -> "Interrupted"
    }

    /**
     * THE canonical provider-apply path. This is the SINGLE place where a
     * provider switch creates the new root, updates SessionHolder state,
     * re-runs ADB connect, and initializes the runtime.
     *
     * Both the UI switchNode and DebugBridge.handleProviderSwitch reach here
     * via [switchProvider]. There is no second applyConfig implementation.
     *
     * After this completes, ChatActivity must rebind its mirror fields
     * (root / runtime) from SessionHolder — see the onUiUpdate callback.
     */
    private suspend fun applyConfigCanonical(
        cfg: com.androidagent.provider.openai.OpenAiCompatibleConfig,
        node: ProviderNode,
    ) {
        val ctx = SessionHolder.appContext ?: return

        // AG.PROVIDER-CONFIGURED-MODELS-0: persist the newly-selected model back to
        // the node's device-local config file so the selected model survives a
        // cold restart. This is NON-DESTRUCTIVE: it preserves the existing
        // configuredModels (and their aliases) and the credential/timeout. If the
        // selected model is not already in the configured set, it is added.
        persistSelectedModel(ctx, node, cfg)

        SessionHolder.createRoot(
            cfg,
            ctx,
            node,
            onRunEvent = { SessionHolder.onRunEvent(it) },
            onDebugEvent = { e -> DebugBridgeHolder.bridge?.eventSink?.emit(e) },
            streamObserver = sessionStreamObserverShared,
        )
        SessionHolder.setProviderStatus(HostStatusModel.providerLine(node.displayName))
        SessionHolder.setEndpoint(cfg.safeChatCompletionsEndpoint)
        SessionHolder.setAtStatus(null)
        SessionHolder.setRunStatus(null)
        SessionHolder.setActiveModel(cfg.model)
        SessionHolder.notifyUiUpdate()
        adbReconnect()
        SessionHolder.initialize()
    }

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: write [cfg.model] (the selected model) back
     * to [node]'s device-local config file, preserving the existing configured
     * models + aliases + credential. No-op when the file is absent/invalid (we
     * never create a config the user didn't provision).
     */
    private fun persistSelectedModel(
        ctx: android.content.Context,
        node: ProviderNode,
        cfg: com.androidagent.provider.openai.OpenAiCompatibleConfig,
    ) {
        val loaded = DeviceLocalProviderNodeLoader.loadWithConfigured(ctx, node)
        if (loaded !is DeviceLocalProviderNodeLoader.NodeLoadResult.Loaded) return
        val existing = loaded.configuredModels
        val configured = if (existing.any { it.modelId == cfg.model }) {
            existing
        } else {
            existing + ConfiguredModel(cfg.model)
        }
        DeviceLocalProviderNodeLoader.saveConfigured(ctx, node, cfg, configured)
    }

    /** Process-scoped stream observer (shared; mirrors ChatActivity's). */
    private val sessionStreamObserverShared = object : com.androidagent.core.ModelStreamObserver {
        override fun onReasoningStarted() = SessionHolder.onStreamEvent("reasoning_started", null)
        override fun onReasoningDelta(text: String) = SessionHolder.onStreamEvent("reasoning_delta", text)
        override fun onReasoningFinished() = SessionHolder.onStreamEvent("reasoning_finished", null)
        override fun onContentStarted() = SessionHolder.onStreamEvent("content_started", null)
        override fun onContentDelta(text: String) = SessionHolder.onStreamEvent("content_delta", text)
        override fun onContentFinished() = SessionHolder.onStreamEvent("content_finished", null)
    }
}

// ---------------------------------------------------------------------------
// Result types (automation-assertable; no new framework).
// ---------------------------------------------------------------------------

/** [HostActions.send] outcome. */
sealed interface SendResult {
    /** A run was started and reached terminal. [outcome] is the real [RunOutcome]. */
    data class Sent(val outcome: RunOutcome) : SendResult
    /** No run was started (blank / no runtime / busy). [reason] is assertable. */
    data class Rejected(val reason: String) : SendResult
}

/** [HostActions.stop] outcome (faithful [AgentRuntime.InterruptResult] mapping). */
sealed interface StopResult {
    data object Requested : StopResult
    data object AlreadyInterrupting : StopResult
    data object NoActiveRun : StopResult
}

/** [HostActions.providerSwitch] outcome. */
sealed interface ProviderSwitchResult {
    /** The node became active; [endpoint] is the existing SAFE endpoint. */
    data class Applied(val node: ProviderNode, val endpoint: String) : ProviderSwitchResult
    /** The node could not be applied; the active provider is unchanged. */
    data class Rejected(val node: ProviderNode, val reason: String) : ProviderSwitchResult
}

/** A session summary for the Session Browser UI. */
data class SessionSummary(
    val id: String,
    val title: String?,
    val titleOrigin: com.androidagent.runtime.persistence.TitleOrigin? = null,
    val model: String?,
    val provider: String?,
    val createdAt: Long,
    val lastUsedAt: Long,
    val messageCount: Int = 0,
)

/** [HostActions.switchSession] outcome. */
sealed interface SessionSwitchResult {
    data class Ok(val sessionId: String) : SessionSwitchResult
    data class Rejected(val reason: String) : SessionSwitchResult
}

/** [HostActions.renameSession] outcome (AG.SESSION-MANAGEMENT-UX-0). */
sealed interface SessionRenameResult {
    data class Ok(val newTitle: String) : SessionRenameResult
    data class Rejected(val reason: String) : SessionRenameResult
}

/** [HostActions.deleteSession] outcome (AG.SESSION-MANAGEMENT-UX-0). */
sealed interface SessionDeleteResult {
    /** A session was deleted. [currentAfter] is the new current durable id (null = blank draft). */
    data class Deleted(val currentAfter: String?) : SessionDeleteResult
    data class Rejected(val reason: String) : SessionDeleteResult
}

/** [HostActions.switchProfile] outcome (profile-based, supports custom APIs). */
sealed interface ProfileSwitchResult {
    data class Applied(
        val profileId: String,
        val profileName: String,
        val model: String,
        val endpoint: String,
    ) : ProfileSwitchResult
    data class Rejected(val profileId: String, val reason: String) : ProfileSwitchResult
}

/**
 * [HostActions.snapshot] — the read-only automation state.
 *
 * SECRET-FREE by construction: no apiKey, no Authorization, no pairing code,
 * no raw secret config.
 */
data class HostSnapshot(
    val runtimeReady: Boolean,
    val busy: Boolean,
    val currentSessionId: String?,
    val activeProvider: String?,
    val activeModel: String?,
    val adbState: String?,
    val transcriptBlockCount: Int,
    val lastBlockType: String?,
)

/** JSON encoding of [HostSnapshot] for the debug wire (redaction-safe). */
fun hostSnapshotToJson(snapshot: HostSnapshot): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("snapshot"))
    put("runtimeReady", JsonPrimitive(snapshot.runtimeReady))
    put("busy", JsonPrimitive(snapshot.busy))
    put("currentSessionId", JsonPrimitive(snapshot.currentSessionId ?: ""))
    put("activeProvider", JsonPrimitive(snapshot.activeProvider ?: ""))
    put("activeModel", JsonPrimitive(snapshot.activeModel ?: ""))
    put("adbState", JsonPrimitive(snapshot.adbState ?: ""))
    put("transcriptBlockCount", JsonPrimitive(snapshot.transcriptBlockCount))
    put("lastBlockType", JsonPrimitive(snapshot.lastBlockType ?: ""))
}

/**
 * Process-scoped holder for the debug bridge reference, so HostActions can
 * forward debug events without a direct import cycle. Mirrors the existing
 * ChatActivity.sharedDebugBridge pattern but lives at the Host level.
 */
object DebugBridgeHolder {
    @Volatile
    var bridge: com.androidagent.app.debug.DebugBridge? = null
        private set

    fun set(bridge: com.androidagent.app.debug.DebugBridge?) {
        this.bridge = bridge
    }
}
