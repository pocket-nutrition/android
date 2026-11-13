// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * The label on one rung of the portion ladder.
 *
 * A factor step is already labelled — [buildAnchors] gave it "Petite" or "¼" — so it is returned
 * verbatim and nothing is formatted.
 *
 * A unit step is built: `"<count> <unit>"`, where the count becomes a typographic fraction when the
 * table has one, and the unit is pluralised by [pluralize] rather than by a second copy of that rule
 * living here. That delegation is deliberate and the corpus says so: the `unit-count-two` vector
 * exists specifically to tie this suite to the plural rule, so fixing French plurals in Knowledge
 * later fixes them here too.
 *
 * Pinned by the `step-label`/`step-label-en` suites of the shared behavioural corpus.
 */
fun stepLabel(step: ScaleStep, lang: String): String = when (step) {
    is ScaleStep.FactorStep -> step.label
    is ScaleStep.UnitStep -> {
        val label = pickLocalized(step.unit.labels, lang) ?: step.unit.unit
        "${countLabel(step.count, lang)} ${pluralize(label, step.count, lang)}"
    }
}

/**
 * "½ carotte"/"½ carrots" reads better on a button than "0,5 carotte"/"0.5 carrots".
 *
 * **Keyed on the count, not on the factor** — the corpus is explicit about that. The same 0.5 can
 * arrive from a half-item step or from a factor step against a doubled base, and only the former is a
 * half of anything the label names.
 *
 * A count with no entry falls back to [formatNumber], so 3 renders "3" and an unexpected 2.5
 * renders "2,5"/"2.5" rather than breaking the row.
 */
private fun countLabel(count: Double, lang: String): String = FRACTIONS[count] ?: formatNumber(count, lang)

private val FRACTIONS = mapOf(
    0.25 to "¼",
    0.33 to "⅓",
    0.5 to "½",
    0.75 to "¾",
)
