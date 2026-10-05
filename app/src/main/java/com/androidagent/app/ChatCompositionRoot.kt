package com.androidagent.app

import com.androidagent.app.provider.AndroidOpenAiHttpTransport
import com.androidagent.core.AgentCore
import com.androidagent.core.ToolRegistry
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.runtime.AgentRuntime

/**
 * The AGA Composition Root: the single place where implementations are chosen and
 * wired together.
 *
 * It assembles and nothing else. Session/Run lifecycle lives in
 * [AgentRuntime]; Agent semantics live in AG Core; input and rendering live in
 * [ChatActivity].
 *
 * Credential policy for this step: the API key lives in the calling process's
 * memory only. It is never written to disk, SharedPreferences, DataStore,
 * resources or BuildConfig. AGA-0 is an integration host, not a production
 * credential system.
 */
class ChatCompositionRoot(
    private val config: OpenAiCompatibleConfig,
) {

    /**
     * AG.0 registers zero concrete tools, so the registry is intentionally
     * empty and the model is invoked with `tools = empty`. Concrete tools are
     * registered here by a future Composition Root, never inside AG Core.
     */
    private val toolRegistry = ToolRegistry()

    /**
     * The Android transport is chosen explicitly here, in the composition root.
     *
     * The Provider deliberately has no default transport: a hidden JDK default
     * would break Android, which is the exact failure the transport boundary
     * exists to prevent.
     */
    private val provider = OpenAiCompatibleProvider(config, AndroidOpenAiHttpTransport())

    private val agentCore = AgentCore(provider, toolRegistry)

    /** The runnable Agent lifecycle handed to the UI. */
    val runtime: AgentRuntime = AgentRuntime(agentCore)
}