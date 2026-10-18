package com.androidagent.tool.network

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deterministic, machine-readable and model-readable result payload (Preflight §4.5).
 *
 * Encodes the frozen fetch result JSON into the single ToolResult.output String
 * channel (the AG Core ToolResult contract has no structured fields, so all facts
 * live in `output` as JSON — same convention as the shell tool's payload).
 */
internal object NetworkResultPayload {

    fun encode(
        requestedUrl: String,
        finalUrl: String,
        httpStatus: Int,
        contentType: String?,
        content: String,
        truncated: Boolean,
        bytesRead: Int,
        redirectCount: Int,
        supportedType: Boolean,
    ): String = buildJsonObject {
        put("action", JsonPrimitive(NetworkToolSchema.FETCH_ACTION))
        put("requestedUrl", JsonPrimitive(requestedUrl))
        put("finalUrl", JsonPrimitive(finalUrl))
        put("httpStatus", JsonPrimitive(httpStatus))
        put("contentType", contentType?.let { JsonPrimitive(it) } ?: JsonNull)
        put("content", JsonPrimitive(content))
        put("truncated", JsonPrimitive(truncated))
        put("bytesRead", JsonPrimitive(bytesRead))
        put("redirectCount", JsonPrimitive(redirectCount))
        if (!supportedType) {
            put("note", JsonPrimitive("response content type is not a supported text type; body not returned"))
        }
    }.toString()

    /** Encodes a structured mechanism failure (used for the error path). */
    fun encodeError(code: String, message: String): String = buildJsonObject {
        put("error", JsonPrimitive(code))
        put("message", JsonPrimitive(message.take(NetworkToolImpl.MAX_ERROR_CHARS)))
    }.toString()
}
