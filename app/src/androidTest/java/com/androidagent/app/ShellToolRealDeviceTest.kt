package com.androidagent.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidagent.core.ToolApprovalPolicy
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolRegistry
import com.androidagent.tool.androidshell.AndroidShellToolImpl
import com.androidagent.tool.androidshell.ShellConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AG.SHELL-1 real-device acceptance (S1–S5).
 *
 * Preconditions (v0, test-only provisioning):
 *  - Wireless Debugging already enabled on the device.
 *  - A trusted adbkey is provisioned into filesDir/at-identity/ (test fixture,
 *    pushed via `run-as` before this test runs; never committed, cleaned after).
 *
 * No live LLM / no EMG: this proves the ToolRegistry path
 * (ToolRegistry -> android_shell -> AndroidShellToolImpl -> AT Engine -> shell).
 */
@RunWith(AndroidJUnit4::class)
class ShellToolRealDeviceTest {

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext get() = targetContext.applicationContext

/** S1 — ShellConnection: connect -> READY. */
    @Test
    fun S1_connection_reaches_ready() {
        runBlocking {
            val shell = ShellConnection(appContext)
            val status = shell.connect()
            Log.i(TAG, "S1 connect -> code=${status.code} message=${status.message}")
            assertTrue("connect should be READY, got code=${status.code} msg=${status.message}", status.ready)
            shell.disconnect()
        }
    }

    /** S2 — AndroidShellTool `id` through the real ToolRegistry. */
    @Test
    fun S2_tool_id_through_registry() {
        runBlocking {
            val shell = ShellConnection(appContext)
            assertTrue("connect failed: ${shell.connect().message}", shell.connect().ready)
            val registry = registryWithShellTool()

            val result = registry.dispatch(
                ToolCall("id1", AndroidShellToolImpl.TOOL_NAME, """{"command":"id"}"""),
                ToolApprovalPolicy.AllowAll,
            )

            assertTrue("ToolResult.success should be true, got ${result.output}", result.success)
            assertTrue("stdout should contain uid=2000(shell), got: ${result.output}", result.output.contains("uid=2000(shell)"))
            assertTrue("payload should declare COMPLETED", result.output.contains("\"outcome\":\"COMPLETED\""))
            shell.disconnect()
        }
    }

    /** S3 — Result contract: `sh -c 'echo OUT; echo ERR >&2; exit 7'`. */
    @Test
    fun S3_result_contract_exit7() {
        runBlocking {
            val shell = ShellConnection(appContext)
            assertTrue("connect failed: ${shell.connect().message}", shell.connect().ready)
            val registry = registryWithShellTool()

            val result = registry.dispatch(
                ToolCall(
                    "id2",
                    AndroidShellToolImpl.TOOL_NAME,
                    """{"command":"sh -c 'echo OUT; echo ERR >&2; exit 7'"}""",
                ),
                ToolApprovalPolicy.AllowAll,
            )

            assertTrue("exit7 is a mechanism success, got ${result.output}", result.success)
            assertTrue("payload should carry exitCode 7", result.output.contains("\"exitCode\":7"))
            assertTrue("stdout should carry OUT", result.output.contains("OUT"))
            assertTrue("stderr should carry ERR", result.output.contains("ERR"))
            assertTrue("hasError should be false", result.output.contains("\"hasError\":false"))
            shell.disconnect()
        }
    }

    /** S4 — Invalid arguments fail with success=false (executor call count = 0 is unit-proven). */
    @Test
    fun S4_invalid_args_fail() {
        runBlocking {
            val registry = registryWithShellTool()

            val result = registry.dispatch(
                ToolCall("id3", AndroidShellToolImpl.TOOL_NAME, """{}"""),
                ToolApprovalPolicy.AllowAll,
            )

            assertFalse("blank/missing command must be a tool failure", result.success)
        }
    }

    /** S5 — Registered path: ToolRegistry -> android_shell -> AndroidShellToolImpl -> AT Engine -> shell. */
    @Test
    fun S5_registered_tool_path_executes() {
        runBlocking {
            val shell = ShellConnection(appContext)
            assertTrue("connect failed: ${shell.connect().message}", shell.connect().ready)
            val registry = registryWithShellTool()

            val spec = registry.find(AndroidShellToolImpl.TOOL_NAME)?.spec
            assertNotNull("android_shell must be registered", spec)
            assertEquals(AndroidShellToolImpl.TOOL_NAME, spec!!.name)

            val result = registry.dispatch(
                ToolCall("id4", AndroidShellToolImpl.TOOL_NAME, """{"command":"echo AG0_S5_OK"}"""),
                ToolApprovalPolicy.AllowAll,
            )

            assertTrue("S5 dispatch should succeed, got ${result.output}", result.success)
            assertTrue("stdout should carry AG0_S5_OK", result.output.contains("AG0_S5_OK"))
            shell.disconnect()
        }
    }

    private fun registryWithShellTool(): ToolRegistry =
        ToolRegistry().also { it.register(AndroidShellToolImpl()) }

    private companion object {
        const val TAG: String = "AG.SHELL-1"
    }
}