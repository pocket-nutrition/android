// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts [describeQuantity] against the `describe-quantity`/`describe-quantity-en` suites of the
 * shared corpus.
 *
 * The largest suite, and the one with the most ways to be subtly wrong: two tolerance windows of
 * different widths, a nearest-match rule that a first-match port fails, an if/else-if whose nesting
 * changes what a sliver of carrot reads as, and two weight formats that differ by a space.
 */
class DescribeQuantityVectorTest {

    @Test
    fun `every describe-quantity and describe-quantity-en vector holds`() {
        listOf("describe-quantity", "describe-quantity-en").forEach { suiteName ->
            val suite = VectorCorpus.load(suiteName)

            assertEquals(
                "$suiteName suite case count disagrees with index.json",
                VectorCorpus.index().suites[suiteName],
                suite.cases.size,
            )

            val mismatches = suite.cases.mapNotNull { case ->
                val currentG = requireNotNull(case.input["currentG"]).jsonPrimitive.double
                val anchors = requireNotNull(case.input["anchors"]).jsonArray
                    .map(ScaleStepDecoding::anchor)
                val context = ScaleStepDecoding.quantityContext(requireNotNull(case.input["ctx"]))
                val lang = case.input["lang"]?.jsonPrimitive?.content ?: "fr"
                val rawExpected = case.expected.jsonObject
                val expected = QuantityDescription(
                    primary = requireNotNull(rawExpected["primary"]).jsonPrimitive.content,
                    secondary = requireNotNull(rawExpected["secondary"]).jsonPrimitive.content,
                )

                val actual = describeQuantity(currentG, anchors, context, lang)
                if (actual == expected) {
                    null
                } else {
                    "$suiteName/${case.id}: got $actual, expected $expected — ${case.why}"
                }
            }
            assertEquals("$suiteName vectors that do not hold", emptyList<String>(), mismatches)
        }
    }

    @Test
    fun `the nearest anchor wins regardless of scan order`() {
        // The corpus has one case for this; asserted here in both orderings, so a port that happens
        // to scan in the lucky direction is still caught.
        val small = QuantityAnchor("Petite", 63.0)
        val one = QuantityAnchor("1 part", 66.0)
        val context = QuantityContext(portionG = 64.0, displayUnit = "g")

        assertEquals("Petite", describeQuantity(64.0, listOf(small, one), context, "fr").primary)
        assertEquals("Petite", describeQuantity(64.0, listOf(one, small), context, "fr").primary)
    }

    @Test
    fun `the item branch keys on the item unit, not on servingMode`() {
        // The corpus's sliver case proves it by omitting the mode; this proves the converse.
        val withMode = QuantityContext(servingMode = "item", portionG = 64.0, displayUnit = "g")
        assertEquals("≈ 1,3 portion", describeQuantity(80.0, emptyList(), withMode, "fr").primary)

        val withoutMode = QuantityContext(
            itemUnitG = 74.0, itemLabel = "carotte", portionG = 74.0, displayUnit = "g",
        )
        assertEquals("≈ 1,1 carotte", describeQuantity(81.0, emptyList(), withoutMode, "fr").primary)
    }

    @Test
    fun `a sub-quarter item falls through to grams, not to the portion ratio`() {
        // Flattening the if/else-if would produce "≈ 0,1 portion" here, which still looks plausible.
        val context = QuantityContext(
            itemUnitG = 74.0, itemLabel = "carotte", portionG = 74.0, displayUnit = "g",
        )
        assertEquals(
            QuantityDescription("10g", ""),
            describeQuantity(10.0, emptyList(), context, "fr"),
        )
    }

    @Test
    fun `weights render per unit, and never as zero`() {
        val grams = QuantityContext(portionG = 0.0, displayUnit = "g")
        val millilitres = QuantityContext(portionG = 0.0, displayUnit = "ml")

        assertEquals("64g", describeQuantity(64.0, emptyList(), grams, "fr").primary)
        assertEquals("270 mL", describeQuantity(270.0, emptyList(), millilitres, "fr").primary)
        assertEquals("< 1g", describeQuantity(0.4, emptyList(), grams, "fr").primary)
        assertEquals("< 1 mL", describeQuantity(0.5, emptyList(), millilitres, "fr").primary)
    }

    @Test
    fun `the anchor window is tighter than the named-descriptor window`() {
        // 100 g against a 64 g portion sits between them — outside the 96 g anchor's 4 % window,
        // inside Grande's 8 % one — and is the case proving they are not the same number.
        val anchors = listOf(QuantityAnchor("Grande", 96.0))
        val context = QuantityContext(portionG = 64.0, displayUnit = "g")
        assertEquals("≈ Grande", describeQuantity(100.0, anchors, context, "fr").primary)
    }
}
