// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [resolveYieldFactor] against the `yield-factor` suite of the shared corpus.
 *
 * The comparisons use a delta of exactly 0.0, deliberately. The function performs no arithmetic — it
 * selects a value from Knowledge and returns it verbatim — so any drift from 2.2286 means the wrong
 * branch was taken, not that a float wobbled. A real tolerance would hide the very bug the suite
 * exists to catch.
 */
class YieldFactorVectorTest {

    @Test
    fun `every yield-factor vector holds`() {
        val suite = VectorCorpus.load("yield-factor")

        assertEquals(
            "yield-factor suite case count disagrees with index.json",
            VectorCorpus.index().suites["yield-factor"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val factors = requireNotNull(case.input["yieldFactors"]).jsonObject
                .mapValues { (_, element) -> element.jsonPrimitive.double }
            val method = requireNotNull(case.input["method"]).jsonPrimitive.content
            val expected = case.expected.jsonPrimitive.double
            val actual = resolveYieldFactor(factors, method)
            if (actual == expected) {
                null
            } else {
                "yield-factor/${case.id}: ($factors, \"$method\") -> $actual, " +
                    "expected $expected — ${case.why}"
            }
        }
        assertEquals("yield-factor vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `raw wins over a present cooked factor`() {
        // Branch order is the one thing a port can get wrong while still looking right: checking raw
        // after the map lookup passes every other case in the suite and returns 2.2286 for raw food.
        assertEquals(1.0, resolveYieldFactor(mapOf("cooked" to 2.2286), "raw"), 0.0)
    }

    @Test
    fun `a method with no key of its own falls back to cooked`() {
        // The rice bug, named.
        assertEquals(2.2286, resolveYieldFactor(mapOf("cooked" to 2.2286), "boiled"), 0.0)
        assertEquals(
            0.9,
            resolveYieldFactor(mapOf("boiled" to 0.9, "cooked" to 2.2), "boiled"),
            0.0,
        )
    }

    @Test
    fun `an absent yield returns one rather than a guess`() {
        assertEquals(1.0, resolveYieldFactor(emptyMap(), "boiled"), 0.0)
    }
}
