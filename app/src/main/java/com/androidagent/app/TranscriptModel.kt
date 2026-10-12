package com.androidagent.app

import com.androidagent.core.RunFailure
import com.androidagent.core.ToolCall
import com.androidagent.core.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AGA.OBSERVABILITY-0 — the Host transcript, pure and Android-free.
 *
 * It turns the two observation channels of one Session into a displayable
 * transcript:
 *
 *   - the [com.androidagent.core.ModelStreamObserver] stream (reasoning /
 *     content deltas), and
 *   - the [com.androidagent.core.AgentEvent] stream (tool calls, results,
 *     answers, failures).
 *
 * Ownership: this is Host/Observability. It adds no Agent semantics; it only
 * records and renders. The UI is a consumer of [snapshot] / [render].
 *
 * Semantics, frozen for this Step:
 *   - Thinking and Thought are the SAME block: reasoning deltas grow a
 *     Thinking block, and reasoning completion flips that block to Thought.
 *     No second block is ever inserted.
 *   - Assistant content grows one block per model turn from content deltas.
 *   - A final answer marks the existing Assistant block as final; it never
 *     appends a duplicate block when the answer already streamed.
 *   - A model with no reasoning output produces no Thinking/Thought block.
 */
class TranscriptModel {

    sealed interface Block {

        /** The user's submitted message. */
        data class User(val text: String) : Block

        /** One reasoning block: Thinking while streaming, Thought once done. */
        data class Thinking(val text: String, val phase: Phase) : Block {
            enum class Phase { THINKING, THOUGHT }
        }

        /** One formal model output block. [final] marks the Run's final answer. */
        data class Assistant(val text: String, val final: Boolean) : Block

        /** One dispatched tool action. */
        data class Shell(val command: String) : Block

        /** One tool result. */
        data class Result(val text: String) : Block

        /** The Run ended without an answer. */
        data class Failure(val text: String) : Block
    }

    private val blocks = mutableListOf<Block>()

    /** The step of the most recent model turn (0 before the first turn). */
    var lastStep: Int = 0
        private set

    /** Immutable snapshot of the current transcript. */
    fun snapshot(): List<Block> = blocks.toList()

    // ---------------------------------------------------------- stream channel

    fun onReasoningStarted() {
        if (blocks.lastOrNull() !is Block.Thinking) {
            blocks += Block.Thinking("", Block.Thinking.Phase.THINKING)
        }
    }

    fun onReasoningDelta(text: String) {
        val last = blocks.lastOrNull()
        if (last is Block.Thinking) {
            blocks[blocks.size - 1] = last.copy(text = last.text + text)
        } else {
            blocks += Block.Thinking(text, Block.Thinking.Phase.THINKING)
        }
    }

    fun onReasoningFinished() {
        val last = blocks.lastOrNull()
        if (last is Block.Thinking) {
            blocks[blocks.size - 1] = last.copy(phase = Block.Thinking.Phase.THOUGHT)
        }
    }

    fun onContentStarted() {
        if (blocks.lastOrNull() !is Block.Assistant) {
            blocks += Block.Assistant("", final = false)
        }
    }

    fun onContentDelta(text: String) {
        val last = blocks.lastOrNull()
        if (last is Block.Assistant) {
            blocks[blocks.size - 1] = last.copy(text = last.text + text)
        } else {
            blocks += Block.Assistant(text, final = false)
        }
    }

    // ------------------------------------------------------------ event channel

    fun onUser(text: String) {
        blocks += Block.User(text)
    }

    fun onModelTurnStarted(step: Int) {
        lastStep = step
    }

    fun onToolCallStarted(call: ToolCall) {
        blocks += Block.Shell(commandOf(call))
    }

    fun onToolCallFinished(call: ToolCall, result: ToolResult) {
        blocks += Block.Result(renderResult(result))
    }

    /**
     * Final-answer de-duplication: if the answer already streamed into an
     * Assistant block, that block is marked final and nothing is appended.
     * Only when no streamed block exists is a fallback block created.
     */
    fun onAnswerProduced(content: String) {
        val last = blocks.lastOrNull()
        if (last is Block.Assistant) {
            blocks[blocks.size - 1] = last.copy(final = true)
        } else {
            blocks += Block.Assistant(content, final = true)
        }
    }

    fun onRunFailed(failure: RunFailure) {
        blocks += Block.Failure(renderFailure(failure))
    }

    // ------------------------------------------------------------- rendering

    /** Renders the transcript to plain text for a TextView. */
    fun render(): String = render(blocks)

    companion object {

        /** Renders a block list to plain text. */
        fun render(blocks: List<Block>): String = buildString {
            for (block in blocks) {
                when (block) {
                    is Block.User -> append("User\n").append(block.text)
                    is Block.Thinking -> append(
                        if (block.phase == Block.Thinking.Phase.THINKING) "Thinking...\n" else "Thought\n",
                    ).append(block.text)
                    is Block.Assistant -> append("Assistant\n").append(block.text)
                    is Block.Shell -> append("Shell\n$ ").append(block.command)
                    is Block.Result -> append("Result\n").append(block.text)
                    is Block.Failure -> append("Failed\n").append(block.text)
                }
                append("\n\n")
            }
        }

        /** Extracts the shell command from a ToolCall, or shows the raw arguments. */
        fun commandOf(call: ToolCall): String {
            val trimmed = call.arguments.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return try {
                    val root = Json.parseToJsonElement(trimmed).jsonObject
                    (root["command"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: trimmed
                } catch (_: Exception) {
                    trimmed
                }
            }
            return trimmed
        }

        /**
         * Renders a ToolResult. The android_shell payload (a JSON object with
         * outcome/exitCode/stdout/stderr/hasError/error) is rendered field by
         * field; any other output is shown verbatim.
         */
        fun renderResult(result: ToolResult): String {
            val output = result.output.trim()
            val shell = tryParseShellPayload(output)
            return buildString {
                if (shell != null) {
                    shell.exitCode?.let { append("exitCode=").append(it).append("\n") }
                    if (shell.stdout.isNotBlank()) {
                        append("stdout:\n").append(shell.stdout.trimEnd())
                    }
                    if (shell.stderr.isNotBlank()) {
                        if (shell.stdout.isNotBlank()) append("\n")
                        append("stderr:\n").append(shell.stderr.trimEnd())
                    }
                } else {
                    append(output)
                }
                if (!result.success) {
                    append("\n")
                    append("error: ").append(result.error ?: "tool mechanism failure")
                }
            }.trimEnd()
        }

        /** Renders a Run failure with the step at which it occurred. */
        fun renderFailure(failure: RunFailure): String = when (failure) {
            is RunFailure.ProviderFailure -> "Provider failure (${failure.cause})"
            is RunFailure.MaxStepsReached -> "Stopped after ${failure.maxSteps} steps (maxSteps)"
            is RunFailure.SessionBusy -> "Session busy"
            is RunFailure.RuntimeFailure -> "Runtime failure (${failure.cause})"
        }

        private fun tryParseShellPayload(output: String): ShellPayload? {
            val trimmed = output.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            return try {
                val root = Json.parseToJsonElement(trimmed).jsonObject
                ShellPayload(
                    exitCode = (root["exitCode"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                    stdout = (root["stdout"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    stderr = (root["stderr"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                )
            } catch (_: Exception) {
                null
            }
        }

        private data class ShellPayload(
            val exitCode: Int?,
            val stdout: String,
            val stderr: String,
        )
    }
}