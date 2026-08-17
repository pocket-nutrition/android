// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Typed decoding for the three suites whose inputs are whole [IngredientSuggestion] trees.
 *
 * [VectorCorpus] hands back the JSON tree on purpose — `expected` is a number in one suite and a nested
 * object in another, so it knows the corpus structure and nothing about any payload. These three suites
 * are where that becomes inconvenient: unpacking eighteen fields by hand from a `JsonObject` would be
 * both tedious and a second place for the shape to drift. Decoding through the same `@Serializable`
 * declarations the app uses against the real server means a mismatch between corpus shape and data class
 * fails here rather than at runtime.
 *
 * `ignoreUnknownKeys` matters: the corpus carries `nutrition`, `densityGMl` and the `unitLabel*` pair,
 * which are deliberately not declared. Without it every case would fail on a field nothing reads.
 */
object SuggestionDecoding {

    val json = Json { ignoreUnknownKeys = true }

    /** True when the corpus says the expectation is JSON `null` rather than an object. */
    fun isNull(element: JsonElement): Boolean = element is JsonNull

    // The type argument is explicit on purpose: without it the compiler resolves to the
    // (DeserializationStrategy, JsonElement) overload rather than the reified one and fails to build.
    inline fun <reified T> decode(element: JsonElement): T = json.decodeFromJsonElement<T>(element)
}

/**
 * A [MealIngredient] minus its `id`, which is the projection `build-meal-ingredient` asserts.
 *
 * The suite excludes `id` because the reference implementation builds it from the clock. Rather than
 * decode `expected` into a [MealIngredient] and paper over the missing field with a default — which
 * would make an `id` regression invisible — this states exactly what is and is not compared.
 * `apply-method-change` needs no such projection: `id` is supplied in that suite's input and asserted.
 */
@Serializable
data class ComparableRow(
    val label: String,
    val ingredientId: String,
    val cookingMethod: String,
    val quantity: Double,
    val unit: String,
    val measuredState: String,
    val basePortionG: Double,
    val portionFactor: Double,
    val servingUnits: List<ServingUnit> = emptyList(),
    val selectedUnit: ServingUnit? = null,
    val unitCount: Double = 1.0,
    val displayUnit: String = "g",
    val canEatRaw: Boolean? = null,
    val yieldFactors: Map<String, Double> = emptyMap(),
    val imageUrl: String? = null,
    val servingMode: String? = null,
) {
    companion object {
        fun of(row: MealIngredient) = ComparableRow(
            label = row.label,
            ingredientId = row.ingredientId,
            cookingMethod = row.cookingMethod,
            quantity = row.quantity,
            unit = row.unit,
            measuredState = row.measuredState,
            basePortionG = row.basePortionG,
            portionFactor = row.portionFactor,
            servingUnits = row.servingUnits,
            selectedUnit = row.selectedUnit,
            unitCount = row.unitCount,
            displayUnit = row.displayUnit,
            canEatRaw = row.canEatRaw,
            yieldFactors = row.yieldFactors,
            imageUrl = row.imageUrl,
            servingMode = row.servingMode,
        )
    }
}
