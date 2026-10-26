package com.androidagent.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Tool Registry contract, independent of the Agent Loop.
 */
class ToolRegistryTest {

    @Test
    fun `register exposes specs in registration order and finds tools`() {
        val registry = ToolRegistry()
        val alpha = RecordingTool(ToolSpec("alpha", "first"))
        val beta = RecordingTool(ToolSpec("beta", "second"))

        registry.register(alpha)
        registry.register(beta)

        assertEquals(listOf(ToolSpec("alpha", "first"), ToolSpec("beta", "second")), registry.specs())
        assertSame(alpha, registry.find("alpha"))
        assertNull(registry.find("missing"))
    }

    @Test
    fun `T14 duplicate tool registration fails fast without silent overwrite`() {
        val registry = ToolRegistry()
        val first = RecordingTool(ToolSpec("dup", "first"))
        val second = RecordingTool(ToolSpec("dup", "second"))
        registry.register(first)

        // Deterministic: every attempt is rejected, not just the first.
        repeat(3) {
            val error = assertThrows(IllegalArgumentException::class.java) { registry.register(second) }
            assertEquals("tool already registered: dup", error.message)
        }

        // No silent overwrite: the original registration is still the live one,
        // and the registry still holds exactly one entry.
        assertEquals(1, registry.specs().size)
        assertEquals("first", registry.specs().single().description)
        assertSame(first, registry.find("dup"))
    }

    @Test
    fun `T14b a rejected duplicate never becomes reachable`() = runBlocking {
        val registry = ToolRegistry()
        registry.register(RecordingTool(ToolSpec("dup", "first")) { ToolResult(true, "first") })
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(RecordingTool(ToolSpec("dup", "second")) { ToolResult(true, "second") })
        }

        // Dispatch resolves to the original tool, never the rejected one.
        val result = registry.dispatch(ToolCall("c1", "dup"), ToolApprovalPolicy.AllowAll)
        assertEquals(ToolResult(true, "first"), result)
    }

    @Test
    fun `blank tool name is rejected`() {
        val registry = ToolRegistry()
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(RecordingTool(ToolSpec("   ", "blank")))
        }
    }

    @Test
    fun `dispatch routes to the matching tool`() = runBlocking {
        val tool = RecordingTool(ToolSpec("echo", "")) { ToolResult(true, "out") }
        val registry = ToolRegistry().apply { register(tool) }

        val result = registry.dispatch(ToolCall("c1", "echo"), ToolApprovalPolicy.AllowAll)

        assertEquals(ToolResult(true, "out"), result)
        assertEquals(1, tool.executeCount)
    }

    @Test
    fun `dispatch reports an unknown tool without invoking anything`() = runBlocking {
        val registry = ToolRegistry()
        val result = registry.dispatch(ToolCall("c1", "ghost"), ToolApprovalPolicy.AllowAll)

        assertFalse(result.success)
        assertEquals("unknown tool: ghost", result.error)
    }

    @Test
    fun `dispatch converts a thrown exception into a structured failure`() = runBlocking {
        val tool = ThrowingTool(ToolSpec("boom", "")) { UnsupportedOperationException("nope") }
        val registry = ToolRegistry().apply { register(tool) }

        val result = registry.dispatch(ToolCall("c1", "boom"), ToolApprovalPolicy.AllowAll)

        assertFalse(result.success)
        assertEquals("tool 'boom' failed: UnsupportedOperationException", result.error)
        assertEquals(1, tool.executeCount)
    }

    @Test
    fun `policy is consulted before lookup and before execution`() = runBlocking {
        val registry = ToolRegistry()
        val denyAll = ToolApprovalPolicy { call -> ToolApproval.Deny("blocked: ${call.name}") }

        val unknownButDenied = registry.dispatch(ToolCall("c1", "ghost"), denyAll)
        assertEquals("tool call denied by policy: ghost (blocked: ghost)", unknownButDenied.error)
    }

    @Test
    fun `an empty agent core run with no tools still answers`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("no tools needed")))
        val outcome = AgentCore(provider, ToolRegistry(), MessageDurability.Noop).run("s", "hi")

        assertEquals(RunOutcome.Answer("no tools needed"), outcome)
    }

    @Test
    fun `maxSteps below one is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) { RunOptions(maxSteps = 0) }
    }
}