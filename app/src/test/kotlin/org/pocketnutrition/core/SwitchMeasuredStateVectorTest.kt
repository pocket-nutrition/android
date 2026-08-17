// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [switchMeasuredState] against the `switch-measured-state` suite.
 *
 * The suite exists because this conversion is the one that can be wrong by 15–40% while every screen
 * still looks right, and because it spent its whole life inline in a React component where no test could
 * reach it. The case that earns its place on THIS platform is `to-cooked-hits-a-rounding-tie`:
 * 50 × 0.85 = 42.5, which is 43 under [roundHalfUp] and **42** under [kotlin.math.round]. The obvious
 * Kotlin port is the wrong one, so the corpus has to be able to say so.
 */
class SwitchMeasuredStateVectorTest {

    @Test
    fun `every switch-measured-state vector holds`() {
        val suite = VectorCorpus.load("switch-measured-state")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["switch-measured-state"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val rawIngredient = requireNotNull(case.input["ingredient"]) {
                "${case.id}: input has no ingredient"
            }
            val to = requireNotNull(case.input["to"]) { "${case.id}: input has no `to`" }
                .jsonPrimitive.content

            val ingredient = SuggestionDecoding.decode<MealIngredient>(rawIngredient)
            val expected = SuggestionDecoding.decode<MealIngredient>(case.expected)
            val actual = switchMeasuredState(ingredient, to)

            if (actual == expected) {
                null
            } else {
                "${case.id}: ${actual.measuredState}/${actual.quantity} != " +
                    "${expected.measuredState}/${expected.quantity} — ${case.why}"
            }
        }
        assertEquals("switch-measured-state vectors that do not hold", emptyList<String>(), failures)
    }

    /**
     * Flipping twice must land on the number the user started from. Not a vector, because the corpus pins
     * single calls and this is a property of composing two — and it is the property a user actually
     * exercises, by pressing the wrong button and then the other one.
     */
    @Test
    fun `the toggle round-trips`() {
        val raw = MealIngredient(
            id = "row-1",
            label = "poulet",
            ingredientId = "chicken",
            cookingMethod = "grilled",
            quantity = 100.0,
            unit = "g",
            measuredState = "raw",
            basePortionG = 100.0,
            portionFactor = 0.0,
            servingUnits = emptyList(),
            selectedUnit = null,
            unitCount = 1.0,
            displayUnit = "g",
            canEatRaw = false,
            yieldFactors = mapOf("grilled" to 0.7),
            imageUrl = null,
            servingMode = "weight",
        )

        val cooked = switchMeasuredState(raw, "cooked")
        val backToRaw = switchMeasuredState(cooked, "raw")

        assertEquals("100 g raw at yield 0.7 is 70 g cooked", 70.0, cooked.quantity, 0.0)
        assertEquals("the round trip must return the original weight", 100.0, backToRaw.quantity, 0.0)
        assertEquals("the caller's row was mutated", 100.0, raw.quantity, 0.0)
    }
}
