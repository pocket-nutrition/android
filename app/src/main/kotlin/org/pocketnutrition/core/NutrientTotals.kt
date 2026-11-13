// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

// Every nutrient `POST /nutrition` returns for an item or a meal, keyed by its API field name
// (`energyKcal`, `fiberG`, `vitaminB12Ug`…). Mirrors iOS `NutrientTotals.swift`.
//
// Kept so a saved meal stores everything the engine computed, not only the four macros the journal shows
// today. An **unknown value is absent, never 0**: the reference database simply has no figure for some
// nutrients of some foods, and treating that as zero would make a day look poorer in iron than it was.
// Stored as a flat JSON object of known keys (a plain `Map<String, Double>`).

/** The 24 nutrient fields of `NutritionalProfileResponse`, in the schema's order. */
val NUTRIENT_KEYS: List<String> = listOf(
    "energyKcal", "proteinG", "fatG", "carbohydratesG", "fiberG", "waterG", "sugarsG",
    "calciumMg", "ironMg", "magnesiumMg", "sodiumMg", "vitaminCMg", "saturatedFatG",
    "monounsaturatedFatG", "polyunsaturatedFatG", "cholesterolMg", "potassiumMg", "phosphorusMg",
    "zincMg", "vitaminAUg", "vitaminDUg", "vitaminB6Mg", "vitaminB12Ug", "niacinMg",
)

private val KNOWN_NUTRIENTS = NUTRIENT_KEYS.toSet()

/** Keeps only known keys with finite values. */
fun nutrientTotals(values: Map<String, Double?>): Map<String, Double> = buildMap {
    values.forEach { (key, value) ->
        if (key in KNOWN_NUTRIENTS && value != null && value.isFinite()) put(key, value)
    }
}

/** Sum of the known values: a nutrient is absent only when absent on both sides. */
fun sumNutrients(lhs: Map<String, Double>, rhs: Map<String, Double>): Map<String, Double> {
    val merged = lhs.toMutableMap()
    rhs.forEach { (key, value) -> merged[key] = (merged[key] ?: 0.0) + value }
    return nutrientTotals(merged)
}
