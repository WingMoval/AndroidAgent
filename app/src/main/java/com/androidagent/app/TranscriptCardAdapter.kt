package com.androidagent.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * AG.UI-TRANSCRIPT-CARDS-1 — Semantic transcript card adapter.
 *
 * Replaces the single-TextView renderer with per-block cards that support:
 *  - Per-block Copy (ClipboardManager)
 *  - Retry (re-send originating User text as a new turn)
 *  - Edit & Resend (restore User text to Composer)
 *  - Thought collapse/expand
 *  - Tool Result collapse/expand
 *  - Native text selection (textIsSelectable=true on card_body)
 *
 * PRESENTATION ONLY. SSOT is TranscriptModel.Block.
 */
class TranscriptCardAdapter(
    private val context: Context,
    private val onRetry: (text: String) -> Unit,
    private val onEditResend: (text: String) -> Unit,
    private val isBusy: () -> Boolean,
) : RecyclerView.Adapter<TranscriptCardAdapter.CardViewHolder>() {

    private var blocks: List<TranscriptModel.Block> = emptyList()

    fun updateBlocks(newBlocks: List<TranscriptModel.Block>) {
        val oldSize = blocks.size
        blocks = newBlocks
        if (newBlocks.size != oldSize) {
            notifyItemRangeChanged(0, newBlocks.size)
        } else if (newBlocks.isNotEmpty()) {
            notifyItemChanged(blocks.size - 1)
        }
    }

    override fun getItemCount(): Int = blocks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_transcript_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val block = blocks[position]
        holder.bind(block, position)
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.card_label)
        private val body: TextView = itemView.findViewById(R.id.card_body)
        private val copyBtn: ImageButton = itemView.findViewById(R.id.card_copy)
        private val retryBtn: ImageButton = itemView.findViewById(R.id.card_retry)
        private val moreBtn: ImageButton = itemView.findViewById(R.id.card_more)
        private val collapseBtn: TextView = itemView.findViewById(R.id.card_collapse)

        fun bind(block: TranscriptModel.Block, position: Int) {
            when (block) {
                is TranscriptModel.Block.User -> {
                    label.text = "You"
                    body.text = block.text
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.text) }
                    retryBtn.visibility = if (isBusy()) View.GONE else View.VISIBLE
                    retryBtn.setOnClickListener { onRetry(block.text) }
                    moreBtn.visibility = View.VISIBLE
                    moreBtn.setOnClickListener { showMoreMenu(block.text) }
                    collapseBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Thinking -> {
                    if (block.phase == TranscriptModel.Block.Thinking.Phase.THINKING) {
                        label.text = "Thinking..."
                        body.text = block.text
                        collapseBtn.visibility = View.GONE
                    } else {
                        label.text = "Thought"
                        if (block.text.length > 200) {
                            body.text = block.text.take(200) + "..."
                            collapseBtn.visibility = View.VISIBLE
                            collapseBtn.text = "Show more"
                            collapseBtn.setOnClickListener {
                                body.text = block.text
                                collapseBtn.text = "Show less"
                            }
                        } else {
                            body.text = block.text
                            collapseBtn.visibility = View.GONE
                        }
                    }
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.text) }
                    retryBtn.visibility = View.GONE
                    moreBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Assistant -> {
                    label.text = if (block.final) "Assistant" else "Assistant..."
                    body.text = block.text
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.text) }
                    retryBtn.visibility = if (isBusy()) View.GONE else View.VISIBLE
                    retryBtn.setOnClickListener {
                        val origin = findOriginUserText(position)
                        if (origin != null) onRetry(origin)
                    }
                    moreBtn.visibility = View.GONE
                    collapseBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Shell -> {
                    label.text = "Shell"
                    body.text = "\$ " + block.command
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.command) }
                    retryBtn.visibility = View.GONE
                    moreBtn.visibility = View.GONE
                    collapseBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Result -> {
                    label.text = "Result"
                    val text = block.text
                    if (text.length > 300) {
                        body.text = text.take(300) + "..."
                        collapseBtn.visibility = View.VISIBLE
                        collapseBtn.text = "Show more"
                        collapseBtn.setOnClickListener {
                            body.text = text
                            collapseBtn.text = "Show less"
                        }
                    } else {
                        body.text = text
                        collapseBtn.visibility = View.GONE
                    }
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(text) }
                    retryBtn.visibility = View.GONE
                    moreBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Failure -> {
                    label.text = "Failed"
                    body.text = block.text
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.text) }
                    retryBtn.visibility = if (isBusy()) View.GONE else View.VISIBLE
                    retryBtn.setOnClickListener {
                        val origin = findOriginUserText(position)
                        if (origin != null) onRetry(origin)
                    }
                    moreBtn.visibility = View.GONE
                    collapseBtn.visibility = View.GONE
                }

                is TranscriptModel.Block.Interrupted -> {
                    label.text = "Stopped"
                    body.text = block.text
                    body.setTextIsSelectable(true)
                    copyBtn.visibility = View.VISIBLE
                    copyBtn.setOnClickListener { copyToClipboard(block.text) }
                    retryBtn.visibility = if (isBusy()) View.GONE else View.VISIBLE
                    retryBtn.setOnClickListener {
                        val origin = findOriginUserText(position)
                        if (origin != null) onRetry(origin)
                    }
                    moreBtn.visibility = View.GONE
                    collapseBtn.visibility = View.GONE
                }
            }
        }

        private fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", text))
        }

        private fun showMoreMenu(userText: String) {
            android.widget.PopupMenu(context, moreBtn).apply {
                menu.add(0, 1, 0, "Copy")
                menu.add(0, 2, 1, "Edit & Resend")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> copyToClipboard(userText)
                        2 -> onEditResend(userText)
                    }
                    true
                }
                show()
            }
        }

        private fun findOriginUserText(position: Int): String? {
            for (i in position downTo 0) {
                val b = blocks[i]
                if (b is TranscriptModel.Block.User) return b.text
            }
            return null
        }
    }
}
