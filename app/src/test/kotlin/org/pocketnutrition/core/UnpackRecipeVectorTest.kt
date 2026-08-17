// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts [unpackRecipe] against the `unpack-recipe` suite.
 *
 * Two cases, which is enough for what the function is: a flatten that preserves order and carries the
 * recipe's decisions through as explicit overrides. The named tests below cover what two cases cannot —
 * that those overrides are what stop [buildMealIngredient] reaching for the ingredient's own defaults,
 * which is the whole reason the override fields exist.
 */
class UnpackRecipeVectorTest {

    @Test
    fun `every unpack-recipe vector holds`() {
        val suite = VectorCorpus.load("unpack-recipe")
        assertEquals(
            "case count disagrees with index.json",
            VectorCorpus.index().suites["unpack-recipe"],
            suite.cases.size,
        )

        val failures = suite.cases.mapNotNull { case ->
            val rawRecipe = requireNotNull(case.input["recipe"]) { "${case.id}: input has no recipe" }
            val recipe = SuggestionDecoding.decode<IngredientSuggestion>(rawRecipe)
            val expected = SuggestionDecoding.decode<List<AddItem>>(case.expected)
            val actual = unpackRecipe(recipe)
            if (actual == expected) null
            else "${case.id}: produced ${actual.size} items, expected ${expected.size} — ${case.why}"
        }
        assertEquals("unpack-recipe vectors that do not hold", emptyList<String>(), failures)
    }

    /**
     * A recipe the server sent without components is a data gap, not a client failure. Asserted for both
     * an absent field and an empty list, because `recipeComponents` is nullable in the schema and the two
     * arrive differently.
     */
    @Test
    fun `a recipe with no components yields no items, never an error`() {
        val absent = IngredientSuggestion(
            "recipe:x", "x", servingG = 80.0, productType = "recipe", recipeComponents = null,
        )
        val empty = IngredientSuggestion(
            "recipe:x", "x", servingG = 80.0, productType = "recipe", recipeComponents = emptyList(),
        )
        assertTrue(unpackRecipe(absent).isEmpty())
        assertTrue(unpackRecipe(empty).isEmpty())
    }

    /**
     * The point of the overrides: the component's own `typicalState` would have refused this row, and the
     * recipe's method rescues it. This is the interaction the two vectors do not reach, and it is the
     * reason [unpackRecipe] carries a cooking method rather than letting each row re-derive one.
     */
    @Test
    fun `recipe overrides override the ingredient's own defaults`() {
        // typicalState "cooked" is refused on its own — it says cooked without saying how.
        val ambiguous = IngredientSuggestion(
            "potato", "pomme de terre", servingG = 80.0, displayUnit = "g",
            typicalState = "cooked", portionMeasuredState = "raw",
            yieldFactors = mapOf("boiled" to 0.9),
        )
        assertNull(
            "a generic cooked state must be refused without an override",
            buildMealIngredient(AddItem(ambiguous), id = "i"),
        )

        val recipe = IngredientSuggestion(
            "recipe:gratin", "gratin", servingG = 300.0, productType = "recipe",
            recipeComponents = listOf(
                RecipeComponent(ambiguous, cookingMethod = "boiled", weightG = 200.0),
            ),
        )

        val items = unpackRecipe(recipe)
        assertEquals(1, items.size)
        val row = requireNotNull(buildMealIngredient(items[0], id = "i"))
        assertEquals("the recipe's method must win", "boiled", row.cookingMethod)
        // 200 g is the recipe's weight and a recipe weight is pre-cooking by definition, so the yield
        // factor must NOT be applied: 200, not 180.
        assertEquals(
            "a recipe weight is a pre-cooking weight — no yield conversion",
            200.0, row.quantity, 0.0,
        )
        assertEquals("raw", row.measuredState)
        // The base stays the ingredient's own portion, not the recipe's per-person weight.
        assertEquals(80.0, row.basePortionG, 0.0)
    }

    /**
     * Order is preserved. Stated in the suite description, and trivially breakable by anything that
     * groups or sorts components on the way through.
     */
    @Test
    fun `component order is preserved`() {
        fun component(id: String) = RecipeComponent(
            IngredientSuggestion(id, id, servingG = 50.0, typicalState = "raw"),
            cookingMethod = "raw",
            weightG = 10.0,
        )
        val recipe = IngredientSuggestion(
            "recipe:x", "x", servingG = 80.0, productType = "recipe",
            recipeComponents = listOf(component("c"), component("a"), component("b")),
        )
        assertEquals(
            listOf("c", "a", "b"),
            unpackRecipe(recipe).map { it.suggestion.ingredientId },
        )
    }
}
