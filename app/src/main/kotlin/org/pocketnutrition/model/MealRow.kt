// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.util.UUID
import org.pocketnutrition.api.FoodItemRequest
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.AddItem
import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.QuantityAnchor
import org.pocketnutrition.core.QuantityContext
import org.pocketnutrition.core.QuantityDescription
import org.pocketnutrition.core.ScaleStep
import org.pocketnutrition.core.applyMethodChange
import org.pocketnutrition.core.buildAnchors
import org.pocketnutrition.core.buildMealIngredient
import org.pocketnutrition.core.describeQuantity
import org.pocketnutrition.core.pickLocalized
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.core.stepLabel
import org.pocketnutrition.core.stepRawWeight

// Bridges core's MealIngredient to the two app-layer shapes it has to become: a request the API
// understands, and the MealItem the journal persists.
//
// WHY THE BUILDER USES MealIngredient AND THE JOURNAL DOES NOT
// MealIngredient carries the whole portion model — base portion, serving units, serving mode, yield
// factors — because buildAnchors, shortcutProfile and describeQuantity all need it. A *saved* meal
// needs none of that: it is a record of what was eaten, and storing the portion machinery would grow
// journal.json with fields nothing reads back. So the builder works in MealIngredient and converts
// once, at the save boundary. That also keeps the on-disk schema at version 1.

/**
 * One item of a `POST /nutrition` request body.
 *
 * `unit` is the row's own unit rather than a hardcoded "g", which matters for millilitre foods: before
 * the ladder existed the app sent every quantity as grams, so 200 mL of milk was sent as 200 g. The two
 * differ by the density — 206 g for a 200 mL glass in Knowledge's own numbers.
 */
fun MealIngredient.asRequest() = FoodItemRequest(
    name = label,
    quantity = quantity,
    unit = unit,
    cookingMethod = cookingMethod,
    measuredState = measuredState,
    ingredientId = ingredientId,
)

/**
 * A quick-add row: an energy figure typed in the search field, with no ingredient behind it. Held in the
 * builder as a row whose unit is "kcal" and quantity the energy, and never sent to the server.
 */
val MealIngredient.isQuickAddRow: Boolean get() = unit == QUICK_ADD_UNIT

fun quickAddRow(kcal: Double, id: String = UUID.randomUUID().toString()) = MealIngredient(
    id = id,
    label = "${roundHalfUp(kcal)} kcal",
    ingredientId = "quick-add:$id",
    cookingMethod = "",
    quantity = kcal,
    unit = QUICK_ADD_UNIT,
    measuredState = "",
    basePortionG = kcal,
    portionFactor = 1.0,
    displayUnit = QUICK_ADD_UNIT,
)

private const val QUICK_ADD_UNIT = "kcal"

/** The journal's persisted shape. Drops the portion model deliberately — see the note above. */
fun MealIngredient.asMealItem(): MealItem = if (isQuickAddRow) {
    MealItem(ingredientId = null, label = label, quantityG = 0.0, cookingMethod = "", measuredState = "", energyKcal = quantity)
} else MealItem(
    ingredientId = ingredientId,
    label = label,
    quantityG = quantity,
    cookingMethod = cookingMethod,
    measuredState = measuredState,
)

/**
 * Marks the items the server could not compute, so a saved meal still shows them as not recognised.
 * [profiles] are aligned with [items] one for one (see `mergeQuickAdd`); an item without a matching
 * answer is left as it is.
 */
fun withAvailability(items: List<MealItem>, profiles: List<NutritionalProfile>): List<MealItem> =
    items.mapIndexed { index, item ->
        if (profiles.getOrNull(index)?.available == false) item.copy(available = false) else item
    }

/**
 * Rebuilds a builder row from a saved entry, for editing.
 *
 * The reverse of [asMealItem] — but lossy in the other direction: a saved [MealItem] carries none of the
 * portion metadata ([MealIngredient.basePortionG], `servingUnits`, `yieldFactors`) the ladder needs,
 * because that metadata is deliberately dropped at save time (see the note above). This builds the same
 * degraded shape [asMealRow]'s fallback branch already builds when the corpus can't resolve an
 * ingredient: the saved weight becomes the base portion, with no serving units or yield factors to
 * convert it. The one consequence worth knowing — switching cooking method on an edited row will not
 * auto-adjust the weight, since there is no yield factor to convert with.
 */
fun MealItem.asMealIngredient(id: String = UUID.randomUUID().toString()): MealIngredient =
    if (isQuickAdd && energyKcal != null) quickAddRow(energyKcal, id) else MealIngredient(
    id = id,
    label = label,
    ingredientId = ingredientId ?: id,
    cookingMethod = cookingMethod,
    quantity = quantityG,
    unit = "g",
    measuredState = measuredState,
    basePortionG = quantityG,
    portionFactor = 1.0,
    servingUnits = emptyList(),
    selectedUnit = null,
    unitCount = 1.0,
    displayUnit = "g",
    canEatRaw = null,
    yieldFactors = emptyMap(),
    imageUrl = null,
    servingMode = null,
)

/**
 * The ladder's rungs.
 *
 * [buildAnchors] returns [ScaleStep]s and [describeQuantity] wants [QuantityAnchor]s; the bridge is
 * [stepRawWeight] and [stepLabel], both corpus functions. Resolving the weight here rather than in the
 * composable is what keeps `step-raw-weight`'s rule — unit steps ignore the base portion entirely —
 * from being re-derived by hand somewhere it would be got wrong.
 */
fun MealIngredient.ladder(lang: String): List<ScaleStep> =
    buildAnchors(basePortionG = basePortionG, servingUnits = servingUnits, servingMode = servingMode, lang = lang)

fun MealIngredient.anchors(lang: String): List<QuantityAnchor> = ladder(lang).map {
    QuantityAnchor(label = stepLabel(it, lang), grams = stepRawWeight(it, basePortionG))
}

/** The collapsed summary — "≈ 1,3 portion"/"≈ 1.3 servings" and the weight beneath it. */
fun MealIngredient.summary(lang: String): QuantityDescription {
    val itemUnit = servingUnits.firstOrNull { it.isItem }
    return describeQuantity(
        currentG = quantity,
        anchors = anchors(lang),
        context = QuantityContext(
            servingMode = servingMode,
            // describeQuantity keys its item branch on these rather than on servingMode — the corpus has
            // a case with no servingMode that still takes that branch — so passing them is what makes
            // "≈ 2 carottes" possible at all.
            itemUnitG = itemUnit?.grams,
            itemLabel = itemUnit?.let { pickLocalized(it.labels, lang) },
            portionG = basePortionG,
            displayUnit = displayUnit,
        ),
        lang = lang,
    )
}

/**
 * Builds a meal row, falling back when the corpus function refuses.
 *
 * **This is a deliberate, documented divergence and the one place it lives.**
 * [buildMealIngredient] returns null rather than invent food data the server did not send — the right
 * call for the sandbox, which is a reference implementation. Knowledge no longer emits a generic
 * `typicalState: "cooked"` (every ingredient now names a specific method, or "raw" / "processed"), but
 * the corpus can still refuse for other reasons — most commonly a `typicalState` the client's known
 * method set does not recognise — and the fallback below is what keeps that ingredient addable rather
 * than a dead end.
 *
 * So the corpus function is tried first and used whenever it succeeds, which keeps every pinned
 * behaviour — the yield conversions, the millilitre handling, the raw/cooked weight semantics — exactly
 * as verified. Only its refusals are overridden, and the fallback does not *invent* a weight: it seeds
 * an editable field with a visible number the user is expected to correct. It defaults the method to
 * "grilled" — a visible, correctable guess rather than the retired generic "cuit (générique)" bucket,
 * which is no longer a value this app ever produces.
 */
fun IngredientSuggestion.asMealRow(id: String = UUID.randomUUID().toString()): MealIngredient {
    buildMealIngredient(AddItem(this), id)?.let { return it.lockedToRawIfIndustrial(isProduct) }

    val base = servingG ?: FALLBACK_PORTION_G
    val unit = if (displayUnit == "ml") "ml" else "g"
    val fallback = MealIngredient(
        id = id,
        label = label,
        ingredientId = ingredientId,
        cookingMethod = if (typicalState == "raw") "raw" else "grilled",
        quantity = base,
        unit = unit,
        measuredState = portionMeasuredState ?: typicalState ?: "cooked",
        basePortionG = base,
        portionFactor = 1.0,
        servingUnits = servingUnits ?: emptyList(),
        selectedUnit = null,
        unitCount = 1.0,
        displayUnit = unit,
        canEatRaw = canEatRaw,
        yieldFactors = yieldFactors ?: emptyMap(),
        imageUrl = imageUrl,
        servingMode = servingMode,
    )
    return fallback.lockedToRawIfIndustrial(isProduct)
}

/**
 * Locks a row to "raw" the moment it wraps an Open Food Facts product — the backend now always
 * analyses these rows at their own values (source "off_direct", cookingMethod "raw") regardless of
 * what is requested, so offering a cooking-method choice here would be a UI lie. Goes through
 * [applyMethodChange] rather than re-deriving the raw-weight math, so that conversion stays the one
 * place it is pinned. Idempotent for a row that is already raw.
 */
fun MealIngredient.lockedToRawIfIndustrial(isProduct: Boolean): MealIngredient {
    if (!isProduct) return this
    return applyMethodChange(this, "raw").copy(isIndustrialProduct = true)
}

/**
 * The weight to seed a row with when the server sent no reference portion. 100 g because it is the unit
 * every nutrition table is quoted per, so it is the number a user can most easily reason about when
 * correcting it.
 */
private const val FALLBACK_PORTION_G = 100.0
