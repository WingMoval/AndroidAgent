package com.androidagent.app

import android.content.Context
import com.androidagent.app.provider.AndroidOpenAiHttpTransport
import com.androidagent.core.AgentCore
import com.androidagent.core.ToolRegistry
import com.androidagent.provider.openai.OpenAiCompatibleConfig
import com.androidagent.provider.openai.OpenAiCompatibleProvider
import com.androidagent.runtime.AgentRuntime
import com.androidagent.tool.androidshell.AndroidShellToolImpl
import com.androidagent.tool.androidshell.ShellConnection

/**
 * The AGA Composition Root: the single place where implementations are chosen and
 * wired together.
 *
 * It assembles and nothing else. Session/Run lifecycle lives in
 * [AgentRuntime]; Agent semantics live in AG Core; input and rendering live in
 * [ChatActivity].
 *
 * AG.SHELL-1: the concrete Android Shell Tool ([AndroidShellToolImpl]) is
 * registered here in the (previously empty) [ToolRegistry]. The AT Engine
 * lifecycle binding ([ShellConnection]) is created here too and handed to the
 * Host, so no `com.androidterminal.*` type ever appears in Host code.
 *
 * Credential policy for this step: the API key lives in the calling process's
 * memory only. It is never written to disk, SharedPreferences, DataStore,
 * resources or BuildConfig. AGA-0 is an integration host, not a production
 * credential system.
 */
class ChatCompositionRoot(
    private val config: OpenAiCompatibleConfig,
    applicationContext: Context,
) {

    private val toolRegistry = ToolRegistry()

    /**
     * AT Engine lifecycle binding (Host owns the lifecycle policy; this object
     * performs the AT calls). Never exposes AT-owned types.
     */
    val shell: ShellConnection = ShellConnection(applicationContext)

    init {
        // AG.SHELL-1: register the concrete Android Shell Tool. AG.0's registry
        // was intentionally empty; concrete tools are registered here in the
        // composition root, never inside AG Core.
        toolRegistry.register(AndroidShellToolImpl())
    }

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