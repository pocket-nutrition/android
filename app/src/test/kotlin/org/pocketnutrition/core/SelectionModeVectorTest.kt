// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [shouldToggleSelection] against the `selection-mode` suite.
 *
 * Six cases for a three-branch predicate, which is more than it looks like it needs. Two earn their place:
 * `multi-term-recipe-still-adds-immediately`, because a port that dropped the recipe exception would make
 * recipes un-addable on any two-word query with nothing else noticing; and `zero-terms-adds-immediately`,
 * because a port reading `> 1` as `!= 1` toggles on a blank query, which is reachable while the field is
 * being cleared.
 */
class SelectionModeVectorTest {

    @Test
    fun `every selection-mode vector holds`() {
        val suite = VectorCorpus.load("selection-mode")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["selection-mode"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val termCount = requireNotNull(case.input["termCount"]) {
                "${case.id}: input has no termCount"
            }.jsonPrimitive.int

            // Nullable on purpose: one case pins that an absent productType does NOT take the recipe
            // branch, so JsonNull has to be told apart from a string rather than coalesced.
            val rawType = case.input["productType"]
            val productType = if (rawType == null || rawType == JsonNull) {
                null
            } else {
                (rawType as JsonPrimitive).content
            }

            val expected = case.expected.jsonPrimitive.boolean
            val actual = shouldToggleSelection(termCount, productType)

            if (actual == expected) null else "${case.id}: got $actual, want $expected — ${case.why}"
        }
        assertEquals("selection-mode vectors that do not hold", emptyList<String>(), failures)
    }
}
