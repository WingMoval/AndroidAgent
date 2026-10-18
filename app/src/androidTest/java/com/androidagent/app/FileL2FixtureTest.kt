package com.androidagent.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/**
 * AG.FILE-0 L2 fixture setup.
 *
 * Creates test data in filesDir/agent/.l2test/ so the PC-side DebugBridge
 * tool_invoke probes can exercise the file tool against real files.
 * Runs as the app's own UID (instrumentation), so it CAN access filesDir.
 *
 * The PC script (tools/ag_file_l2.py) creates NO files via shell — it only
 * reads via the file tool. This test is the sole writer.
 *
 * Usage:
 *   adb shell am instrument -w com.androidagent.app/com.androidagent.app.FileL2FixtureTest
 */
@RunWith(AndroidJUnit4::class)
class FileL2FixtureTest {

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext get() = targetContext.applicationContext

    @Test
    fun create_fixtures() {
        val agentDir = File(appContext.filesDir, "agent")
        val testDir = File(agentDir, ".l2test")
        val subDir = File(testDir, "sub")
        subDir.mkdirs()

        // hello.txt: "hello\nworld\n" (12 bytes)
        File(testDir, "hello.txt").writeText("hello\nworld\n")

        // empty.txt: 0 bytes
        File(testDir, "empty.txt").writeText("")

        // bom.txt: UTF-8 BOM + "hi"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        Files.write(testDir.toPath().resolve("bom.txt"), bom + "hi".toByteArray())

        // binary.dat: "ab\0cd" (NUL byte)
        Files.write(testDir.toPath().resolve("binary.dat"),
            "ab".toByteArray() + byteArrayOf(0) + "cd".toByteArray())

        // big.txt: 4096 'x' chars
        File(testDir, "big.txt").writeText("x".repeat(4096))

        // lines.txt: "l1\nl2\nl3\nl4\nl5\n" (5 lines)
        File(testDir, "lines.txt").writeText("l1\nl2\nl3\nl4\nl5\n")

        // sub/a.txt: "needle here\n"
        File(subDir, "a.txt").writeText("needle here\n")

        // sub/b.txt: "no match\n"
        File(subDir, "b.txt").writeText("no match\n")

        // many.txt: "hit1\nhit2\n...\nhit10\n" (10 lines)
        File(testDir, "many.txt").writeText((1..10).joinToString("\n") { "hit$it" } + "\n")

        val created = testDir.walkTopDown().filter { it.isFile }.map { it.name }.sorted()
        Log.i(TAG, "FIXTURES_CREATED: $created")
    }

    @Test
    fun cleanup_fixtures() {
        val testDir = File(appContext.filesDir, "agent/.l2test")
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
        Log.i(TAG, "FIXTURES_CLEANED")
    }

    private companion object {
        const val TAG = "AG.FILE-0.L2"
    }
}
