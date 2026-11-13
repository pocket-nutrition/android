// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.api.IngredientDetail
import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.LocalizedText

class ImportMappingTest {

    private fun item(quantity: Double? = null, unit: String? = null, cooking: String? = null) =
        RecognizedItem("raw", quantity, unit, cooking, emptyList())

    private val rice = IngredientSuggestion(
        ingredientId = "rice_white",
        label = "Rice",
        servingG = 150.0,
        typicalState = "boiled",
        portionMeasuredState = "raw",
        yieldFactors = mapOf("boiled" to 2.5),
    )

    @Test
    fun `grams are applied as the pre-cooking weight override`() {
        val overrides = importOverrides(item(quantity = 200.0, unit = "g"))
        assertEquals(200.0, overrides.quantityG!!, 0.0)
        val row = buildImportRow(rice, overrides, "id1")!!
        assertEquals(200.0, row.quantity, 0.0)
        // A recipe-style gram override is a pre-cooking weight, so the row is measured raw.
        assertEquals("boiled", row.cookingMethod)
        assertEquals("raw", row.measuredState)
    }

    @Test
    fun `a piece count is never turned into grams`() {
        val overrides = importOverrides(item(quantity = 2.0, unit = "piece"))
        assertNull(overrides.quantityG)
        val row = buildImportRow(rice, overrides, "id1")!!
        // The suggestion's own serving (150 g raw), untouched by the spoken "2".
        assertEquals(150.0, row.quantity, 0.0)
    }

    @Test
    fun `other units and null units leave the serving untouched`() {
        assertNull(importOverrides(item(quantity = 3.0, unit = "tbsp")).quantityG)
        assertNull(importOverrides(item(quantity = 3.0, unit = null)).quantityG)
        assertNull(importOverrides(item(quantity = null, unit = "g")).quantityG)
    }

    @Test
    fun `non positive or non finite gram quantities are ignored`() {
        assertNull(importOverrides(item(quantity = 0.0, unit = "g")).quantityG)
        assertNull(importOverrides(item(quantity = -5.0, unit = "g")).quantityG)
        assertNull(importOverrides(item(quantity = Double.NaN, unit = "g")).quantityG)
        assertNull(importOverrides(item(quantity = Double.POSITIVE_INFINITY, unit = "g")).quantityG)
    }

    @Test
    fun `only the six known cooking methods are applied`() {
        listOf("raw", "boiled", "steamed", "grilled", "roasted", "fried").forEach {
            assertEquals(it, importOverrides(item(cooking = it)).cookingMethod)
        }
        assertNull(importOverrides(item(cooking = "sauteed")).cookingMethod)
        assertNull(importOverrides(item(cooking = "FRIED")).cookingMethod)
        assertNull(importOverrides(item(cooking = null)).cookingMethod)
    }

    @Test
    fun `an invalid cooking method falls back to what the suggestion itself says`() {
        val row = buildImportRow(rice, importOverrides(item(cooking = "sauteed")), "id1")!!
        assertEquals("boiled", row.cookingMethod)
    }

    @Test
    fun `a suggestion the corpus builder refuses yields no row and no invented weight`() {
        val noServing = rice.copy(servingG = null)
        assertNull(buildImportRow(noServing, importOverrides(item()), "id1"))
        val noState = rice.copy(typicalState = null)
        assertNull(buildImportRow(noState, importOverrides(item()), "id1"))
        // Cooked with no portion state and no gram override is refused rather than guessed...
        val ambiguous = rice.copy(portionMeasuredState = null)
        assertNull(buildImportRow(ambiguous, importOverrides(item()), "id1"))
        // ...but a gram override is a pre-cooking weight by definition, so it builds.
        assertNotNull(buildImportRow(ambiguous, importOverrides(item(quantity = 100.0, unit = "g")), "id1"))
    }

    @Test
    fun `an Open Food Facts product is locked to raw`() {
        val product = rice.copy(ingredientId = "off:123", typicalState = "processed", portionMeasuredState = "raw")
        val row = buildImportRow(product, importOverrides(item()), "id1")!!
        assertTrue(row.isIndustrialProduct)
        assertEquals("raw", row.cookingMethod)
    }

    @Test
    fun `detail maps to a suggestion using only fields the detail actually carries`() {
        val detail = IngredientDetail(
            ingredientId = "rice_white",
            names = listOf(LocalizedText("fr", "Riz"), LocalizedText("en", "Rice")),
            foodGroup = "cereals",
            referenceWeightG = 150.0,
            displayUnit = "g",
            canEatRaw = false,
            typicalState = "boiled",
        )
        val suggestion = detail.asSuggestion("en")
        assertEquals("rice_white", suggestion.ingredientId)
        assertEquals("Rice", suggestion.label)
        assertEquals(150.0, suggestion.servingG!!, 0.0)
        assertEquals("boiled", suggestion.typicalState)
        // Never invented from the detail: the detail has no such fields.
        assertNull(suggestion.portionMeasuredState)
        assertNull(suggestion.yieldFactors)
        assertNull(suggestion.servingMode)
    }

    @Test
    fun `detail without any name falls back to the candidate name`() {
        val detail = IngredientDetail(ingredientId = "x")
        assertEquals("Fallback", detail.asSuggestion("fr", fallbackLabel = "Fallback").label)
    }

    @Test
    fun `default selection is the best candidate and nothing skipped`() {
        val chosen = defaultSelection(
            RecognizedItem("r", null, null, null, listOf(Candidate("a", "A", 0.9), Candidate("b", "B", 0.5))),
        )
        assertEquals(0, chosen.candidateIndex)
        assertEquals(false, chosen.skipped)
        val none = defaultSelection(RecognizedItem("r", null, null, null, emptyList()))
        assertEquals(true, none.skipped)
    }
}
