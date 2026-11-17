package com.androidagent.app

/**
 * AG.PROVIDER-CONFIGURED-MODELS-0 — a model the user EXPLICITLY configured on a
 * provider profile, i.e. a model that is allowed to appear in the main model
 * switcher.
 *
 * This is deliberately DISTINCT from a "discovered" model: discovery is the
 * provider `/models` catalogue used only as candidates for "Add Model". A
 * discovered model only becomes switchable once the user explicitly adds it,
 * at which point a [ConfiguredModel] is persisted.
 *
 * @property modelId The FULL API model id, e.g. `deepseek/deepseek-v4-flash-0731`.
 *   This is the value that is ALWAYS sent to the provider. Never blank.
 * @property alias Optional user-defined display name. Blank/whitespace-only is
 *   treated as "no alias" ([normalizedAlias] returns null). The alias affects
 *   ONLY the UI display; it is never sent to the provider.
 */
data class ConfiguredModel(
    val modelId: String,
    val alias: String? = null,
) {
    init {
        require(modelId.isNotBlank()) { "ConfiguredModel.modelId must not be blank" }
    }

    /** The alias trimmed, or null when blank. UI display only. */
    val normalizedAlias: String?
        get() = alias?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The short home-composer display name:
     *  - the alias, when present (non-blank after trim)
     *  - otherwise the modelId's provider prefix (the part before "/")
     *  - otherwise the modelId itself (no "/")
     *
     * This is UI display only and never substitutes for [modelId] in requests.
     */
    val shortDisplay: String
        get() = normalizedAlias ?: modelId.substringBefore("/", modelId)

    override fun toString(): String = "ConfiguredModel(modelId=$modelId, alias=$alias)"
}

/**
 * Resolves the home-composer short display name for a model id, given the
 * optional alias the user configured for it.
 *
 * Rule (frozen):
 * ```
 * if alias is non-blank after trim  -> alias.trim()
 * else if modelId contains "/"      -> substringBefore("/")
 * else                              -> modelId
 * ```
 *
 * No brand mapping table is applied: the default display is derived strictly
 * from the model id (a user who wants "DeepSeek" types it as an alias).
 */
fun modelShortDisplay(modelId: String?, alias: String? = null): String {
    val id = modelId?.trim().orEmpty()
    if (id.isEmpty()) return "Model"
    val a = alias?.trim().orEmpty()
    return if (a.isNotEmpty()) a else id.substringBefore("/", id)
}
