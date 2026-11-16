package com.androidagent.app

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AG.SESSION-MANAGEMENT-UX-0: the SINGLE central Session Management surface.
 *
 * Mirrors the AdbConnectionActivity pattern (a dedicated management Activity
 * reached from the sidebar). This is the ONLY place for session management:
 *   - list durable sessions (last_used DESC),
 *   - New Session (the SAME canonical enterBlankDraft via HostActions),
 *   - Open (switch to a session via the SAME HostActions.switchSession),
 *   - Rename (durable metadata update; disabled for busy sessions),
 *   - Delete (permanent; disabled for busy sessions; current delete -> blank draft).
 *
 * The blank transient draft is NOT listed (NO USER MESSAGE = NO SESSION): the
 * list shows only durable TopicSession rows. The sidebar "Current Session" /
 * "Sessions" entries both open THIS surface (SESSION_MANAGEMENT_SURFACE_COUNT =
 * 1); the old in-place switcher dialog was removed.
 */
class SessionManagementActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageButton
    private lateinit var newSessionBtn: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_management)

        backBtn = findViewById(R.id.sessions_back_btn)
        newSessionBtn = findViewById(R.id.sessions_new_btn)
        listContainer = findViewById(R.id.sessions_list_container)
        emptyLabel = findViewById(R.id.sessions_empty)

        backBtn.setOnClickListener { finish() }
        newSessionBtn.setOnClickListener { onNewSession() }
    }

    override fun onResume() {
        super.onResume()
        renderSessionList()
    }

    /** Enters the canonical blank draft (NO durable row created). */
    private fun onNewSession() {
        val entered = HostActions.newSession()
        if (entered) {
            finish() // return to chat, which is now the blank draft
        }
    }

    /** Rebuilds the durable session list (last_used DESC; blank draft not listed). */
    private fun renderSessionList() {
        lifecycleScope.launch {
            val sessions = HostActions.listSessions()
            val currentId = SessionHolder.runtime?.currentSessionId
            val busy = SessionHolder.isBusy

            runOnUiThread {
                // Remove previously built rows (keep the empty label at index 0).
                while (listContainer.childCount > 1) {
                    listContainer.removeViewAt(listContainer.childCount - 1)
                }
                emptyLabel.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE

                for (s in sessions) {
                    listContainer.addView(buildRow(s, s.id == currentId, busy))
                }
            }
        }
    }

    /** Builds one session row: title (or untitled), time + count, and actions. */
    private fun buildRow(s: SessionSummary, isCurrent: Boolean, busy: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingStart, dp(12), paddingEnd, dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            background = resources.getDrawable(R.drawable.bg_status_card, theme)
        }

        val title = TextView(this).apply {
            text = s.title?.takeIf { it.isNotBlank() } ?: getString(R.string.sm_title_empty)
            textSize = 15f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.ws_text_primary))
        }
        row.addView(title)

        val meta = TextView(this).apply {
            val time = formatTime(s.lastUsedAt)
            val msgs = getString(R.string.sm_messages, s.messageCount)
            val marker = if (isCurrent) getString(R.string.sm_current) + " · " else ""
            text = marker + time + " · " + msgs
            textSize = 12f
            setTextColor(getColor(R.color.ws_text_secondary))
            setPadding(0, dp(2), 0, dp(8))
        }
        row.addView(meta)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        // Open (switch) — disabled when it is already current.
        val openBtn = Button(this).apply {
            setText(R.string.sm_open)
            isAllCaps = false
            textSize = 13f
            isEnabled = !isCurrent
            setOnClickListener { onOpen(s.id) }
        }
        actions.addView(openBtn)

        // Rename — disabled for busy sessions (rename a busy session = disabled).
        val renameBtn = Button(this).apply {
            setText(R.string.sm_rename)
            isAllCaps = false
            textSize = 13f
            isEnabled = !busy && s.id == currentIdOrNull()
            setOnClickListener { onRename(s) }
        }
        actions.addView(renameBtn)

        // Delete — disabled for busy sessions; allowed for any durable session.
        val deleteBtn = Button(this).apply {
            setText(R.string.sm_delete)
            isAllCaps = false
            textSize = 13f
            isEnabled = !busy
            setTextColor(getColor(R.color.ws_text_secondary))
            setOnClickListener { onDelete(s) }
        }
        actions.addView(deleteBtn)

        row.addView(actions)
        return row
    }

    /** The current durable session id (null when in a blank draft). */
    private fun currentIdOrNull(): String? = SessionHolder.runtime?.currentSessionId

    /** Switches to the session (SAME HostActions.switchSession entry). */
    private fun onOpen(sessionId: String) {
        lifecycleScope.launch {
            val result = HostActions.switchSession(sessionId)
            if (result is SessionSwitchResult.Ok) {
                finish() // back to chat, now on the selected session
            } else {
                // Stay; the chat status line will reflect the rejection.
                renderSessionList()
            }
        }
    }

    /** Rename dialog for the current session (durable metadata update). */
    private fun onRename(s: SessionSummary) {
        val input = EditText(this).apply {
            hint = getString(R.string.sm_title_hint)
            setText(s.title ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.sm_rename_title)
            .setView(input)
            .setNegativeButton(R.string.ws_cancel, null)
            .setPositiveButton(R.string.sm_save) { _, _ ->
                lifecycleScope.launch {
                    val res = HostActions.renameSession(input.text.toString())
                    if (res is SessionRenameResult.Ok) {
                        renderSessionList()
                    }
                }
            }
            .create()
        dialog.show()
        // Disable Save while the field is blank (empty string = reject).
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                save.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        save.isEnabled = !input.text.isNullOrBlank()
    }

    /** Delete confirmation (permanent). Current delete -> blank draft. */
    private fun onDelete(s: SessionSummary) {
        val isCurrent = s.id == currentIdOrNull()
        val label = s.title?.takeIf { it.isNotBlank() } ?: getString(R.string.sm_title_empty)
        AlertDialog.Builder(this)
            .setTitle(R.string.sm_delete_confirm_title)
            .setMessage(getString(R.string.sm_delete_confirm_msg) + "\n\n" + label)
            .setNegativeButton(R.string.ws_cancel, null)
            .setPositiveButton(R.string.sm_delete_confirm) { _, _ ->
                lifecycleScope.launch {
                    val res = HostActions.deleteSession(s.id)
                    if (res is SessionDeleteResult.Deleted) {
                        if (isCurrent) {
                            // Deleted the current session -> blank draft: go back
                            // to chat (now blank). No replacement durable row.
                            finish()
                        } else {
                            renderSessionList()
                        }
                    }
                }
            }
            .show()
    }

    private fun formatTime(millis: Long): String = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(millis))
    } catch (e: Exception) {
        ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
