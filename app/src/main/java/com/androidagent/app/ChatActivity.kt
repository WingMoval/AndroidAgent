package com.androidagent.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidagent.core.RunFailure
import com.androidagent.core.RunOutcome
import com.androidagent.runtime.AgentRuntime
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.app.trace.AgentTrace
import com.androidagent.app.trace.TraceFormatter
import kotlinx.coroutines.launch
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
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var newSession: Button

    private lateinit var baseUrlField: EditText
    private lateinit var modelField: EditText
    private lateinit var apiKeyField: EditText
    private lateinit var timeoutField: EditText

    private var root: ChatCompositionRoot? = null
    private var runtime: AgentRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        transcript = findViewById(R.id.transcript)
        status = findViewById(R.id.status)
        input = findViewById(R.id.input)
        send = findViewById(R.id.send)
        newSession = findViewById(R.id.new_session)
        baseUrlField = findViewById(R.id.base_url)
        modelField = findViewById(R.id.model)
        apiKeyField = findViewById(R.id.api_key)
        timeoutField = findViewById(R.id.timeout)

        baseUrlField.setText(DEFAULT_BASE_URL)
        modelField.setText(DEFAULT_MODEL)
        timeoutField.setText(DEFAULT_TIMEOUT_SECONDS.toString())

        findViewById<Button>(R.id.apply).setOnClickListener { applyConfig() }
        send.setOnClickListener { onSend() }
        newSession.setOnClickListener { onNewSession() }

        setStatus("Enter the API key, tap Apply, then send a message.")
    }

    private fun applyConfig() {
        val cfg = try {
            OpenAiCompatibleConfig(
                baseUrl = baseUrlField.text.toString().trim().ifEmpty { DEFAULT_BASE_URL },
                model = modelField.text.toString().trim().ifEmpty { DEFAULT_MODEL },
                apiKey = apiKeyField.text.toString(),
                requestTimeout = Duration.ofSeconds(
                    timeoutField.text.toString().trim().toLongOrNull()
                        ?: DEFAULT_TIMEOUT_SECONDS
                ),
            )
        } catch (e: IllegalArgumentException) {
            setStatus("Invalid configuration: ${e.message}")
            return
        }

        root = ChatCompositionRoot(cfg, applicationContext)
        runtime = root?.runtime
        // Clear the field so the key is not left sitting in the view hierarchy.
        apiKeyField.setText("")
        setStatus(
            "Provider ready (${cfg.model}, timeout ${cfg.requestTimeout.seconds}s). " +
                "API_KEY = ${if (cfg.apiKey.isBlank()) "ABSENT" else "PRESENT"}"
        )

        // AG.SHELL-1: wire the AT Engine lifecycle (Host owns the policy; the
        // composition-root ShellConnection performs the calls off the main thread).
        // Minimal status display only — no dedicated settings UI in this step.
        val shellConnection = root?.shell
        lifecycleScope.launch {
            val status = shellConnection?.connect()
            if (status == null) {
                setStatus("AT Engine: not connected.")
            } else if (status.ready) {
                setStatus("AT Engine ready.")
            } else {
                setStatus("AT Engine: ${status.message}")
            }
        }
    }

    private fun onNewSession() {
        val switched = runtime?.newSession() ?: false
        if (switched) {
            transcript.text = ""
            setStatus("New session started.")
        } else {
            setStatus("Cannot start a new session while a run is in progress.")
        }
    }

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        val r = runtime
        if (r == null) {
            setStatus("Tap Apply first to configure the provider.")
            return
        }
        if (r.isBusy) {
            setStatus("A run is already in progress for this session.")
            return
        }

        append("You", text)
        input.setText("")
        setBusy(true)
        setStatus("Running...")

        lifecycleScope.launch {
            val outcome = r.send(text)
            // AGENT-0.PREFLIGHT: the composition-root collector has reconstructed
            // this Run from the AgentEvent stream. Render the redacted trace so the
            // run's tool calls, arguments, results and outcome can be replayed.
            logTrace()
            when (outcome) {
                is RunOutcome.Answer -> {
                    append("Agent", outcome.content)
                    setStatus("Ready.")
                }
                is RunOutcome.Failure -> {
                    append("Agent", safeError(outcome.failure))
                    setStatus("Run failed.")
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
        const val DEFAULT_TIMEOUT_SECONDS = 120L
    }
}