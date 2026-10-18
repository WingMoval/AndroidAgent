package com.androidagent.tool.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * V0 network tool argument schema: a JSON object with exactly the fields
 * `action` (string, must be "fetch") and `url` (string, non-blank).
 *
 * No method / headers / body / auth / cookie / destination / query / backend /
 * transport / principal / outputPath is accepted at V0.
 *
 * Every malformed input returns [ParseResult.Error] so the caller fails WITHOUT
 * touching the Fetcher (guaranteed fetcher call count = 0 on invalid arguments).
 */
internal object NetworkToolSchema {

    const val ACTION_FIELD: String = "action"
    const val URL_FIELD: String = "url"
    const val FETCH_ACTION: String = "fetch"

    sealed interface ParseResult {
        data class Ok(val url: String) : ParseResult
        data class Error(val reason: String) : ParseResult
    }

    fun parse(arguments: String): ParseResult {
        if (arguments.isBlank()) return ParseResult.Error("arguments are blank")

        val root = try {
            Json.parseToJsonElement(arguments)
        } catch (_: Exception) {
            return ParseResult.Error("arguments are not valid JSON")
        }
        val obj = root as? JsonObject
            ?: return ParseResult.Error("arguments must be a JSON object")

        val actionElement = obj[ACTION_FIELD]
            ?: return ParseResult.Error("missing 'action'")
        val action = (actionElement as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ParseResult.Error("'action' must be a string")
        if (action != FETCH_ACTION) {
            return ParseResult.Error("unsupported action '$action' (only 'fetch' is available)")
        }

        val urlElement = obj[URL_FIELD]
            ?: return ParseResult.Error("missing 'url'")
        val url = (urlElement as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ParseResult.Error("'url' must be a string")
        if (url.isBlank()) return ParseResult.Error("'url' must not be blank")

        return ParseResult.Ok(url)
    }
}
