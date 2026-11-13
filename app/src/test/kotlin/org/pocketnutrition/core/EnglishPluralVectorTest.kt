// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [englishPlural] against the `english-plural` suite of the shared corpus.
 *
 * The boundary this suite pins is the opposite of [FrenchPluralVectorTest]'s: English pluralises
 * anything but exactly 1, so 1.5 is already plural, where French keeps 1.5 singular.
 */
class EnglishPluralVectorTest {

    @Test
    fun `every english-plural vector holds`() {
        val suite = VectorCorpus.load("english-plural")

        assertEquals(
            "english-plural suite case count disagrees with index.json",
            VectorCorpus.index().suites["english-plural"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val label = requireNotNull(case.input["label"]).jsonPrimitive.content
            val count = requireNotNull(case.input["count"]).jsonPrimitive.double
            val expected = case.expected.jsonPrimitive.content
            val actual = englishPlural(label, count)
            if (actual == expected) {
                null
            } else {
                "english-plural/${case.id}: (\"$label\", $count) -> \"$actual\", " +
                    "expected \"$expected\" — ${case.why}"
            }
        }
        assertEquals("english-plural vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `the plural holds at any count but exactly one`() {
        // Where French keeps 1.5 singular, English is already plural here.
        assertEquals("carrots", englishPlural("carrot", 1.5))
        assertEquals("carrot", englishPlural("carrot", 1.0))
        assertEquals("carrots", englishPlural("carrot", 0.5))
    }

    @Test
    fun `a word already ending in s is left alone`() {
        assertEquals("hummus", englishPlural("hummus", 3.0))
    }
}
