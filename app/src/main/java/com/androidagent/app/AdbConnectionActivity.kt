package com.androidagent.app

import android.content.Intent
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
import kotlinx.coroutines.launch

/**
 * AG.ADB-CONNECTION-UX-1R: Unified ADB Management surface.
 *
 * Reuses the EXISTING process-scoped AT composition (via SessionHolder.root).
 * Does NOT create a second ATAndroid.create() call.
 *
 * Functions:
 *   - Current ADB status (from AT bootstrap state)
 *   - Reconnect (when applicable; uses AT ensureReady)
 *   - Pair / Re-pair (always available; click to expand)
 *   - Open Wireless Debugging Settings (always available)
 *   - Refresh (always available)
 *
 * NO Disconnect (AT v0.4.4 has no formal disconnect API).
 */
class AdbConnectionActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var statusDot: View

    private lateinit var reconnectBtn: Button
    private lateinit var pairEntryBtn: Button
    private lateinit var openWdBtn: Button
    private lateinit var refreshBtn: Button

    // Pairing panel (expanded on demand)
    private lateinit var pairPanel: View
    private lateinit var pairHint: TextView
    private lateinit var codeInput: EditText
    private lateinit var pairBtn: Button
    private lateinit var pairStatus: TextView

    private lateinit var backBtn: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adb_connection)

        statusTitle = findViewById(R.id.adb_status_title)
        statusDesc = findViewById(R.id.adb_status_desc)
        statusDot = findViewById(R.id.adb_status_dot)

        reconnectBtn = findViewById(R.id.adb_reconnect_btn)
        pairEntryBtn = findViewById(R.id.adb_pair_entry_btn)
        openWdBtn = findViewById(R.id.adb_open_wd_btn)
        refreshBtn = findViewById(R.id.adb_refresh_btn)

        pairPanel = findViewById(R.id.adb_pair_panel)
        pairHint = findViewById(R.id.adb_pair_hint)
        codeInput = findViewById(R.id.adb_pair_code)
        pairBtn = findViewById(R.id.adb_pair_btn)
        pairStatus = findViewById(R.id.adb_pair_status)

        backBtn = findViewById(R.id.adb_back_btn)

        // Actions
        backBtn.setOnClickListener { finish() }
        reconnectBtn.setOnClickListener { onReconnect() }
        pairEntryBtn.setOnClickListener { togglePairPanel() }
        openWdBtn.setOnClickListener { openWirelessDebuggingSettings() }
        refreshBtn.setOnClickListener { renderState() }
        pairBtn.setOnClickListener { onPair() }

        // Numeric input, max 6
        codeInput.inputType = InputType.TYPE_CLASS_NUMBER
        codeInput.maxEms = 6
        codeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pairBtn.isEnabled = s?.length == 6
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        codeInput.setOnEditorActionListener { _, _, _ -> onPair(); true }
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    /** Refresh the management surface from the existing AT composition. */
    private fun renderState() {
        val shell = SessionHolder.root?.shell ?: return
        val state = shell.readiness()
        renderBootstrapState(state.code, state.ready)
    }

    private fun renderBootstrapState(code: String, ready: Boolean) {
        val p = adbManagementPresentation(code, if (ready) null else "Connecting...")

        statusTitle.text = getString(p.titleRes)
        statusDesc.text = getString(p.descRes)
        statusDot.background.setTint(ContextCompat.getColor(this, p.colorRes))

        // Button visibility per state
        reconnectBtn.visibility = if (p.reconnectVisible) View.VISIBLE else View.GONE
        pairEntryBtn.visibility = if (p.pairVisible) View.VISIBLE else View.GONE
        openWdBtn.visibility = if (p.settingsVisible) View.VISIBLE else View.GONE
        refreshBtn.visibility = if (p.refreshVisible) View.VISIBLE else View.GONE

        // Pairing panel: hide when not in a pairing-relevant state
        if (code != "PAIRING_REQUIRED" && code != "USER_ACTION_REQUIRED_WD") {
            // Keep panel visible if user already expanded it (don't yank it away)
            // But for PAIRING_REQUIRED, auto-expand
            if (code == "PAIRING_REQUIRED") {
                pairPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun togglePairPanel() {
        val visible = pairPanel.visibility == View.VISIBLE
        pairPanel.visibility = if (visible) View.GONE else View.VISIBLE
        pairEntryBtn.text = getString(if (visible) R.string.adb_pair_entry else R.string.adb_pair_entry_collapsed)
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

            // Use shared presentation mapping
            val msgRes = adbPairingResultMessage(result.outcome, result.error)
            pairStatus.text = getString(msgRes)

            if (result.outcome == com.androidagent.integration.at.AtPairingResult.PairingOutcome.SUCCESS) {
                val shell = SessionHolder.root?.shell
                shell?.connect()
                renderState()
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
