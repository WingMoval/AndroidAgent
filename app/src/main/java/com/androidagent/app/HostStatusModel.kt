package com.androidagent.app

import com.androidagent.core.AgentEvent

/**
 * Pure, testable Host status logic for AGA.HOST-CONTROL-0.
 *
 * Everything here is Android-free on purpose so the Host status behaviour can be
 * exercised on the JVM without an Activity. It owns no UI, no event stream, no
 * connection state — only the small mapping/decision rules the UI needs:
 *
 *  - [effectiveKey]: blank-key manual Apply must retain the active credential
 *    (the 401 regression fix).
 *  - [safeAtMessage]: collapse an AT diagnostic into a short, secret-free line.
 *  - [runPhaseText]: map an AG Core [AgentEvent] to the transient run phase.
 *  - [providerLine]: the main screen shows only "Provider: <node>".
 */
object HostStatusModel {

    /**
     * Blank-key 401 fix: a typed key wins; a blank key must NOT overwrite the last
     * successfully applied credential.
     */
    fun effectiveKey(typedKey: String, lastAppliedKey: String): String =
        typedKey.ifEmpty { lastAppliedKey }

    /**
     * Collapse an AT diagnostic into a short, secret-free status fragment.
     */
    fun safeAtMessage(message: String): String = when {
        message.contains("Wireless Debugging", ignoreCase = true) -> "Wireless debugging unavailable"
        message.contains("connect failed", ignoreCase = true) -> "Not connected"
        message.isBlank() -> "Not connected"
        else -> "Not connected"
    }

    /**
     * Transient run phase rendered from an AG Core event. `null` means "no change
     * to the transient line". Never exposes tool arguments or model reasoning.
     */
    fun runPhaseText(event: AgentEvent): String? = when (event) {
        is AgentEvent.RunStarted -> "Thinking..."
        is AgentEvent.ModelTurnStarted -> "Thinking... · Step ${event.step}"
        is AgentEvent.ModelTurnFinished -> null
        is AgentEvent.ToolCallStarted -> "Executing ${event.call.name}..."
        is AgentEvent.ToolCallFinished -> "Processing result..."
        is AgentEvent.AnswerProduced -> "Preparing answer..."
        is AgentEvent.RunFailed -> "Failed."
        is AgentEvent.RunFinished -> "Ready."
    }

    /** The persistent Provider line on the main screen (node only, no details). */
    fun providerLine(node: String): String = "Provider: $node"
}