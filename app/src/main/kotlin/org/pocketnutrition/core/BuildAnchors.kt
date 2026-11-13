// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * The coarse anchor set behind the collapsed-row summary — the handful of portions offered as one-tap
 * buttons.
 *
 * Two ladders, and which one applies is not a matter of taste:
 *
 * - **Countable foods get counted.** ½ · 1 · 2 · 3 carottes reads the way a person thinks about
 *   carrots. This needs `servingMode == "item"` **and** an actual item unit to count. Branching on
 *   the mode alone is the trap the corpus names: an ingredient can be marked countable while offering
 *   only a tablespoon, and a port that trusts the mode returns an empty ladder or throws.
 * - **Everything else is scaled** by the five named factors.
 *
 * Both ladders drop rungs that resolve too small to be worth a button, at different thresholds: 5 g
 * for an item, 1 g for a factor. Half a hazelnut is 4 g and is not a portion anyone wants to tap; a
 * quarter of a 2 g base portion is 0.5 g and is not either. The filters exist so no button resolves
 * to nothing, which is why they are contract rather than nicety.
 *
 * Both comparisons are `>=`: a rung landing exactly on its floor survives. Writing `>` loses exactly
 * one rung from each ladder and breaks nothing else, which is why the boundary is asserted by name in
 * BuildAnchorsVectorTest.
 *
 * Pinned by the `build-anchors`/`build-anchors-en` suites of the shared behavioural corpus.
 */
fun buildAnchors(
    basePortionG: Double,
    servingUnits: List<ServingUnit>,
    servingMode: String?,
    lang: String,
): List<ScaleStep> {
    val item = servingUnits.firstOrNull { it.isItem }
    if (servingMode == "item" && item != null) {
        return ITEM_COUNTS
            .filter { it * item.grams >= MINIMUM_ITEM_GRAMS }
            .map { ScaleStep.UnitStep(unit = item, count = it) }
    }

    return namedFactors(lang)
        .filter { (factor, _) -> factor * basePortionG >= MINIMUM_FACTOR_GRAMS }
        .map { (factor, label) -> ScaleStep.FactorStep(factor = factor, label = label) }
}

/**
 * The item ladder. Half an item is offered, quarters are not — you can halve a carrot, not quarter
 * one usefully.
 */
private val ITEM_COUNTS = listOf(0.5, 1.0, 2.0, 3.0)

/**
 * The weight ladder, per language.
 *
 * These five labels are also `portionAnchors.quarter` / `.small` / `.one` / `.large` / `.double` in
 * the copy deck. They are written here rather than injected because the corpus pins them as part of
 * this function's output — the vector test is what keeps the two copies honest, and it fails loudly
 * if the deck is reworded without the vectors following. Same rule for the English set against
 * `build-anchors-en`.
 *
 * Internal rather than private because [describeQuantity] snaps its portion ratio to the same five.
 * One copy per language: a rung renamed here must change both surfaces at once, which is what a user
 * would expect and the opposite of what two lists would give.
 */
internal fun namedFactors(lang: String): List<Pair<Double, String>> =
    if (lang == "en") NAMED_FACTORS_EN else NAMED_FACTORS_FR

private val NAMED_FACTORS_FR = listOf(
    0.25 to "¼",
    0.5 to "Petite",
    1.0 to "1 part",
    1.5 to "Grande",
    2.0 to "Double",
)

private val NAMED_FACTORS_EN = listOf(
    0.25 to "¼",
    0.5 to "Small",
    1.0 to "1 serving",
    1.5 to "Large",
    2.0 to "Double",
)

/**
 * A rung below this resolves to a button not worth offering. Different for the two ladders because a
 * countable thing at 4 g is absurd where a measured 4 g is ordinary.
 */
private const val MINIMUM_ITEM_GRAMS = 5.0
private const val MINIMUM_FACTOR_GRAMS = 1.0
