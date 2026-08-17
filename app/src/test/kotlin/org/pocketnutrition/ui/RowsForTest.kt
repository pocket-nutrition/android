// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.IngredientSuggestion
import org.pocketnutrition.core.RecipeComponent

/**
 * Pins the fix for a live bug: tapping a recipe added one row named after the recipe instead of the
 * ingredients it stands for.
 *
 * MealBuilderScreen called `asMealRow()` unconditionally, so "Recette : raclette traditionnelle" became
 * a single 80 g row called "raclette traditionnelle" with no method of its own. `unpackRecipe` was in
 * core and vector-tested by the `unpack-recipe` suite the whole time; it simply had no UI caller. iOS
 * did it correctly, so both clients passed the same vectors while behaving differently — which is why
 * `rowsFor` is asserted here rather than left to the corpus. The corpus pins `unpackRecipe`; only this
 * can pin that the screen calls it.
 */
class RowsForTest {

    @Test
    fun `a recipe expands into one row per component, not one row for the recipe`() {
        val rows = rowsFor(RACLETTE)

        assertEquals("a recipe must expand into its components", 2, rows.size)
        assertEquals(listOf("potato", "raclette_cheese"), rows.map { it.ingredientId })
        assertTrue(
            "no row may be the recipe itself",
            rows.none { it.ingredientId == RACLETTE.ingredientId },
        )
    }

    @Test
    fun `each component keeps the weight and method the recipe assigned it`() {
        // The point of unpacking: the recipe decides that the potato is boiled at 200 g and the cheese
        // grilled at 100 g. A single collapsed row could express neither.
        val rows = rowsFor(RACLETTE).associateBy { it.ingredientId }

        assertEquals(200.0, rows.getValue("potato").quantity, 0.001)
        assertEquals("boiled", rows.getValue("potato").cookingMethod)
        assertEquals(100.0, rows.getValue("raclette_cheese").quantity, 0.001)
        assertEquals("grilled", rows.getValue("raclette_cheese").cookingMethod)
    }

    @Test
    fun `component rows get distinct ids so the list can key on them`() {
        val rows = rowsFor(RACLETTE)

        assertEquals("ids must be unique", rows.size, rows.map { it.id }.toSet().size)
        assertNotEquals("an id must not be left blank", "", rows[0].id)
    }

    @Test
    fun `a non-recipe suggestion still produces exactly one row`() {
        val rows = rowsFor(POTATO)

        assertEquals(1, rows.size)
        assertEquals("potato", rows[0].ingredientId)
    }

    @Test
    fun `a recipe with no components produces no rows rather than a bogus one`() {
        // The failure mode the old code hid: with nothing to expand, adding a row named after the
        // recipe is worse than adding nothing, because it enters the meal as a food that does not exist.
        assertEquals(emptyList<Any>(), rowsFor(RACLETTE.copy(recipeComponents = emptyList())))
        assertEquals(emptyList<Any>(), rowsFor(RACLETTE.copy(recipeComponents = null)))
    }

    private companion object {
        val POTATO = IngredientSuggestion(
            ingredientId = "potato",
            label = "pomme de terre",
            lang = "fr",
            productType = "ingredient",
            servingG = 80.0,
            typicalState = "raw",
            canEatRaw = false,
            canEatCooked = true,
        )

        val CHEESE = IngredientSuggestion(
            ingredientId = "raclette_cheese",
            label = "fromage à raclette",
            lang = "fr",
            productType = "ingredient",
            servingG = 60.0,
            typicalState = "raw",
            canEatRaw = true,
            canEatCooked = true,
        )

        /** Shaped after the `expands-components-with-overrides` case in the unpack-recipe suite. */
        val RACLETTE = IngredientSuggestion(
            ingredientId = "recipe:raclette",
            label = "raclette traditionnelle",
            lang = "fr",
            productType = "recipe",
            servingG = 80.0,
            recipeComponents = listOf(
                RecipeComponent(weightG = 200.0, cookingMethod = "boiled", ingredient = POTATO),
                RecipeComponent(weightG = 100.0, cookingMethod = "grilled", ingredient = CHEESE),
            ),
        )
    }
}
