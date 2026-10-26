package com.androidagent.app

import android.content.Context
import com.androidagent.app.provider.AndroidOpenAiHttpTransport
import com.androidagent.app.trace.AgentTrace
import com.androidagent.app.trace.AgentTraceCollector
import com.androidagent.app.persistence.RoomTopicSessionStore
import com.androidagent.app.persistence.StoreMessageDurability
import com.androidagent.app.persistence.TopicSessionDatabase
import com.androidagent.core.AgentCore
import com.androidagent.core.AgentEvent
import com.androidagent.core.AgentEventSink
import com.androidagent.core.MessageDurability
import com.androidagent.core.RunOptions
import com.androidagent.core.ToolRegistry
import com.androidagent.runtime.persistence.TopicSessionStore
import com.androidagent.integration.at.AtExecutionAdapter
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.runtime.AgentRuntime
import com.androidagent.tool.androidshell.AndroidShellToolImpl
import com.androidagent.tool.androidshell.ShellConnection
import com.androidagent.tool.file.FileToolImpl
import com.androidagent.tool.network.NetworkToolImpl
import com.at.composition.android.ATAndroid
import com.at.composition.android.ATAndroidConfig
import java.io.File

/**
 * The AGA Composition Root: the single place where implementations are chosen and
 * wired together.
 *
 * It assembles and nothing else. Session/Run lifecycle lives in
 * [AgentRuntime]; Agent semantics live in AG Core; input and rendering live in
 * [ChatActivity].
 *
 * AG.SHELL-1: the concrete Android Shell Tool ([AndroidShellToolImpl]) is
 * registered here in the (previously empty) [ToolRegistry]. The AT Engine
 * lifecycle binding ([ShellConnection]) is created here too and handed to the
 * Host, so no `com.androidterminal.*` type ever appears in Host code.
 *
 * AGENT-0.PREFLIGHT: this is also the single SSOT for the Agent Instructions
 * ([AgentInstructions]) and the minimal Agent Trace. The Instructions content is
 * Host-owned and injected into every Run's [RunOptions]; the [AgentTraceCollector]
 * is a Host-side consumer of AG Core's `AgentEvent` stream. Neither the
 * Instructions content nor the trace lives in AG Core or the Agent Runtime.
 *
 * Credential policy for this step: the API key lives in the calling process's
 * memory only. It is never written to disk, SharedPreferences, DataStore,
 * resources or BuildConfig. AGA-0 is an integration host, not a production
 * credential system.
 */
class ChatCompositionRoot(
    private val config: OpenAiCompatibleConfig,
    applicationContext: Context,
    private val onRunEvent: (AgentEvent) -> Unit = {},
    private val onDebugEvent: (AgentEvent) -> Unit = {},
    private val streamObserver: com.androidagent.core.ModelStreamObserver =
        com.androidagent.core.ModelStreamObserver.Noop,
    // AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F: production durability chain
    // is injected here so tests can supply a fake store (and a deterministic
    // clock). Production default (SessionHolder) wires the REAL Room-backed
    // chain: TopicSessionDatabase -> RoomTopicSessionStore -> StoreMessageDurability.
    private val messageDurability: MessageDurability,
    private val topicSessionStore: TopicSessionStore,
) {

    private val toolRegistry = ToolRegistry()

    /**
     * Read-only view of the registry, for the debug-only Direct Tool Validation
     * surface (AG.NETWORK-0 L2). The Host owns the registry; exposing it read-only
     * to the debug bridge lets a PC client invoke the SAME production tools
     * directly without an LLM. Not a capability owner — just a reference.
     */
    val toolRegistryView: ToolRegistry get() = toolRegistry

    /**
     * AT V4 composition (Host owns the lifecycle policy; this object
     * performs the AT calls). Created via the canonical V4 entry point
     * ATAndroid.create(context, config).
     */
    private val atComposition = ATAndroid.create(
        context = applicationContext,
        config = ATAndroidConfig(identityProfile = AtExecutionAdapter.IDENTITY_PROFILE),
    )

    /**
     * Host-layer ADB semantic bridge. Delegates to the AT composition facade
     * (ATAndroidAdbControl). The adapter receives this via constructor
     * injection and never imports com.at.composition.android.* types.
     */
    private val adbSemantic = CompositionAdbSemantic(atComposition.adbControl)

    /**
     * AT execution adapter (the single AT consumption point).
     * Receives injected ATEngine instances + AdbSemantic (V4 boundary-correct).
     */
    private val atAdapter = AtExecutionAdapter(
        localEngine = atComposition.localEngine,
        adbEngine = atComposition.adbEngine,
        adbSemantic = adbSemantic,
        cliBridgeDir = File(applicationContext.filesDir, "at-cli-bridge"),
    ).apply {
        appApkPath = applicationContext.applicationInfo.sourceDir
    }

    /**
     * AT Engine lifecycle binding (Host owns the lifecycle policy; this object
     * performs the AT calls). Never exposes AT-owned types.
     */
    val shell: ShellConnection = ShellConnection(atAdapter)

    init {
        // AG.AT-V4-INTEGRATION-1: register the concrete Android Shell Tool (Tool #1).
        // The tool consumes AT through the unified adapter (V4 composition).
        toolRegistry.register(AndroidShellToolImpl(atAdapter))
        toolRegistry.register(NetworkToolImpl())
        // AG.FILE-0: register the concrete File Tool (Tool #3, APP_SELF READ family)
        // in the SAME ToolRegistry.
        toolRegistry.register(FileToolImpl(File(applicationContext.filesDir, "agent")))

        // Start the AT CLI bridge (same-UID UDS).
        atAdapter.startCliBridge()
    }

    /**
     * The Android transport is chosen explicitly here, in the composition root.
     *
     * The Provider deliberately has no default transport: a hidden JDK default
     * would break Android, which is the exact failure the transport boundary
     * exists to prevent.
     */
    private val provider = OpenAiCompatibleProvider(config, AndroidOpenAiHttpTransport())

    private val agentCore = AgentCore(provider, toolRegistry, messageDurability)

    /**
     * AGENT-0.PREFLIGHT: the Agent Instructions are frozen in [AgentInstructions]
     * and injected into every Run. The same content is reused across Runs, so the
     * version + hash are stable and auditable in the Trace.
     *
     * The [AgentTraceCollector] is a Host-side consumer of AG Core's `AgentEvent`
     * stream and is installed as the Run's event sink. AGA admits at most one
     * Run at a time (single Session + Host busy guard), so one collector serves
     * the process: its [AgentTrace] is rebuilt per admitted Run. The collector
     * captures the Host-known metadata AG Core cannot supply (model id,
     * instructions identity, available tools).
     */
    val traceCollector = AgentTraceCollector(
        modelId = config.model,
        instructionsVersion = AgentInstructions.VERSION,
        instructionsHash = AgentInstructions.HASH,
        availableToolNames = toolRegistry.specs().map { it.name },
    )

    private val runOptions = RunOptions(
        instructions = AgentInstructions.CONTENT,
        // The Host may observe the same event stream for transient run status and
        // the PC debug stream; tracing stays intact either way. The model stream
        // observer feeds the transcript and the PC debug stream incrementally.
        eventSink = CompositeSink(traceCollector, onRunEvent, onDebugEvent),
        streamObserver = streamObserver,
    )

    /**
     * The runnable Agent lifecycle handed to the UI.
     *
     * AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0F.NORMALIZE-1: the session-id
     * factory is PURE (UUID generation only, no DB side effect). The durable
     * TopicSession creation is owned by [AgentRuntime] (frozen architecture:
     * TOPIC_SESSION_LIFECYCLE_OWNER = AG Runtime). The Host calls
     * [AgentRuntime.initialize] (suspend) before the first Run to durably create
     * the initial session; [AgentRuntime.newSession] durably creates subsequent
     * sessions. SESSION_ROW_EXISTS_BEFORE_D1 = YES by construction: the Runtime
     * only makes a session current AFTER the store row is committed.
     */
    val runtime: AgentRuntime = AgentRuntime(
        agentCore,
        runOptions = runOptions,
        sessionIdFactory = { "session-" + java.util.UUID.randomUUID() },
        topicSessionStore = topicSessionStore,
    )

    /** Forwards every event to every observer. No observer may break the Run. */
    private class CompositeSink(
        private val trace: AgentEventSink,
        private val host: (AgentEvent) -> Unit,
        private val debug: (AgentEvent) -> Unit,
    ) : AgentEventSink {
        override fun emit(event: AgentEvent) {
            trace.emit(event)
            emitSafe(host, event)
            emitSafe(debug, event)
        }

        private fun emitSafe(observer: (AgentEvent) -> Unit, event: AgentEvent) {
            try {
                observer(event)
            } catch (_: Exception) {
                // A misbehaving observer must not break the Agent Run.
            }
        }
    }
}