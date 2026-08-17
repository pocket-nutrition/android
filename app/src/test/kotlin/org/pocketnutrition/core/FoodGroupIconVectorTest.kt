// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Asserts [foodGroupIcon] against the `food-group-icon` suite of the shared corpus.
 *
 * The map exists because Open Food Facts supplies a photo for roughly four fifths of its products while
 * the knowledge base has no image field at all — so a row drawing only `imageUrl` gave every packaged
 * product a photograph and every curated ingredient a blank, making the industrial result look more
 * trustworthy than the reference one.
 *
 * `expected` is an explicit JSON null for the three unmapped groups, not an absent key, so
 * [ScaleStepDecoding.nullableString] is what reads it — `jsonPrimitive.content` would turn that null into
 * the string "null" and pass for the wrong reason.
 */
class FoodGroupIconVectorTest {

    @Test
    fun `every food-group-icon vector holds`() {
        val suite = VectorCorpus.load("food-group-icon")

        assertEquals(
            "food-group-icon suite case count disagrees with index.json",
            VectorCorpus.index().suites["food-group-icon"],
            suite.cases.size,
        )

        val mismatches = suite.cases.mapNotNull { case ->
            // The input is nullable on purpose: one case pins that a null group yields no icon rather
            // than throwing, so it must be read the same way as the expectation.
            val group = ScaleStepDecoding.nullableString(requireNotNull(case.input["foodGroup"]) {
                "${case.id}: input has no foodGroup key"
            })
            val expected = ScaleStepDecoding.nullableString(case.expected)
            val actual = foodGroupIcon(group)
            if (actual == expected) {
                null
            } else {
                "food-group-icon/${case.id}: $group -> $actual, expected $expected — ${case.why}"
            }
        }
        assertEquals("food-group-icon vectors that do not hold", emptyList<String>(), mismatches)
    }

    /**
     * Every value the schema declares is accounted for, one way or the other. Asserted by name because
     * the suite cannot catch a group added to Knowledge and forgotten here — an unmapped group and an
     * unknown one both return null, so the vectors would still pass while real ingredients lost an icon.
     */
    @Test
    fun `all 21 declared food groups are accounted for`() {
        MAPPED_GROUPS.forEach { group ->
            assertNotNull("$group is mapped in the corpus but not here", foodGroupIcon(group))
        }
        UNMAPPED_GROUPS.forEach { group ->
            assertNull("$group has no glyph by contract", foodGroupIcon(group))
        }
        assertEquals(
            "ingredient.schema.json declares 21 food groups",
            21,
            MAPPED_GROUPS.size + UNMAPPED_GROUPS.size,
        )
    }

    /**
     * No two groups may share a glyph. The suite asserts each mapping in isolation, so it would pass a
     * port that mapped both `meat` and `processed_meat` to 🥩 — and the list's whole purpose is telling
     * rows apart.
     */
    @Test
    fun `no two food groups share a glyph`() {
        val glyphs = MAPPED_GROUPS.mapNotNull { foodGroupIcon(it) }
        assertEquals("a mapped group returned null", MAPPED_GROUPS.size, glyphs.size)
        assertEquals("two food groups render the same glyph", glyphs.size, glyphs.toSet().size)
    }

    /**
     * The two collisions the map was designed around, pinned so a later tidy-up cannot reintroduce them:
     * 🍲 marks a recipe row in the suggestion list, and 🥛 is the `glass` serving unit.
     */
    @Test
    fun `the glyphs reserved elsewhere in the UI are not reused`() {
        assertNotEquals("🍲 marks a recipe row", "🍲", foodGroupIcon("prepared_dish"))
        assertNotEquals(
            "🥛 is the glass serving unit",
            servingUnitEmoji("glass"),
            foodGroupIcon("dairy"),
        )
    }

    private companion object {
        val MAPPED_GROUPS = listOf(
            "meat", "processed_meat", "fish", "shellfish", "egg", "dairy", "vegetable", "fruit",
            "cereal", "legume", "nut_seed", "fat", "spice_herb", "sweetener", "baked_good",
            "beverage", "alcohol", "prepared_dish",
        )
        val UNMAPPED_GROUPS = listOf("sauce", "seaweed", "other")
    }
}
