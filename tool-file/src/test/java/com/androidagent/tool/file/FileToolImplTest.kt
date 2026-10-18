package com.androidagent.tool.file

import com.androidagent.core.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * L1 unit tests for FileToolImpl (AG.FILE-0). Real temp dirs + NIO.2.
 * Covers argument validation, read/search/list/stat ops, bounds, binary
 * refusal, path confinement via the public execute() path.
 */
class FileToolImplTest {

    @TempDir
    lateinit var root: Path

    private fun tool(): FileToolImpl = FileToolImpl(root.toFile())

    private fun call(args: String): ToolCall =
        ToolCall(id = "t1", name = FileToolImpl.TOOL_NAME, arguments = args)

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // ------------------------------------------------------------------ argument validation

    @Test
    fun A1_blank_arguments_rejected_no_file_op() {
        val r = runBlocking { tool().execute(call("")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A2_not_valid_json_rejected() {
        val r = runBlocking { tool().execute(call("{not json")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A3_missing_action_rejected() {
        val r = runBlocking { tool().execute(call("""{"path":"a.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A4_unknown_action_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"write","path":"a.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A5_missing_path_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"read"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A6_blank_path_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"   "}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A7_search_without_pattern_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"a.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A8_negative_offsetLines_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"a.txt","offsetLines":-1}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A9_zero_limitLines_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"a.txt","limitLines":0}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun A10_negative_maxEntries_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"list","path":".","maxEntries":-5}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    // ------------------------------------------------------------------ path confinement (through execute)

    @Test
    fun C1_dotdot_escape_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"../escape.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.PATH_OUTSIDE_ROOT.name))
    }

    @Test
    fun C2_stat_missing_inside_root_ok_type_missing() {
        val r = runBlocking { tool().execute(call("""{"action":"stat","path":"nope.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("missing", o["type"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ read

    @Test
    fun R1_read_plain_file() {
        val f = root.resolve("a.txt")
        Files.write(f, "hello\nworld\n".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"a.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("read", o["action"]!!.jsonPrimitive.content)
        assertEquals("a.txt", o["path"]!!.jsonPrimitive.content)
        assertEquals("hello\nworld", o["content"]!!.jsonPrimitive.content)
        assertEquals(2, o["totalLines"]!!.jsonPrimitive.int)
        assertFalse(o["isBinary"]!!.jsonPrimitive.boolean)
        assertFalse(o["truncated"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun R2_read_missing_file() {
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"missing.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.NOT_FOUND.name))
    }

    @Test
    fun R3_read_directory_rejected() {
        Files.createDirectory(root.resolve("d"))
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"d"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.NOT_A_FILE.name))
    }

    @Test
    fun R4_read_empty_file_success_empty_content() {
        Files.write(root.resolve("empty.txt"), ByteArray(0))
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"empty.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("", o["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun R5_read_binary_refused_isBinary() {
        val f = root.resolve("bin.dat")
        Files.write(f, "ab".toByteArray() + byteArrayOf(0) + "cd".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"bin.dat"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertTrue(o["isBinary"]!!.jsonPrimitive.boolean)
        assertEquals("", o["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun R6_read_offsetLimit_window() {
        val f = root.resolve("lines.txt")
        Files.write(f, "l1\nl2\nl3\nl4\nl5\n".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"lines.txt","offsetLines":1,"limitLines":2}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("l2\nl3", o["content"]!!.jsonPrimitive.content)
        assertEquals(1, o["startLine"]!!.jsonPrimitive.int)
        assertEquals(5, o["totalLines"]!!.jsonPrimitive.int)
    }

    @Test
    fun R7_read_utf8_bom_stripped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        Files.write(root.resolve("bom.txt"), bom + "hi".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"bom.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("hi", o["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun R8_read_maxBytes_truncates() {
        val big = "x".repeat(4096)
        Files.write(root.resolve("big.txt"), big.toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"big.txt","maxBytes":100}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertTrue(o["truncated"]!!.jsonPrimitive.boolean)
        assertEquals(100, o["bytesRead"]!!.jsonPrimitive.int)
        assertEquals(4096, o["totalBytes"]!!.jsonPrimitive.long)
    }

    // ------------------------------------------------------------------ search

    @Test
    fun S1_search_file_substring_caseSensitive_default() {
        val f = root.resolve("s.txt")
        Files.write(f, "Hello\nhello world\nHELLO".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"s.txt","pattern":"hello"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(1, o["totalMatches"]!!.jsonPrimitive.int)
        val m = o["matches"]!!.jsonArray[0].jsonObject
        assertEquals(2, m["line"]!!.jsonPrimitive.int)
    }

    @Test
    fun S2_search_caseInsensitive() {
        val f = root.resolve("s2.txt")
        Files.write(f, "Hello\nhello world\nHELLO".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"s2.txt","pattern":"hello","caseSensitive":false}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(3, o["totalMatches"]!!.jsonPrimitive.int)
    }

    @Test
    fun S3_search_regex() {
        val f = root.resolve("s3.txt")
        Files.write(f, "abc123\nx9y\nabc456".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"s3.txt","pattern":"abc[0-9]+","regex":true}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(2, o["totalMatches"]!!.jsonPrimitive.int)
    }

    @Test
    fun S4_search_invalid_regex_rejected() {
        val f = root.resolve("s4.txt")
        Files.write(f, "abc".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"s4.txt","pattern":"([","regex":true}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.INVALID_ARGUMENT.name))
    }

    @Test
    fun S5_search_directory_subtree() {
        Files.createDirectory(root.resolve("sub"))
        Files.write(root.resolve("sub/a.txt"), "needle here\n".toByteArray())
        Files.write(root.resolve("sub/b.txt"), "no match\n".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"sub","pattern":"needle"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(1, o["totalMatches"]!!.jsonPrimitive.int)
        val m = o["matches"]!!.jsonArray[0].jsonObject
        assertEquals("sub/a.txt", m["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun S6_search_maxMatches_truncates() {
        val lines = (1..10).joinToString("\n") { "hit$it" }
        Files.write(root.resolve("many.txt"), lines.toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"many.txt","pattern":"hit","maxMatches":3}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(3, o["matches"]!!.jsonArray.size)
        assertTrue(o["truncated"]!!.jsonPrimitive.boolean)
        assertTrue(o["totalMatches"]!!.jsonPrimitive.int >= 3)
    }

    @Test
    fun S7_search_missing_path() {
        val r = runBlocking { tool().execute(call("""{"action":"search","path":"ghost.txt","pattern":"x"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.NOT_FOUND.name))
    }

    // ------------------------------------------------------------------ list

    @Test
    fun L1_list_directory() {
        Files.write(root.resolve("a.txt"), "1".toByteArray())
        Files.createDirectory(root.resolve("sub"))
        val r = runBlocking { tool().execute(call("""{"action":"list","path":"."}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(2, o["totalEntries"]!!.jsonPrimitive.int)
        val names = (o["entries"] as JsonArray).map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        assertTrue(names.contains("a.txt"))
        assertTrue(names.contains("sub"))
    }

    @Test
    fun L2_list_file_rejected() {
        Files.write(root.resolve("a.txt"), "1".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"list","path":"a.txt"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.NOT_A_DIRECTORY.name))
    }

    @Test
    fun L3_list_missing_rejected() {
        val r = runBlocking { tool().execute(call("""{"action":"list","path":"ghostdir"}""")) }
        assertFalse(r.success)
        assertTrue(r.error!!.contains(FileError.NOT_FOUND.name))
    }

    @Test
    fun L4_list_maxEntries_truncates() {
        for (i in 1..5) Files.write(root.resolve("f$i.txt"), "x".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"list","path":".","maxEntries":2}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals(2, o["entries"]!!.jsonArray.size)
        assertTrue(o["truncated"]!!.jsonPrimitive.boolean)
        assertEquals(5, o["totalEntries"]!!.jsonPrimitive.int)
    }

    // ------------------------------------------------------------------ stat

    @Test
    fun ST1_stat_file() {
        Files.write(root.resolve("a.txt"), "hello".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"stat","path":"a.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("file", o["type"]!!.jsonPrimitive.content)
        assertEquals(5, o["sizeBytes"]!!.jsonPrimitive.long)
        assertTrue(o["readable"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun ST2_stat_directory() {
        Files.createDirectory(root.resolve("d"))
        val r = runBlocking { tool().execute(call("""{"action":"stat","path":"d"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("dir", o["type"]!!.jsonPrimitive.content)
        assertEquals(0, o["sizeBytes"]!!.jsonPrimitive.long)
    }

    @Test
    fun ST3_stat_missing() {
        val r = runBlocking { tool().execute(call("""{"action":"stat","path":"ghost.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("missing", o["type"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------------ nested path display

    @Test
    fun N1_nested_path_display_is_root_relative() {
        Files.createDirectory(root.resolve("folder"))
        Files.write(root.resolve("folder/a.txt"), "x".toByteArray())
        val r = runBlocking { tool().execute(call("""{"action":"read","path":"folder/a.txt"}""")) }
        assertTrue(r.success)
        val o = obj(r.output)
        assertEquals("folder/a.txt", o["path"]!!.jsonPrimitive.content)
    }
}
