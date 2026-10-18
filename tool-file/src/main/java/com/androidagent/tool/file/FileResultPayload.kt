package com.androidagent.tool.file

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deterministic, machine- and model-readable result payloads (Preflight §13-14).
 *
 * The AG Core ToolResult contract has no structured fields, so all facts live in
 * `output` as JSON — same convention as the shell and network tools. Each action
 * echoes `action` + a canonical root-relative `path`, plus its own fields.
 */
internal object FileResultPayload {

    fun encodeRead(
        path: String,
        content: String,
        startLine: Int,
        totalLines: Int?,
        truncated: Boolean,
        bytesRead: Int,
        totalBytes: Long,
        isBinary: Boolean,
    ): String = buildJsonObject {
        put("action", JsonPrimitive(FileToolSchema.ACTION_READ))
        put("path", JsonPrimitive(path))
        put("content", JsonPrimitive(content))
        put("startLine", JsonPrimitive(startLine))
        put("totalLines", totalLines?.let { JsonPrimitive(it) } ?: JsonNull)
        put("truncated", JsonPrimitive(truncated))
        put("bytesRead", JsonPrimitive(bytesRead))
        put("totalBytes", JsonPrimitive(totalBytes))
        put("isBinary", JsonPrimitive(isBinary))
    }.toString()

    fun encodeSearch(
        path: String,
        matches: List<Map<String, Any?>>,
        totalMatches: Int,
        truncated: Boolean,
    ): String = buildJsonObject {
        put("action", JsonPrimitive(FileToolSchema.ACTION_SEARCH))
        put("path", JsonPrimitive(path))
        put("matches", matches.toJsonArray())
        put("totalMatches", JsonPrimitive(totalMatches))
        put("truncated", JsonPrimitive(truncated))
    }.toString()

    fun encodeList(
        path: String,
        entries: List<Map<String, Any?>>,
        totalEntries: Int,
        truncated: Boolean,
    ): String = buildJsonObject {
        put("action", JsonPrimitive(FileToolSchema.ACTION_LIST))
        put("path", JsonPrimitive(path))
        put("entries", entries.toJsonArray())
        put("totalEntries", JsonPrimitive(totalEntries))
        put("truncated", JsonPrimitive(truncated))
    }.toString()

    fun encodeStat(
        path: String,
        type: String,
        sizeBytes: Long,
        lastModified: Long,
        readable: Boolean,
        writable: Boolean,
    ): String = buildJsonObject {
        put("action", JsonPrimitive(FileToolSchema.ACTION_STAT))
        put("path", JsonPrimitive(path))
        put("type", JsonPrimitive(type))
        put("sizeBytes", JsonPrimitive(sizeBytes))
        put("lastModified", JsonPrimitive(lastModified))
        put("readable", JsonPrimitive(readable))
        put("writable", JsonPrimitive(writable))
    }.toString()

    private fun List<Map<String, Any?>>.toJsonArray(): JsonArray = buildJsonArray {
        for (m in this@toJsonArray) {
            add(buildJsonObject {
                for ((k, v) in m) {
                    when (v) {
                        null -> put(k, JsonNull)
                        is String -> put(k, JsonPrimitive(v))
                        is Int -> put(k, JsonPrimitive(v))
                        is Long -> put(k, JsonPrimitive(v))
                        is Boolean -> put(k, JsonPrimitive(v))
                    }
                }
            })
        }
    }
}
