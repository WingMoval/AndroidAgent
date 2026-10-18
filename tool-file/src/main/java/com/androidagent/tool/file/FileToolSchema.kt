package com.androidagent.tool.file

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * V0 file tool argument schema (AG.FILE-0 Preflight §12-14, FROZEN).
 *
 * A JSON object with `action` (one of read/search/list/stat) and `path`
 * (string, non-blank), plus op-specific fields:
 *   read   : offsetLines?, limitLines?, maxBytes?
 *   search : pattern (required), regex?, maxMatches?, contextLines?, caseSensitive?
 *   list   : maxEntries?
 *   stat   : (path only)
 *
 * Every malformed input returns [ParseResult.Error] so the caller fails WITHOUT
 * touching the filesystem (guaranteed file-op call count = 0 on invalid args).
 */
internal object FileToolSchema {

    const val ACTION_FIELD: String = "action"
    const val PATH_FIELD: String = "path"

    const val ACTION_READ: String = "read"
    const val ACTION_SEARCH: String = "search"
    const val ACTION_LIST: String = "list"
    const val ACTION_STAT: String = "stat"

    val VALID_ACTIONS: Set<String> = setOf(ACTION_READ, ACTION_SEARCH, ACTION_LIST, ACTION_STAT)

    sealed interface ParseResult {
        data class Ok(val parsed: Parsed) : ParseResult
        data class Error(val reason: String) : ParseResult
    }

    data class Parsed(
        val action: String,
        val path: String,
        // read
        val offsetLines: Int? = null,
        val limitLines: Int? = null,
        val maxBytes: Int? = null,
        // search
        val pattern: String? = null,
        val regex: Boolean = false,
        val maxMatches: Int? = null,
        val contextLines: Int = 0,
        val caseSensitive: Boolean = true,
        // list
        val maxEntries: Int? = null,
    )

    fun parse(arguments: String): ParseResult {
        if (arguments.isBlank()) return ParseResult.Error("arguments are blank")

        val root = try {
            Json.parseToJsonElement(arguments)
        } catch (_: Exception) {
            return ParseResult.Error("arguments are not valid JSON")
        }
        val obj = root as? JsonObject
            ?: return ParseResult.Error("arguments must be a JSON object")

        val action = stringField(obj, ACTION_FIELD, "missing 'action'")
            ?: return ParseResult.Error("missing 'action'")
        if (!action.isString || action.content.isNullOrBlank()) {
            return ParseResult.Error("'action' must be a non-blank string")
        }
        val actionStr = action.content
        if (actionStr !in VALID_ACTIONS) {
            return ParseResult.Error("unsupported action '$actionStr' (only ${VALID_ACTIONS.sorted()} are available)")
        }

        val pathEl = obj[PATH_FIELD]
            ?: return ParseResult.Error("missing 'path'")
        val path = (pathEl as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ParseResult.Error("'path' must be a string")
        if (path.isBlank()) return ParseResult.Error("'path' must not be blank")

        // op-specific fields (local vars, then constructed once)
        var offsetLines: Int? = null
        var limitLines: Int? = null
        var maxBytes: Int? = null
        var pattern: String? = null
        var regex: Boolean = false
        var maxMatches: Int? = null
        var contextLines: Int = 0
        var caseSensitive: Boolean = true
        var maxEntries: Int? = null

        when (actionStr) {
            ACTION_READ -> {
                offsetLines = intField(obj, "offsetLines")
                limitLines = intField(obj, "limitLines")
                maxBytes = intField(obj, "maxBytes")
                if (offsetLines != null && offsetLines < 0)
                    return ParseResult.Error("'offsetLines' must be >= 0")
                if (limitLines != null && limitLines <= 0)
                    return ParseResult.Error("'limitLines' must be > 0")
                if (maxBytes != null && maxBytes <= 0)
                    return ParseResult.Error("'maxBytes' must be > 0")
            }
            ACTION_SEARCH -> {
                val pat = (obj["pattern"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return ParseResult.Error("search requires a 'pattern' string")
                if (pat.isBlank()) return ParseResult.Error("'pattern' must not be blank")
                pattern = pat
                regex = boolField(obj, "regex", false)
                maxMatches = intField(obj, "maxMatches")
                if (maxMatches != null && maxMatches <= 0)
                    return ParseResult.Error("'maxMatches' must be > 0")
                val ctx = intField(obj, "contextLines", 0)
                if (ctx != null && ctx < 0) return ParseResult.Error("'contextLines' must be >= 0")
                contextLines = ctx ?: 0
                caseSensitive = boolField(obj, "caseSensitive", true)
            }
            ACTION_LIST -> {
                maxEntries = intField(obj, "maxEntries")
                if (maxEntries != null && maxEntries <= 0)
                    return ParseResult.Error("'maxEntries' must be > 0")
            }
            ACTION_STAT -> { /* path only */ }
        }

        val parsed = Parsed(
            action = actionStr,
            path = path,
            offsetLines = offsetLines,
            limitLines = limitLines,
            maxBytes = maxBytes,
            pattern = pattern,
            regex = regex,
            maxMatches = maxMatches,
            contextLines = contextLines,
            caseSensitive = caseSensitive,
            maxEntries = maxEntries,
        )
        return ParseResult.Ok(parsed)
    }

    private fun stringField(obj: JsonObject, key: String, missingMsg: String): JsonPrimitive? =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }

    private fun intField(obj: JsonObject, key: String, default: Int? = null): Int? {
        val el = obj[key] ?: return default
        val p = el as? JsonPrimitive ?: return default
        val v = p.intOrNull ?: return default
        return v
    }

    private fun boolField(obj: JsonObject, key: String, default: Boolean): Boolean {
        val el = obj[key] ?: return default
        val p = el as? JsonPrimitive ?: return default
        return p.booleanOrNull ?: default
    }
}
