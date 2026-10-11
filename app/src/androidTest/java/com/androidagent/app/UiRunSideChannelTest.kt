package com.androidagent.app

import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AG.DEBUG-0 T3 — UI-originated Run side-channel fixture.
 *
 * Drives the REAL ChatActivity production submit path (the existing input field
 * and the existing Send button) so a passive PC Debug Client can observe the
 * resulting Run in real time. It does NOT call AgentRuntime.send / AgentCore.run /
 * DebugBridge directly — that would not prove a UI-originated Run.
 *
 * The wait before Send gives the passive PC monitor time to attach to the debug
 * bridge. The test then waits for the Run to finish (Send re-enabled).
 */
@RunWith(AndroidJUnit4::class)
class UiRunSideChannelTest {

    @Test
    fun uiSend_producesObservableRun() {
        ActivityScenario.launch(ChatActivity::class.java).use { scenario ->
            // Let the PC passive monitor attach to the debug bridge.
            Thread.sleep(WAIT_BEFORE_SEND_MS)

            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.input)
                val send = activity.findViewById<Button>(R.id.send)
                assertNotNull("input field must exist", input)
                assertNotNull("send button must exist", send)
                input.setText(GOAL)
                send.performClick()
            }

            // Wait for the Run to complete: Send is disabled while busy.
            val deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                var enabled = false
                scenario.onActivity { activity ->
                    enabled = activity.findViewById<Button>(R.id.send).isEnabled
                }
                if (enabled) break
                Thread.sleep(500)
            }
            // Allow the monitor to receive the terminal events.
            Thread.sleep(TERMINAL_GRACE_MS)
        }
    }

    private companion object {
        /** Simple, tool-free request so T3 does not depend on AT / Wireless Debugging. */
        const val GOAL = "Reply exactly with DEBUG_UI_OK."
        const val WAIT_BEFORE_SEND_MS = 15_000L
        const val RUN_TIMEOUT_MS = 120_000L
        const val TERMINAL_GRACE_MS = 3_000L
    }
}