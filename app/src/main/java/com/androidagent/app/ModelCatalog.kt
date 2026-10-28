package com.androidagent.app

import android.content.Context

/**
 * AG.UI-SHELL-1.PRODUCT-CONTROLS-ALIGNMENT / FUNCTIONAL-CONTROLS-CLOSURE —
 * Model catalog for the Composer LLM selector.
 *
 * A [ModelOption] represents one selectable LLM. The display name is the model
 * id. Internally, the UI knows which profile owns this model so switching can
 * route through the canonical apply path.
 *
 * The catalog aggregates models from ALL profiles (built-in + custom):
 *  - Each profile contributes at minimum its configured model
 *  - Optional /v1/models discovery adds more models per profile
 */
data class ModelOption(
    val modelId: String,
    val profileId: String,
    val displayLabel: String,
) {
    override fun toString(): String = displayLabel
}

/**
 * Builds the model catalog from ALL saved provider profiles.
 *
 * AG.UI-SHELL-1.FUNCTIONAL-CONTROLS-CLOSURE §26:
 * "Model catalog should aggregate: EMG models, OpenRouter models, Custom API #1 models, ..."
 */
fun buildModelCatalog(
    context: Context,
    discoveredModels: Map<String, List<String>>? = null,
): List<ModelOption> {
    val options = mutableListOf<ModelOption>()
    val seen = mutableSetOf<String>() // modelId dedup (global)

    val profiles = ProviderProfileStore.loadAll(context)

    for (profile in profiles) {
        // Always include the saved config's model
        val configModel = profile.config.model
        if (seen.add(configModel)) {
            options.add(ModelOption(
                modelId = configModel,
                profileId = profile.profileId,
                displayLabel = configModel,
            ))
        }

        // Merge discovered models for this profile
        discoveredModels?.get(profile.profileId)?.forEach { modelId ->
            if (seen.add(modelId)) {
                options.add(ModelOption(
                    modelId = modelId,
                    profileId = profile.profileId,
                    displayLabel = modelId,
                ))
            }
        }
    }

    return options.sortedBy { it.displayLabel.lowercase() }
}
