// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Reinterprets a row's weight as a pre- or post-cooking measurement, converting it so the number still
 * describes the same food.
 *
 * This is the one control where the user tells us what their kitchen scale was measuring, and the only
 * place in the product where a value can be wrong by 15–40% while looking entirely correct. 100 g of
 * raw chicken is 70 g grilled. If the user weighed the cooked portion and the app believes it was raw,
 * every nutrient is overstated by the inverse of the yield, and nothing on screen says so.
 *
 * Multiply going to `cooked`, divide coming back — the same direction convention as
 * [applyMethodChange]'s first branch, so the two compose without a sign error.
 *
 * **[roundHalfUp], never [kotlin.math.round].** This is the function where that mistake costs a visible
 * gram: 50 × 0.85 = 42.5, which is 43 half-up and **42** ties-to-even. The `switch-measured-state`
 * suite carries that case specifically so a wrong port fails instead of quietly disagreeing with the
 * web client and iOS.
 *
 * Returns the row unchanged when it is already in the requested state. The guard is load-bearing rather
 * than defensive: a toggle re-emitting its current value is ordinary, and without it every such event
 * would multiply the weight by the yield again.
 *
 * Pinned by the `switch-measured-state` suite of the shared behavioural corpus.
 */
fun switchMeasuredState(ingredient: MealIngredient, to: String): MealIngredient {
    if (ingredient.measuredState == to) return ingredient

    val factor = resolveYieldFactor(ingredient.yieldFactors, ingredient.cookingMethod)
    val converted = if (to == "cooked") ingredient.quantity * factor else ingredient.quantity / factor
    return ingredient.copy(measuredState = to, quantity = roundHalfUp(converted).toDouble())
}
