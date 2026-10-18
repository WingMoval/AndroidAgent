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
        // AG.V2-2: V3 AdbRuntime.ensureReady returns distinct messages:
        //   "user action required: enable Wireless Debugging"  -> USER_ACTION_REQUIRED
        //   "Wireless Debugging is disabled; enable it..."     -> connectAuto DISABLED
        //   "connection failed: ..."                          -> CONNECT_FAILED
        //   "connected"                                        -> READY
        // Show the real V3 message instead of collapsing everything.
        message.contains("user action required", ignoreCase = true) -> "Enable Wireless Debugging in Dev Options"
        message.contains("Wireless Debugging", ignoreCase = true) -> "Wireless debugging unavailable"
        message.contains("connect failed", ignoreCase = true) -> "Not connected"
        message.contains("pairing", ignoreCase = true) -> "Pairing required"
        message.isBlank() -> "Not connected"
        else -> "Not connected"
    }

    /**
     * Transient run phase rendered from an AG Core event. `null` means "no change
     * to the transient line". Never exposes tool arguments or model reasoning.
     */
    fun runPhaseText(event: AgentEvent): String? = when (event) {
        is AgentEvent.RunStarted -> "Running..."
        is AgentEvent.ModelTurnStarted -> "Running · Step ${event.step}"
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

/**
 * AGA.OBSERVABILITY-0 — the outcome of a Provider node switch attempt.
 *
 * Pure decision rule so the failed-switch honesty guarantee is JVM-testable:
 * the UI must never show a node that was not actually applied.
 */
sealed interface ProviderSwitchOutcome {

    /** The attempted node loaded and became the active provider. */
    data class Applied(val node: ProviderNode, val config: com.androidagent.provider.openai.OpenAiCompatibleConfig) :
        ProviderSwitchOutcome

    /** The attempted node could not be applied; the active provider is unchanged. */
    data class Rejected(val attempted: ProviderNode, val reason: String) : ProviderSwitchOutcome
}

/** Decides a node switch from its device-local load result. Never throws. */
fun resolveProviderSwitch(
    attempted: ProviderNode,
    load: DeviceLocalProviderConfig,
): ProviderSwitchOutcome = when (load) {
    is DeviceLocalProviderConfig.Loaded -> ProviderSwitchOutcome.Applied(attempted, load.config)
    is DeviceLocalProviderConfig.Missing ->
        ProviderSwitchOutcome.Rejected(attempted, "${attempted.displayName} config not provisioned")
    is DeviceLocalProviderConfig.Invalid ->
        ProviderSwitchOutcome.Rejected(attempted, "${attempted.displayName} config invalid")
}