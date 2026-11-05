package com.androidagent.app

import org.junit.Assert.*
import org.junit.Test

/**
 * AG.HOST-TRANSCRIPT-PROJECTION-CLOSEOUT-0: Session projection regression.
 *
 * Verifies that TranscriptModel.reproject() correctly replaces the block
 * set when switching between sessions of different sizes.
 *
 * Session A = 5 blocks, B = 0, C = 2, D = 8.
 * Full cycle: A→B→C→D→A, A→C.
 */
class TranscriptProjectionTest {

    private fun makeBlocks(n: Int, prefix: String): List<TranscriptModel.Block> =
        List(n) { TranscriptModel.Block.User(prefix + "_" + it) }

    @Test
    fun `A to B 5 to 0 transcript is empty`() {
        val model = TranscriptModel()
        model.reproject(coreMessages(5, "A"))
        assertEquals(5, model.snapshot().size)

        model.reproject(emptyList())
        assertEquals(0, model.snapshot().size)
    }

    @Test
    fun `B to C 0 to 2 transcript has 2 blocks`() {
        val model = TranscriptModel()
        model.reproject(emptyList())
        assertEquals(0, model.snapshot().size)

        model.reproject(coreMessages(2, "C"))
        assertEquals(2, model.snapshot().size)
        assertEquals("C_0", (model.snapshot()[0] as TranscriptModel.Block.User).text)
    }

    @Test
    fun `C to D 2 to 8 transcript has 8 blocks`() {
        val model = TranscriptModel()
        model.reproject(coreMessages(2, "C"))
        model.reproject(coreMessages(8, "D"))
        assertEquals(8, model.snapshot().size)
        assertEquals("D_0", (model.snapshot()[0] as TranscriptModel.Block.User).text)
    }

    @Test
    fun `D to A 8 to 5 transcript has 5 blocks no D leak`() {
        val model = TranscriptModel()
        model.reproject(coreMessages(8, "D"))
        model.reproject(coreMessages(5, "A"))
        assertEquals(5, model.snapshot().size)
        for (b in model.snapshot()) {
            val text = (b as TranscriptModel.Block.User).text
            assertTrue("D block leaked: $text", text.startsWith("A_"))
        }
    }

    @Test
    fun `A to C 5 to 2 transcript has 2 blocks no A leak`() {
        val model = TranscriptModel()
        model.reproject(coreMessages(5, "A"))
        model.reproject(coreMessages(2, "C"))
        assertEquals(2, model.snapshot().size)
        for (b in model.snapshot()) {
            val text = (b as TranscriptModel.Block.User).text
            assertTrue("A block leaked: $text", text.startsWith("C_"))
        }
    }

    @Test
    fun `full cycle A B C D A no leakage`() {
        val model = TranscriptModel()

        model.reproject(coreMessages(5, "A"))
        assertEquals(5, model.snapshot().size)

        model.reproject(emptyList())
        assertEquals(0, model.snapshot().size)

        model.reproject(coreMessages(2, "C"))
        assertEquals(2, model.snapshot().size)

        model.reproject(coreMessages(8, "D"))
        assertEquals(8, model.snapshot().size)

        model.reproject(coreMessages(5, "A"))
        assertEquals(5, model.snapshot().size)
        for (b in model.snapshot()) {
            val text = (b as TranscriptModel.Block.User).text
            assertTrue("Non-A block after full cycle: $text", text.startsWith("A_"))
        }
    }

    /** Converts N user text strings to Core Message objects for reproject(). */
    private fun coreMessages(n: Int, prefix: String): List<com.androidagent.core.Message> =
        List(n) { com.androidagent.core.UserMessage(prefix + "_" + it) }
}
