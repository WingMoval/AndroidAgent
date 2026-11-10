package com.androidagent.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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

/** Composer model label: the REAL active model id only (no fake effort). */
fun workspaceModelLabel(activeModel: String?): String =
    activeModel?.takeIf { it.isNotBlank() } ?: "Model"


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
    private lateinit var reconnect: Button
    private lateinit var adbStatus: TextView
    private lateinit var pairRow: View
    private lateinit var pairingCode: EditText
    private lateinit var pairButton: Button
    private lateinit var providerSettingsEntry: View
    private lateinit var providerStatusLabel: TextView
    private lateinit var currentSessionLabel: TextView
    private lateinit var adbPageEntry: View

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

    /** Available models for the LLM selector. */
    private var modelCatalog: List<ModelOption> = emptyList()

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
        reconnect = findViewById(R.id.reconnect)
        adbStatus = findViewById(R.id.adb_status)
        pairRow = findViewById(R.id.pair_row)
        pairingCode = findViewById(R.id.pairing_code)
        pairButton = findViewById(R.id.pair)
        providerSettingsEntry = findViewById(R.id.sidebar_provider_settings)
        providerStatusLabel = findViewById(R.id.sidebar_provider_status)
        currentSessionLabel = findViewById(R.id.sidebar_current_session)
        adbPageEntry = findViewById(R.id.sidebar_adb_page_entry)

        // ---- Composer actions
        send.setOnClickListener { onSend() }
        stop.setOnClickListener { onStopRun() }
        composerAdd.setOnClickListener { onAddContext() }
        modelSelector.setOnClickListener { showModelSelector() }
        newSession.setOnClickListener { onNewSession() }
        reconnect.setOnClickListener { onReconnect() }
        pairButton.setOnClickListener { onPair() }

        // ---- Sidebar
        btnOpenSidebar.setOnClickListener { openSidebar() }
        btnCloseSidebar.setOnClickListener { closeSidebar() }
        sidebarScrim.setOnClickListener { closeSidebar() }
        providerSettingsEntry.setOnClickListener { showProviderSettings() }
        currentSessionLabel.setOnClickListener { showSessionSwitcher() }
        adbPageEntry.setOnClickListener {
            startActivity(Intent(this, AdbConnectionActivity::class.java))
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
        if (modelCatalog.isEmpty()) {
            refreshModelCatalog()
            if (modelCatalog.isEmpty()) {
                Toast.makeText(this, "No models configured", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val labels = modelCatalog.map { it.displayLabel }
        val currentIdx = modelCatalog.indexOfFirst { it.modelId == activeModel }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.ws_model_label_settings)
            .setSingleChoiceItems(labels.toTypedArray(), currentIdx) { dialog, which ->
                val selected = modelCatalog[which]
                dialog.dismiss()
                lifecycleScope.launch {
                    val result = HostActions.switchProfile(selected.profileId, selected.modelId)
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
            .setNegativeButton(R.string.ws_cancel, null)
            .show()
    }

    /**
     * Rebuilds the model catalog from all saved provider profiles + discovery.
     */
    private fun refreshModelCatalog() {
        modelCatalog = buildModelCatalog(applicationContext)
        // Attempt model discovery for the active profile (best-effort, async)
        lifecycleScope.launch {
            val profiles = ProviderProfileStore.loadAll(applicationContext)
            val activeProfile = profiles.firstOrNull {
                it.isBuiltIn && SessionHolder.providerNode.name.lowercase() == it.profileId
            } ?: profiles.firstOrNull()
            if (activeProfile != null) {
                val result = ModelDiscovery.discover(activeProfile.config)
                if (result is ModelDiscoveryResult.Success) {
                    val discovered = mapOf(activeProfile.profileId to result.models)
                    modelCatalog = buildModelCatalog(applicationContext, discovered)
                }
            }
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

            if (profile.isBuiltIn) {
                val node = ProviderProfileStore.toProviderNode(profile.profileId)
                    ?: return@setOnClickListener
                DeviceLocalProviderNodeLoader.save(applicationContext, node, newConfig)
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
                val newProfile = profile.copy(config = newConfig)
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
            val profile = ProviderProfileStore.createCustom(applicationContext, "My API", config)
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
            val result = SessionHolder.root?.shell?.readiness()
            pairRow.visibility =
                if (result != null && result.pairingOffered) View.VISIBLE else View.GONE
            updateAdbIndicator()
            renderStatus()
        }
    }

    private fun onReconnect() {
        if (reconnecting) return
        reconnecting = true
        reconnect.isEnabled = false
        connectShell()
        lifecycleScope.launch {
            reconnecting = false
            reconnect.isEnabled = true
        }
    }

    // ------------------------------------------------------------- ADB pairing

    private fun onPair() {
        val code = pairingCode.text.toString().trim()
        if (code.length != 6) {
            updateAtStatus("Enter the 6-digit pairing code")
            renderStatus()
            return
        }
        pairButton.isEnabled = false
        updateAtStatus("Pairing...")
        renderStatus()
        lifecycleScope.launch {
            val adapter = root?.shell?.adapter
            if (adapter == null) {
                updateAtStatus("Adapter not available")
                renderStatus()
                pairButton.isEnabled = true
                return@launch
            }
            val result = adapter.pairDiscovered(code)
            pairingCode.text.clear()
            if (result.success) {
                updateAtStatus("Paired. Reconnecting...")
                connectShell()
            } else {
                updateAtStatus("Pairing failed: ${result.outcome.name}")
            }
            renderStatus()
            updateAdbIndicator()
            pairButton.isEnabled = true
        }
    }

    // ------------------------------------------------------------- ADB indicator

    private fun updateAdbIndicator() {
        val shell = root?.shell
        val ind = workspaceAdbIndicator(atStatusText, shell?.readiness()?.code)
        adbDot.background.setTint(ContextCompat.getColor(this, ind.colorRes))
        adbDot.contentDescription = getString(ind.textRes)
        adbStatus.text = getString(ind.textRes)
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
        modelLabel.text = workspaceModelLabel(activeModel)
    }

    /** Updates the sidebar provider status line (e.g. "EMG · Configured"). */
    private fun updateProviderStatus() {
        val profiles = ProviderProfileStore.loadAll(applicationContext)
        val configured = if (profiles.isNotEmpty()) getString(R.string.ws_provider_configured)
            else getString(R.string.ws_provider_not_configured)
        val activeName = SessionHolder.providerNode.displayName
        providerStatusLabel.text = "$activeName · $configured"
    }

    // ------------------------------------------------------------- Session Browser

    /**
     * AG.UI-TRANSCRIPT-CARDS-1: Shows a dialog to switch between historical sessions.
     * The current session is highlighted. Clicking a session calls
     * HostActions.switchSession (shared entry with Debug).
     */
    private fun showSessionSwitcher() {
        lifecycleScope.launch {
            val sessions = HostActions.listSessions()
            val currentId = SessionHolder.runtime?.currentSessionId
            val labels = sessions.map { s ->
                val title = s.title ?: "Session"
                val time = formatSessionTime(s.lastUsedAt)
                val marker = if (s.id == currentId) "● " else "  "
                "$marker$title · $time"
            }
            val currentIdx = sessions.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

            runOnUiThread {
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("Sessions")
                    .setSingleChoiceItems(labels.toTypedArray(), currentIdx) { dialog, which ->
                        val selected = sessions[which]
                        dialog.dismiss()
                        if (selected.id != currentId) {
                            lifecycleScope.launch {
                                val result = HostActions.switchSession(selected.id)
                                when (result) {
                                    is SessionSwitchResult.Ok -> {
                                        transcriptModel = SessionHolder.transcriptModel
                                        autoFollow = true
                                        renderTranscript()
                                        renderStatus()
                                    }
                                    is SessionSwitchResult.Rejected -> {
                                        updateRunStatus(result.reason)
                                        renderStatus()
                                    }
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.ws_cancel, null)
                    .show()
            }
        }
    }

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
        lifecycleScope.launch {
            val switched = HostActions.newSession()
            if (switched) {
                transcriptModel = SessionHolder.transcriptModel
                autoFollow = true
                runStatusText = SessionHolder.runStatusText
                renderTranscript()
                renderStatus()
                refreshSessionList()
            } else {
                runStatusText = SessionHolder.runStatusText
                renderStatus()
            }
        }
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
