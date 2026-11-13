// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException
import org.pocketnutrition.api.FeedbackRequest
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.api.PocketNutritionApi
import org.pocketnutrition.core.MealIngredient
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.design.PnColor
import org.pocketnutrition.model.BuilderEntry
import org.pocketnutrition.model.Dish
import org.pocketnutrition.model.DishGroup
import org.pocketnutrition.model.MealShortcutsStore
import org.pocketnutrition.model.applyDish
import org.pocketnutrition.model.batchItems
import org.pocketnutrition.model.builderEntries
import org.pocketnutrition.model.groupMembers
import org.pocketnutrition.model.isQuickAddRow
import org.pocketnutrition.model.rescaleGroup
import org.pocketnutrition.model.updatableGroup
import org.pocketnutrition.model.withoutRow

/**
 * The dishes applied to the meal being built, over the builder's own [items] list. Holds which rows
 * belong to which dish and which dish is unfolded; every rule lives in model/DishGroups.kt.
 */
class MealBuilderDishes(private val items: SnapshotStateList<MealIngredient>) {

    private val groups = mutableStateListOf<DishGroup>()
    private val expanded = mutableStateListOf<String>()

    val entries: List<BuilderEntry> get() = builderEntries(items, groups, expanded.toSet())

    fun isExpanded(group: DishGroup): Boolean = group.id in expanded

    fun toggle(group: DishGroup) {
        if (!expanded.remove(group.id)) expanded.add(group.id)
    }

    fun members(group: DishGroup): List<MealIngredient> = groupMembers(items, group)

    /** Adds one portion of [dish] as a folded group. */
    fun apply(dish: Dish, store: MealShortcutsStore) {
        val (group, rows) = applyDish(dish) ?: return
        groups.add(group)
        items.addAll(rows)
        store.markUsed(dish.id)
    }

    fun setParts(group: DishGroup, parts: Double) {
        val index = groups.indexOfFirst { it.id == group.id }
        if (index < 0) return
        val updated = groups[index].copy(parts = parts)
        groups[index] = updated
        val rescaled = rescaleGroup(items, updated, parts)
        rescaled.forEachIndexed { i, row -> if (items[i] != row) items[i] = row }
    }

    /** Removes one row, and its group once it has no row left. */
    fun removeRow(rowId: String) {
        items.removeAll { it.id == rowId }
        val remaining = withoutRow(groups, rowId)
        groups.clear()
        groups.addAll(remaining)
    }

    /** Deleting a dish's head deletes its rows. */
    fun removeGroup(group: DishGroup) {
        items.removeAll { it.id in group.base }
        groups.removeAll { it.id == group.id }
        expanded.remove(group.id)
    }

    /** The applied dish that no longer matches what is saved, offered as "Update “name”". */
    fun updatable(store: MealShortcutsStore): DishGroup? = updatableGroup(items, groups, store.dishes)

    /** Writes the group's rows back to its dish, scaled to the dish's full batch. */
    fun writeBack(group: DishGroup, store: MealShortcutsStore) {
        store.updateItems(group.dishId, batchItems(items, group))
    }
}

/** How many past foods the empty meal offers, and how many are searched while typing. */
internal const val RECENTS_EMPTY_LIMIT = 8
internal const val RECENTS_SEARCH_LIMIT = 50

/** When the analysed meal was eaten and its part of the day, handed from the builder to the save. */
data class AnalysedMeal(val atEpochMs: Long, val slot: MealSlot)

/**
 * The server's answers for the real rows, with each quick-add row's own energy put back in place, so the
 * profiles stay aligned with [rows] one for one.
 */
fun mergeQuickAdd(rows: List<MealIngredient>, answers: List<NutritionalProfile>): List<NutritionalProfile> {
    val remaining = answers.iterator()
    return rows.mapNotNull { row ->
        when {
            row.isQuickAddRow -> NutritionalProfile.quickAdd(row.label, row.quantity)
            remaining.hasNext() -> remaining.next()
            else -> null
        }
    }
}

/**
 * Tells the catalogue team a barcode is missing, through the anonymous feedback endpoint. Only the code
 * is sent; adding the product properly is Knowledge's job. Best effort: a failure is not shown, the same
 * as iOS, since the person has nothing to do about it.
 */
internal suspend fun reportMissingBarcode(code: String) {
    try {
        PocketNutritionApi.submitFeedback(
            FeedbackRequest(
                name = "EAN $code",
                corrections = emptyList(),
                comment = "Barcode not found in the catalogue: $code",
                source = "barcode",
            ),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Best effort, see above.
    }
}

/** The builder's overflow menu: save the meal as a dish, and update an applied dish that was changed. */
@Composable
internal fun DishMenu(canSave: Boolean, updatable: DishGroup?, onSave: () -> Unit, onUpdate: (DishGroup) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text(
                "⋯",
                color = PnColor.InkSoft,
                fontSize = 20.sp,
                modifier = Modifier.semantics { contentDescription = Strings.active.DISH_MENU_A11Y },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(Strings.active.SAVE_AS_DISH) },
                enabled = canSave,
                onClick = {
                    expanded = false
                    onSave()
                },
            )
            updatable?.let { group ->
                DropdownMenuItem(
                    text = { Text(Strings.active.dishUpdate(group.name)) },
                    onClick = {
                        expanded = false
                        onUpdate(group)
                    },
                )
            }
        }
    }
}
