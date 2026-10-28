package com.androidagent.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE — Track A: SAF Resource Backend.
 *
 * Minimal Storage Access Framework support for user-selected files/folders.
 * Uses ContentResolver + DocumentsContract directly (no androidx.documentfile
 * dependency needed).
 *
 * READ-ONLY: read, list, stat. Matches the FileTool contract.
 */

/** A user-selected SAF resource (file or folder) with persistable URI permission. */
data class SafResource(
    val uri: Uri,
    val displayName: String,
    val isFolder: Boolean,
) {
    val id: String get() = uri.toString()
}

/**
 * Stores SAF resource grants in the app's private filesDir.
 * URI permissions are persisted via Context.takePersistableUriPermission (system).
 */
object SafResourceStore {

    private const val FILE_NAME = "saf_resources.json"

    fun load(context: Context): List<SafResource> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            file.readText().lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size != 3) return@mapNotNull null
                    val uri = Uri.parse(parts[0])
                    if (!uri.isHierarchical) return@mapNotNull null
                    SafResource(uri, parts[1].replace("\\|", "|"), parts[2].toBoolean())
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, resources: List<SafResource>) {
        val file = File(context.filesDir, FILE_NAME)
        val text = resources.joinToString("\n") {
            "${it.uri}|${it.displayName.replace("|", "\\|")}|${it.isFolder}"
        }
        try { file.writeText(text + "\n") } catch (e: Exception) { }
    }

    fun add(context: Context, resource: SafResource): List<SafResource> {
        val current = load(context).toMutableList()
        if (current.none { it.uri == resource.uri }) {
            current.add(resource)
            save(context, current)
        }
        return current
    }

    fun remove(context: Context, uri: Uri): List<SafResource> {
        val current = load(context).toMutableList()
        current.removeAll { it.uri == uri }
        save(context, current)
        return current
    }
}

/**
 * SAF file operations via ContentResolver + DocumentsContract.
 * READ-ONLY. Never throws.
 */
object SafFileOps {

    sealed interface SafResult {
        data class Ok(val data: String) : SafResult
        data class Err(val reason: String) : SafResult
    }

    /** Reads a SAF file. Bounded: maxBytes (1 MiB default). */
    fun read(context: Context, uri: Uri, maxBytes: Int = 1_048_576): SafResult {
        return try {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(uri) ?: return SafResult.Err("OPEN_FAILED")
            val bytes = readBounded(input, maxBytes)
            // Binary sniff: NUL in first 8KB
            val sniffLen = minOf(bytes.size, 8192)
            for (i in 0 until sniffLen) {
                if (bytes[i] == 0.toByte()) return SafResult.Err("BINARY")
            }
            val text = String(bytes, Charsets.UTF_8)
            val capped = if (text.length > 30_000) text.take(30_000) + "\n... [truncated]" else text
            SafResult.Ok(capped)
        } catch (e: SecurityException) {
            SafResult.Err("PERMISSION_DENIED")
        } catch (e: Exception) {
            SafResult.Err("READ_FAILED")
        }
    }

    /** Lists children of a SAF folder tree URI. Bounded: maxEntries (500). */
    fun list(context: Context, treeUri: Uri, maxEntries: Int = 500): SafResult {
        return try {
            val resolver = context.contentResolver
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val cursor = resolver.query(childrenUri, null, null, null, null)
                ?: return SafResult.Err("QUERY_FAILED")

            val names = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val docs = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val types = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            val entries = mutableListOf<String>()
            var count = 0
            while (cursor.moveToNext() && count < maxEntries) {
                val name = if (names >= 0) cursor.getString(names) ?: "unknown" else "unknown"
                val mimeType = if (types >= 0) cursor.getString(types) ?: "" else ""
                val type = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) "directory" else "file"
                val size = 0L // Size requires a separate query per doc; skip for v1
                entries.add("$name|$type|$size")
                count++
            }
            cursor.close()
            SafResult.Ok(entries.joinToString("\n"))
        } catch (e: SecurityException) {
            SafResult.Err("PERMISSION_DENIED")
        } catch (e: Exception) {
            SafResult.Err("LIST_FAILED")
        }
    }

    /** Gets metadata for a SAF resource. */
    fun stat(context: Context, uri: Uri): SafResult {
        return try {
            val resolver = context.contentResolver
            val cursor = resolver.query(uri, null, null, null, null)
                ?: return SafResult.Err("QUERY_FAILED")
            cursor.use { c ->
                if (!c.moveToFirst()) return SafResult.Err("NOT_FOUND")
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "unknown" else "unknown"
                val mime = if (mimeIdx >= 0) c.getString(mimeIdx) ?: "" else ""
                val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                val type = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) "directory" else "file"
                SafResult.Ok("name=$name\ntype=$type\nsizeBytes=$size")
            }
        } catch (e: SecurityException) {
            SafResult.Err("PERMISSION_DENIED")
        } catch (e: Exception) {
            SafResult.Err("STAT_FAILED")
        }
    }

    /** Resolves a child document URI within a tree. */
    fun resolveChild(context: Context, treeUri: Uri, childName: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = resolver.query(childrenUri, null, null, null, null) ?: return null
            cursor.use { c ->
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val docsIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                    if (name == childName && docsIdx >= 0) {
                        val childDocId = c.getString(docsIdx) ?: return null
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(minOf(maxBytes, 8192))
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        input.close()
        return output.toByteArray()
    }
}
