// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Asserts [servingUnitEmoji] against the `serving-unit-emoji` suite of the shared corpus.
 *
 * This suite exists because `shortcut-profile` could only ever exercise the two or three units its own
 * cases happen to use, which left four of the seven mapped glyphs unpinned and this client shipping an
 * incomplete table. Every entry now has a vector.
 *
 * `expected` is an explicit JSON null for the units with no glyph, not an absent key — so
 * [ScaleStepDecoding.nullableString] is what reads it, and `jsonPrimitive.content` would have turned
 * that null into the string "null" and passed for the wrong reason.
 */
class ServingUnitEmojiVectorTest {

    @Test
    fun `every serving-unit-emoji vector holds`() {
        val suite = VectorCorpus.load("serving-unit-emoji")

        assertEquals(
            "serving-unit-emoji suite case count disagrees with index.json",
            VectorCorpus.index().suites["serving-unit-emoji"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val unit = requireNotNull(case.input["unit"]).jsonPrimitive.content
            val expected = ScaleStepDecoding.nullableString(case.expected)
            val actual = servingUnitEmoji(unit)
            if (actual == expected) {
                null
            } else {
                "serving-unit-emoji/${case.id}: $unit -> $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("serving-unit-emoji vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `every unit in the closed vocabulary is accounted for`() {
        // Asserted by name so a unit added to Knowledge without a decision here is visible rather than
        // silently iconless — the suite would still pass, since an unmapped unit and an unknown one
        // look identical from outside.
        val withGlyph = listOf("pinch", "teaspoon", "tablespoon", "cup", "handful", "small_handful", "glass")
        val withoutGlyph = listOf("shot", "bowl", "scoop")

        withGlyph.forEach { assertNotNull("$it is mapped in the corpus but not here", servingUnitEmoji(it)) }
        withoutGlyph.forEach { assertNull("$it has no glyph by contract", servingUnitEmoji(it)) }
        assertEquals("serving_unit_types.yaml declares ten", 10, withGlyph.size + withoutGlyph.size)
    }

    @Test
    fun `the two spoons share a glyph`() {
        // shortcut-profile only ever asserts one of them per case, so a port that keys one and forgets
        // the other passes that suite entirely.
        assertEquals("🥄", servingUnitEmoji("teaspoon"))
        assertEquals("🥄", servingUnitEmoji("tablespoon"))
    }
}
