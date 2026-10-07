package com.androidagent.tool.androidshell

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * v0 tool argument schema: a JSON object with a single string field `command`.
 *
 * No cwd / env / timeout / stdin / PTY / stream / background is accepted at v0
 * (AT stable contract does not require any of them yet).
 */
internal object ShellToolSchema {

    const val COMMAND_FIELD: String = "command"

    sealed interface ParseResult {
        data class Ok(val command: String) : ParseResult

        data class Error(val reason: String) : ParseResult
    }

    /**
     * Parses the opaque [ToolCall.arguments] string. Returns [ParseResult.Error]
     * for every malformed input so the caller can fail WITHOUT touching the AT
     * runtime (guaranteed executor call count = 0 on invalid arguments).
     */
    fun parse(arguments: String): ParseResult {
        if (arguments.isBlank()) {
            return ParseResult.Error("arguments are blank")
        }
        val root = try {
            Json.parseToJsonElement(arguments)
        } catch (_: Exception) {
            return ParseResult.Error("arguments are not valid JSON")
        }
        val obj = root as? JsonObject
            ?: return ParseResult.Error("arguments must be a JSON object")

        val commandElement = obj[COMMAND_FIELD]
            ?: return ParseResult.Error("missing 'command'")

        val commandPrimitive = commandElement as? JsonPrimitive
        if (commandPrimitive == null || !commandPrimitive.isString) {
            return ParseResult.Error("'command' must be a string")
        }
        val command = commandPrimitive.content

        if (command.isBlank()) {
            return ParseResult.Error("'command' must not be blank")
        }
        return ParseResult.Ok(command)
    }
}