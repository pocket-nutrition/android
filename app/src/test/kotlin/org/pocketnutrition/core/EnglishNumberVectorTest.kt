// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [formatEnglishNumber] against the `english-number` suite of the shared corpus — the mirror
 * of [FrenchNumberVectorTest], with the dot kept instead of swapped for a comma.
 */
class EnglishNumberVectorTest {

    @Test
    fun `every english-number vector holds`() {
        val suite = VectorCorpus.load("english-number")

        assertEquals(
            "english-number suite case count disagrees with index.json",
            VectorCorpus.index().suites["english-number"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val value = requireNotNull(case.input["value"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.content
            val actual = formatEnglishNumber(value)
            if (actual == expected) {
                null
            } else {
                "english-number/${case.id}: $value -> \"$actual\", expected \"$expected\" — ${case.why}"
            }
        }
        assertEquals("english-number vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `a whole number carries no decimal part`() {
        assertEquals("0", formatEnglishNumber(0.0))
        assertEquals("2", formatEnglishNumber(2.0))
        assertEquals("10", formatEnglishNumber(10.0))
    }

    @Test
    fun `a fractional value uses a dot and keeps its leading zero`() {
        assertEquals("1.1", formatEnglishNumber(1.1))
        assertEquals("0.5", formatEnglishNumber(0.5))
    }
}
