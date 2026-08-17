// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asserts [shortcutProfile] against the `shortcut-profile` suite of the shared corpus. */
class ShortcutProfileVectorTest {

    @Test
    fun `every shortcut-profile vector holds`() {
        val suite = VectorCorpus.load("shortcut-profile")

        assertEquals(
            "shortcut-profile suite case count disagrees with index.json",
            VectorCorpus.index().suites["shortcut-profile"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            val units = requireNotNull(case.input["servingUnits"]).jsonArray
                .map(ScaleStepDecoding::servingUnit)
            val displayUnit = requireNotNull(case.input["displayUnit"]).jsonPrimitive.content
            val expected = case.expected.jsonArray.map(ScaleStepDecoding::shortcut)

            val actual = shortcutProfile(units, displayUnit)
            if (actual == expected) {
                null
            } else {
                "shortcut-profile/${case.id}: got $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("shortcut-profile vectors that do not hold", emptyList<String>(), mismatches)
    }

    @Test
    fun `the row always holds between two and four entries`() {
        // The suite states this invariant in prose and no single vector can express it.
        VectorCorpus.load("shortcut-profile").cases.forEach { case ->
            val units = requireNotNull(case.input["servingUnits"]).jsonArray
                .map(ScaleStepDecoding::servingUnit)
            val displayUnit = requireNotNull(case.input["displayUnit"]).jsonPrimitive.content
            val size = shortcutProfile(units, displayUnit).size
            assertTrue("shortcut-profile/${case.id} produced $size entries", size in 2..4)
        }

        assertTrue(shortcutProfile(emptyList(), "g").size in 2..4)
        // Ten usable units must still cap at one anchor plus three increments.
        val many = (1..10).map { ServingUnit("cup", "tasse $it", 0.0, it * 10.0) }
        assertEquals(4, shortcutProfile(many, "g").size)
    }

    @Test
    fun `a volume food reads volumeMl, not grams`() {
        // Reading the wrong one yields 0, the unit is dropped as sub-gram, and the row silently
        // degrades to the multipliers.
        val spoon = ServingUnit("tablespoon", "c. à soupe", 15.0, 0.0)
        assertEquals(
            listOf(
                Shortcut.Multiplier("1 Part", 1.0),
                Shortcut.Increment("c. à soupe", "🥄", 15),
            ),
            shortcutProfile(listOf(spoon), "ml"),
        )
        assertEquals(3, shortcutProfile(listOf(spoon), "g").size)
    }

    @Test
    fun `an unrecognised unit has no emoji`() {
        // The corpus omits the key rather than emitting null, so null here is the correct model and
        // "" would be a different thing.
        val scoop = ServingUnit("scoop", "boule", 0.0, 50.0)
        assertEquals(
            listOf(
                Shortcut.Multiplier("1 Part", 1.0),
                Shortcut.Increment("boule", null, 50),
            ),
            shortcutProfile(listOf(scoop), "g"),
        )
    }
}
