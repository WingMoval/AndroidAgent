package com.androidagent.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidagent.app.debug.DebugBridge
import com.androidagent.app.debug.DebugProtocol
import com.androidagent.app.trace.AgentTrace
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelStreamObserver
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.runtime.AgentRuntime
import kotlinx.coroutines.launch

/**
 * Minimal AGA chat host.
 *
 * Deliberately plain Android Views: this step verifies that a real Android host
 * can consume the frozen pure-JVM runtime, not that a UI framework is ergonomic.
 * No Compose, no Navigation, no DI, no Room, no third-party HTTP client.
 *
 * Credential policy: the API key is held in process memory only. It is never
 * written to disk, SharedPreferences, DataStore, resources or BuildConfig, and
 * never echoed back into the view hierarchy.
 *
 * AGA.OBSERVABILITY-0 — provider truth: the Provider node selector is
 * switch-is-active (no Apply button), the endpoint label always shows the
 * ACTIVE provider's safe endpoint (scheme + host + path), and a failed switch
 * reverts the selector to the still-active node instead of lying about it.
 * The transcript renders the live run: real provider reasoning deltas become a
 * Thinking block that becomes a Thought block, Assistant content streams
 * incrementally, dispatched tool calls and their results are shown, and
 * failures are visible with their reason.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var endpointLabel: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var newSession: Button
    private lateinit var reconnect: Button
    private lateinit var nodeSpinner: Spinner

    private var root: ChatCompositionRoot? = null
    private var runtime: AgentRuntime? = null

    /** The node that is ACTUALLY applied as the active provider. */
    private var activeNode: ProviderNode = ProviderNode.EMG
    private var applyingNode: Boolean = false

    /** Pure transcript model; recreated per session. */
    private var transcriptModel: TranscriptModel = TranscriptModel()

    /** Persistent: the active Provider node (EMG / OpenRouter). */
    private var providerStatusText: String? = null

    /** Persistent: the AT Engine connection state. */
    private var atStatusText: String? = null

    /** Transient: the run phase (Running / Executing / ... / Ready / Failed). */
    private var runStatusText: String? = null

    /** Whether a manual reconnect attempt is in flight (button disabled then). */
    private var reconnecting: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        transcript = findViewById(R.id.transcript)
        status = findViewById(R.id.status)
        endpointLabel = findViewById(R.id.provider_endpoint)
        input = findViewById(R.id.input)
        send = findViewById(R.id.send)
        newSession = findViewById(R.id.new_session)
        reconnect = findViewById(R.id.reconnect)
        nodeSpinner = findViewById(R.id.provider_node)

        send.setOnClickListener { onSend() }
        newSession.setOnClickListener { onNewSession() }
        reconnect.setOnClickListener { onReconnect() }
        setupNodeSpinner()

        // AGA.DEV-CONFIG-0: at startup, try the device-local provider config.
        // If present and valid, the Provider is ready with no manual key entry.
        // Otherwise (missing or invalid) the Provider stays unconfigured and the
        // status explains why.
        tryDeviceLocalConfig()
    }

    // ------------------------------------------------------------- Provider node

    private fun setupNodeSpinner() {
        val nodes = ProviderNode.entries
        val labels = nodes.map { it.displayName }
        nodeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        nodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (applyingNode) return
                val node = nodes[position]
                if (node != activeNode) {
                    switchNode(node)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Switch-is-active: selecting a node immediately tries to apply it. On a
     * failed switch the selector is reverted to the still-active node and the
     * reason is shown — the UI never claims a provider that is not active.
     */
    private fun switchNode(node: ProviderNode) {
        val outcome = resolveProviderSwitch(node, DeviceLocalProviderNodeLoader.load(applicationContext, node))
        when (outcome) {
            is ProviderSwitchOutcome.Applied -> {
                activeNode = outcome.node
                applyConfig(outcome.config, node = outcome.node)
            }

            is ProviderSwitchOutcome.Rejected -> {
                revertSpinner()
                runStatusText = outcome.reason
                renderStatus()
            }
        }
    }

    private fun revertSpinner() {
        applyingNode = true
        nodeSpinner.setSelection(ProviderNode.entries.indexOf(activeNode))
        applyingNode = false
    }

    private fun tryDeviceLocalConfig() {
        val defaultNode = ProviderNode.entries.firstOrNull {
            DeviceLocalProviderNodeLoader.load(applicationContext, it) is DeviceLocalProviderConfig.Loaded
        } ?: ProviderNode.EMG
        activeNode = defaultNode
        applyingNode = true
        nodeSpinner.setSelection(ProviderNode.entries.indexOf(defaultNode))
        applyingNode = false
        switchNode(defaultNode)
    }

    /**
     * Applies a config for a node. AT startup auto-connect is unconditional in
     * this consolidated Host: the single AGA APK always wires AT.
     */
    private fun applyConfig(cfg: OpenAiCompatibleConfig, node: ProviderNode) {
        root = ChatCompositionRoot(
            cfg,
            applicationContext,
            onRunEvent = { onRunEvent(it) },
            onDebugEvent = { e -> sharedDebugBridge?.eventSink?.emit(e) },
            streamObserver = streamObserver,
        )
        runtime = root?.runtime
        sharedRuntime = root?.runtime
        // Start/reuse the process-wide debug bridge AFTER the runtime exists, but
        // BEFORE any Run can happen (applyConfig is the only entry point).
        ensureDebugBridge()
        providerStatusText = HostStatusModel.providerLine(node.displayName)
        endpointLabel.text = cfg.safeChatCompletionsEndpoint
        atStatusText = null
        runStatusText = null
        renderStatus()
        connectShell()
    }

    /**
     * AG.DEBUG-0: start the PC debug bridge in debug builds.
     *
     * The bridge is PROCESS-scoped, not Activity-scoped: Activity recreation (for
     * example under instrumentation's ActivityScenario) must not create a second
     * bind on the same port or leave the event sink pointing at a dead instance.
     * It reads the current runtime through [sharedRuntime], so Provider node
     * switches stay correct without restarting the listener.
     *
     * No-op in release (BuildConfig.DEBUG == false): there is no listener and no
     * external debug ingress in a release build.
     */
    private fun ensureDebugBridge() {
        if (!BuildConfig.DEBUG) return
        val existing = sharedDebugBridge
        if (existing != null && existing.isRunning) return
        val bridge = DebugBridge(runtimeProvider = { sharedRuntime })
        // Only publish the instance once it is actually listening; a failed bind
        // must never replace a working bridge or become the event destination.
        if (bridge.start()) {
            sharedDebugBridge = bridge
        }
    }

    override fun onDestroy() {
        // The debug bridge is process-scoped on purpose: it must survive Activity
        // recreation so a passive PC monitor keeps its connection. It dies with
        // the process. Release builds never create one.
        super.onDestroy()
    }

    // ----------------------------------------------------------------- AT Engine

    /** Startup / switch / manual-reconnect entry: connect the AT Engine. */
    private fun connectShell() {
        val shellConnection = root?.shell
        atStatusText = "Connecting..."
        renderStatus()
        lifecycleScope.launch {
            val status = shellConnection?.connect()
            atStatusText = if (status == null) {
                "Not connected"
            } else if (status.ready) {
                "Connected"
            } else {
                HostStatusModel.safeAtMessage(status.message)
            }
            renderStatus()
        }
    }

    /** Manual reconnect button: only re-runs the connect flow. */
    private fun onReconnect() {
        if (reconnecting) return
        reconnecting = true
        reconnect.isEnabled = false
        connectShell()
        // The connect coroutine finishes asynchronously; re-enable is handled in
        // the same coroutine by rendering after the status lands.
        lifecycleScope.launch {
            reconnecting = false
            reconnect.isEnabled = true
        }
    }

    // ----------------------------------------------------------------- Run status

    /**
     * Host-side observer for AG Core's [AgentEvent] stream. Renders the transient
     * run phase and feeds tool actions/results into the transcript. All
     * transcript mutations happen on the UI thread (the stream observer also
     * mutates it there), so the two channels never race.
     */
    private fun onRunEvent(event: AgentEvent) {
        runStatusText = HostStatusModel.runPhaseText(event)
        runOnUiThread {
            when (event) {
                is AgentEvent.ModelTurnStarted -> transcriptModel.onModelTurnStarted(event.step)
                is AgentEvent.ToolCallStarted -> transcriptModel.onToolCallStarted(event.call)
                is AgentEvent.ToolCallFinished -> transcriptModel.onToolCallFinished(event.call, event.result)
                else -> {}
            }
            renderStatus()
            renderTranscript()
        }
    }

    // ------------------------------------------------------------- Model stream

    /**
     * AGA.OBSERVABILITY-0: live model-stream observer. Every callback arrives on
     * the provider's HTTP thread; the transcript model is updated on the UI
     * thread, and the event is forwarded to the PC debug bridge (which broadcasts
     * from its own single-thread executor, so it is thread-safe to call here).
     */
    private val streamObserver: ModelStreamObserver = object : ModelStreamObserver {
        override fun onReasoningStarted() {
            bridgeStream(DebugProtocol.STREAM_REASONING_STARTED, null)
            runOnUiThread {
                transcriptModel.onReasoningStarted()
                renderTranscript()
            }
        }

        override fun onReasoningDelta(text: String) {
            bridgeStream(DebugProtocol.STREAM_REASONING_DELTA, text)
            runOnUiThread {
                transcriptModel.onReasoningDelta(text)
                renderTranscript()
            }
        }

        override fun onReasoningFinished() {
            bridgeStream(DebugProtocol.STREAM_REASONING_FINISHED, null)
            runOnUiThread {
                transcriptModel.onReasoningFinished()
                renderTranscript()
            }
        }

        override fun onContentStarted() {
            bridgeStream(DebugProtocol.STREAM_CONTENT_STARTED, null)
            runOnUiThread {
                transcriptModel.onContentStarted()
                renderTranscript()
            }
        }

        override fun onContentDelta(text: String) {
            bridgeStream(DebugProtocol.STREAM_CONTENT_DELTA, text)
            runOnUiThread {
                transcriptModel.onContentDelta(text)
                renderTranscript()
            }
        }

        override fun onContentFinished() {
            bridgeStream(DebugProtocol.STREAM_CONTENT_FINISHED, null)
        }
    }

    private fun bridgeStream(kind: String, text: String?) {
        sharedDebugBridge?.streamSink?.let { sink ->
            when (kind) {
                DebugProtocol.STREAM_REASONING_STARTED -> sink.onReasoningStarted()
                DebugProtocol.STREAM_REASONING_DELTA -> sink.onReasoningDelta(text ?: "")
                DebugProtocol.STREAM_REASONING_FINISHED -> sink.onReasoningFinished()
                DebugProtocol.STREAM_CONTENT_STARTED -> sink.onContentStarted()
                DebugProtocol.STREAM_CONTENT_DELTA -> sink.onContentDelta(text ?: "")
                DebugProtocol.STREAM_CONTENT_FINISHED -> sink.onContentFinished()
            }
        }
    }

    // ----------------------------------------------------------------- UI

    /** Renders persistent Provider + AT lines and the transient run phase. */
    private fun renderStatus() {
        val parts = listOfNotNull(
            providerStatusText,
            atStatusText?.let { "AT: $it" },
            runStatusText,
        )
        setStatus(parts.joinToString("\n"))
    }

    private fun renderTranscript() {
        transcript.text = transcriptModel.render()
    }

    private fun onNewSession() {
        val switched = runtime?.newSession() ?: false
        if (switched) {
            transcriptModel = TranscriptModel()
            transcript.text = ""
            runStatusText = "New session started."
            renderStatus()
        } else {
            runStatusText = "Cannot start a new session while a run is in progress."
            renderStatus()
        }
    }

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        val r = runtime
        if (r == null) {
            runStatusText = "No provider configured. Select a node first."
            renderStatus()
            return
        }
        if (r.isBusy) {
            runStatusText = "A run is already in progress for this session."
            renderStatus()
            return
        }

        transcriptModel.onUser(text)
        renderTranscript()
        input.setText("")
        setBusy(true)
        runStatusText = "Running..."
        renderStatus()

        lifecycleScope.launch {
            val outcome = r.send(text)
            // AGENT-0.PREFLIGHT: the composition-root collector has reconstructed
            // this Run from the AgentEvent stream. Render the redacted trace so the
            // run's tool calls, arguments, results and outcome can be replayed.
            logTrace()
            when (outcome) {
                is RunOutcome.Answer -> {
                    transcriptModel.onAnswerProduced(outcome.content)
                    renderTranscript()
                }

                is RunOutcome.Failure -> {
                    transcriptModel.onRunFailed(outcome.failure)
                    renderTranscript()
                }
            }
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        send.isEnabled = !busy
        newSession.isEnabled = !busy
    }

    /**
     * Logs the redacted trace of the most recent Run. The content is the
     * [AgentTrace] rendered by [TraceFormatter], which redacts any secret-bearing
     * value before it reaches the log. A rejected (busy) Run produced no trace.
     */
    private fun logTrace() {
        val collector = root?.traceCollector ?: return
        val trace: AgentTrace = collector.trace ?: return
        val rendered = TraceFormatter.format(trace)
        for (line in rendered.lineSequence()) {
            Log.i("AgentTrace", line)
        }
    }

    private fun setStatus(text: String) {
        status.text = text
    }

    private companion object {
        /**
         * AG.DEBUG-0: process-scoped debug bridge and the runtime it currently
         * points at. Shared across Activity instances so recreation never splits
         * the listener. Both are only ever set in debug builds.
         */
        @Volatile
        private var sharedDebugBridge: DebugBridge? = null

        @Volatile
        private var sharedRuntime: AgentRuntime? = null
    }
}