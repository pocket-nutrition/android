// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Asserts [buildMealIngredient] against the `build-meal-ingredient` suite.
 *
 * Three of the eleven cases expect null, and those are the point of the suite rather than edge-case
 * padding: the rule being pinned is that the client refuses to invent food data. A port that returned a
 * row with a guessed weight would pass the eight positive cases and fail exactly these three.
 */
class BuildMealIngredientVectorTest {

    @Test
    fun `every build-meal-ingredient vector holds`() {
        val suite = VectorCorpus.load("build-meal-ingredient")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["build-meal-ingredient"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val rawItem = requireNotNull(case.input["item"]) { "${case.id}: input has no item" }
            val item = SuggestionDecoding.decode<AddItem>(rawItem)
            // No case asserts the id, so any stable value serves. Deliberately not a timestamp: the
            // vectors exclude id precisely because the reference implementation's embeds one.
            val actual = buildMealIngredient(item, id = "test-${case.id}")

            when {
                SuggestionDecoding.isNull(case.expected) ->
                    if (actual == null) null
                    else "${case.id}: expected null, built ${actual.quantity}${actual.unit} — ${case.why}"
                actual == null -> "${case.id}: expected a row, got null — ${case.why}"
                else -> {
                    val expected = SuggestionDecoding.decode<ComparableRow>(case.expected)
                    val produced = ComparableRow.of(actual)
                    if (produced == expected) null
                    else "${case.id}: $produced != $expected — ${case.why}"
                }
            }
        }
        assertEquals("build-meal-ingredient vectors that do not hold", emptyList<String>(), failures)
    }

    /**
     * The null returns are a refusal to guess, and each has a distinct cause. Named individually so a
     * port that collapses them into one early return still fails on the ones it no longer reaches.
     */
    @Test
    fun `each refusal has its own cause`() {
        // A generic "cooked" says the food is eaten cooked without saying how.
        assertNull(cookingMethodFor("cooked"))
        // "processed" means eaten as sold — no cooking transform, which is what raw encodes.
        assertEquals("raw", cookingMethodFor("processed"))
        // An unrecognised state is refused rather than passed through as a method.
        assertNull(cookingMethodFor("fermented"))
        assertNull(cookingMethodFor(null))
        // The six that already are cooking methods pass through.
        listOf("raw", "boiled", "steamed", "grilled", "roasted", "fried").forEach {
            assertEquals("$it should pass through", it, cookingMethodFor(it))
        }

        // No servingG: no anchor for the portion controls, so no row.
        val noBase = IngredientSuggestion("x", "x", servingG = null, typicalState = "raw")
        assertNull(buildMealIngredient(AddItem(noBase), id = "i"))

        // A cooked row with no portionMeasuredState cannot know whether to apply the yield.
        val ambiguousWeight = IngredientSuggestion(
            "x", "x", servingG = 80.0, typicalState = "grilled", portionMeasuredState = null,
        )
        assertNull(buildMealIngredient(AddItem(ambiguousWeight), id = "i"))

        // ...but an explicit quantity override removes the question: a recipe weight is pre-cooking by
        // definition, so the same suggestion now builds.
        assertNotNull(
            buildMealIngredient(
                AddItem(ambiguousWeight, cookingMethod = "grilled", quantityG = 200.0), id = "i",
            ),
        )

        // A raw row needs no portionMeasuredState at all — nothing is being converted.
        val raw = IngredientSuggestion(
            "x", "x", servingG = 80.0, displayUnit = "g", typicalState = "raw",
            portionMeasuredState = "raw",
        )
        assertNotNull(buildMealIngredient(AddItem(raw), id = "i"))
    }

    /**
     * A gram override beats a millilitre display unit. Both are plausible readings of the same row and
     * the suite has a case for it; asserted here too because getting it backwards silently relabels a
     * weight as a volume.
     */
    @Test
    fun `a gram override defeats a millilitre display unit`() {
        val milk = IngredientSuggestion(
            "milk", "lait", servingG = 200.0, displayUnit = "ml", typicalState = "raw",
            portionMeasuredState = "raw",
            servingUnits = listOf(ServingUnit("glass", "verre", 200.0, 206.0)),
        )

        val byVolume = requireNotNull(buildMealIngredient(AddItem(milk), id = "i"))
        assertEquals("ml", byVolume.unit)
        assertNotNull("a millilitre row selects its first serving unit", byVolume.selectedUnit)

        val byWeight = requireNotNull(buildMealIngredient(AddItem(milk, quantityG = 150.0), id = "i"))
        assertEquals("g", byWeight.unit)
        assertEquals(150.0, byWeight.quantity, 0.0)
        assertNull("a gram row selects no serving unit", byWeight.selectedUnit)
    }

    /**
     * Proves the suite is actually exercising the branches rather than passing on shape alone: every
     * case id in the corpus is visited, and the ratio of refusals to rows is asserted. A corpus that
     * silently lost its null cases would otherwise still pass the loop above.
     */
    @Test
    fun `the suite still covers both refusals and rows`() {
        val cases = VectorCorpus.load("build-meal-ingredient").cases
        val refusals = cases.count { SuggestionDecoding.isNull(it.expected) }
        assertEquals("the suite should still hold three refusals", 3, refusals)
        assertEquals("the suite should still hold eight buildable rows", 8, cases.size - refusals)
        // The `index` key is present in every input but read by nothing here — the id is passed in
        // instead. Asserted so its removal upstream is a visible change rather than a silent one.
        assertEquals(
            "every case carries the index the reference implementation folds into its id",
            cases.size,
            cases.count { it.input["index"]?.jsonPrimitive != null },
        )
    }
}
