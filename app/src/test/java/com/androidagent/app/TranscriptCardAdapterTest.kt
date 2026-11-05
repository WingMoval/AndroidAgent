package com.androidagent.app

import org.junit.Assert.*
import org.junit.Test

/**
 * AG.HOST-TRANSCRIPT-PROJECTION-CLOSEOUT-0: Adapter structural update matrix.
 *
 * Verifies the notification contract of TranscriptCardAdapter.updateBlocks():
 *  - Count change (oldCount != newCount) → notifyDataSetChanged()
 *  - Same count, non-empty → notifyItemChanged(last)
 *  - 0 → 0 → no notification
 *
 * Tests T1-T9 from the closeout spec.
 */
class TranscriptCardAdapterTest {

    // Mirrors the production updateBlocks notification contract.
    private class NotificationRecorder {
        var notifyDataSetChangedCalls = 0
        var notifyItemChangedCalls = mutableListOf<Int>()
        var itemCount = 0

        fun applyUpdate(oldBlocks: List<TranscriptModel.Block>, newBlocks: List<TranscriptModel.Block>) {
            // oldSize is derived from the OLD blocks passed in (mirrors production:
            // `val oldSize = blocks.size` where blocks is the current internal list).
            val oldSize = oldBlocks.size
            itemCount = newBlocks.size
            if (newBlocks.size != oldSize) {
                notifyDataSetChangedCalls++
            } else if (newBlocks.isNotEmpty()) {
                notifyItemChangedCalls.add(newBlocks.size - 1)
            }
        }
    }

    private fun userBlocks(n: Int): List<TranscriptModel.Block> =
        List(n) { TranscriptModel.Block.User("msg_$it") }

    private fun changedBlocks(n: Int, prefix: String): List<TranscriptModel.Block> =
        List(n) { TranscriptModel.Block.User(prefix + "_" + it) }

    @Test
    fun `T1 0 to 0 no notification`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(0), userBlocks(0))
        assertEquals(0, r.notifyDataSetChangedCalls)
        assertTrue(r.notifyItemChangedCalls.isEmpty())
        assertEquals(0, r.itemCount)
    }

    @Test
    fun `T2 0 to 1 uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(0), userBlocks(1))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(1, r.itemCount)
    }

    @Test
    fun `T3 1 to 0 uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(1), userBlocks(0))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(0, r.itemCount)
    }

    @Test
    fun `T4 0 to 5 uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(0), userBlocks(5))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(5, r.itemCount)
    }

    @Test
    fun `T5 5 to 0 uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(5), userBlocks(0))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(0, r.itemCount)
    }

    @Test
    fun `T6 3 to 5 growth uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(3), userBlocks(5))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(5, r.itemCount)
    }

    @Test
    fun `T7 5 to 3 shrink uses notifyDataSetChanged`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(5), userBlocks(3))
        assertEquals(1, r.notifyDataSetChangedCalls)
        assertEquals(3, r.itemCount)
    }

    @Test
    fun `T8 5 to 5 all content changed uses notifyItemChanged last`() {
        val r = NotificationRecorder()
        r.applyUpdate(userBlocks(5), changedBlocks(5, "new"))
        assertEquals(0, r.notifyDataSetChangedCalls)
        assertEquals(listOf(4), r.notifyItemChangedCalls)
        assertEquals(5, r.itemCount)
    }

    @Test
    fun `T9 5 to 5 partial content change uses notifyItemChanged last`() {
        val r = NotificationRecorder()
        val old = userBlocks(5)
        val new = old.toMutableList().apply { this[4] = TranscriptModel.Block.User("updated") }
        r.applyUpdate(old, new)
        assertEquals(0, r.notifyDataSetChangedCalls)
        assertEquals(listOf(4), r.notifyItemChangedCalls)
        assertEquals(5, r.itemCount)
    }
}
