// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [applyMethodChange] against the `apply-method-change` suite.
 *
 * The suite's own description says branch order "matters and is part of the contract". It does, because
 * the branches overlap: a row can have both a portion factor and a cooked weight, and the two rules
 * produce different numbers. `id` is supplied in this suite's input, so it is asserted too — unlike
 * `build-meal-ingredient`, nothing here is excluded.
 */
class ApplyMethodChangeVectorTest {

    @Test
    fun `every apply-method-change vector holds`() {
        val suite = VectorCorpus.load("apply-method-change")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["apply-method-change"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val rawIngredient = requireNotNull(case.input["ingredient"]) {
                "${case.id}: input has no ingredient"
            }
            val newMethod = requireNotNull(case.input["newMethod"]) {
                "${case.id}: input has no newMethod"
            }.jsonPrimitive.content

            val ingredient = SuggestionDecoding.decode<MealIngredient>(rawIngredient)
            val expected = SuggestionDecoding.decode<MealIngredient>(case.expected)
            val actual = applyMethodChange(ingredient, newMethod)

            if (actual == expected) {
                null
            } else {
                "${case.id}: ${actual.cookingMethod}/${actual.measuredState}/${actual.quantity} != " +
                    "${expected.cookingMethod}/${expected.measuredState}/${expected.quantity} — ${case.why}"
            }
        }
        assertEquals("apply-method-change vectors that do not hold", emptyList<String>(), failures)
    }

    /**
     * Branch 3 leaves `measuredState` alone: the weight was cooked before and is still cooked after, just
     * cooked differently. Asserted by name because "fixing" it to `cooked` is a no-op today and would be
     * a lie the moment branch 3 became reachable from a raw weight.
     */
    @Test
    fun `a method swap on a cooked weight leaves the measured state alone`() {
        val row = MealIngredient(
            id = "row-1", label = "riz", ingredientId = "rice",
            cookingMethod = "boiled", quantity = 200.0, unit = "g", measuredState = "cooked",
            // portionFactor 0 so branch 2 cannot claim it — this is the branch-3 path.
            basePortionG = 80.0, portionFactor = 0.0,
            yieldFactors = mapOf("boiled" to 2.5, "steamed" to 2.0),
        )

        val steamed = applyMethodChange(row, "steamed")

        assertEquals("branch 3 must not rewrite measuredState", "cooked", steamed.measuredState)
        assertEquals("steamed", steamed.cookingMethod)
        // 200 boiled -> 80 raw equivalent -> 160 steamed. Rounding lands once, at the end.
        assertEquals(160.0, steamed.quantity, 0.0)
        // The caller's row is untouched: data class copy(), not mutation.
        assertEquals("boiled", row.cookingMethod)
        assertEquals(200.0, row.quantity, 0.0)
        assertEquals("identity must survive a method change", row.id, steamed.id)
    }

    /**
     * `portionFactor > 0` rather than a truthiness test. A row where the user typed a weight directly
     * carries 0, and treating that as "has a factor" would recompute from the base and silently discard
     * what they typed.
     */
    @Test
    fun `a zero portion factor does not recompute from the base`() {
        val typed = MealIngredient(
            id = "row-1", label = "pomme de terre", ingredientId = "potato",
            cookingMethod = "raw", quantity = 237.0, unit = "g", measuredState = "raw",
            basePortionG = 80.0, portionFactor = 0.0,
            yieldFactors = mapOf("boiled" to 0.9),
        )

        // Target raw, raw weight, no factor: branch 1's final fallback keeps the quantity as typed.
        assertEquals(237.0, applyMethodChange(typed, "raw").quantity, 0.0)

        // Target cooked, no factor, weight already raw: branch 4, method only.
        val boiled = applyMethodChange(typed, "boiled")
        assertEquals("branch 4 must not touch the weight", 237.0, boiled.quantity, 0.0)
        assertEquals("raw", boiled.measuredState)
        assertEquals("boiled", boiled.cookingMethod)
    }

    /**
     * roundHalfUp, not kotlin.math.round. The suite has a tie case; this states the rule directly,
     * because ties-to-even would pass most weights and quietly differ on the halves.
     */
    @Test
    fun `a half portion lands on the larger gram`() {
        val row = MealIngredient(
            id = "row-1", label = "x", ingredientId = "x",
            cookingMethod = "boiled", quantity = 100.0, unit = "g", measuredState = "cooked",
            // 0.5 * 125 = 62.5 — a tie. Half-up gives 63; ties-to-even would give 62.
            basePortionG = 125.0, portionFactor = 0.5,
            yieldFactors = mapOf("boiled" to 1.0),
        )
        assertEquals(63.0, applyMethodChange(row, "raw").quantity, 0.0)
    }
}
