// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Asserts [stepRawWeight] against the `step-raw-weight` suite of the shared corpus. */
class StepRawWeightVectorTest {

    @Test
    fun `every step-raw-weight vector holds`() {
        val suite = VectorCorpus.load("step-raw-weight")

        assertEquals(
            "step-raw-weight suite case count disagrees with index.json",
            VectorCorpus.index().suites["step-raw-weight"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val step = ScaleStepDecoding.step(requireNotNull(case.input["step"]))
            val basePortionG = requireNotNull(case.input["basePortionG"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.double
            val actual = stepRawWeight(step, basePortionG)
            if (actual == expected) {
                null
            } else {
                "step-raw-weight/${case.id}: got $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("step-raw-weight vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `a unit step ignores the base portion entirely`() {
        // The corpus passes 75 every time, so a port that multiplied by the base would need only
        // that one value changed to be caught. Asserted across three wildly different bases.
        val carrot = ServingUnit("item", "carotte", 0.0, 74.0)
        listOf(0.0, 75.0, 1000.0).forEach { base ->
            assertEquals(
                "base portion $base must not affect a unit step",
                148.0,
                stepRawWeight(ScaleStep.UnitStep(carrot, 2.0), base),
                0.0,
            )
        }
    }

    @Test
    fun `a factor step is not rounded`() {
        // Rounding in the middle of a chain is how a total stops matching the sum of its rows.
        assertEquals(37.5, stepRawWeight(ScaleStep.FactorStep(0.5, "½"), 75.0), 0.0)
        assertEquals(2.5, stepRawWeight(ScaleStep.FactorStep(0.25, "¼"), 10.0), 0.0)
    }
}
