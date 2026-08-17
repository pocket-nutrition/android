// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Asserts [formatFrenchNumber] against the `french-number` suite of the shared corpus — the same JSON
 * the sandbox and iOS assert against.
 *
 * Every case in the suite is aimed at a different way a locale-aware formatter goes wrong: a dropped
 * leading zero, an inserted thousands separator, a forced decimal place on a whole number. Swapping
 * this implementation for a `NumberFormat` configured for `Locale.FRANCE` is the obvious refactor and
 * it fails here, which is the point.
 */
class FrenchNumberVectorTest {

    @Test
    fun `every french-number vector holds`() {
        val suite = VectorCorpus.load("french-number")

        assertEquals(
            "french-number suite case count disagrees with index.json",
            VectorCorpus.index().suites["french-number"],
            suite.cases.size,
        )

        // Collected rather than asserted per case, so a wrong port reports every vector it breaks
        // instead of stopping at the first.
        val mismatches = suite.cases.mapNotNull { case ->
            val value = requireNotNull(case.input["value"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.content
            val actual = formatFrenchNumber(value)
            if (actual == expected) {
                null
            } else {
                "french-number/${case.id}: $value -> \"$actual\", expected \"$expected\" — ${case.why}"
            }
        }
        assertEquals("french-number vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `a whole number carries no decimal part`() {
        // The rule a formatter-based port breaks first, named here as well as covered by the suite.
        assertEquals("0", formatFrenchNumber(0.0))
        assertEquals("2", formatFrenchNumber(2.0))
        assertEquals("10", formatFrenchNumber(10.0))
    }

    @Test
    fun `a fractional value uses a comma and keeps its leading zero`() {
        assertEquals("1,1", formatFrenchNumber(1.1))
        assertEquals("0,5", formatFrenchNumber(0.5))
        assertFalse(
            "a value below one must keep its leading zero",
            formatFrenchNumber(0.5).startsWith(","),
        )
    }
}
