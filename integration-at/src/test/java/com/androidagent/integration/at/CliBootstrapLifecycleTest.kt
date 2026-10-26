package com.androidagent.integration.at

import com.at.cli.LocalCliBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX — unit tests for the canonical
 * bootstrap lifecycle logic.
 *
 * These tests exercise the same staleness-detection + atomic-refresh
 * algorithm that AtExecutionAdapter.ensureCliBootstrapCurrent() uses,
 * without requiring the Android runtime (no ATEngine, no AdbSemantic).
 */
class CliBootstrapLifecycleTest {

    @TempDir
    lateinit var appFilesDir: File

    private val mainClass = "com.at.cli.AtCliMain"
    private val bootstrap = LocalCliBootstrap(mainClass)
    private val sockPath = "/data/user/0/com.androidagent.app/files/at-cli-bridge/cli_bridge.sock"

    private val canonicalFile: File
        get() = File(File(appFilesDir, "at-cli"), "adb_bootstrap.sh")

    @BeforeEach
    fun setUp() {
        // Ensure clean state
        canonicalFile.parentFile?.deleteRecursively()
    }

    // B1 — Missing Bootstrap
    @Test
    fun B1_missing_bootstrap_generates_with_current_apk() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        assertTrue(!canonicalFile.exists())

        val result = ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)

        assertTrue(result != null)
        assertTrue(canonicalFile.exists())
        val content = canonicalFile.readText()
        assertTrue(content.contains(apkA), "bootstrap must contain current APK path")
        assertTrue(content.contains("adb()"), "bootstrap must define adb function")
    }

    // B2 — Valid Existing Bootstrap (no stale path)
    @Test
    fun B2_valid_bootstrap_is_not_regenerated() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        // Generate
        ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)
        val originalContent = canonicalFile.readText()
        val originalLastModified = canonicalFile.lastModified()

        // Ensure again with same APK path
        val result = ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)

        assertTrue(result != null)
        assertEquals(originalContent, canonicalFile.readText(), "valid bootstrap must not be rewritten")
    }

    // B3 — Stale APK Path (CORE REGRESSION)
    @Test
    fun B3_stale_apk_path_triggers_atomic_refresh() {
        val apkOld = "/data/app/~~OLD/com.androidagent.app-old/base.apk"
        val apkNew = "/data/app/~~NEW/com.androidagent.app-new/base.apk"

        // Generate with old path
        ensureBootstrap(appFilesDir, apkOld, sockPath, bootstrap)
        assertTrue(canonicalFile.readText().contains(apkOld))
        assertFalse(canonicalFile.readText().contains(apkNew))

        // Ensure with new path (simulates install -r)
        val result = ensureBootstrap(appFilesDir, apkNew, sockPath, bootstrap)

        assertTrue(result != null)
        val content = canonicalFile.readText()
        assertTrue(content.contains(apkNew), "bootstrap must contain new APK path after refresh")
        assertFalse(content.contains(apkOld), "bootstrap must NOT contain old APK path after refresh")
    }

    // B4 — No Latest-Wins Dependency (legacy per-session files)
    @Test
    fun B4_legacy_per_session_files_do_not_interfere() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        val cliDir = File(appFilesDir, "at-cli").apply { mkdirs() }

        // Simulate old per-session bootstrap files
        val legacy1 = File(cliDir, "adb_bootstrap_1.sh")
        val legacy999 = File(cliDir, "adb_bootstrap_999.sh")
        legacy1.writeText("export AT_APP_APK='/stale/old/path.apk'\n")
        legacy999.writeText("export AT_APP_APK='/stale/old/path.apk'\n")

        // Ensure canonical bootstrap
        val result = ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)

        assertTrue(result != null)
        val content = canonicalFile.readText()
        assertTrue(content.contains(apkA))
        // The source line must reference the canonical file, NOT legacy files
        assertTrue(result!!.contains(canonicalFile.name), "source line must reference canonical file")
        assertFalse(result.contains("adb_bootstrap_999"), "must not reference legacy per-session files")
    }

    // B5 — Bridge Config Change (socket path)
    @Test
    fun B5_socket_path_change_triggers_refresh() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        val sockOld = "/data/user/0/com.androidagent.app/files/at-cli-bridge/cli_bridge_old.sock"
        val sockNew = "/data/user/0/com.androidagent.app/files/at-cli-bridge/cli_bridge.sock"

        // Generate with old socket
        ensureBootstrap(appFilesDir, apkA, sockOld, bootstrap)
        assertTrue(canonicalFile.readText().contains(sockOld))

        // Ensure with new socket (same APK, different bridge)
        val result = ensureBootstrap(appFilesDir, apkA, sockNew, bootstrap)

        assertTrue(result != null)
        val content = canonicalFile.readText()
        assertTrue(content.contains(sockNew), "bootstrap must contain new socket path")
        assertFalse(content.contains(sockOld), "bootstrap must NOT contain old socket path")
    }

    // B6 — No Session Prerequisite
    @Test
    fun B6_no_session_needed_first_call_generates() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        assertTrue(!canonicalFile.exists())

        // No session was ever opened; just call ensure
        val result = ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)

        assertTrue(result != null)
        assertTrue(canonicalFile.exists())
        assertTrue(canonicalFile.readText().contains(apkA))
    }

    // B7 — Empty APK path returns null (not yet initialized)
    @Test
    fun B7_empty_apk_path_returns_null() {
        val result = ensureBootstrap(appFilesDir, "", sockPath, bootstrap)
        assertNull(result)
        assertTrue(!canonicalFile.exists())
    }

    // B8 — Atomic write leaves no .tmp behind
    @Test
    fun B8_no_tmp_file_remains_after_successful_write() {
        val apkA = "/data/app/~~AAA/com.androidagent.app-xxx/base.apk"
        ensureBootstrap(appFilesDir, apkA, sockPath, bootstrap)

        val tmp = File(File(appFilesDir, "at-cli"), "adb_bootstrap.sh.tmp")
        assertFalse(tmp.exists(), ".tmp file must not remain after successful write")
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Mirrors the logic of AtExecutionAdapter.ensureCliBootstrapCurrent():
     * staleness check (APK path + socket path in content), atomic write via .tmp + rename.
     */
    private fun ensureBootstrap(
        appFilesDir: File,
        apkPath: String,
        sockPath: String,
        bootstrap: LocalCliBootstrap,
    ): String? {
        if (apkPath.isEmpty()) return null

        val file = File(File(appFilesDir, "at-cli"), "adb_bootstrap.sh")
        val dir = file.parentFile
        dir?.mkdirs()

        // Staleness: file must contain BOTH current APK path AND current socket path.
        if (file.exists() && file.length() > 0) {
            val content = file.readText()
            if (content.contains(apkPath) && content.contains(sockPath)) {
                return bootstrap.sourceLine(file.absolutePath)
            }
        }

        // Stale or absent: atomic rewrite.
        val tmp = File(dir, "adb_bootstrap.sh.tmp")
        bootstrap.writeScript(tmp.absolutePath, apkPath, sockPath, 0)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return null
            }
        }
        return bootstrap.sourceLine(file.absolutePath)
    }
}
