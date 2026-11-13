// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [stepLabel] against the `step-label`/`step-label-en` suites of the shared corpus.
 *
 * One case in this suite is load-bearing beyond its own assertion: `unit-count-two-pluralises` exists,
 * in the corpus author's words, to tie this suite to the plural rule "not by a second copy of the
 * logic living here". If someone inlines an `if (count > 1)` here, that case still passes — which is
 * why the delegation is asserted separately below.
 */
class StepLabelVectorTest {

    @Test
    fun `every step-label and step-label-en vector holds`() {
        listOf("step-label", "step-label-en").forEach { suiteName ->
            val suite = VectorCorpus.load(suiteName)

            assertEquals(
                "$suiteName suite case count disagrees with index.json",
                VectorCorpus.index().suites[suiteName],
                suite.cases.size,
            )

            val mismatches = suite.cases.mapNotNull { case ->
                val step = ScaleStepDecoding.step(requireNotNull(case.input["step"]))
                val lang = case.input["lang"]?.jsonPrimitive?.content ?: "fr"
                val expected = case.expected.jsonPrimitive.content
                val actual = stepLabel(step, lang)
                if (actual == expected) {
                    null
                } else {
                    "$suiteName/${case.id}: got \"$actual\", expected \"$expected\" — ${case.why}"
                }
            }
            assertEquals("$suiteName vectors that do not hold", emptyList<String>(), mismatches)
        }
    }

    @Test
    fun `a factor step's label is passed through untouched`() {
        // Asserted with a label the function could not have produced, so a port that rebuilds the
        // label instead of passing it through fails here rather than passing by coincidence.
        assertEquals("Petite", stepLabel(ScaleStep.FactorStep(0.5, "Petite"), "fr"))
        assertEquals("n'importe quoi", stepLabel(ScaleStep.FactorStep(1.0, "n'importe quoi"), "fr"))
    }

    @Test
    fun `pluralisation follows the French rule, not an inlined English one`() {
        // An inlined English rule renders "1,5 carottes" and every corpus case still passes, because
        // the suite has no fractional-count-above-one case.
        val carrot = ServingUnit("item", listOf(LocalizedText("fr", "carotte")), 0.0, 74.0)
        assertEquals("1,5 carotte", stepLabel(ScaleStep.UnitStep(carrot, 1.5), "fr"))
        assertEquals("2,5 carottes", stepLabel(ScaleStep.UnitStep(carrot, 2.5), "fr"))
    }

    @Test
    fun `pluralisation follows the English rule when lang is en`() {
        // The mirror check for English: the threshold is "anything but exactly 1", so 1.5 already
        // pluralises, unlike French's "1,5 carotte".
        val carrot = ServingUnit("item", listOf(LocalizedText("en", "carrot")), 0.0, 74.0)
        assertEquals("1.5 carrots", stepLabel(ScaleStep.UnitStep(carrot, 1.5), "en"))
        assertEquals("1 carrot", stepLabel(ScaleStep.UnitStep(carrot, 1.0), "en"))
    }

    @Test
    fun `the fraction table covers the counts the contract names`() {
        // Two of these four are unreachable from the current anchor ladder and so are absent from the
        // corpus, but the suite description names all four.
        val carrot = ServingUnit("item", listOf(LocalizedText("fr", "carotte")), 0.0, 74.0)
        listOf(0.25 to "¼", 0.33 to "⅓", 0.5 to "½", 0.75 to "¾").forEach { (count, glyph) ->
            assertEquals("$glyph carotte", stepLabel(ScaleStep.UnitStep(carrot, count), "fr"))
        }
    }
}
