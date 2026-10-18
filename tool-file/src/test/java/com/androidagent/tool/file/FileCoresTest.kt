package com.androidagent.tool.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * L1 core tests for the file tool's pure logic (AG.FILE-0 task §21):
 * PathResolver (canonical + root confinement + traversal + symlink escape) and
 * TextCodec (UTF-8 / BOM / REPLACE). Real temp dirs + NIO, no Android.
 */
class FileCoresTest {

    @TempDir
    lateinit var root: Path

    @TempDir
    lateinit var outside: Path

    private fun rel(p: String): Path = root.resolve(p)

    // ------------------------------------------------------------------ PathResolver

    @Test
    fun P1_relative_path_resolves_inside_root() {
        val r = PathResolver(root.toFile())
        val res = r.resolve("notes.txt")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root.resolve("notes.txt"), (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P2_root_itself_resolves() {
        val r = PathResolver(root.toFile())
        val res = r.resolve(".")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root, (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P3_nested_relative_resolves() {
        val r = PathResolver(root.toFile())
        val res = r.resolve("folder/a.txt")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root.resolve("folder/a.txt"), (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P4_dotdot_traversal_outside_root_rejected() {
        val r = PathResolver(root.toFile())
        val res = r.resolve("../escape.txt")
        assertTrue(res is PathResolver.Result.OutsideRoot)
    }

    @Test
    fun P5_dotdot_inside_root_stays() {
        val r = PathResolver(root.toFile())
        // "folder/sub/../../notes.txt" normalizes to "notes.txt" — inside root.
        val res = r.resolve("folder/sub/../../notes.txt")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root.resolve("notes.txt"), (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P6_absolute_path_inside_root_accepted() {
        val r = PathResolver(root.toFile())
        val target = root.resolve("inner.txt")
        val res = r.resolve(target.toString())
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(target, (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P7_absolute_path_outside_root_rejected() {
        val r = PathResolver(root.toFile())
        val res = r.resolve(outside.resolve("other.txt").toString())
        assertTrue(res is PathResolver.Result.OutsideRoot)
    }

    @Test
    fun P8_absolute_system_path_rejected() {
        val r = PathResolver(root.toFile())
        val res = r.resolve("/etc/hostname")
        assertTrue(res is PathResolver.Result.OutsideRoot)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun P9_symlink_inside_root_is_followed() {
        val real = root.resolve("real.txt")
        Files.write(real, "data".toByteArray())
        val link = root.resolve("link.txt")
        Files.createSymbolicLink(link, real)
        val r = PathResolver(root.toFile())
        val res = r.resolve("link.txt")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root.resolve("real.txt"), (res as PathResolver.Result.Ok).path)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun P10_symlink_escaping_root_rejected() {
        val outsideFile = outside.resolve("secret.txt")
        Files.write(outsideFile, "secret".toByteArray())
        val link = root.resolve("escape-link.txt")
        Files.createSymbolicLink(link, outsideFile)
        val r = PathResolver(root.toFile())
        val res = r.resolve("escape-link.txt")
        assertTrue(res is PathResolver.Result.OutsideRoot)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun P11_broken_symlink_normalizes_and_stays_inside() {
        val link = root.resolve("dangling.txt")
        Files.createSymbolicLink(link, root.resolve("does-not-exist.txt"))
        val r = PathResolver(root.toFile())
        // Broken symlink: toRealPath fails -> normalize; still inside root.
        val res = r.resolve("dangling.txt")
        assertTrue(res is PathResolver.Result.Ok)
        assertEquals(root.resolve("dangling.txt"), (res as PathResolver.Result.Ok).path)
    }

    @Test
    fun P12_blank_path_rejected() {
        val r = PathResolver(root.toFile())
        assertTrue(r.resolve("   ") is PathResolver.Result.OutsideRoot)
    }

    @Test
    fun P13_rel_display_is_root_relative_no_duplication() {
        val r = PathResolver(root.toFile())
        val res = r.resolve("notes.txt") as PathResolver.Result.Ok
        assertEquals("notes.txt", res.relDisplay)
    }

    // ------------------------------------------------------------------ TextCodec

    @Test
    fun T1_plain_utf8_decodes() {
        assertEquals("hello", TextCodec.decodeUtf8("hello".toByteArray()))
    }

    @Test
    fun T2_bom_is_stripped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val withBom = bom + "hi".toByteArray()
        assertEquals("hi", TextCodec.decodeUtf8(withBom))
    }

    @Test
    fun T3_malformed_utf8_replaces_not_throws() {
        // 0xFF 0xFE is not valid UTF-8 start; must not throw.
        val bytes = "ok".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val s = TextCodec.decodeUtf8(bytes)
        assertTrue(s.startsWith("ok"))
        assertFalse(s.isEmpty())
    }

    @Test
    fun T4_empty_is_empty() {
        assertEquals("", TextCodec.decodeUtf8(ByteArray(0)))
    }

    // ------------------------------------------------------------------ binary sniff

    private fun sniff(bytes: ByteArray): Boolean =
        (0 until bytes.size.coerceAtMost(FileToolImpl.BINARY_SNIFF_BYTES)).any { bytes[it] == 0.toByte() }

    @Test
    fun B1_nul_byte_is_binary() {
        assertTrue(sniff("ab".toByteArray() + byteArrayOf(0) + "cd".toByteArray()))
    }

    @Test
    fun B2_plain_text_is_not_binary() {
        assertFalse(sniff("plain text\nline two".toByteArray()))
    }

    @Test
    fun B3_nul_beyond_window_is_not_sniffed() {
        val prefix = "x".repeat(FileToolImpl.BINARY_SNIFF_BYTES + 1)
        val bytes = prefix.toByteArray() + byteArrayOf(0)
        assertFalse(sniff(bytes))
    }
}
