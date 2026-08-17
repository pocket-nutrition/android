// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [stepLabel] against the `step-label` suite of the shared corpus.
 *
 * One case in this suite is load-bearing beyond its own assertion: `unit-count-two-pluralises` exists,
 * in the corpus author's words, to tie this suite to the plural rule "not by a second copy of the
 * logic living here". If someone inlines an `if (count > 1)` here, that case still passes — which is
 * why the delegation is asserted separately below.
 */
class StepLabelVectorTest {

    @Test
    fun `every step-label vector holds`() {
        val suite = VectorCorpus.load("step-label")

        assertEquals(
            "step-label suite case count disagrees with index.json",
            VectorCorpus.index().suites["step-label"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val step = ScaleStepDecoding.step(requireNotNull(case.input["step"]))
            val expected = case.expected.jsonPrimitive.content
            val actual = stepLabel(step)
            if (actual == expected) {
                null
            } else {
                "step-label/${case.id}: got \"$actual\", expected \"$expected\" — ${case.why}"
            }
        }
        assertEquals("step-label vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `a factor step's label is passed through untouched`() {
        // Asserted with a label the function could not have produced, so a port that rebuilds the
        // label instead of passing it through fails here rather than passing by coincidence.
        assertEquals("Petite", stepLabel(ScaleStep.FactorStep(0.5, "Petite")))
        assertEquals("n'importe quoi", stepLabel(ScaleStep.FactorStep(1.0, "n'importe quoi")))
    }

    @Test
    fun `pluralisation follows the French rule, not an inlined English one`() {
        // An inlined English rule renders "1,5 carottes" and every corpus case still passes, because
        // the suite has no fractional-count-above-one case.
        val carrot = ServingUnit("item", "carotte", 0.0, 74.0)
        assertEquals("1,5 carotte", stepLabel(ScaleStep.UnitStep(carrot, 1.5)))
        assertEquals("2,5 carottes", stepLabel(ScaleStep.UnitStep(carrot, 2.5)))
    }

    @Test
    fun `the fraction table covers the counts the contract names`() {
        // Two of these four are unreachable from the current anchor ladder and so are absent from the
        // corpus, but the suite description names all four.
        val carrot = ServingUnit("item", "carotte", 0.0, 74.0)
        listOf(0.25 to "¼", 0.33 to "⅓", 0.5 to "½", 0.75 to "¾").forEach { (count, glyph) ->
            assertEquals("$glyph carotte", stepLabel(ScaleStep.UnitStep(carrot, count)))
        }
    }
}
