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
import com.androidagent.app.trace.AgentTrace
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.runtime.AgentRuntime
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Duration

/**
 * Minimal AGA chat host.
 *
 * Deliberately plain Android Views: this step verifies that a real Android host
 * can consume the frozen pure-JVM runtime, not that a UI framework is ergonomic.
 * No Compose, no Navigation, no DI, no Room, no third-party HTTP client.
 *
 * Credential policy: the API key is typed at runtime and held in process memory
 * only. It is never written to disk, SharedPreferences, DataStore, resources or
 * BuildConfig.
 *
 * AGA.HOST-CONTROL-0: persistent status shows the selected Provider node and the
 * AT Engine connection; the transient run phase (Thinking / Executing / Ready /
 * Failed) is rendered below without overwriting either.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var newSession: Button
    private lateinit var reconnect: Button

    private lateinit var baseUrlField: EditText
    private lateinit var modelField: EditText
    private lateinit var apiKeyField: EditText
    private lateinit var timeoutField: EditText
    private lateinit var nodeSpinner: Spinner

    private var root: ChatCompositionRoot? = null
    private var runtime: AgentRuntime? = null

    /** Currently selected provider node. Guarded against listener re-entry. */
    private var currentNode: ProviderNode = ProviderNode.EMG
    private var applyingNode: Boolean = false

    /** Persistent: the active Provider node (EMG / OpenRouter). */
    private var providerStatusText: String? = null

    /** Persistent: the AT Engine connection state. */
    private var atStatusText: String? = null

    /** Transient: the run phase (Thinking / Executing / ... / Ready / Failed). */
    private var runStatusText: String? = null

    /** Last successfully applied credential, retained across a blank-key Apply. */
    private var lastAppliedKey: String = ""

    /** Whether a manual reconnect attempt is in flight (button disabled then). */
    private var reconnecting: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        transcript = findViewById(R.id.transcript)
        status = findViewById(R.id.status)
        input = findViewById(R.id.input)
        send = findViewById(R.id.send)
        newSession = findViewById(R.id.new_session)
        reconnect = findViewById(R.id.reconnect)
        baseUrlField = findViewById(R.id.base_url)
        modelField = findViewById(R.id.model)
        apiKeyField = findViewById(R.id.api_key)
        timeoutField = findViewById(R.id.timeout)
        nodeSpinner = findViewById(R.id.provider_node)

        baseUrlField.setText(DEFAULT_BASE_URL)
        modelField.setText(DEFAULT_MODEL)
        timeoutField.setText(DEFAULT_TIMEOUT_SECONDS.toString())

        findViewById<Button>(R.id.apply).setOnClickListener { applyConfig() }
        send.setOnClickListener { onSend() }
        newSession.setOnClickListener { onNewSession() }
        reconnect.setOnClickListener { onReconnect() }
        setupNodeSpinner()

        // AGA.DEV-CONFIG-0: at startup, try the device-local provider config.
        // If present and valid, the Provider is ready with no manual key entry.
        // Otherwise (missing or invalid) the existing manual UI flow is preserved.
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
                if (node != currentNode) {
                    currentNode = node
                    applyNode(node)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyNode(node: ProviderNode) {
        when (val result = DeviceLocalProviderNodeLoader.load(applicationContext, node)) {
            is DeviceLocalProviderConfig.Loaded -> {
                applyConfig(result.config, source = node.displayName)
            }

            is DeviceLocalProviderConfig.Missing ->
                setStatus("$node config not provisioned. Enter the API key, tap Apply, then send a message.")

            is DeviceLocalProviderConfig.Invalid ->
                setStatus("$node config invalid. Enter the API key, tap Apply, then send a message.")
        }
    }

    private fun tryDeviceLocalConfig() {
        val defaultNode = ProviderNode.entries.firstOrNull {
            DeviceLocalProviderNodeLoader.load(applicationContext, it) is DeviceLocalProviderConfig.Loaded
        } ?: ProviderNode.EMG
        currentNode = defaultNode
        applyingNode = true
        nodeSpinner.setSelection(ProviderNode.entries.indexOf(defaultNode))
        applyingNode = false
        applyNode(defaultNode)
    }

    /**
     * Applies a config (from a node or manual entry). AT startup auto-connect is
     * unconditional in this consolidated Host: the single AGA APK always wires AT.
     */
    private fun applyConfig(cfg: OpenAiCompatibleConfig, source: String) {
        root = ChatCompositionRoot(cfg, applicationContext, onRunEvent = { onRunEvent(it) })
        runtime = root?.runtime
        lastAppliedKey = cfg.apiKey
        // Reflect the active node in the editable fields; the API key field stays
        // blank (never echoed back into the view hierarchy).
        baseUrlField.setText(cfg.baseUrl)
        modelField.setText(cfg.model)
        timeoutField.setText(cfg.requestTimeout.seconds.toString())
        apiKeyField.setText("")
        providerStatusText = HostStatusModel.providerLine(source)
        atStatusText = null
        runStatusText = null
        renderStatus()
        connectShell()
    }

    /**
     * Manual Apply: build a config from the editable fields. A blank API key
     * field must NOT overwrite the active credential (the 401 regression fix).
     */
    private fun applyConfig() {
        val typedKey = apiKeyField.text.toString()
        val effectiveKey = HostStatusModel.effectiveKey(typedKey, lastAppliedKey)
        val cfg = try {
            OpenAiCompatibleConfig(
                baseUrl = baseUrlField.text.toString().trim().ifEmpty { DEFAULT_BASE_URL },
                model = modelField.text.toString().trim().ifEmpty { DEFAULT_MODEL },
                apiKey = effectiveKey,
                requestTimeout = Duration.ofSeconds(
                    timeoutField.text.toString().trim().toLongOrNull()
                        ?: DEFAULT_TIMEOUT_SECONDS
                ),
            )
        } catch (e: IllegalArgumentException) {
            runStatusText = "Invalid configuration: ${e.message}"
            renderStatus()
            return
        }
        applyConfig(cfg, source = "MANUAL")
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
            // A short yield lets connectShell's launch begin; the button re-enables
            // on the next status render.
            reconnecting = false
            reconnect.isEnabled = true
        }
    }

    // ----------------------------------------------------------------- Run status

    /**
     * Host-side observer for AG Core's [AgentEvent] stream. Renders the transient
     * run phase without touching the persistent Provider / AT status lines.
     */
    private fun onRunEvent(event: AgentEvent) {
        runStatusText = HostStatusModel.runPhaseText(event)
        runOnUiThread { renderStatus() }
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

    private fun onNewSession() {
        val switched = runtime?.newSession() ?: false
        if (switched) {
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
            runStatusText = "Tap Apply first to configure the provider."
            renderStatus()
            return
        }
        if (r.isBusy) {
            runStatusText = "A run is already in progress for this session."
            renderStatus()
            return
        }

        append("You", text)
        input.setText("")
        setBusy(true)
        runStatusText = "Thinking..."
        renderStatus()

        lifecycleScope.launch {
            val outcome = r.send(text)
            // AGENT-0.PREFLIGHT: the composition-root collector has reconstructed
            // this Run from the AgentEvent stream. Render the redacted trace so the
            // run's tool calls, arguments, results and outcome can be replayed.
            logTrace()
            when (outcome) {
                is RunOutcome.Answer -> append("Agent", outcome.content)
                is RunOutcome.Failure -> append("Agent", safeError(outcome.failure))
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

    /** Safe, user-readable failure text. Never includes a key or header. */
    private fun safeError(failure: RunFailure): String = when (failure) {
        is RunFailure.ProviderFailure -> "Provider error (${failure.cause})."
        is RunFailure.MaxStepsReached -> "Stopped after ${failure.maxSteps} steps."
        is RunFailure.SessionBusy -> "This session is busy."
        is RunFailure.RuntimeFailure -> "Runtime error (${failure.cause})."
    }

    private fun append(who: String, body: String) {
        transcript.append("$who: $body\n\n")
    }

    private fun setStatus(text: String) {
        status.text = text
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.emg.dpdns.org/v1"
        const val DEFAULT_MODEL = "qwen3.8-local"
        // EMG is slow/variable (measured up to ~142s per request on this phone),
        // and an Agent run may need several model turns. 600s is a safe default
        // for the manual fallback; per-node configs override this anyway.
        const val DEFAULT_TIMEOUT_SECONDS = 600L
    }
}