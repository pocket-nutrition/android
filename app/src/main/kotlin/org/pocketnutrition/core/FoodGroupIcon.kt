// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

/**
 * Category glyph for a curated ingredient, which never carries a product photo.
 *
 * Open Food Facts supplies a front image for roughly four fifths of its products; the knowledge base has
 * no image field at all. A row that draws only `imageUrl` therefore gives every packaged product a
 * photograph and every curated ingredient a blank slot — which makes the industrial result look more
 * trustworthy than the reference one, the opposite of what this app is for. This fills the curated slot
 * from data already on the wire.
 *
 * The vocabulary is closed: 21 values, enumerated in
 * `pocket-nutrition-knowledge/schemas/ingredient.schema.json`. Three are deliberately unmapped and
 * return null rather than a poor guess, following the same rule as [servingUnitEmoji] — a wrong icon is
 * worse than none, and null falls back to the neutral thumbnail placeholder the row already has for a
 * missing photo. `sauce` is the notable one: it is the second-largest group in the catalogue, and no
 * glyph reads as "sauce" rather than as a jar, a bottle, or the honey already taken by `sweetener`.
 *
 * Products and recipes are always `"other"` server-side, so this never distinguishes a product from an
 * ingredient — [IngredientSuggestion.isProduct] does that.
 *
 * Pinned entry by entry by the `food-group-icon` suite of the shared behavioural corpus.
 */
fun foodGroupIcon(foodGroup: String?): String? {
    if (foodGroup.isNullOrEmpty()) return null
    return FOOD_GROUP_ICONS[foodGroup]
}

/**
 * Every glyph is distinct, and that is a constraint rather than a coincidence: two groups sharing one
 * icon would render as the same thing in a list whose whole purpose is telling rows apart. Two
 * near-misses were rejected for collisions elsewhere in the UI — 🍲 already marks a recipe row, so
 * `prepared_dish` takes 🍱; and 🥛 is the `glass` serving unit, so `dairy` takes 🧀.
 */
private val FOOD_GROUP_ICONS: Map<String, String> = mapOf(
    "meat" to "🥩",
    "processed_meat" to "🥓",
    "fish" to "🐟",
    "shellfish" to "🦐",
    "egg" to "🥚",
    "dairy" to "🧀",
    "vegetable" to "🥦",
    "fruit" to "🍎",
    "cereal" to "🌾",
    "legume" to "🫘",
    "nut_seed" to "🥜",
    "fat" to "🧈",
    "spice_herb" to "🌿",
    "sweetener" to "🍯",
    "baked_good" to "🥐",
    "beverage" to "🥤",
    "alcohol" to "🍷",
    "prepared_dish" to "🍱",
    // sauce, seaweed and other are intentionally absent — see foodGroupIcon.
)
