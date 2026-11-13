// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.util.UUID
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.roundHalfUp
import org.pocketnutrition.core.scaleDishQuantity

// The meal builder's view of a dish applied to the meal. Its rows stay ordinary rows; the group remembers
// each one's saved quantity so a portion step rescales from the dish, not from a row the person may have
// edited since. Pure, so the builder composable only wires it.

/** A dish applied to the meal being built. [base] maps a row id to its grams as saved in the dish. */
data class DishGroup(
    val id: String = UUID.randomUUID().toString(),
    val dishId: String,
    val name: String,
    val servings: Int,
    val parts: Double,
    val base: Map<String, Double>,
)

/** One line of the builder list: a dish's head, or a row by id. */
sealed interface BuilderEntry {
    val key: String

    data class Group(val group: DishGroup) : BuilderEntry {
        override val key: String get() = "group-${group.id}"
    }

    data class Row(val rowId: String) : BuilderEntry {
        override val key: String get() = rowId
    }
}

/** The meal list: each dish's head just before its first row; a folded dish hides its rows. */
fun builderEntries(items: List<MealIngredient>, groups: List<DishGroup>, expanded: Set<String>): List<BuilderEntry> {
    val entries = mutableListOf<BuilderEntry>()
    val emitted = mutableSetOf<String>()
    for (item in items) {
        val group = groups.firstOrNull { item.id in it.base }
        if (group != null) {
            if (emitted.add(group.id)) entries += BuilderEntry.Group(group)
            if (group.id !in expanded) continue
        }
        entries += BuilderEntry.Row(item.id)
    }
    return entries
}

/** A row set to an explicit weight: the portion ladder no longer describes it. */
fun MealIngredient.resizedTo(quantity: Double): MealIngredient =
    copy(quantity = quantity, basePortionG = quantity, portionFactor = 1.0, unitCount = 1.0)

/** One portion of [dish] (a quarter of a gratin saved for 4): its group and its rows. Null when empty. */
fun applyDish(dish: Dish): Pair<DishGroup, List<MealIngredient>>? {
    if (dish.items.isEmpty()) return null
    val base = mutableMapOf<String, Double>()
    val rows = dish.items.map { item ->
        val row = item.asMealIngredient()
        base[row.id] = item.quantityG
        row.resizedTo(scaleDishQuantity(item.quantityG, dish.servings.toDouble(), 1.0))
    }
    return DishGroup(dishId = dish.id, name = dish.name, servings = dish.servings, parts = 1.0, base = base) to rows
}

/** [items] with the rows of [group] rescaled to [parts] portions of the dish. */
fun rescaleGroup(items: List<MealIngredient>, group: DishGroup, parts: Double): List<MealIngredient> =
    items.map { row ->
        val saved = group.base[row.id] ?: return@map row
        row.resizedTo(scaleDishQuantity(saved, group.servings.toDouble(), parts))
    }

/** The rows of [group], in meal order. */
fun groupMembers(items: List<MealIngredient>, group: DishGroup): List<MealIngredient> =
    items.filter { it.id in group.base }

/** Groups without the row [rowId]; a group left with no row disappears. */
fun withoutRow(groups: List<DishGroup>, rowId: String): List<DishGroup> =
    groups.mapNotNull { group ->
        val base = group.base - rowId
        if (base.isEmpty()) null else group.copy(base = base)
    }

/** The first applied dish whose rows differ from what is saved (labels, quantities or cooking). */
fun updatableGroup(items: List<MealIngredient>, groups: List<DishGroup>, dishes: List<Dish>): DishGroup? =
    groups.firstOrNull { group ->
        val dish = dishes.firstOrNull { it.id == group.dishId } ?: return@firstOrNull false
        val current = groupMembers(items, group).map { it.asMealItem() }
        val saved = dish.items.map {
            it.copy(quantityG = scaleDishQuantity(it.quantityG, group.servings.toDouble(), group.parts))
        }
        current.map { it.label } != saved.map { it.label } ||
            current.zip(saved).any { (a, b) -> a.quantityG != b.quantityG || a.cookingMethod != b.cookingMethod }
    }

/** The group's rows back to quantities for the dish's full batch, from the portion shown. */
fun batchItems(items: List<MealIngredient>, group: DishGroup): List<MealItem> {
    val ratio = if (group.parts > 0) group.servings / group.parts else 1.0
    return groupMembers(items, group).map { row ->
        val item = row.asMealItem()
        item.copy(quantityG = roundHalfUp(item.quantityG * ratio * 10) / 10.0)
    }
}
