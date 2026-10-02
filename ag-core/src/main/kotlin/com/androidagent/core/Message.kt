package com.androidagent.core

/**
 * One entry in a Session's conversation history.
 *
 * History is provider-neutral on purpose: AG Core must not mirror any vendor
 * message schema. A concrete ModelProvider translates these into whatever wire
 * format that provider speaks.
 */
sealed interface Message

/** Input supplied by the user for a single Run. */
data class UserMessage(val content: String) : Message

/**
 * What the model produced on one turn.
 *
 * [toolCalls] is empty for a final answer and non-empty when the model asked for
 * tools. A single type covers both so that a model turn is always recorded even
 * when it carries no prose.
 */
data class ModelMessage(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
) : Message

/** The outcome of one tool invocation, appended after the model's request. */
data class ToolResultMessage(
    val toolCallId: String,
    val toolName: String,
    val result: ToolResult,
) : Message