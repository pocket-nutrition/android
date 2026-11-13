// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.util.UUID
import org.pocketnutrition.api.IngredientDetail
import org.pocketnutrition.core.AddItem
import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.buildMealIngredient
import org.pocketnutrition.core.pickLocalized
import org.pocketnutrition.model.lockedToRawIfIndustrial

// Turns what the speaker said into meal rows, under the app's one rule: NEVER INVENT FOOD DATA.
// There is no default weight, no label-to-method table and no name-search fallback here. An item the
// catalogue cannot resolve, or that the corpus builder refuses, is skipped and reported.

private val KNOWN_COOKING_METHODS = setOf("raw", "boiled", "steamed", "grilled", "roasted", "fried")

/** What a spoken item may override on the row built from its catalogue entry. */
data class ImportOverrides(val quantityG: Double?, val cookingMethod: String?)

/**
 * Grams only: the quantity is applied, as a pre-cooking weight, solely when the unit is `g`. A piece
 * count or any other unit leaves the suggestion's own serving untouched, because turning "2 pieces"
 * into grams would need a weight table. A method outside the six the app knows is ignored.
 */
fun importOverrides(item: RecognizedItem): ImportOverrides {
    val quantity = item.quantity
    val grams = if (item.unit == "g" && quantity != null && quantity.isFinite() && quantity > 0) quantity else null
    val method = item.cookingMethod?.takeIf { it in KNOWN_COOKING_METHODS }
    return ImportOverrides(quantityG = grams, cookingMethod = method)
}

/**
 * The row for one chosen candidate, or `null` when the corpus builder refuses it. Deliberately goes
 * through [buildMealIngredient] and not `asMealRow`, whose fallback seeds a 100 g / "grilled" guess.
 */
fun buildImportRow(suggestion: IngredientSuggestion, overrides: ImportOverrides, id: String): MealIngredient? =
    buildMealIngredient(AddItem(suggestion, overrides.cookingMethod, overrides.quantityG), id)
        ?.lockedToRawIfIndustrial(suggestion.isProduct)

/**
 * `GET /ingredients/{id}` as an [IngredientSuggestion], mapping only fields the detail really carries.
 * `portionMeasuredState`, `yieldFactors` and `servingMode` stay null — the detail has none — so a cooked
 * item with no gram override is refused by the builder rather than guessed.
 *
 * ASSUMPTION worth a maintainer's eye: `referenceWeightG` is read as the serving weight
 * (`servingG`). The detail's schema calls it the "reference weight"; if that is not the portion the
 * search endpoint reports, imported rows without a spoken gram weight will carry the wrong portion.
 */
fun IngredientDetail.asSuggestion(lang: String, fallbackLabel: String? = null): IngredientSuggestion =
    IngredientSuggestion(
        ingredientId = ingredientId,
        label = pickLocalized(names, lang) ?: fallbackLabel ?: ingredientId,
        lang = lang,
        servingG = referenceWeightG,
        displayUnit = displayUnit,
        typicalState = typicalState,
        canEatRaw = canEatRaw,
        canEatCooked = canEatCooked,
        servingUnits = servingUnits,
        foodGroup = foodGroup,
    )

/** The user's choice for one item on the review screen. */
data class ItemSelection(val candidateIndex: Int, val skipped: Boolean)

/** Best candidate preselected; an item with no candidate starts skipped. */
fun defaultSelection(item: RecognizedItem): ItemSelection =
    ItemSelection(candidateIndex = 0, skipped = item.candidates.isEmpty())

enum class SkipReason { NO_CANDIDATE, NOT_FOUND, NOT_BUILDABLE }

data class SkippedItem(val rawText: String, val reason: SkipReason)

data class ImportPlan(val rows: List<MealIngredient>, val skipped: List<SkippedItem>)

/**
 * Resolves each non-skipped item through [lookup] (the `/ingredients/{id}` call; `null` = unknown id).
 * Items the user skipped are silently omitted; items that could not be resolved or built are reported
 * in [ImportPlan.skipped] with the reason. A lookup that throws aborts the whole import — a network
 * failure must not be mistaken for "the ingredient does not exist".
 */
suspend fun resolveImport(
    items: List<RecognizedItem>,
    selections: List<ItemSelection>,
    lang: String,
    lookup: suspend (ingredientId: String) -> IngredientDetail?,
    newId: () -> String = { UUID.randomUUID().toString() },
): ImportPlan {
    val rows = mutableListOf<MealIngredient>()
    val skipped = mutableListOf<SkippedItem>()
    items.forEachIndexed { index, item ->
        val selection = selections.getOrElse(index) { defaultSelection(item) }
        if (selection.skipped) return@forEachIndexed
        val candidate = item.candidates.getOrNull(selection.candidateIndex)
        if (candidate == null) {
            skipped += SkippedItem(item.rawText, SkipReason.NO_CANDIDATE)
            return@forEachIndexed
        }
        val detail = lookup(candidate.ingredientId)
        if (detail == null) {
            skipped += SkippedItem(item.rawText, SkipReason.NOT_FOUND)
            return@forEachIndexed
        }
        val row = buildImportRow(detail.asSuggestion(lang, candidate.name), importOverrides(item), newId())
        if (row == null) skipped += SkippedItem(item.rawText, SkipReason.NOT_BUILDABLE) else rows += row
    }
    return ImportPlan(rows, skipped)
}
