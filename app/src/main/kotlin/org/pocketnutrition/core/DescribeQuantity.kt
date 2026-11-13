// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlin.math.abs

/**
 * One rung of the ladder as [describeQuantity] sees it: a label and the weight it resolves to.
 *
 * Deliberately not [ScaleStep]. By the time a row is being summarised the steps have already been
 * resolved to weights, and carrying the factor/unit distinction this far would mean re-deriving
 * something the caller already knows.
 */
data class QuantityAnchor(val label: String, val grams: Double)

/** What the row knows about the ingredient it is summarising. */
data class QuantityContext(
    /**
     * Present in the corpus input and deliberately **not** the item branch's condition — see
     * [describeQuantity]. Carried because the caller has it and a later suite may need it.
     */
    val servingMode: String? = null,
    val itemUnitG: Double? = null,
    val itemLabel: String? = null,
    val portionG: Double,
    /** "g" or "ml". Drives both which weight is read and how it is rendered. */
    val displayUnit: String,
)

/**
 * The two lines of the collapsed-row summary. [secondary] is empty, not null, when there is nothing
 * to put under the headline.
 */
data class QuantityDescription(val primary: String, val secondary: String)

/**
 * The collapsed-row quantity summary — the one line a user reads without expanding anything.
 *
 * Three branches, tried in order:
 *
 * 1. **On an anchor.** Within `max(2, grams × 4 %)` of one, show that anchor's clean label. The 2 g
 *    floor is what keeps small portions usable: 4 % of a 16 g anchor is 0.64 g, a window nobody can
 *    hit by dragging. When two anchors both fall inside tolerance the **nearest** wins, not the first
 *    scanned — a port that returns on first match disagrees on that vector alone.
 * 2. **An "≈ descriptor" headline**, by item count when an item unit is known, otherwise by portion
 *    ratio, snapping to a named anchor when within 8 %. That window is deliberately wider than the
 *    4 % in branch 1: it is describing a neighbourhood, not claiming an exact hit.
 * 3. **Grams alone**, with an empty secondary line.
 *
 * **The nesting in branch 2 is load-bearing.** The item path is an if/else-if, not two independent
 * checks: below a quarter of an item, "≈ 0,1 carotte" is noise, so it falls through to grams — and
 * specifically *not* to the portion-ratio branch. Flattening those two into sibling conditions changes
 * what a 10 g sliver of carrot reads as.
 *
 * Pinned by the `describe-quantity`/`describe-quantity-en` suites of the shared behavioural corpus.
 */
fun describeQuantity(
    currentG: Double,
    anchors: List<QuantityAnchor>,
    context: QuantityContext,
    lang: String,
): QuantityDescription {
    val weight = formatWeight(currentG, context.displayUnit)

    // ── 1. On an anchor ──────────────────────────────────────────────────────
    val nearest = anchors
        .filter { abs(currentG - it.grams) <= maxOf(ANCHOR_FLOOR_GRAMS, it.grams * ANCHOR_TOLERANCE) }
        .minByOrNull { abs(currentG - it.grams) }
    if (nearest != null) return QuantityDescription(nearest.label, weight)

    // ── 2. An "≈ descriptor" headline ────────────────────────────────────────
    //
    // The item path is entered on having an item unit at all, NOT on servingMode. The corpus's
    // item-sliver case carries no servingMode and still takes this branch, which rules out the
    // obvious reading.
    val itemUnitG = context.itemUnitG
    val itemLabel = context.itemLabel
    if (itemUnitG != null && itemLabel != null && itemUnitG > 0) {
        val count = currentG / itemUnitG
        if (count >= MINIMUM_ITEM_COUNT) {
            val rounded = roundToOneDecimal(count)
            return QuantityDescription(
                primary = "≈ ${formatNumber(rounded, lang)} ${pluralize(itemLabel, rounded, lang)}",
                secondary = weight,
            )
        }
        // Deliberately falls through to branch 3, not to the portion ratio below.
    } else if (context.portionG > 0) {
        val ratio = currentG / context.portionG
        val named = namedFactors(lang).firstOrNull { (factor, _) ->
            abs(ratio - factor) / factor <= NAMED_TOLERANCE
        }
        if (named != null) return QuantityDescription("≈ ${named.second}", weight)

        val rounded = roundToOneDecimal(ratio)
        // "portion"/"serving" is pluralised by the same rule as everything else in the active
        // language. No vector reaches a ratio of 2 or more, so this is the consistent choice rather
        // than a pinned one.
        val portionWord = if (lang == "en") "serving" else "portion"
        return QuantityDescription(
            primary = "≈ ${formatNumber(rounded, lang)} ${pluralize(portionWord, rounded, lang)}",
            secondary = weight,
        )
    }

    // ── 3. Grams alone ───────────────────────────────────────────────────────
    return QuantityDescription(weight, "")
}

/**
 * Two formats in one function, both user-visible: grams render "64g" with no space, millilitres render
 * "270 mL" with a space and a capital L. Not a typo on either side — it is what the contract pins, and
 * the sub-one guard reads "< 1g" / "< 1 mL" so a trace of an ingredient never shows "0g".
 */
private fun formatWeight(grams: Double, displayUnit: String): String {
    val isVolume = displayUnit == "ml"
    if (grams < 1) return if (isVolume) "< 1 mL" else "< 1g"
    return if (isVolume) "${roundHalfUp(grams)} mL" else "${roundHalfUp(grams)}g"
}

/**
 * One decimal place, through the shared rounding rule rather than a formatter.
 *
 * This is where the whole `rounding` suite earns its place: 80 g against a 64 g portion is exactly
 * 1.25, so this evaluates `roundHalfUp(12.5) / 10`. Half-up gives 1,3 and kotlin.math.round's
 * ties-to-even gives 1,2 — no error either way, just two clients quietly disagreeing.
 */
private fun roundToOneDecimal(value: Double): Double = roundHalfUp(value * 10) / 10.0

private const val ANCHOR_TOLERANCE = 0.04
private const val ANCHOR_FLOOR_GRAMS = 2.0
private const val NAMED_TOLERANCE = 0.08

/** Below a quarter of an item, "≈ 0,1 carotte" is noise rather than information. */
private const val MINIMUM_ITEM_COUNT = 0.25
