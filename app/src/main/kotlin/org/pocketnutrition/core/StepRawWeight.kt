// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * The raw weight one rung of the portion ladder resolves to.
 *
 * A factor step scales the base portion. A unit step multiplies the unit weight and **ignores the
 * base portion entirely** — two carrots weigh 148 g whatever portion size was passed in. A port that
 * multiplies by both is wrong by a factor of the base portion, which produces a plausible-looking
 * number rather than an obvious one.
 *
 * Returns an unrounded value on purpose: half of 75 g is 37.5 g and stays 37.5 here. Rounding happens
 * once, at display or storage, never in the middle of a calculation — rounding twice is how a total
 * stops matching the sum of its rows.
 *
 * Pinned by the `step-raw-weight` suite of the shared behavioural corpus.
 */
fun stepRawWeight(step: ScaleStep, basePortionG: Double): Double = when (step) {
    is ScaleStep.FactorStep -> step.factor * basePortionG
    is ScaleStep.UnitStep -> step.count * step.unit.grams
}
