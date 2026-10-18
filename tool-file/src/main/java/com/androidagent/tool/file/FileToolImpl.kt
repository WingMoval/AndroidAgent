package com.androidagent.tool.file

import com.androidagent.core.Tool
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import com.androidagent.core.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.toPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

/**
 * Concrete File Tool — the third Tool Layer tool (sibling of AndroidShellTool and
 * NetworkTool). APP_SELF realm only; model-facing root = Host-injected
 * `filesDir/agent/` (relative paths resolve against it).
 *
 * Responsibilities, frozen (AG.FILE-0 Preflight §12-20):
 *
 * ```
 * ToolCall.arguments ({"action":"read|search|list|stat","path":"...",...})
 *   ↓ FileToolSchema.parse        (invalid -> success=false, 0 file ops)
 *   ↓ PathResolver.resolve        (canonical + root confinement + symlink defense)
 *   ↓ op (read/search/list/stat)  (NIO.2, bounded, UTF-8, binary -> refuse)
 *   ↓ FileResultPayload.encode    (JSON -> ToolResult.output)
 * ToolResult
 * ```
 *
 * ToolResult.success = mechanism success. A reached, readable resource reports
 * success=true (including an empty file); a domain refusal (NOT_FOUND,
 * NOT_A_FILE, NOT_A_DIRECTORY, PATH_OUTSIDE_ROOT, UNSUPPORTED_CONTENT) reports
 * success=false with a structured [FileError] code. CancellationException is
 * rethrown unchanged (frozen runtime semantics).
 *
 * V0 is READ-only (read/search/list/stat). write/edit/patch/delete/move/rename/
 * copy/mkdir are NOT implemented (authority semantics not yet allowed). No SAF,
 * no AT/ADB, no backend abstraction.
 */
class FileToolImpl(
    rootFile: File,
) : Tool {

    private val resolver = PathResolver(rootFile)

    override val spec: ToolSpec = ToolSpec(
        name = TOOL_NAME,
        description =
            "WHAT: Read files inside the agent's own private workspace. Actions: " +
                "\"read\" (bounded text of one file, with optional line offset/limit), " +
                "\"search\" (line-aware substring or regex search in one file or a " +
                "directory subtree), \"list\" (entries of one directory), \"stat\" " +
                "(metadata: exists/type/size/mtime). All paths are relative to the " +
                "workspace root (e.g. \"notes.txt\", \"folder/a.txt\"); absolute paths " +
                "are only accepted if they stay inside the root.\n" +
                "WHEN: Use when the task needs the content or listing of files the agent " +
                "can see in its workspace. Do NOT use for shell commands, network fetches, " +
                "or writing/editing files (write is not available).\n" +
                "INPUT: A single JSON object: {\"action\":\"read|search|list|stat\"," +
                "\"path\":\"<root-relative-or-inside-root path>\", ...}. read: optional " +
                "\"offsetLines\",\"limitLines\",\"maxBytes\". search: required \"pattern\", " +
                "optional \"regex\",\"maxMatches\",\"contextLines\",\"caseSensitive\". " +
                "list: optional \"maxEntries\". stat: path only.\n" +
                "OUTPUT: A JSON object echoing \"action\" and a canonical \"path\", plus " +
                "action-specific fields. read: \"content\",\"startLine\",\"totalLines\"," +
                "\"truncated\",\"bytesRead\",\"totalBytes\",\"isBinary\". search: " +
                "\"matches\":[{\"line\",\"text\"}],\"totalMatches\",\"truncated\". list: " +
                "\"entries\":[{\"name\",\"type\",\"sizeBytes\"}],\"totalEntries\"," +
                "\"truncated\". stat: \"type\",\"sizeBytes\",\"lastModified\",\"readable\"," +
                "\"writable\". A clean read reports success=true; a refusal " +
                "(not found / outside root / directory where a file is expected / binary) " +
                "reports success=false with an \"error\" field. \"content\" is bounded; " +
                "truncated=true when a limit was hit. Binary files return isBinary=true " +
                "with empty content (never force-decoded).",
    )

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        when (val parsed = FileToolSchema.parse(call.arguments)) {
            is FileToolSchema.ParseResult.Error ->
                ToolResult(success = false, output = "", error = "${FileError.INVALID_ARGUMENT}: ${parsed.reason}")

            is FileToolSchema.ParseResult.Ok -> runOp(parsed.parsed)
        }
    }

    private suspend fun runOp(p: FileToolSchema.Parsed): ToolResult {
        // 1. Resolve + confine to root (canonical + traversal + symlink defense).
        val resolved = resolver.resolve(p.path)
        if (resolved is PathResolver.Result.OutsideRoot) {
            return ToolResult(
                success = false,
                output = "",
                error = "${FileError.PATH_OUTSIDE_ROOT}: ${resolved.reason}",
            )
        }
        val path = (resolved as PathResolver.Result.Ok).path
        val rel = normalizeDisplay(resolved.relDisplay)

        return try {
            when (p.action) {
                FileToolSchema.ACTION_READ -> readOp(path, rel, p)
                FileToolSchema.ACTION_SEARCH -> searchOp(path, rel, p)
                FileToolSchema.ACTION_LIST -> listOp(path, rel, p)
                FileToolSchema.ACTION_STAT -> statOp(path, rel)
                else -> ToolResult(success = false, output = "", error = "${FileError.UNKNOWN_ACTION}: ${p.action}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileOpException) {
            ToolResult(success = false, output = "", error = "${e.code}: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, output = "", error = "${FileError.IO_ERROR}: ${e.message ?: "io error"}")
        }
    }

    // ------------------------------------------------------------------ read

    private fun readOp(path: Path, rel: String, p: FileToolSchema.Parsed): ToolResult {
        if (!Files.exists(path)) throw FileOpException(FileError.NOT_FOUND, "file does not exist")
        if (Files.isDirectory(path)) throw FileOpException(FileError.NOT_A_FILE, "path is a directory, not a file")

        val totalBytes = Files.size(path)
        val maxBytes = p.maxBytes?.coerceAtMost(MAX_FILE_READ_BYTES) ?: MAX_FILE_READ_BYTES

        // BYTE bound (storage-level): read at most maxBytes.
        val bytes = Files.readAllBytes(path)
        val readBytes = if (bytes.size <= maxBytes) bytes.size else maxBytes
        val bounded = if (readBytes < bytes.size) bytes.copyOfRange(0, maxBytes) else bytes
        val byteTruncated = readBytes < bytes.size

        // Binary detection (NUL in the leading window) -> refuse, do not force-decode.
        if (isBinary(bounded)) {
            return ok(
                FileResultPayload.encodeRead(
                    path = rel, content = "", startLine = 0, totalLines = null,
                    truncated = byteTruncated, bytesRead = readBytes, totalBytes = totalBytes, isBinary = true,
                ),
            )
        }

        // Decode UTF-8 (BOM stripped, malformed -> REPLACE).
        val text = TextCodec.decodeUtf8(bounded)

        // Line-based windowing. A trailing newline produces a final empty
        // element from split(); drop it so "a\nb\n" -> ["a","b"] not ["a","b",""].
        val rawLines = text.splitToSequence("\n").toList()
        val lines = if (rawLines.isNotEmpty() && rawLines.last().isEmpty()) rawLines.dropLast(1) else rawLines
        val totalLines = lines.size
        val offset = p.offsetLines ?: 0
        val limit = p.limitLines
        val windowLines: List<String>
        val lineTruncated: Boolean
        if (limit == null) {
            windowLines = lines.drop(offset)
            lineTruncated = false
        } else {
            val start = offset.coerceAtMost(totalLines)
            windowLines = lines.subList(start, (start + limit).coerceAtMost(totalLines))
            lineTruncated = (offset + limit) > totalLines
        }

        val joined = windowLines.joinToString("\n")
        var content = joined
        var charTruncated = false
        if (content.length > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS)
            charTruncated = true
        }

        return ok(
            FileResultPayload.encodeRead(
                path = rel, content = content, startLine = offset, totalLines = totalLines,
                truncated = byteTruncated || lineTruncated || charTruncated,
                bytesRead = readBytes, totalBytes = totalBytes, isBinary = false,
            ),
        )
    }

    // ------------------------------------------------------------------ search

    private fun searchOp(path: Path, rel: String, p: FileToolSchema.Parsed): ToolResult {
        if (!Files.exists(path)) throw FileOpException(FileError.NOT_FOUND, "path does not exist")

        val pattern = p.pattern ?: throw FileOpException(FileError.INVALID_ARGUMENT, "search requires a pattern")
        val maxMatches = p.maxMatches?.coerceAtMost(MAX_SEARCH_MATCHES) ?: MAX_SEARCH_MATCHES

        val matcher: (String) -> Boolean
        if (p.regex) {
            val re = try {
                java.util.regex.Pattern.compile(pattern, if (p.caseSensitive) 0 else java.util.regex.Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                throw FileOpException(FileError.INVALID_ARGUMENT, "invalid regex pattern")
            }
            matcher = { line: String -> re.matcher(line).find() }
        } else {
            val pat = if (p.caseSensitive) pattern else pattern.lowercase()
            matcher = { line: String -> (if (p.caseSensitive) line else line.lowercase()).contains(pat) }
        }

        val targets: List<Path> = if (Files.isDirectory(path)) {
            listFilesRecursive(path)
        } else {
            listOf(path)
        }

        val matches = mutableListOf<Map<String, Any?>>()
        var totalMatches = 0
        var truncated = false

        outer@ for (file in targets) {
            if (Files.isDirectory(file) || isBinary(Files.readAllBytes(file))) continue
            val text = TextCodec.decodeUtf8(Files.readAllBytes(file))
            val lines = text.split("\n")
            for (i in lines.indices) {
                if (matcher(lines[i])) {
                    totalMatches++
                    if (matches.size < maxMatches) {
                        matches.add(
                            mapOf(
                                "path" to resolverRel(file),
                                "line" to (i + 1),
                                "text" to lines[i].take(MAX_SEARCH_LINE_CHARS),
                            ),
                        )
                    } else {
                        truncated = true
                    }
                    if (truncated && totalMatches >= maxMatches + 1) break@outer
                }
            }
        }

        return ok(
            FileResultPayload.encodeSearch(path = rel, matches = matches, totalMatches = totalMatches, truncated = truncated),
        )
    }

    private fun listFilesRecursive(dir: Path): List<Path> {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return emptyList()
        return try {
            Files.walk(dir, MAX_SEARCH_DEPTH)
                .use { s -> s.filter { Files.isRegularFile(it) }.sorted().collect(java.util.stream.Collectors.toList()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ list

    private fun listOp(path: Path, rel: String, p: FileToolSchema.Parsed): ToolResult {
        if (!Files.exists(path)) throw FileOpException(FileError.NOT_FOUND, "directory does not exist")
        if (!Files.isDirectory(path)) throw FileOpException(FileError.NOT_A_DIRECTORY, "path is a file, not a directory")

        val maxEntries = p.maxEntries?.coerceAtMost(MAX_LIST_ENTRIES) ?: MAX_LIST_ENTRIES
        val entries = mutableListOf<Map<String, Any?>>()
        var totalEntries = 0
        var truncated = false

        Files.list(path).use { dir ->
            val sorted = dir.sorted().collect(java.util.stream.Collectors.toList())
            for (entry in sorted) {
                totalEntries++
                if (entries.size < maxEntries) {
                    val isDir = Files.isDirectory(entry)
                    val size = if (isDir) 0L else Files.size(entry)
                    entries.add(mapOf("name" to entry.fileName.toString(), "type" to if (isDir) "dir" else "file", "sizeBytes" to size))
                } else {
                    truncated = true
                }
            }
        }

        return ok(
            FileResultPayload.encodeList(path = rel, entries = entries, totalEntries = totalEntries, truncated = truncated),
        )
    }

    // ------------------------------------------------------------------ stat

    private fun statOp(path: Path, rel: String): ToolResult {
        if (!Files.exists(path)) {
            return ok(
                FileResultPayload.encodeStat(path = rel, type = "missing", sizeBytes = 0L, lastModified = 0L, readable = false, writable = false),
            )
        }
        val isDir = Files.isDirectory(path)
        val size = if (isDir) 0L else Files.size(path)
        val mtime = try { Files.getLastModifiedTime(path).toMillis() } catch (e: Exception) { 0L }
        return ok(
            FileResultPayload.encodeStat(
                path = rel,
                type = if (isDir) "dir" else "file",
                sizeBytes = size,
                lastModified = mtime,
                readable = Files.isReadable(path),
                writable = Files.isWritable(path),
            ),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun ok(output: String): ToolResult = ToolResult(success = true, output = output)

    private fun resolverRel(path: Path): String {
        return try {
            val root = resolver.rootPath
            if (path == root) "." else normalizeDisplay(root.relativize(path).toString())
        } catch (e: Exception) {
            path.toString()
        }
    }

    /** Normalize a display path to use forward slashes (the model-facing path space). */
    private fun normalizeDisplay(p: String): String = p.replace('\\', '/')

    private fun isBinary(bytes: ByteArray): Boolean {
        // NUL-byte detection in the leading window (Preflight §17). Text almost never
        // contains NUL; this is the cheap, reliable binary sniff. No MIME/extension.
        val window = bytes.size.coerceAtMost(BINARY_SNIFF_BYTES)
        for (i in 0 until window) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    companion object {
        const val TOOL_NAME: String = "file"

        // Frozen bounds (Preflight §20; initial constants, product-tunable).
        const val MAX_FILE_READ_BYTES: Int = 1_048_576   // 1 MiB read into memory
        const val MAX_CONTENT_CHARS: Int = 30_000        // extracted text chars
        const val MAX_SEARCH_MATCHES: Int = 200          // search result cap
        const val MAX_LIST_ENTRIES: Int = 500            // list result cap
        const val MAX_ERROR_CHARS: Int = 2_000           // error text cap

        // Implementation-time details (Preflight §30, not architecture):
        const val BINARY_SNIFF_BYTES: Int = 8_192        // NUL-scan window
        const val MAX_SEARCH_DEPTH: Int = 16             // recursion depth cap
        const val MAX_SEARCH_LINE_CHARS: Int = 2_000     // per-match line display cap
    }
}

/** Internal carrier for a domain refusal inside an op, mapped to a structured error. */
internal class FileOpException(val code: FileError, message: String) : Exception(message)
