// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * One quick button under the value field.
 *
 * [Increment.emoji] is nullable and genuinely absent rather than empty for an unrecognised unit — the
 * corpus emits no `emoji` key at all in that case, and the distinction matters: a port that
 * substitutes "" or a placeholder glyph renders a gap where the design expects nothing.
 */
sealed interface Shortcut {
    data class Multiplier(val label: String, val factor: Double) : Shortcut
    data class Increment(val label: String, val emoji: String?, val grams: Int) : Shortcut
}

/**
 * The row of at most four quick buttons under the value field.
 *
 * Two shapes:
 *
 * - **No usable serving unit** → the three base-portion multipliers, Demi / 1 Part / Double. Three
 *   entries, not four: no anchor is added on this path, because the multipliers already include 1.
 * - **At least one** → a single "1 Part" anchor followed by up to three Knowledge increments, in
 *   whatever order Knowledge returned them. They are deliberately not sorted by weight.
 *
 * Two filters decide "usable". The item unit is excluded, because a countable thing is already covered
 * by the multipliers and offering "1 carotte" beside "1 Part" is the same button twice. And a unit
 * whose weight rounds below 1 is dropped, because a 0,2 g pinch is a button that adds nothing. Either
 * filter can empty the list, and when it does the multipliers come back — which is how the stated
 * invariant holds: **the result always has between 2 and 4 entries.**
 *
 * Pinned by the `shortcut-profile` suite of the shared behavioural corpus.
 */
fun shortcutProfile(servingUnits: List<ServingUnit>, displayUnit: String): List<Shortcut> {
    val increments = servingUnits
        .filterNot { it.isItem }
        .map { it to roundHalfUp(measure(it, displayUnit)) }
        .filter { (_, grams) -> grams >= 1 }
        .take(MAXIMUM_INCREMENTS)
        .map { (unit, grams) ->
            Shortcut.Increment(label = unit.labelFr, emoji = servingUnitEmoji(unit.unit), grams = grams)
        }

    if (increments.isEmpty()) return BASE_MULTIPLIERS
    return listOf(Shortcut.Multiplier(label = "1 Part", factor = 1.0)) + increments
}

/**
 * Which of the two weights a unit contributes.
 *
 * **A naming wart worth knowing before porting.** For a volume food the increment comes from
 * `volumeMl` while the output field is still called `grams`. Reading `grams` for a millilitre food
 * gives 0, the unit is then dropped as sub-gram, and the row silently degrades to the multipliers —
 * no error anywhere.
 */
private fun measure(unit: ServingUnit, displayUnit: String): Double =
    if (displayUnit == "ml") unit.volumeMl else unit.grams

/**
 * The fallback path when nothing is usable.
 *
 * These three labels differ from the anchor ladder's — `portionAnchors.half` is "Demi" and `.onePart`
 * is "1 Part", where the ladder uses "Petite" and "1 part" with a lowercase p. Two similar-looking
 * sets that are not the same set.
 */
private val BASE_MULTIPLIERS = listOf(
    Shortcut.Multiplier("Demi", 0.5),
    Shortcut.Multiplier("1 Part", 1.0),
    Shortcut.Multiplier("Double", 2.0),
)

/** One anchor plus three increments fills the row. */
private const val MAXIMUM_INCREMENTS = 3

/**
 * Cosmetic icon per serving-unit type, hardcoded because this is a pure function over a closed
 * vocabulary — and pinned entry by entry by the `serving-unit-emoji` suite, so drift fails a test.
 *
 * Seven of the ten units in `pocket-nutrition-knowledge/serving_unit_types.yaml` carry a glyph.
 * `shot`, `bowl` and `scoop` deliberately do not: none has an unambiguous glyph, 🥣 is already the cup,
 * and a wrong icon is worse than none. A unit outside the vocabulary is treated the same way, so
 * nothing here ever needs a placeholder.
 *
 * Before contracts 1.3.0 the corpus pinned only three of these entries, so this table shipped
 * incomplete. Filling it in from the sandbox was not an option — that repo was AGPL-3.0 to this one's
 * MPL-2.0 — so the map was pinned upstream as its own suite instead. Adding a glyph still means a
 * vector first.
 */
fun servingUnitEmoji(unit: String): String? = when (unit) {
    "pinch" -> "🤏"
    "teaspoon", "tablespoon" -> "🥄"
    "cup" -> "🥣"
    "handful" -> "✊"
    "small_handful" -> "🤲"
    "glass" -> "🥛"
    else -> null
}
