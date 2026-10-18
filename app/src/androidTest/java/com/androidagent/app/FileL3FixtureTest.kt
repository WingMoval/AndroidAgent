package com.androidagent.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * AG.FILE-0 L3 fixture: creates a single facts.txt in filesDir/agent/
 * with a unique marker (SEVENTEEN) that the model must read via the file tool.
 * The model cannot guess this value — it must come from the file content.
 */
@RunWith(AndroidJUnit4::class)
class FileL3FixtureTest {

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext get() = targetContext.applicationContext

    @Test
    fun create_facts() {
        val agentDir = File(appContext.filesDir, "agent")
        agentDir.mkdirs()
        val facts = File(agentDir, "facts.txt")
        facts.writeText("The answer to the L3 probe is: SEVENTEEN\n")
        Log.i(TAG, "L3_FACTS_CREATED: ${facts.readText()}")
    }

    @Test
    fun cleanup_facts() {
        val facts = File(appContext.filesDir, "agent/facts.txt")
        if (facts.exists()) {
            facts.delete()
        }
        Log.i(TAG, "L3_FACTS_CLEANED")
    }

    private companion object {
        const val TAG = "AG.FILE-0.L3"
    }
}
