package com.androidagent.core

import java.util.concurrent.CancellationException

/**
 * Holds the Tool objects AG Core is allowed to call, and dispatches calls to them.
 *
 * The registry owns exactly one responsibility: turning a model's request into a
 * [ToolResult], or into a structured failure the Agent Loop can hand back to the
 * model. It contains no agent policy and no execution protocol.
 *
 * Discovery is explicit registration only. There is no reflection, classpath
 * scanning, plugin loading, or remote tool transport.
 */
class ToolRegistry {

    private val lock = Any()
    private val tools = LinkedHashMap<String, Tool>()

    /**
     * Registers [tool] under its own spec name.
     *
     * The usual shape is to register every tool during composition, before any
     * Run. The lock exists so that deviating from that shape cannot corrupt the
     * map while concurrent Runs are reading it.
     *
     * @throws IllegalArgumentException if the name is blank or already taken.
     *   Duplicate registration is a programming error in the composition root,
     *   so it fails fast here instead of silently shadowing a tool mid-Run.
     */
    fun register(tool: Tool) = synchronized(lock) {
        val name = tool.spec.name
        require(name.isNotBlank()) { "tool name must not be blank" }
        require(!tools.containsKey(name)) { "tool already registered: $name" }
        tools[name] = tool
    }

    /** Returns the tool registered under [name], or null when there is none. */
    fun find(name: String): Tool? = synchronized(lock) { tools[name] }

    /** Current specifications, in registration order. */
    fun specs(): List<ToolSpec> = synchronized(lock) { tools.values.map { it.spec } }

    /**
     * Runs one call through [policy] and the matching tool.
     *
     * This never propagates a tool implementation failure to the caller: every
     * rejection path returns a `success = false` [ToolResult] so the Agent Loop
     * stays in control. [CancellationException] is deliberately rethrown because
     * it is coroutine control flow, not a tool failure.
     *
     * Order is policy, then lookup, then execute. Policy is consulted first so
     * that "nothing runs without approval" holds unconditionally, regardless of
     * what happens to be registered.
     */
    suspend fun dispatch(call: ToolCall, policy: ToolApprovalPolicy): ToolResult {
        when (val approval = policy.decide(call)) {
            is ToolApproval.Deny -> return ToolResult(
                success = false,
                output = "",
                error = "tool call denied by policy: ${call.name} (${approval.reason})",
            )

            ToolApproval.Allow -> Unit
        }

        val tool = find(call.name) ?: return ToolResult(
            success = false,
            output = "",
            error = "unknown tool: ${call.name}",
        )

        return try {
            tool.execute(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            // Preserve interrupt-driven shutdown, which a swallowed
            // InterruptedException would silently break.
            Thread.currentThread().interrupt()
            ToolResult(
                success = false,
                output = "",
                error = "tool '${call.name}' interrupted",
            )
        } catch (e: Exception) {
            // Report the failure type only. The implementation's own message and
            // stack trace may carry host detail that has no business reaching the
            // model as history.
            ToolResult(
                success = false,
                output = "",
                error = "tool '${call.name}' failed: ${e::class.simpleName ?: "unknown"}",
            )
        }
    }
}