package com.androidagent.app

import android.content.Context

/**
 * AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT / AG.PROVIDER-CONFIGURED-MODELS-0 —
 * Model catalog for the Composer LLM selector.
 *
 * A [ModelOption] represents one selectable LLM. The display name is the model
 * id. Internally, the UI knows which profile owns this model so switching can
 * route through the canonical apply path.
 *
 * AG.PROVIDER-CONFIGURED-MODELS-0 boundary:
 *  - The MAIN model picker is built from CONFIGURED models ONLY
 *    ([buildModelCatalog]). Each profile contributes the models the user
 *    explicitly configured ([ProviderProfile.configuredModels]) — NOT the
 *    provider's discovered `/models` catalogue.
 *  - Discovered models are candidates for "Add Model" only. They are never fed
 *    into the main switcher. See [ModelDiscovery] for the discovery source.
 */
data class ModelOption(
    val modelId: String,
    val profileId: String,
    val displayLabel: String,
) {
    override fun toString(): String = displayLabel
}

/**
 * Builds the model catalog for the MAIN switcher from the CONFIGURED models of
 * ALL saved provider profiles (built-in + custom).
 *
 * Each profile contributes its configured models (at minimum the selected
 * model). Duplicate modelIds across profiles are deduped globally (first wins),
 * so a model configured on one profile is listed once.
 *
 * @param context app context (reads saved profiles)
 * @return the switchable models, sorted by display label.
 */
fun buildModelCatalog(context: Context): List<ModelOption> {
    val options = mutableListOf<ModelOption>()
    val seen = mutableSetOf<String>() // modelId dedup (global)

    val profiles = ProviderProfileStore.loadAll(context)

    for (profile in profiles) {
        for (cm in profile.configuredModels) {
            if (seen.add(cm.modelId)) {
                options.add(
                    ModelOption(
                        modelId = cm.modelId,
                        profileId = profile.profileId,
                        displayLabel = cm.modelId,
                    ),
                )
            }
        }
    }

    return options.sortedBy { it.displayLabel.lowercase() }
}
