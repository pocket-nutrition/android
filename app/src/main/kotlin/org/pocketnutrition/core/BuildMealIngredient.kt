// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * The six cooking methods the app knows. `PnCooking.Methods` in the design tokens is keyed by the same
 * ids, and the API may still hand back a value outside this set — see [cookingMethodFor].
 */
private val COOKING_METHODS = setOf("raw", "boiled", "steamed", "grilled", "roasted", "fried")

/**
 * Translates the server's `typicalState` into the cooking method to open a row with, or null.
 *
 * This is enum translation, not food knowledge. Five of Knowledge's state values already *are* cooking
 * methods and pass through unchanged, and `processed` means "eaten as sold" — no cooking transform —
 * which is what `raw` encodes here.
 *
 * The generic `cooked` returns null, and that is the interesting case. It says the food is eaten cooked
 * without saying how, and nothing here can choose between boiled and fried without inventing an
 * answer. Inventing one is exactly what the sandbox's deleted label table did: it answered "grilled"
 * whenever its regexes had no opinion. Roughly 274 ingredients — 8% of the catalogue — sit on that
 * value, and they are dropped rather than guessed until the API exposes a resolved
 * `defaultCookingMethod`.
 */
fun cookingMethodFor(typicalState: String?): String? {
    if (typicalState == null || typicalState == "cooked") return null
    if (typicalState == "processed") return "raw"
    return if (typicalState in COOKING_METHODS) typicalState else null
}

/**
 * Builds a meal row from a server suggestion, or returns null when the server did not supply the food
 * data the row needs.
 *
 * THE CLIENT HOLDS NO FOOD DATA. There is no label-to-cooking-method table and no label-to-portion
 * table, and nothing here substitutes a weight or a state that the server omitted. `servingG`,
 * `typicalState` and `portionMeasuredState` are all documented as server-supplied — the API defaults
 * them when Knowledge has no value — so a null is a contract violation on the server's side. The row is
 * dropped rather than guessed at, and **callers must handle null by skipping the item**. Returning a row
 * with an invented weight would be worse than showing nothing: the user cannot tell a guessed gram
 * figure from a known one.
 *
 * [id] is a parameter rather than something built here. The reference implementation composes it from
 * the ingredient id, the clock and the item's index; the vectors exclude `id` for exactly that reason —
 * it embeds a timestamp — so keeping the clock out of this function is what makes it testable.
 *
 * Pinned by the `build-meal-ingredient` suite of the shared behavioural corpus.
 */
fun buildMealIngredient(item: AddItem, id: String): MealIngredient? {
    val suggestion = item.suggestion

    // basePortionG is the anchor every portion control scales from; without it there is no row.
    val base = suggestion.servingG ?: return null

    // A recipe component carries its own method, so typicalState is only consulted without one.
    val defaultCooking = item.cookingMethod ?: cookingMethodFor(suggestion.typicalState) ?: return null

    val isCooked = defaultCooking != "raw"

    // Whether `base` is a pre- or post-cooking weight decides whether the yield factor applies, so it
    // changes the number on screen. Refuse rather than assume. An overridden quantity needs no such
    // check: a recipe weight is a pre-cooking weight by definition.
    if (item.quantityG == null && isCooked && suggestion.portionMeasuredState == null) return null

    val measureRaw =
        if (item.quantityG != null) isCooked
        else isCooked && suggestion.portionMeasuredState == "raw"

    // A gram override defeats a millilitre display unit: the caller gave a weight, so the row is a
    // weight. Only an un-overridden millilitre food is measured by volume.
    val isMl = item.quantityG == null && suggestion.displayUnit == "ml"
    val firstUnit = suggestion.servingUnits?.firstOrNull()

    val quantity = when {
        item.quantityG != null -> item.quantityG
        isMl -> roundHalfUp(base).toDouble()
        measureRaw -> base
        isCooked ->
            roundHalfUp(base * resolveYieldFactor(suggestion.yieldFactors ?: emptyMap(), defaultCooking))
                .toDouble()
        else -> base
    }

    return MealIngredient(
        id = id,
        label = suggestion.label,
        ingredientId = suggestion.ingredientId,
        cookingMethod = defaultCooking,
        quantity = quantity,
        unit = if (isMl) "ml" else "g",
        measuredState = if (measureRaw) "raw" else if (isCooked) "cooked" else "raw",
        basePortionG = base,
        portionFactor = 1.0,
        servingUnits = suggestion.servingUnits ?: emptyList(),
        selectedUnit = if (isMl) firstUnit else null,
        unitCount = 1.0,
        displayUnit = if (isMl) "ml" else "g",
        canEatRaw = suggestion.canEatRaw,
        yieldFactors = suggestion.yieldFactors ?: emptyMap(),
        imageUrl = suggestion.imageUrl,
        servingMode = suggestion.servingMode,
    )
}

/**
 * Re-derives a row's weight when the user changes its cooking method.
 *
 * **Branch order is part of the contract**, not an implementation detail, because the branches overlap:
 * a row can simultaneously have a portion factor and a cooked weight, and which rule wins changes the
 * number. In order:
 *
 * 1. Target is `raw` — recompute from the portion factor if there is one, otherwise invert the current
 *    yield if the weight was cooked, otherwise leave the quantity alone.
 * 2. A portion factor is present — recompute from the base and force the result cooked.
 * 3. The weight was cooked — convert through the raw equivalent, leaving [MealIngredient.measuredState]
 *    untouched.
 * 4. Otherwise only the method changes.
 *
 * Branch 3 leaving `measuredState` alone is deliberate and easy to "fix" wrongly: the weight was cooked
 * before and is still cooked after, just cooked a different way. Setting it to `cooked` there would be a
 * no-op today and a lie if branch 3 ever became reachable from a raw weight.
 *
 * The round trip is *through* the raw equivalent rather than a ratio of the two factors, so the half-up
 * rounding lands once, at the end, on the value the user sees. Rounding the intermediate would drift by
 * a gram on some pairs.
 *
 * Pinned by the `apply-method-change` suite of the shared behavioural corpus.
 */
fun applyMethodChange(ingredient: MealIngredient, newMethod: String): MealIngredient {
    if (newMethod == "raw") {
        val rawQuantity = when {
            ingredient.portionFactor > 0 ->
                roundHalfUp(ingredient.basePortionG * ingredient.portionFactor).toDouble()
            ingredient.measuredState == "cooked" ->
                roundHalfUp(
                    ingredient.quantity /
                        resolveYieldFactor(ingredient.yieldFactors, ingredient.cookingMethod)
                ).toDouble()
            else -> ingredient.quantity
        }
        return ingredient.copy(cookingMethod = "raw", measuredState = "raw", quantity = rawQuantity)
    }

    if (ingredient.portionFactor > 0) {
        val rawBase = ingredient.basePortionG * ingredient.portionFactor
        return ingredient.copy(
            cookingMethod = newMethod,
            measuredState = "cooked",
            quantity = roundHalfUp(rawBase * resolveYieldFactor(ingredient.yieldFactors, newMethod))
                .toDouble(),
        )
    }

    if (ingredient.measuredState == "cooked") {
        val rawEquivalent =
            ingredient.quantity / resolveYieldFactor(ingredient.yieldFactors, ingredient.cookingMethod)
        return ingredient.copy(
            cookingMethod = newMethod,
            quantity = roundHalfUp(rawEquivalent * resolveYieldFactor(ingredient.yieldFactors, newMethod))
                .toDouble(),
        )
    }

    return ingredient.copy(cookingMethod = newMethod)
}

/**
 * Flattens a recipe suggestion into one [AddItem] per component.
 *
 * Each component becomes an item whose method and weight come from the *recipe*, not from the
 * ingredient's own defaults — that is what makes a recipe a recipe. Order is preserved, and a recipe
 * with no components gives an empty list rather than an error: a recipe the server sent without
 * components is a data gap, not a client failure, and the caller has nothing to add.
 *
 * Pinned by the `unpack-recipe` suite of the shared behavioural corpus.
 */
fun unpackRecipe(recipe: IngredientSuggestion): List<AddItem> =
    (recipe.recipeComponents ?: emptyList()).map { component ->
        AddItem(
            suggestion = component.ingredient,
            cookingMethod = component.cookingMethod,
            quantityG = component.weightG,
        )
    }
