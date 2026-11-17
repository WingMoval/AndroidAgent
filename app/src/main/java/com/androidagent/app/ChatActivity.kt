package com.androidagent.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.androidagent.app.debug.DebugBridge
import com.androidagent.app.debug.DebugProtocol
import com.androidagent.app.trace.AgentTrace
import com.androidagent.app.trace.TraceFormatter
import com.androidagent.core.AgentEvent
import com.androidagent.core.ModelStreamObserver
import com.androidagent.integration.at.AtAdbBootstrap
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.runtime.AgentRuntime
import kotlinx.coroutines.launch
// ---------------------------------------------------------------------------
// AG.UI-SHELL-1 — pure workspace presentation mappings (JVM-testable).
// ---------------------------------------------------------------------------

/** Send/Stop single-slot visibility: exactly one is shown, driven by real busy. */
data class WorkspaceActionSlot(val sendVisible: Boolean, val stopVisible: Boolean) {
    init { check(sendVisible != stopVisible) { "Send and Stop must occupy the slot exclusively" } }
}

fun workspaceActionSlot(busy: Boolean): WorkspaceActionSlot =
    if (busy) WorkspaceActionSlot(sendVisible = false, stopVisible = true)
    else WorkspaceActionSlot(sendVisible = true, stopVisible = false)

/** Header ADB indicator: semantic color bucket + accessible text + pair offer. */
data class WorkspaceAdbIndicator(val colorRes: Int, val textRes: Int, val pairOffered: Boolean)

fun workspaceAdbIndicator(atStatusText: String?, bootstrapCode: String?): WorkspaceAdbIndicator = when {
    bootstrapCode == null ->
        WorkspaceAdbIndicator(R.color.ws_adb_disconnected, R.string.ws_adb_disconnected, pairOffered = false)
    atStatusText == "Connecting..." ->
        WorkspaceAdbIndicator(R.color.ws_adb_connecting, R.string.ws_adb_connecting, pairOffered = false)
    bootstrapCode == AtAdbBootstrap.READY.name ->
        WorkspaceAdbIndicator(R.color.ws_adb_ready, R.string.ws_adb_connected, pairOffered = false)
    else ->
        WorkspaceAdbIndicator(R.color.ws_adb_disconnected, R.string.ws_adb_disconnected, pairOffered = true)
}

/**
 * Composer model label: the SHORT display name only (no fake effort).
 *
 * AG.PROVIDER-CONFIGURED-MODELS-0: the home composer shows the provider prefix
 * (the part before "/") of the active model id, or the id itself when there is
 * no "/". The full long modelId is shown in the model picker instead.
 */
fun workspaceModelLabel(activeModel: String?): String =
    modelShortDisplay(activeModel)


/**
 * VNext light Agent Workspace host (AG.UI-SHELL-1).
 *
 * Product Controls Alignment:
 *  - Composer shows ＋ / input / LLM selector / Send-Stop (one unified surface)
 *  - LLM selector switches models via HostActions.switchModel (canonical path)
 *  - Provider Settings Dialog manages endpoint/key/model
 *  - Sidebar order: ADB → Provider/Models → Session
 *  - Header shows compact "ADB ●" only
 */
/** A two-line row in the main model picker: short/alias name + full modelId. */
private data class PickerRow(
    val line1: String,
    val fullId: String,
    val opt: ModelOption,
)

/** Sentinel item for the "Add Model" row in the model picker adapter. */
private object ADD_MODEL_SENTINEL

class ChatActivity : AppCompatActivity() {

    // ---- Conversation viewport (AG.UI-TRANSCRIPT-CARDS-1: RecyclerView)
    private lateinit var transcriptRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var cardAdapter: TranscriptCardAdapter

    // ---- Composer
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: ImageButton
    private lateinit var stop: ImageButton
    private lateinit var modelSelector: View
    private lateinit var modelLabel: TextView
    private lateinit var composerAdd: ImageButton

    // ---- Header
    private lateinit var btnOpenSidebar: ImageButton
    private lateinit var adbDot: View

    // ---- Sidebar
    private lateinit var sidebar: View
    private lateinit var sidebarScrim: View
    private lateinit var btnCloseSidebar: ImageButton
    private lateinit var newSession: Button
    private lateinit var sidebarAdbRow: View
    private lateinit var sidebarAdbDot: View
    private lateinit var providerSettingsEntry: View
    private lateinit var providerStatusLabel: TextView
    private lateinit var currentSessionLabel: TextView
    private lateinit var sessionsEntry: View

    private var root: ChatCompositionRoot? = null
    private var runtime: AgentRuntime? = null

    /** The node that is ACTUALLY applied as the active provider. */
    private var activeNode: ProviderNode = ProviderNode.OPENROUTER

    /** The REAL active model id from the applied provider config. */
    private var activeModel: String? = null

    /** Pure transcript model; rebound from the process-scoped holder. */
    private var transcriptModel: TranscriptModel = TranscriptModel()

    private var atStatusText: String? = null
    private var runStatusText: String? = null
    private var reconnecting: Boolean = false
    private var sidebarOpen: Boolean = false
    private var autoFollow: Boolean = true

    /** Available (CONFIGURED) models for the LLM selector. */
    private var modelCatalog: List<ModelOption> = emptyList()

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: display metadata for each configured
     * model in the active profile, keyed by modelId. Used to render the home
     * short label (alias or provider prefix) and the two-line picker items.
     */
    private var modelDisplay: Map<String, ConfiguredModel> = emptyMap()

    /** Available sessions for the Session Browser (AG.UI-TRANSCRIPT-CARDS-1). */
    private var availableSessions: List<SessionSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        transcriptRecycler = findViewById(R.id.transcript_recycler)
        status = findViewById(R.id.status)
        input = findViewById(R.id.composer_input)
        send = findViewById(R.id.composer_send)
        stop = findViewById(R.id.composer_stop)
        modelSelector = findViewById(R.id.composer_model_selector)
        modelLabel = findViewById(R.id.composer_model_label)
        composerAdd = findViewById(R.id.composer_add)
        btnOpenSidebar = findViewById(R.id.btn_open_sidebar)
        adbDot = findViewById(R.id.adb_dot)
        sidebar = findViewById(R.id.sidebar)
        sidebarScrim = findViewById(R.id.sidebar_scrim)
        btnCloseSidebar = findViewById(R.id.btn_close_sidebar)
        newSession = findViewById(R.id.sidebar_new_session)
        sidebarAdbRow = findViewById(R.id.sidebar_adb_row)
        sidebarAdbDot = findViewById(R.id.sidebar_adb_dot)
        providerSettingsEntry = findViewById(R.id.sidebar_provider_settings)
        providerStatusLabel = findViewById(R.id.sidebar_provider_status)
        currentSessionLabel = findViewById(R.id.sidebar_current_session)
        sessionsEntry = findViewById(R.id.sidebar_sessions_entry)

        // ---- Composer actions
        send.setOnClickListener { onSend() }
        stop.setOnClickListener { onStopRun() }
        composerAdd.setOnClickListener { onAddContext() }
        modelSelector.setOnClickListener { showModelSelector() }
        newSession.setOnClickListener { onNewSession() }

        // ---- Sidebar
        btnOpenSidebar.setOnClickListener { openSidebar() }
        btnCloseSidebar.setOnClickListener { closeSidebar() }
        sidebarScrim.setOnClickListener { closeSidebar() }
        sidebarAdbRow.setOnClickListener {
            closeSidebar()
            startActivity(Intent(this, AdbConnectionActivity::class.java))
        }
        providerSettingsEntry.setOnClickListener { showProviderSettings() }
        currentSessionLabel.setOnClickListener { openSessionManagement() }
        sessionsEntry.setOnClickListener {
            closeSidebar()
            openSessionManagement()
        }

        // ---- AG.UI-TRANSCRIPT-CARDS-1: RecyclerView card renderer
        cardAdapter = TranscriptCardAdapter(
            context = this,
            onRetry = { text -> retrySend(text) },
            onEditResend = { text -> editAndResend(text) },
            isBusy = { SessionHolder.isBusy },
        )
        transcriptRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        transcriptRecycler.adapter = cardAdapter

        // Scroll behaviour: follow bottom during streaming unless user scrolled up
        ViewCompat.setOnApplyWindowInsetsListener(transcriptRecycler) { v, insets ->
            val bottom = insets.getSystemWindowInsets().bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }
        transcriptRecycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                val atBottom = lastVisible >= cardAdapter.itemCount - 3
                autoFollow = atBottom
            }
        })

        // ---- Rebind to SessionHolder
        val restored = SessionHolder.restoreState(savedInstanceState)
        Log.i("AG.UI", "onCreate: restoreState=$restored")
        ensureDebugBridge()

        if (restored) {
            root = SessionHolder.root
            runtime = SessionHolder.runtime
            transcriptModel = SessionHolder.transcriptModel
            activeNode = SessionHolder.providerNode
            atStatusText = SessionHolder.atStatusText
            runStatusText = SessionHolder.runStatusText
            setBusy(SessionHolder.isBusy)
            renderStatus()
            renderTranscript()
            updateModelLabel()
            updateAdbIndicator()
            updateProviderStatus()
        } else {
            SessionHolder.ensureAppContext(applicationContext)
            tryDeviceLocalConfig()
        }

        // Refresh model catalog
        refreshModelCatalog()

        SessionHolder.onUiUpdate = {
            runOnUiThread {
                root = SessionHolder.root
                runtime = SessionHolder.runtime
                transcriptModel = SessionHolder.transcriptModel
                runStatusText = SessionHolder.runStatusText
                atStatusText = SessionHolder.atStatusText
                activeModel = SessionHolder.activeModel
                setBusy(SessionHolder.isBusy)
                renderStatus()
                renderTranscript()
                updateModelLabel()
                updateProviderStatus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderTranscript()
        renderStatus()
        updateModelLabel()
        updateAdbIndicator()
        setBusy(runtime?.isBusy == true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        SessionHolder.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Deprecated("Back closes the sidebar first, then exits.")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (sidebarOpen) closeSidebar()
        else super.onBackPressed()
    }

    // ------------------------------------------------------------- Sidebar

    private fun openSidebar() {
        sidebar.visibility = View.VISIBLE
        sidebarScrim.visibility = View.VISIBLE
        sidebarOpen = true
        updateProviderStatus()
    }

    private fun closeSidebar() {
        sidebar.visibility = View.INVISIBLE
        sidebarScrim.visibility = View.INVISIBLE
        sidebarOpen = false
    }

    // ------------------------------------------------------------- Model Selector

    /**
     * Shows the LLM selector dialog. Lists all available models from saved
     * provider configs + optional discovery. Selecting a model delegates to
     * HostActions.switchModel (canonical apply path).
     */
    private fun showModelSelector() {
        refreshModelCatalog()
        if (modelCatalog.isEmpty()) {
            Toast.makeText(this, "No models configured", Toast.LENGTH_SHORT).show()
            return
        }

        val currentIdx = modelCatalog.indexOfFirst { it.modelId == activeModel }.coerceAtLeast(0)

        // AG.PROVIDER-CONFIGURED-MODELS-0: the main picker shows CONFIGURED models
        // only, each as a TWO-LINE row:
        //   line 1 = alias (if set) else the provider prefix (short name)
        //   line 2 = the FULL modelId (always visible, so the real model is identifiable)
        // plus an "Add Model" entry — the ONLY place the discovered /models
        // catalogue is reachable.
        val rows = modelCatalog.map { opt ->
            val meta = modelDisplay[opt.modelId]
            val line1 = meta?.shortDisplay ?: opt.modelId.substringBefore("/", opt.modelId)
            PickerRow(line1, opt.modelId, opt)
        }

        // Build a two-line list: each configured model contributes one row, and the
        // LAST row is the "Add Model" entry. getCount/getItem must report rows.size+1
        // so the ListAdapter actually creates the Add Model row.
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = rows.size + 1
            override fun getItem(position: Int): Any =
                if (position < rows.size) rows[position] else ADD_MODEL_SENTINEL
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val row = if (position < rows.size) rows[position] else null
                val view = convertView ?: LayoutInflater.from(this@ChatActivity).inflate(
                    R.layout.item_model_picker, parent, false,
                )
                // Make each row reliably clickable (do NOT rely on the ListView's
                // item-click callback — a custom getView that sets its own listeners
                // can swallow the row touch and the callback never fires).
                view.isClickable = true
                view.setOnLongClickListener(null)
                val t1 = view.findViewById<TextView>(R.id.model_picker_line1)
                val t2 = view.findViewById<TextView>(R.id.model_picker_line2)
                if (row != null) {
                    t1.text = row.line1
                    t1.visibility = View.VISIBLE
                    t2.text = row.fullId
                    t2.visibility = View.VISIBLE
                    view.setOnClickListener {
                        onPickModel(row.opt)
                    }
                    // Long-press a configured model to manage it (rename alias / remove).
                    view.setOnLongClickListener {
                        showModelManageMenu(row.opt)
                        true
                    }
                } else {
                    t1.text = getString(R.string.ws_model_add)
                    t1.visibility = View.VISIBLE
                    t2.visibility = View.GONE
                    view.setOnClickListener {
                        showAddModelDialog()
                    }
                }
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_label_settings)
            .setAdapter(adapter, null)
            .setNegativeButton(R.string.ws_cancel, null)
            .show()

        @Suppress("UNUSED_EXPRESSION") currentIdx
    }

    /** Switch the active model to [opt] through the canonical apply path. */
    private fun onPickModel(opt: ModelOption) {
        lifecycleScope.launch {
            val result = HostActions.switchProfile(opt.profileId, opt.modelId)
            when (result) {
                is ProfileSwitchResult.Applied -> {
                    activeModel = SessionHolder.activeModel
                    updateModelLabel()
                    updateProviderStatus()
                    renderStatus()
                }
                is ProfileSwitchResult.Rejected -> {
                    updateRunStatus(result.reason)
                    renderStatus()
                }
            }
        }
    }

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: long-press menu for a configured model —
     * rename its alias and/or remove it. Editing happens HERE in the picker,
     * not only at Add time. Credential (endpoint + key) is profile-level and is
     * never touched by per-model edits.
     */
    private fun showModelManageMenu(opt: ModelOption) {
        val options = arrayOf(
            getString(R.string.ws_model_rename),
            getString(R.string.ws_model_remove),
        )
        AlertDialog.Builder(this)
            .setTitle(opt.modelId)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAliasDialogForEdit(opt)
                    1 -> confirmRemoveModel(opt)
                }
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /**
     * Edit the alias of an already-configured model. Blank input clears the
     * alias (falls back to the provider-prefix short name).
     */
    private fun showAliasDialogForEdit(opt: ModelOption) {
        val profile = activeProfile() ?: return
        val existing = profile.configuredModels.firstOrNull { it.modelId == opt.modelId }
            ?.normalizedAlias
        val aliasInput = EditText(this)
        aliasInput.hint = getString(R.string.ws_model_alias_hint)
        aliasInput.setText(existing ?: "")
        val pad = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            addView(
                aliasInput,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_rename)
            .setMessage(opt.modelId)
            .setView(container)
            .setPositiveButton(R.string.ws_save) { _, _ ->
                val alias = aliasInput.text.toString().trim().takeIf { it.isNotEmpty() }
                persistModelAlias(profile, opt.modelId, alias)
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /**
     * Update (or clear) the alias of a configured model, preserving everything
     * else (modelId, credential, timeout, other models, selected model).
     */
    private fun persistModelAlias(profile: ProviderProfile, modelId: String, alias: String?) {
        val updated = profile.copy(
            configuredModels = profile.configuredModels.map { cm ->
                if (cm.modelId == modelId) ConfiguredModel(modelId, alias) else cm
            },
        )
        saveProfileKeepingCredential(updated)
        Toast.makeText(this, R.string.ws_model_alias_saved, Toast.LENGTH_SHORT).show()
        refreshModelCatalog()
        updateModelLabel()
    }

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: rebuild the main-switcher catalog from the
     * CONFIGURED models of all saved profiles. Discovery is intentionally NOT run
     * here — the discovered catalogue is reachable only through "Add Model".
     */
    private fun refreshModelCatalog() {
        val profiles = ProviderProfileStore.loadAll(applicationContext)
        modelCatalog = buildModelCatalog(applicationContext)
        val active = profiles.firstOrNull {
            it.isBuiltIn && SessionHolder.providerNode.name.lowercase() == it.profileId
        } ?: profiles.firstOrNull()
        modelDisplay = active?.configuredModels?.associateBy { it.modelId } ?: emptyMap()
        // Keep the display map complete for any catalog entry (covers models owned
        // by other profiles if the catalog is later extended).
        for (opt in modelCatalog) {
            if (!modelDisplay.containsKey(opt.modelId)) {
                ProviderProfileStore.load(applicationContext, opt.profileId)
                    ?.configuredModels
                    ?.firstOrNull { it.modelId == opt.modelId }
                    ?.let { modelDisplay += opt.modelId to it }
            }
        }
    }

    /**
     * The profile that is currently applied (built-in by node, else first).
     * Returns null when NO profiles exist yet (e.g. a fresh install with no
     * provider configured) — callers must handle that.
     */
    private fun activeProfile(): ProviderProfile? =
        ProviderProfileStore.loadAll(applicationContext).firstOrNull {
            it.isBuiltIn && SessionHolder.providerNode.name.lowercase() == it.profileId
        } ?: ProviderProfileStore.loadAll(applicationContext).firstOrNull()

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: "Add Model" — fetch the provider /models
     * catalogue (ONLY here), let the user pick one, optionally give it an alias,
     * and persist it into the profile's configured models.
     */
    private fun showAddModelDialog() {
        val profile = activeProfile() ?: run {
            Toast.makeText(this, "No provider configured", Toast.LENGTH_SHORT).show()
            return
        }
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_add)
            .setMessage(R.string.ws_model_discovering)
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ModelDiscovery.discover(profile.config) }
            progress.dismiss()
            when (result) {
                is ModelDiscoveryResult.Success -> {
                    val candidates = result.models
                        .filter { profile.configuredModels.none { cm -> cm.modelId == it } }
                    if (candidates.isEmpty()) {
                        Toast.makeText(this@ChatActivity, R.string.ws_model_all_configured, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    showPickDiscoveredDialog(profile, candidates)
                }
                is ModelDiscoveryResult.Failure -> {
                    Toast.makeText(this@ChatActivity, R.string.ws_model_discovery_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPickDiscoveredDialog(profile: ProviderProfile, candidates: List<String>) {
        val labels = candidates.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_add)
            .setItems(labels) { _, which ->
                showAliasDialog(profile, candidates[which])
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /** Optional alias entry for a model being added (or already configured). */
    private fun showAliasDialog(profile: ProviderProfile, modelId: String) {
        val aliasInput = EditText(this)
        aliasInput.hint = getString(R.string.ws_model_alias_hint)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            addView(
                aliasInput,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        AlertDialog.Builder(this)
            .setTitle(modelId)
            .setView(container)
            .setPositiveButton(R.string.ws_model_add) { _, _ ->
                val alias = aliasInput.text.toString().trim().takeIf { it.isNotEmpty() }
                persistConfiguredModel(profile, modelId, alias)
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /**
     * Persist a configured model into [profile] (dedup by modelId), preserving
     * endpoint / apiKey / request timeout / selected model. No-op (with a toast)
     * when the modelId is already configured.
     */
    private fun persistConfiguredModel(profile: ProviderProfile, modelId: String, alias: String?) {
        if (profile.configuredModels.any { it.modelId == modelId }) {
            Toast.makeText(this, R.string.ws_model_already_configured, Toast.LENGTH_SHORT).show()
            return
        }
        val updated = profile.copy(
            configuredModels = profile.configuredModels + ConfiguredModel(modelId, alias),
        )
        saveProfileKeepingCredential(updated)
        Toast.makeText(this, R.string.ws_model_added, Toast.LENGTH_SHORT).show()
        refreshModelCatalog()
    }

    /**
     * AG.PROVIDER-CONFIGURED-MODELS-0: confirm + remove a configured model from the
     * active profile's switcher. The currently SELECTED model cannot be removed
     * (a profile must keep at least its active model). Removal is non-destructive:
     * credential / endpoint / timeout / the other configured models are preserved.
     */
    private fun confirmRemoveModel(opt: ModelOption) {
        val profile = activeProfile() ?: return
        if (opt.modelId == activeModel) {
            Toast.makeText(this, R.string.ws_model_cannot_remove_selected, Toast.LENGTH_SHORT).show()
            return
        }
        if (profile.configuredModels.none { it.modelId == opt.modelId }) {
            Toast.makeText(this, R.string.ws_model_already_configured, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_remove)
            .setMessage("${opt.modelId}\n\n${getString(R.string.ws_model_remove_confirm)}")
            .setPositiveButton(R.string.ws_model_remove) { _, _ ->
                val updated = profile.copy(
                    configuredModels = profile.configuredModels.filterNot { it.modelId == opt.modelId },
                )
                saveProfileKeepingCredential(updated)
                Toast.makeText(this, R.string.ws_model_removed, Toast.LENGTH_SHORT).show()
                refreshModelCatalog()
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /**
     * Persist a profile, reusing the canonical save path for built-in profiles and
     * ProviderProfileStore for custom ones. The credential (endpoint + apiKey) and
     * request timeout come from [profile] and are never altered here.
     */
    private fun saveProfileKeepingCredential(profile: ProviderProfile) {
        if (profile.isBuiltIn) {
            val node = ProviderProfileStore.toProviderNode(profile.profileId)
            if (node != null) {
                DeviceLocalProviderNodeLoader.saveConfigured(
                    applicationContext, node, profile.config, profile.configuredModels,
                )
            }
        } else {
            ProviderProfileStore.saveCustom(applicationContext, profile)
        }
    }

    // ------------------------------------------------------------- Provider Settings

    /**
     * Opens the Provider Settings: list of saved profiles + Add API.
     * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE Track B.
     */
    private fun showProviderSettings() {
        val profiles = ProviderProfileStore.loadAll(applicationContext)
        val labels = profiles.map { "${it.displayName} (${if (it.isBuiltIn) "built-in" else "custom"})" } + "+ Add API"

        AlertDialog.Builder(this)
            .setTitle(R.string.ws_section_models_provider)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < profiles.size) {
                    editProfile(profiles[which])
                } else {
                    addNewProfile()
                }
            }
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    private fun editProfile(profile: ProviderProfile) {
        val view = layoutInflater.inflate(R.layout.dialog_provider_settings, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val providerSelector = view.findViewById<Spinner>(R.id.provider_selector)
        val endpointInput = view.findViewById<EditText>(R.id.provider_endpoint_input)
        val apiKeyInput = view.findViewById<EditText>(R.id.provider_api_key_input)
        val keyToggle = view.findViewById<ImageButton>(R.id.provider_key_toggle)
        val modelInput = view.findViewById<EditText>(R.id.provider_model_input)
        val cancelBtn = view.findViewById<Button>(R.id.provider_cancel)
        val saveBtn = view.findViewById<Button>(R.id.provider_save)

        // Pre-fill from profile config (NEVER pre-fill the actual API key)
        endpointInput.setText(profile.config.baseUrl)
        modelInput.setText(profile.config.model)
        if (profile.config.apiKey.isNotBlank()) {
            apiKeyInput.hint = getString(R.string.ws_saved)
        }

        // Provider selector: for built-in, show name; for custom, just the name
        val allProfiles = ProviderProfileStore.loadAll(applicationContext)
        providerSelector.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            allProfiles.map { it.displayName }
        )
        providerSelector.setSelection(allProfiles.indexOfFirst { it.profileId == profile.profileId }.coerceAtLeast(0))
        providerSelector.isEnabled = false // Editing an existing profile: name is fixed

        var keyVisible = false
        keyToggle.setOnClickListener {
            keyVisible = !keyVisible
            apiKeyInput.inputType = if (keyVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            val endpoint = endpointInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()
            val model = modelInput.text.toString().trim()

            if (endpoint.isBlank() || model.isBlank()) {
                Toast.makeText(this, "Endpoint and Model are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Secret semantics: blank key = keep existing
            val finalKey = if (apiKey.isBlank()) profile.config.apiKey else apiKey
            val newConfig = OpenAiCompatibleConfig(
                baseUrl = endpoint, apiKey = finalKey, model = model,
            )

            // AG.PROVIDER-CONFIGURED-MODELS-0: the model field in the settings dialog
            // is the SELECTED model. Editing it may point at a model that was not
            // previously configured (e.g. a new endpoint with a new model id); in
            // that case it is added to the configured set. Existing configured
            // models and their aliases are preserved.
            val newConfigured = if (profile.configuredModels.any { it.modelId == model }) {
                profile.configuredModels
            } else {
                profile.configuredModels + ConfiguredModel(model)
            }
            val newProfile = profile.copy(config = newConfig, configuredModels = newConfigured)

            if (profile.isBuiltIn) {
                val node = ProviderProfileStore.toProviderNode(profile.profileId)
                    ?: return@setOnClickListener
                DeviceLocalProviderNodeLoader.saveConfigured(
                    applicationContext, node, newConfig, newConfigured,
                )
                lifecycleScope.launch {
                    val result = HostActions.switchProvider(node)
                    when (result) {
                        is ProviderSwitchResult.Applied -> onProfileApplied(
                            ProfileSwitchResult.Applied(profile.profileId, profile.displayName, model, result.endpoint),
                            profile.displayName
                        )
                        is ProviderSwitchResult.Rejected -> onProfileApplied(
                            ProfileSwitchResult.Rejected(profile.profileId, result.reason),
                            profile.displayName
                        )
                    }
                }
            } else {
                ProviderProfileStore.saveCustom(applicationContext, newProfile)
                lifecycleScope.launch {
                    val result = HostActions.switchProfile(profile.profileId, model)
                    onProfileApplied(result, profile.displayName)
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun addNewProfile() {
        val view = layoutInflater.inflate(R.layout.dialog_provider_settings, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val providerSelector = view.findViewById<Spinner>(R.id.provider_selector)
        val endpointInput = view.findViewById<EditText>(R.id.provider_endpoint_input)
        val apiKeyInput = view.findViewById<EditText>(R.id.provider_api_key_input)
        val keyToggle = view.findViewById<ImageButton>(R.id.provider_key_toggle)
        val modelInput = view.findViewById<EditText>(R.id.provider_model_input)
        val cancelBtn = view.findViewById<Button>(R.id.provider_cancel)
        val saveBtn = view.findViewById<Button>(R.id.provider_save)

        // For "Add API": provider selector is disabled (it's a new custom profile)
        providerSelector.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Custom (OpenAI-compatible)")
        )
        providerSelector.isEnabled = false

        var keyVisible = false
        keyToggle.setOnClickListener {
            keyVisible = !keyVisible
            apiKeyInput.inputType = if (keyVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            val name = endpointInput.text.toString().trim()
                .let { if (it.contains("://")) "My API" else if (it.isNotBlank()) it else "My API" }
            val endpoint = endpointInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()
            val model = modelInput.text.toString().trim()

            if (endpoint.isBlank() || !endpoint.contains("://")) {
                Toast.makeText(this, "Enter a valid Base URL (https://...)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (model.isBlank()) {
                Toast.makeText(this, "Model is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = OpenAiCompatibleConfig(
                baseUrl = endpoint, apiKey = apiKey, model = model,
            )
            // AG.PROVIDER-CONFIGURED-MODELS-0: a fresh custom profile has exactly
            // the one model the user typed as its configured set.
            val profile = ProviderProfileStore.createCustom(
                applicationContext, "My API", config,
                configuredModels = listOf(ConfiguredModel(model)),
            )
            if (profile == null) {
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = HostActions.switchProfile(profile.profileId, model)
                onProfileApplied(result, profile.displayName)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private suspend fun onProfileApplied(result: ProfileSwitchResult, name: String) {
        when (result) {
            is ProfileSwitchResult.Applied -> {
                activeModel = SessionHolder.activeModel
                updateModelLabel()
                updateProviderStatus()
                refreshModelCatalog()
                renderStatus()
            }
            is ProfileSwitchResult.Rejected -> {
                updateRunStatus(result.reason)
                renderStatus()
            }
        }
    }

    // ------------------------------------------------------------- Add Context (+)

    /**
     * The + button opens a menu with "Open File" / "Open Folder".
     * Uses Android SAF (ACTION_OPEN_DOCUMENT / ACTION_OPEN_DOCUMENT_TREE).
     * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE Track A.
     */
    private fun onAddContext() {
        val items = arrayOf("Open File", "Open Folder")
        AlertDialog.Builder(this)
            .setTitle(R.string.ws_add_context)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openFilePicker()
                    1 -> openFolderPicker()
                }
            }
            .show()
    }

    private fun openFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            filePicker.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No document picker available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFolderPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            folderPicker.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No document picker available", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult // Cancelled
        try {
            // Take persistable URI permission
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // Get display name
            val name = queryDisplayName(uri) ?: "file"
            val resource = SafResource(uri = uri, displayName = name, isFolder = false)
            val resources = SafResourceStore.add(this, resource)
            // Show chip (lightweight UI indicator)
            Toast.makeText(this, "Opened: $name", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show()
        }
    }

    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult // Cancelled
        try {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val name = queryTreeDisplayName(uri) ?: "folder"
            val resource = SafResource(uri = uri, displayName = name, isFolder = true)
            SafResourceStore.add(this, resource)
            Toast.makeText(this, "Opened folder: $name", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun queryTreeDisplayName(uri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            contentResolver.query(docUri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    // ------------------------------------------------------------- Provider node (internal)

    private fun switchNode(node: ProviderNode) {
        lifecycleScope.launch {
            val result = HostActions.switchProvider(node)
            Log.i("AG.UI", "switchNode($node) -> ${result::class.java.simpleName}")
            when (result) {
                is ProviderSwitchResult.Applied -> {
                    activeNode = result.node
                    root = SessionHolder.root
                    runtime = SessionHolder.runtime
                    activeModel = SessionHolder.activeModel
                    updateModelLabel()
                    updateProviderStatus()
                    ensureDebugBridge()
                    updateAdbIndicator()
                    renderStatus()
                }
                is ProviderSwitchResult.Rejected -> {
                    updateRunStatus(result.reason)
                    renderStatus()
                }
            }
        }
    }

    private fun tryDeviceLocalConfig() {
        SessionHolder.ensureAppContext(applicationContext)
        val loadResults = ProviderNode.entries.associateWith {
            when (DeviceLocalProviderNodeLoader.load(applicationContext, it)) {
                is DeviceLocalProviderConfig.Loaded -> "Loaded"
                is DeviceLocalProviderConfig.Missing -> "Missing"
                is DeviceLocalProviderConfig.Invalid -> "Invalid"
            }
        }
        Log.i("AG.UI", "tryDeviceLocalConfig loadResults=$loadResults")
        val defaultNode = ProviderNode.entries.firstOrNull {
            DeviceLocalProviderNodeLoader.load(applicationContext, it) is DeviceLocalProviderConfig.Loaded
        } ?: ProviderNode.OPENROUTER
        activeNode = defaultNode
        switchNode(defaultNode)
    }

    // ------------------------------------------------------------- Stream observer

    private val sessionStreamObserver = object : ModelStreamObserver {
        override fun onReasoningStarted() {
            bridgeStream(DebugProtocol.STREAM_REASONING_STARTED, null)
            SessionHolder.onStreamEvent("reasoning_started", null)
        }
        override fun onReasoningDelta(text: String) {
            bridgeStream(DebugProtocol.STREAM_REASONING_DELTA, text)
            SessionHolder.onStreamEvent("reasoning_delta", text)
        }
        override fun onReasoningFinished() {
            bridgeStream(DebugProtocol.STREAM_REASONING_FINISHED, null)
            SessionHolder.onStreamEvent("reasoning_finished", null)
        }
        override fun onContentStarted() {
            bridgeStream(DebugProtocol.STREAM_CONTENT_STARTED, null)
            SessionHolder.onStreamEvent("content_started", null)
        }
        override fun onContentDelta(text: String) {
            bridgeStream(DebugProtocol.STREAM_CONTENT_DELTA, text)
            SessionHolder.onStreamEvent("content_delta", text)
        }
        override fun onContentFinished() {
            bridgeStream(DebugProtocol.STREAM_CONTENT_FINISHED, null)
            SessionHolder.onStreamEvent("content_finished", null)
        }
    }

    private fun ensureDebugBridge() {
        if (!BuildConfig.DEBUG) return
        val existing = sharedDebugBridge
        if (existing != null && existing.isRunning) return
        val bridge = DebugBridge(
            runtimeProvider = { sharedRuntime },
            toolRegistryProvider = { SessionHolder.root?.toolRegistryView },
            userInputSink = { text ->
                runOnUiThread {
                    SessionHolder.transcriptModel.onUser(text)
                    SessionHolder.notifyUiUpdate()
                }
            },
            onRunStateChanged = { busy ->
                runOnUiThread { setBusy(busy) }
            },
            adapterProvider = { SessionHolder.root?.shell?.adapter },
        )
        if (bridge.start()) {
            sharedDebugBridge = bridge
            DebugBridgeHolder.set(bridge)
        }
    }

    // ------------------------------------------------------------- AT Engine

    private fun connectShell() {
        updateAtStatus("Connecting...")
        updateAdbIndicator()
        renderStatus()
        lifecycleScope.launch {
            HostActions.adbReconnect()
            updateAdbIndicator()
            renderStatus()
        }
    }

    // ADB pairing / reconnect is now handled in AdbConnectionActivity (management surface).

    // ------------------------------------------------------------- ADB indicator

    private fun updateAdbIndicator() {
        val shell = root?.shell
        val ind = adbIndicator(atStatusText, shell?.readiness()?.code)
        adbDot.background.setTint(ContextCompat.getColor(this, ind.colorRes))
        adbDot.contentDescription = getString(ind.textRes)
        sidebarAdbDot.background.setTint(ContextCompat.getColor(this, ind.colorRes))
    }

    // ------------------------------------------------------------- Status / rendering

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

    private fun renderStatus() {
        status.text = runStatusText ?: ""
    }

    private fun updateAtStatus(text: String?) {
        atStatusText = text
        SessionHolder.setAtStatus(text)
        updateAdbIndicator()
    }

    private fun updateRunStatus(text: String?) {
        runStatusText = text
        SessionHolder.setRunStatus(text)
    }

    private fun renderTranscript() {
        cardAdapter.updateBlocks(transcriptModel.snapshot())
        if (autoFollow) scrollToBottom()
    }

    private fun scrollToBottom() {
        if (cardAdapter.itemCount > 0) {
            transcriptRecycler.post {
                transcriptRecycler.scrollToPosition(cardAdapter.itemCount - 1)
            }
        }
    }

    private fun updateModelLabel() {
        // AG.PROVIDER-CONFIGURED-MODELS-0: home shows the SHORT display name
        // (alias, else provider prefix). Never the full long modelId.
        val cm = activeModel?.let { modelDisplay[it] }
        modelLabel.text = if (cm != null) {
            cm.shortDisplay
        } else {
            modelShortDisplay(activeModel)
        }
    }

    /** Updates the sidebar provider status line (e.g. "EMG · Configured"). */
    private fun updateProviderStatus() {
        val profiles = ProviderProfileStore.loadAll(applicationContext)
        val configured = if (profiles.isNotEmpty()) getString(R.string.ws_provider_configured)
            else getString(R.string.ws_provider_not_configured)
        val activeName = SessionHolder.providerNode.displayName
        providerStatusLabel.text = "$activeName · $configured"
    }

    // ------------------------------------------------------------- Session

    /**
     * AG.SESSION-MANAGEMENT-UX-0: session switching/management now lives in the
     * central [SessionManagementActivity] (SESSION_MANAGEMENT_SURFACE_COUNT = 1).
     * The previous in-place switcher dialog was removed so the sidebar is a
     * concise navigation entry only.
     */

    private fun formatSessionTime(millis: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(millis))
        } catch (e: Exception) {
            ""
        }
    }

    // ------------------------------------------------------------- Session

    private fun onNewSession() {
        // AG.SESSION-MANAGEMENT-UX-0: New Session enters a transient blank draft
        // (NO durable row). The UI update flows through the onUiUpdate callback
        // (renderTranscript/renderStatus), so this is a direct call.
        val switched = HostActions.newSession()
        if (switched) {
            autoFollow = true
            refreshSessionList()
        }
    }

    /**
     * Opens the central Session Management surface (AG.SESSION-MANAGEMENT-UX-0).
     * This is the SINGLE management surface (SESSION_MANAGEMENT_SURFACE_COUNT =
     * 1). The old in-place switcher dialog is removed in favour of this Activity.
     */
    private fun openSessionManagement() {
        startActivity(Intent(this, SessionManagementActivity::class.java))
    }

    /** Refreshes the session list in the sidebar (AG.UI-TRANSCRIPT-CARDS-1). */
    private fun refreshSessionList() {
        lifecycleScope.launch {
            val sessions = HostActions.listSessions()
            currentSessionLabel.text = "Current Session"
            // For v1: just update the label. Full list rendering in sidebar
            // would require a dynamic view group; the session switch is via
            // a simple dialog for now.
            runOnUiThread {
                // Store sessions for the switch dialog
                availableSessions = sessions
            }
        }
    }

    // ------------------------------------------------------------- Retry / Edit & Resend

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: Retry = re-send the originating User text as a
     * NEW User Turn. Does NOT modify/delete old history.
     * Reuses HostActions.send (same as a normal send).
     */
    private fun retrySend(text: String) {
        lifecycleScope.launch {
            val result = HostActions.send(text)
            when (result) {
                is SendResult.Rejected -> {
                    updateRunStatus(result.reason)
                    renderStatus()
                }
                is SendResult.Sent -> {
                    if (result.outcome is com.androidagent.core.RunOutcome.Interrupted) {
                        updateRunStatus("Stopped.")
                    }
                }
            }
            renderTranscript()
        }
    }

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: Edit & Resend = restore the old User text to
     * the Composer for editing. The user then presses Send to create a new turn.
     * Does NOT modify the old durable message.
     */
    private fun editAndResend(text: String) {
        input.setText(text)
        input.setSelection(text.length)
        input.requestFocus()
    }

    // ------------------------------------------------------------- Send / Stop

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        autoFollow = true
        lifecycleScope.launch {
            val result = HostActions.send(text)
            when (result) {
                is SendResult.Rejected -> {
                    val msg = when (result.reason) {
                        "runtime not ready" -> "No provider configured. Select a model first."
                        "busy" -> "A run is already in progress for this session."
                        else -> result.reason
                    }
                    updateRunStatus(msg)
                    renderStatus()
                }
                is SendResult.Sent -> {
                    logTrace()
                    if (result.outcome is com.androidagent.core.RunOutcome.Interrupted) {
                        updateRunStatus("Stopped.")
                        renderStatus()
                    }
                }
            }
            renderTranscript()
        }
    }

    private fun onStopRun() {
        when (HostActions.stop()) {
            StopResult.Requested -> {
                updateRunStatus("Stopping...")
                stop.isEnabled = false
                renderStatus()
            }
            StopResult.AlreadyInterrupting -> renderStatus()
            StopResult.NoActiveRun -> setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        SessionHolder.setBusy(busy)
        val slot = workspaceActionSlot(busy)
        send.visibility = if (slot.sendVisible) View.VISIBLE else View.GONE
        stop.visibility = if (slot.stopVisible) View.VISIBLE else View.GONE
        stop.isEnabled = slot.stopVisible
        newSession.isEnabled = !busy
    }

    private fun logTrace() {
        val collector = root?.traceCollector ?: return
        val trace: AgentTrace = collector.trace ?: return
        val rendered = TraceFormatter.format(trace)
        for (line in rendered.lineSequence()) {
            Log.i("AgentTrace", line)
        }
    }

    private companion object {
        @Volatile
        var sharedDebugBridge: DebugBridge? = null
            private set

        val sharedRuntime: AgentRuntime?
            get() = SessionHolder.runtime
    }
}
