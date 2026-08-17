// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import kotlinx.serialization.Serializable

/**
 * One suggestion from `GET /ingredients/search`, and the shapes built on it.
 *
 * WHY THIS LIVES IN core RATHER THAN api
 * `build-meal-ingredient`, `apply-method-change` and `unpack-recipe` all take this whole shape as
 * input, so it has to be visible to the functions they pin. It was in `api` while those three suites
 * were unported, which read as a layering decision but was only ever an ordering one.
 *
 * Most fields are nullable in the spec and genuinely absent in practice: `nutrition` came back null
 * for every result when this was written. Fields the spec defines but nothing reads (`nutrition`,
 * `densityGMl`, `unitLabelFr`, `unitLabelEn`) are deliberately not declared — the JSON decoder is
 * configured to ignore unknown keys, so they decode away harmlessly, and declaring them would suggest
 * something consults them. It also means two suggestions comparing equal have compared only the fields
 * that affect behaviour, which is the comparison the vectors make.
 */
@Serializable
data class IngredientSuggestion(
    val ingredientId: String,
    val label: String,
    val lang: String = "fr",
    val servingG: Double? = null,
    val displayUnit: String? = null,
    val typicalState: String? = null,
    val portionMeasuredState: String? = null,
    val canEatRaw: Boolean? = null,
    val canEatCooked: Boolean? = null,
    val yieldFactors: Map<String, Double>? = null,
    val productType: String? = null,
    val servingMode: String? = null,
    val servingUnits: List<ServingUnit>? = null,
    val imageUrl: String? = null,
    /**
     * Brand behind an Open Food Facts product; null for curated ingredients and recipes.
     *
     * Part of the deduplication key, not decoration — see [deduplicateByLabel]. Thousands of OFF
     * products carry a `product_name` that is the bare food word, so before this reached the row a
     * search for "poulet" collapsed to a single result: every product keyed on the same label.
     */
    val brand: String? = null,
    /**
     * Knowledge food group, a closed 21-value vocabulary; `"other"` for products and recipes.
     *
     * Read only by [foodGroupIcon], which gives a curated ingredient a category glyph in the thumbnail
     * slot where a product would show its photo.
     */
    val foodGroup: String? = null,
    val recipeComponents: List<RecipeComponent>? = null,
) {
    /**
     * A response can mix a curated ingredient with Open Food Facts products.
     *
     * The value is `"off_product"`. This used to read `productType == "product"`, which the server
     * never emits — the whole vocabulary is `ingredient`, `recipe`, `off_product` — so the badge worked
     * only by falling through to the id prefix. The prefix check stays as a belt-and-braces guard for a
     * response that omits `productType` entirely, which the schema permits.
     */
    val isProduct: Boolean get() = productType == "off_product" || ingredientId.startsWith("off:")

    /**
     * True for a recipe, which expands into several meal rows when tapped rather than adding one.
     *
     * Named here rather than compared inline at each call site: the list pins recipes to the top and
     * renders them differently, and a string literal repeated across three files is how the
     * `"product"` typo above survived.
     */
    val isRecipe: Boolean get() = productType == "recipe"
}

/**
 * One component of a recipe suggestion.
 *
 * [cookingMethod] and [weightG] are the recipe's decisions about this ingredient, and [unpackRecipe]
 * turns them into the explicit overrides that stop [buildMealIngredient] reaching for the ingredient's
 * own defaults. Both are nullable because the server's schema makes them so; an absent
 * [cookingMethod] falls back to the ingredient's `typicalState`, which may then refuse the row.
 */
@Serializable
data class RecipeComponent(
    val ingredient: IngredientSuggestion,
    val cookingMethod: String? = null,
    val weightG: Double? = null,
)

/**
 * One item on its way into the meal.
 *
 * A plain search result carries only the suggestion. A recipe component carries overrides: the
 * per-person weight the recipe specifies and the method it cooks that ingredient by. The distinction
 * is load-bearing rather than cosmetic — [buildMealIngredient] treats an overridden [quantityG] as a
 * *pre-cooking* weight by definition, and skips the yield conversion it would otherwise apply.
 */
@Serializable
data class AddItem(
    val suggestion: IngredientSuggestion,
    val cookingMethod: String? = null,
    val quantityG: Double? = null,
)

/**
 * One row of the meal being composed: the output of [buildMealIngredient] and the subject of
 * [applyMethodChange].
 *
 * WEIGHT SEMANTICS, WHICH ARE THE EASY THING TO GET WRONG
 * [quantity] alone is ambiguous — [measuredState] says whether it is a raw or a cooked weight, and
 * [basePortionG] is always the raw anchor the portion controls scale from. A yield factor converts
 * between the two and is applied in exactly one direction: raw base to cooked weight. Read [quantity]
 * without [measuredState] and you will eventually divide by a yield factor twice.
 *
 * A `data class` with `copy()` is the direct equivalent of the reference implementation's
 * `{ ...ingredient, quantity }`, and keeps [applyMethodChange] from restating seventeen constructor
 * arguments at each of its four branches — where one mis-ordered argument compiles and silently swaps
 * two values.
 */
@Serializable
data class MealIngredient(
    val id: String,
    val label: String,
    val ingredientId: String,
    val cookingMethod: String,
    val quantity: Double,
    val unit: String,
    val measuredState: String,
    /** The raw anchor. Never a cooked weight, whatever [measuredState] says about [quantity]. */
    val basePortionG: Double,
    /**
     * Multiplier on [basePortionG]. `1` means "one portion"; `0` means the user typed a weight
     * directly and the portion ladder no longer describes this row — which is why every branch of
     * [applyMethodChange] tests `> 0` rather than truthiness.
     */
    val portionFactor: Double,
    val servingUnits: List<ServingUnit> = emptyList(),
    val selectedUnit: ServingUnit? = null,
    val unitCount: Double = 1.0,
    val displayUnit: String = "g",
    val canEatRaw: Boolean? = null,
    val yieldFactors: Map<String, Double> = emptyMap(),
    val imageUrl: String? = null,
    val servingMode: String? = null,
)
