package com.androidagent.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T1 - T10 and T13: the Agent Loop contract.
 *
 * Everything here is deterministic and offline.
 */
class AgentLoopTest {

    // ---------------------------------------------------------------- T1

    @Test
    fun `T1 user to model to final answer`() = runBlocking {
        val tool = RecordingTool(ToolSpec("unused", "never called"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("42")))
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "what is 6*7?")

        assertEquals(RunOutcome.Answer("42"), outcome)
        assertEquals(1, provider.callCount)
        assertEquals(0, tool.executeCount, "no tool may run on a direct answer")
        assertEquals(
            listOf(
                UserMessage("what is 6*7?"),
                ModelMessage("42"),
            ),
            core.session("s1").history(),
        )
    }

    // ---------------------------------------------------------------- T2

    @Test
    fun `T2 model to tool to model to final answer`() = runBlocking {
        val tool = RecordingTool(ToolSpec("echo", "echoes")) {
            ToolResult(success = true, output = "echoed")
        }
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "echo", "hello"))),
                ModelTurn.FinalAnswer("done: echoed"),
            )
        )
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "echo hello")

        assertEquals(RunOutcome.Answer("done: echoed"), outcome)
        assertEquals(2, provider.callCount, "exactly two model turns")
        assertEquals(1, tool.executeCount, "exactly one tool invocation")

        // The ToolResult reached the second model request.
        val second = provider.requests[1]
        val toolMessage = second.history.filterIsInstance<ToolResultMessage>().single()
        assertEquals("c1", toolMessage.toolCallId)
        assertEquals("echo", toolMessage.toolName)
        assertEquals(ToolResult(success = true, output = "echoed"), toolMessage.result)

        // The tool spec was advertised to the model.
        assertEquals(listOf(ToolSpec("echo", "echoes")), second.tools)

        // The call's arguments survive dispatch into the tool unchanged.
        assertEquals("hello", tool.calls.single().arguments)

        // Full history shape.
        assertEquals(
            listOf(
                UserMessage("echo hello"),
                ModelMessage("", listOf(ToolCall("c1", "echo", "hello"))),
                ToolResultMessage("c1", "echo", ToolResult(true, "echoed")),
                ModelMessage("done: echoed"),
            ),
            core.session("s1").history(),
        )
    }

    // ---------------------------------------------------------------- T3

    @Test
    fun `T3 multiple tool calls execute serially in model order`() = runBlocking {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        fun tool(name: String) = RecordingTool(ToolSpec(name, ""), started, completed)
        val alpha = tool("alpha")
        val beta = tool("beta")
        val gamma = tool("gamma")
        val registry = ToolRegistry().apply {
            register(alpha)
            register(beta)
            register(gamma)
        }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(
                    listOf(
                        ToolCall("c1", "alpha"),
                        ToolCall("c2", "beta"),
                        ToolCall("c3", "gamma"),
                    )
                ),
                ModelTurn.FinalAnswer("all three done"),
            )
        )
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "run all three")

        assertEquals(RunOutcome.Answer("all three done"), outcome)
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            started,
            "tools must start in the order the model returned them",
        )
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            completed,
            "each tool must finish before the next one starts, i.e. execution is serial",
        )
        assertEquals(1, alpha.executeCount)
        assertEquals(1, beta.executeCount)
        assertEquals(1, gamma.executeCount)

        // All three results were appended before the next model turn.
        val second = provider.requests[1]
        val results = second.history.filterIsInstance<ToolResultMessage>()
        assertEquals(listOf("c1", "c2", "c3"), results.map { it.toolCallId })

        // Tool specs are advertised in registration order.
        assertEquals(
            listOf(ToolSpec("alpha", ""), ToolSpec("beta", ""), ToolSpec("gamma", "")),
            second.tools,
        )
    }

    // ---------------------------------------------------------------- T4

    @Test
    fun `T4 unknown tool becomes a structured result the model can react to`() = runBlocking {
        val registry = ToolRegistry()
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "no_such_tool"))),
                ModelTurn.FinalAnswer("I will answer without tools"),
            )
        )
        val core = AgentCore(provider, registry)

        // Must not throw.
        val outcome = core.run("s1", "use a tool that does not exist")

        assertEquals(RunOutcome.Answer("I will answer without tools"), outcome)
        val message = core.session("s1").history().filterIsInstance<ToolResultMessage>().single()
        assertFalse(message.result.success)
        assertEquals("unknown tool: no_such_tool", message.result.error)
        assertEquals("", message.result.output)
    }

    // ---------------------------------------------------------------- T5

    @Test
    fun `T5 tool exception is contained and does not leak implementation detail`() = runBlocking {
        val secret = "sensitive-host-detail-abc123"
        val tool = ThrowingTool(ToolSpec("boom", "")) { IllegalStateException(secret) }
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "boom"))),
                ModelTurn.FinalAnswer("recovered from the failure"),
            )
        )
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "trigger a broken tool")

        assertEquals(RunOutcome.Answer("recovered from the failure"), outcome)
        assertEquals(1, tool.executeCount, "the tool did run before failing")

        val message = core.session("s1").history().filterIsInstance<ToolResultMessage>().single()
        assertFalse(message.result.success)
        assertEquals("tool 'boom' failed: IllegalStateException", message.result.error)
        assertFalse(
            message.result.error!!.contains(secret),
            "the implementation's exception message must not reach the model",
        )
    }

    // ---------------------------------------------------------------- T6

    @Test
    fun `T6 policy deny blocks execution and returns an explicit deny result`() = runBlocking {
        val tool = RecordingTool(ToolSpec("danger", "dangerous"))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "danger"))),
                ModelTurn.FinalAnswer("understood, skipping it"),
            )
        )
        val denyPolicy = ToolApprovalPolicy { ToolApproval.Deny("not permitted in this run") }
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "run the dangerous tool", RunOptions(approvalPolicy = denyPolicy))

        assertEquals(RunOutcome.Answer("understood, skipping it"), outcome)
        assertEquals(0, tool.executeCount, "a denied call must never reach execute")

        val message = core.session("s1").history().filterIsInstance<ToolResultMessage>().single()
        assertFalse(message.result.success)
        assertEquals(
            "tool call denied by policy: danger (not permitted in this run)",
            message.result.error,
        )
    }

    @Test
    fun `T6b default policy is allow-all`() = runBlocking {
        val tool = RecordingTool(ToolSpec("safe", ""))
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "safe"))),
                ModelTurn.FinalAnswer("ok"),
            )
        )

        AgentCore(provider, registry).run("s1", "go")

        assertEquals(1, tool.executeCount)
    }

    // ---------------------------------------------------------------- T7

    @Test
    fun `T7 maxSteps stops a runaway tool loop and releases the session`() = runBlocking {
        val tool = RecordingTool(ToolSpec("again", ""))
        val registry = ToolRegistry().apply { register(tool) }
        // The model never answers while it still has budget: it only asks for
        // another tool call. The third scripted turn is a final answer, which the
        // first Run must never reach.
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "again"))),
                ModelTurn.ToolCalls(listOf(ToolCall("c2", "again"))),
                ModelTurn.FinalAnswer("recovered"),
            )
        )
        val sink = RecordingEventSink()
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "loop forever", RunOptions(maxSteps = 2, eventSink = sink))

        assertEquals(RunOutcome.Failure(RunFailure.MaxStepsReached(2)), outcome)
        assertEquals(2, provider.callCount, "provider is called exactly maxSteps times")
        assertEquals(2, tool.executeCount, "one tool run per completed turn")
        assertTrue(sink.events.contains(AgentEvent.RunFailed(RunFailure.MaxStepsReached(2))))

        // Ownership was released, so the very same Session runs again immediately.
        assertEquals(RunOutcome.Answer("recovered"), core.run("s1", "try again"))
    }

    // ---------------------------------------------------------------- T8

    @Test
    fun `T8 provider failure ends the run and does not leave the session busy`() = runBlocking {
        val provider = FailsOnceModelProvider(
            failure = IllegalStateException("provider offline"),
            then = ModelTurn.FinalAnswer("second attempt worked"),
        )
        val registry = ToolRegistry()
        val sink = RecordingEventSink()
        val core = AgentCore(provider, registry)

        val first = core.run("s1", "hello", RunOptions(eventSink = sink))

        assertEquals(RunOutcome.Failure(RunFailure.ProviderFailure("IllegalStateException")), first)
        assertTrue(sink.events.contains(AgentEvent.RunFailed(RunFailure.ProviderFailure("IllegalStateException"))))
        assertEquals(
            AgentEvent.RunFinished(first),
            sink.events.last(),
            "a failed run still emits its terminal event",
        )

        // Same session, immediately reusable: ownership was released.
        val second = core.run("s1", "hello again")
        assertEquals(RunOutcome.Answer("second attempt worked"), second)
    }

    // ---------------------------------------------------------------- T9

    @Test
    fun `T9 event order is deterministic for a direct answer`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("hello")))
        val sink = RecordingEventSink()
        AgentCore(provider, ToolRegistry()).run("s1", "hi", RunOptions(eventSink = sink))

        assertEquals(
            listOf(
                AgentEvent.RunStarted("s1"),
                AgentEvent.ModelTurnStarted(1),
                AgentEvent.ModelTurnFinished(1, ModelTurn.FinalAnswer("hello")),
                AgentEvent.AnswerProduced("hello"),
                AgentEvent.RunFinished(RunOutcome.Answer("hello")),
            ),
            sink.events,
        )
    }

    @Test
    fun `T9b event order is deterministic across a tool call`() = runBlocking {
        val tool = RecordingTool(ToolSpec("echo", "")) { ToolResult(true, "out") }
        val registry = ToolRegistry().apply { register(tool) }
        val call = ToolCall("c1", "echo")
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(call)),
                ModelTurn.FinalAnswer("finished"),
            )
        )
        val sink = RecordingEventSink()
        AgentCore(provider, registry).run("s1", "go", RunOptions(eventSink = sink))

        assertEquals(
            listOf(
                AgentEvent.RunStarted("s1"),
                AgentEvent.ModelTurnStarted(1),
                AgentEvent.ModelTurnFinished(1, ModelTurn.ToolCalls(listOf(call))),
                AgentEvent.ToolCallStarted(call),
                AgentEvent.ToolCallFinished(call, ToolResult(true, "out")),
                AgentEvent.ModelTurnStarted(2),
                AgentEvent.ModelTurnFinished(2, ModelTurn.FinalAnswer("finished")),
                AgentEvent.AnswerProduced("finished"),
                AgentEvent.RunFinished(RunOutcome.Answer("finished")),
            ),
            sink.events,
        )
    }

    // ---------------------------------------------------------------- T10

    @Test
    fun `T10 session history carries across runs`() = runBlocking {
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.FinalAnswer("acknowledged"),
                ModelTurn.FinalAnswer("your name is A"),
            )
        )
        val core = AgentCore(provider, ToolRegistry())

        core.run("s1", "my name is A")
        core.run("s1", "what is my name?")

        val secondRequest = provider.requests[1]
        assertTrue(
            secondRequest.history.contains(UserMessage("my name is A")),
            "the second run must observe the first run's user message",
        )
        assertTrue(
            secondRequest.history.contains(ModelMessage("acknowledged")),
            "the second run must observe the first run's model turn",
        )
        assertEquals(
            listOf(
                UserMessage("my name is A"),
                ModelMessage("acknowledged"),
                UserMessage("what is my name?"),
            ),
            secondRequest.history,
        )
    }

    @Test
    fun `T10b instructions and tool specs reach the provider`() = runBlocking {
        val registry = ToolRegistry().apply { register(RecordingTool(ToolSpec("t", "a tool"))) }
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("done")))
        val core = AgentCore(provider, registry)

        core.run("s1", "hi", RunOptions(instructions = "be terse"))

        val request = provider.requests.single()
        assertEquals("be terse", request.instructions)
        assertEquals("s1", request.sessionId)
        assertEquals(listOf(ToolSpec("t", "a tool")), request.tools)
    }

    @Test
    fun `T10c an event sink that throws becomes a runtime failure`() = runBlocking {
        val provider = ScriptedModelProvider(listOf(ModelTurn.FinalAnswer("fine now")))
        val core = AgentCore(provider, ToolRegistry())

        // The very first event (RunStarted) throws, so the provider is never asked.
        val outcome = core.run("s1", "hi", RunOptions(eventSink = ThrowingEventSink()))
        assertEquals(RunOutcome.Failure(RunFailure.RuntimeFailure("IllegalStateException")), outcome)
        assertEquals(0, provider.callCount)

        // Session released despite the abort.
        assertEquals(RunOutcome.Answer("fine now"), core.run("s1", "again"))
    }

    // ---------------------------------------------------------------- T13

    @Test
    fun `T13 tool mechanism success is independent of domain status`() = runBlocking {
        // A shell-like tool: the command ran fine, the domain outcome was not.
        val tool = RecordingTool(ToolSpec("shell", "runs a command")) {
            ToolResult(
                success = true,
                output = "stdout:\nhello\nstderr:\nboom\nexitCode=7",
            )
        }
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "shell", "false"))),
                ModelTurn.FinalAnswer("the command failed with exit code 7"),
            )
        )
        val core = AgentCore(provider, registry)

        val outcome = core.run("s1", "run false")

        // The invocation mechanism succeeded, so ToolResult.success is true even
        // though the domain result is a non-zero exit.
        val message = core.session("s1").history().filterIsInstance<ToolResultMessage>().single()
        assertTrue(message.result.success, "a non-zero domain exit is not a mechanism failure")
        assertNull(message.result.error)
        assertTrue(message.result.output.contains("exitCode=7"))

        // The Run itself succeeded: the model produced an answer.
        assertEquals(RunOutcome.Answer("the command failed with exit code 7"), outcome)
    }

    @Test
    fun `T13b mechanism failure is the opposite case`() = runBlocking {
        val tool = RecordingTool(ToolSpec("shell", "")) {
            ToolResult(success = false, output = "", error = "could not reach device")
        }
        val registry = ToolRegistry().apply { register(tool) }
        val provider = ScriptedModelProvider(
            listOf(
                ModelTurn.ToolCalls(listOf(ToolCall("c1", "shell"))),
                ModelTurn.FinalAnswer("I could not run that"),
            )
        )
        val core = AgentCore(provider, registry)

        core.run("s1", "run it")

        val message = core.session("s1").history().filterIsInstance<ToolResultMessage>().single()
        assertFalse(message.result.success, "a genuine mechanism failure reports success = false")
        assertEquals("could not reach device", message.result.error)
    }
}