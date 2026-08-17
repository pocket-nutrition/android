// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [frenchPlural] against the `french-plural` suite of the shared corpus.
 *
 * What the suite really pins is the boundary. An English-shaped rule — plural above one — passes four
 * of these six cases; only 1.5 and 2 tell the two rules apart. That is why both are in the corpus, and
 * why they are named again below rather than left to the loop.
 */
class FrenchPluralVectorTest {

    @Test
    fun `every french-plural vector holds`() {
        val suite = VectorCorpus.load("french-plural")

        assertEquals(
            "french-plural suite case count disagrees with index.json",
            VectorCorpus.index().suites["french-plural"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val label = requireNotNull(case.input["label"]).jsonPrimitive.content
            val count = requireNotNull(case.input["count"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.content
            val actual = frenchPlural(label, count)
            if (actual == expected) {
                null
            } else {
                "french-plural/${case.id}: (\"$label\", $count) -> \"$actual\", " +
                    "expected \"$expected\" — ${case.why}"
            }
        }
        assertEquals("french-plural vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `the singular holds below two, not below one`() {
        // An English rule would differ on exactly one of these two, so they are asserted as a pair.
        assertEquals("carotte", frenchPlural("carotte", 1.5))
        assertEquals("carottes", frenchPlural("carotte", 2.0))
    }

    @Test
    fun `a word already ending in s is left alone`() {
        // Cheap to get wrong, and the result — "ananass" — is the kind of thing that ships.
        assertEquals("ananas", frenchPlural("ananas", 3.0))
    }
}
