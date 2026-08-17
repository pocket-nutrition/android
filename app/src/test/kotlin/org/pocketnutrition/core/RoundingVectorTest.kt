// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts [roundHalfUp] against the `rounding` suite of the shared corpus — the same JSON the sandbox
 * and iOS assert against.
 *
 * This is the first behavioural function ported, and it is first for a reason: every weight and every
 * portion label in the app passes through it, and Kotlin's own `round` gets it wrong. Swapping the
 * implementation for `kotlin.math.round` fails three cases here — tie-half, tie-two-and-a-half,
 * tie-twelve-and-a-half — which is precisely what the corpus exists to do.
 */
class RoundingVectorTest {

    @Test
    fun `every rounding vector holds`() {
        val suite = VectorCorpus.load("rounding")

        // Guard against a truncated corpus: iterating cases alone would pass on an empty list, so the
        // count is checked against what index.json declares.
        assertEquals(
            "rounding suite case count disagrees with index.json",
            VectorCorpus.index().suites["rounding"],
            suite.cases.size,
        )

        // Collected rather than asserted per case: assertEquals inside the loop aborts at the first
        // mismatch, which would report one failure for a port that actually breaks three vectors.
        // Seeing all of them at once is the difference between "rounding is off somewhere" and
        // "ties are being broken the wrong way".
        val mismatches = suite.cases.mapNotNull { case ->
            val input = requireNotNull(case.input["value"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.int
            val actual = roundHalfUp(input)
            if (actual == expected) {
                null
            } else {
                "rounding/${case.id}: $input -> $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("rounding vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `the tie cases that catch a wrong port are present`() {
        // A port could pass the suite by quietly deleting the awkward cases. These three are where
        // ties-to-even disagrees, so their presence is asserted by id rather than assumed.
        val ids = VectorCorpus.load("rounding").cases.map { it.id }.toSet()
        listOf("tie-half", "tie-two-and-a-half", "tie-twelve-and-a-half").forEach { id ->
            assertTrue("rounding/$id is missing — the tie rule would be unpinned", id in ids)
        }
    }
}
