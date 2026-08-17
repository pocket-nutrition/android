// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.Serializable

/**
 * One way an ingredient can be portioned, as Knowledge describes it.
 *
 * [volumeMl] is 0 for countable things and non-zero for spoons and cups. Both measures are carried
 * even where only one is read, because they arrive together from [IngredientSuggestion.servingUnits]
 * and dropping one here would mean re-fetching to get it back.
 *
 * `@Serializable` because it arrives inside a search response. Unlike the iOS twin — a separate
 * SwiftPM module holding a hard no-imports rule — this `core` package lives in the app module and
 * already has kotlinx.serialization on its classpath, so there is no invariant to protect here.
 */
@Serializable
data class ServingUnit(
    val unit: String,
    val labelFr: String,
    val volumeMl: Double,
    val grams: Double,
) {
    /**
     * The one unit kind that can be counted rather than measured. [buildAnchors] needs both this
     * *and* a serving mode of "item" before it will count anything.
     */
    val isItem: Boolean get() = unit == "item"
}

/**
 * One rung of the portion ladder.
 *
 * Two shapes rather than one with nullable fields, because the two are read differently everywhere: a
 * factor step scales the base portion and carries a ready-made label, a unit step multiplies a unit
 * weight and has its label built from the count. `step-raw-weight` puts it plainly — unit steps
 * "IGNORE the base portion entirely".
 *
 * The corpus calls the second kind "unit"; it is [UnitStep] here rather than `Unit` because that name
 * is taken by kotlin.Unit and shadowing it inside a sealed hierarchy is a trap for the next reader.
 * The iOS twin, with no such collision, spells the cases `.factor` and `.unit`.
 */
sealed interface ScaleStep {
    data class FactorStep(val factor: Double, val label: String) : ScaleStep
    data class UnitStep(val unit: ServingUnit, val count: Double) : ScaleStep
}
