package com.androidagent.core

/**
 * The declaration a Tool publishes so a model can discover it.
 *
 * AG Core only carries the specification; it never interprets arguments.
 */
data class ToolSpec(
    val name: String,
    val description: String,
)

/**
 * A model's request to invoke one tool.
 *
 * [arguments] is deliberately an opaque provider-neutral string at v0. AG.0
 * ships no tool that consumes arguments, so introducing a typed argument schema
 * now would be speculative. A concrete ModelProvider renders it into whatever
 * format its wire protocol requires.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String = "",
)

/**
 * The outcome of a single tool invocation.
 *
 * [success] describes the tool *mechanism* only: did the invocation mechanism
 * complete normally? It is deliberately NOT a statement about domain success.
 *
 * A shell tool whose command exits 7 still returns `success = true` as long as
 * the command ran and stdout/stderr/exit code were captured. Only unknown tool,
 * policy denial, or a failure inside the tool implementation itself produce
 * `success = false`.
 */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
)

/**
 * A capability the model may invoke.
 *
 * Implementations live outside AG Core (dependency inversion). AG Core knows
 * this interface and nothing about any concrete tool.
 */
interface Tool {
    val spec: ToolSpec

    /**
     * Executes one call.
     *
     * Implementations report their own domain failures through [ToolResult]
     * rather than by throwing. Throwing is reserved for mechanism-level
     * breakage, which the registry converts into `success = false`.
     */
    suspend fun execute(call: ToolCall): ToolResult
}