package com.androidagent.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.androidagent.integration.at.AtAdbBootstrap
import kotlinx.coroutines.launch

/**
 * AG.ADB-CONNECTION-PAGE-0: Dedicated ADB Connection / Pairing page.
 *
 * Reuses the EXISTING process-scoped AT composition (via SessionHolder.root).
 * Does NOT create a second ATAndroid.create() call.
 *
 * State source: ShellConnection.readiness() -> AtAdbBootstrap enum.
 * Pairing: AtExecutionAdapter.pairDiscovered(code) -> AtPairingResult.
 * Reconnect: AtExecutionAdapter.ensureAdbReady().
 */
class AdbConnectionActivity : AppCompatActivity() {

    private lateinit var title: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var statusDot: View
    private lateinit var actionSection: View
    private lateinit var reconnectBtn: Button
    private lateinit var openWdBtn: Button
    private lateinit var refreshBtn: Button

    private lateinit var pairSection: View
    private lateinit var pairHint: TextView
    private lateinit var codeInput: EditText
    private lateinit var pairBtn: Button
    private lateinit var pairStatus: TextView

    private lateinit var backBtn: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adb_connection)

        title = findViewById(R.id.adb_page_title)
        statusTitle = findViewById(R.id.adb_status_title)
        statusDesc = findViewById(R.id.adb_status_desc)
        statusDot = findViewById(R.id.adb_status_dot)
        actionSection = findViewById(R.id.adb_action_section)
        reconnectBtn = findViewById(R.id.adb_reconnect_btn)
        openWdBtn = findViewById(R.id.adb_open_wd_btn)
        refreshBtn = findViewById(R.id.adb_refresh_btn)

        pairSection = findViewById(R.id.adb_pair_section)
        pairHint = findViewById(R.id.adb_pair_hint)
        codeInput = findViewById(R.id.adb_pair_code)
        pairBtn = findViewById(R.id.adb_pair_btn)
        pairStatus = findViewById(R.id.adb_pair_status)

        backBtn = findViewById(R.id.adb_back_btn)

        backBtn.setOnClickListener { finish() }
        reconnectBtn.setOnClickListener { onReconnect() }
        openWdBtn.setOnClickListener { openWirelessDebuggingSettings() }
        refreshBtn.setOnClickListener { renderState() }
        pairBtn.setOnClickListener { onPair() }

        // Numeric input, max 6
        codeInput.inputType = InputType.TYPE_CLASS_NUMBER
        codeInput.maxEms = 6

        // Disable pair button when input is not 6 digits
        codeInput.setOnEditorActionListener { v, _, _ ->
            onPair()
            true
        }
        codeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pairBtn.isEnabled = s?.length == 6
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    /** Refresh the page state from the existing AT composition. */
    private fun renderState() {
        val shell = SessionHolder.root?.shell ?: return
        val state = shell.readiness()
        renderBootstrapState(state.code, state.ready)
    }

    private fun renderBootstrapState(code: String, ready: Boolean) {
        when (code) {
            AtAdbBootstrap.READY.name -> {
                statusTitle.text = getString(R.string.adb_state_ready)
                statusDesc.text = getString(R.string.adb_state_ready_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_ready)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.GONE
                openWdBtn.visibility = View.GONE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
            AtAdbBootstrap.PAIRING_REQUIRED.name -> {
                statusTitle.text = getString(R.string.adb_state_pairing_required)
                statusDesc.text = getString(R.string.adb_state_pairing_required_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_pairing)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.GONE
                openWdBtn.visibility = View.VISIBLE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.VISIBLE
                pairStatus.text = ""
            }
            "USER_ACTION_REQUIRED_WD" -> {
                statusTitle.text = getString(R.string.adb_state_wd_off)
                statusDesc.text = getString(R.string.adb_state_wd_off_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_disconnected)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.GONE
                openWdBtn.visibility = View.VISIBLE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
            AtAdbBootstrap.CONNECT_FAILED.name -> {
                statusTitle.text = getString(R.string.adb_state_connect_failed)
                statusDesc.text = getString(R.string.adb_state_connect_failed_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_disconnected)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.VISIBLE
                openWdBtn.visibility = View.VISIBLE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
            "ENDPOINT_NOT_FOUND" -> {
                statusTitle.text = getString(R.string.adb_state_endpoint_not_found)
                statusDesc.text = getString(R.string.adb_state_endpoint_not_found_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_disconnected)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.VISIBLE
                openWdBtn.visibility = View.VISIBLE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
            "NOT_READY" -> {
                statusTitle.text = getString(R.string.adb_state_not_ready)
                statusDesc.text = getString(R.string.adb_state_not_ready_desc)
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_disconnected)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.VISIBLE
                openWdBtn.visibility = View.GONE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
            else -> {
                statusTitle.text = getString(R.string.adb_state_unknown)
                statusDesc.text = code
                statusDot.background = ContextCompat.getDrawable(this, R.drawable.adb_dot_disconnected)
                actionSection.visibility = View.VISIBLE
                reconnectBtn.visibility = View.VISIBLE
                openWdBtn.visibility = View.GONE
                refreshBtn.visibility = View.VISIBLE
                pairSection.visibility = View.GONE
            }
        }
    }

    private fun onReconnect() {
        reconnectBtn.isEnabled = false
        statusDesc.text = getString(R.string.adb_reconnecting)
        lifecycleScope.launch {
            val shell = SessionHolder.root?.shell
            shell?.connect()
            renderState()
            reconnectBtn.isEnabled = true
        }
    }

    private fun onPair() {
        val code = codeInput.text.toString().trim()
        if (code.length != 6) {
            pairStatus.text = getString(R.string.adb_pair_invalid_code)
            return
        }
        pairBtn.isEnabled = false
        pairStatus.text = getString(R.string.adb_pair_in_progress)
        lifecycleScope.launch {
            val adapter = SessionHolder.root?.shell?.adapter
            if (adapter == null) {
                pairStatus.text = getString(R.string.adb_pair_adapter_unavailable)
                pairBtn.isEnabled = true
                return@launch
            }
            val result = adapter.pairDiscovered(code)
            codeInput.text.clear()
            when (result.outcome) {
                com.androidagent.integration.at.AtPairingResult.PairingOutcome.SUCCESS -> {
                    pairStatus.text = getString(R.string.adb_pair_success)
                    // Auto-reconnect after successful pairing
                    val shell = SessionHolder.root?.shell
                    shell?.connect()
                    renderState()
                }
                com.androidagent.integration.at.AtPairingResult.PairingOutcome.REJECTED ->
                    pairStatus.text = getString(R.string.adb_pair_rejected)
                com.androidagent.integration.at.AtPairingResult.PairingOutcome.SERVICE_NOT_FOUND ->
                    pairStatus.text = getString(R.string.adb_pair_service_not_found)
                com.androidagent.integration.at.AtPairingResult.PairingOutcome.MULTIPLE_ENDPOINTS ->
                    pairStatus.text = getString(R.string.adb_pair_multiple_endpoints)
                com.androidagent.integration.at.AtPairingResult.PairingOutcome.FAILED ->
                    pairStatus.text = getString(R.string.adb_pair_failed) +
                        (result.error?.let { " $it" } ?: "")
            }
            pairBtn.isEnabled = true
        }
    }

    private fun openWirelessDebuggingSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                pairStatus.text = getString(R.string.adb_wd_open_failed)
            }
        }
    }
}
