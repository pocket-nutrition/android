// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.pocketnutrition.core.LoggedUse
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.rankRecents

/**
 * A meal the person saved to apply again: its ingredients, quantities and cooking, for [servings]
 * portions. Never its nutrition: that is recomputed through `POST /nutrition` each time it is applied,
 * so model improvements reach saved dishes and cooking keeps being accounted for.
 */
@Serializable
data class Dish(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** How many portions the saved quantities make (a gratin for 4 is saved once, eaten a quarter at a time). */
    val servings: Int,
    val items: List<MealItem>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long? = null,
    val useCount: Int = 0,
)

@Serializable
private data class ShortcutsFile(val version: Int, val dishes: List<Dish>, val pinned: List<String>)

/**
 * What makes logging fast: saved dishes, and the ★ pins on dishes and on recent foods.
 *
 * Local only, by decision (2026-11-02): dishes are "made on the phone". A JSON file beside the journal,
 * written atomically (temp file + rename, as [JournalStore] does), excluded from backup like the journal.
 */
class MealShortcutsStore(private val file: File) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val _dishes = mutableStateListOf<Dish>()
    private val _pinned = mutableStateListOf<String>()

    val dishes: List<Dish> get() = _dishes.toList()

    init {
        load()
    }

    /** Dishes to offer: pinned first, then most recently used (or created). */
    val dishesByRelevance: List<Dish>
        get() = _dishes.sortedWith(
            compareByDescending<Dish> { isPinned(dishKey(it.id)) }
                .thenByDescending { it.lastUsedAtEpochMs ?: it.createdAtEpochMs },
        )

    fun saveDish(name: String, servings: Int, items: List<MealItem>): Dish {
        val now = System.currentTimeMillis()
        val dish = Dish(
            name = name.trim(),
            servings = servings.coerceAtLeast(1),
            items = items.filterNot { it.isQuickAdd }.map { it.copy(id = UUID.randomUUID().toString()) },
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        _dishes.add(dish)
        persist()
        return dish
    }

    fun updateItems(id: String, items: List<MealItem>) {
        val index = _dishes.indexOfFirst { it.id == id }
        if (index < 0) return
        _dishes[index] = _dishes[index].copy(
            items = items.filterNot { it.isQuickAdd },
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        persist()
    }

    fun delete(id: String) {
        _dishes.removeAll { it.id == id }
        _pinned.remove(dishKey(id))
        persist()
    }

    fun markUsed(id: String) {
        val index = _dishes.indexOfFirst { it.id == id }
        if (index < 0) return
        val dish = _dishes[index]
        _dishes[index] = dish.copy(lastUsedAtEpochMs = System.currentTimeMillis(), useCount = dish.useCount + 1)
        persist()
    }

    fun isPinned(key: String): Boolean = key in _pinned

    fun togglePin(key: String) {
        if (!_pinned.remove(key)) _pinned.add(key)
        persist()
    }

    private fun load() {
        if (!file.exists()) return
        val decoded = runCatching { json.decodeFromString<ShortcutsFile>(file.readText()) }.getOrNull() ?: return
        _dishes.addAll(decoded.dishes)
        _pinned.addAll(decoded.pinned.distinct())
    }

    private fun persist() {
        val payload = json.encodeToString(
            ShortcutsFile(version = 1, dishes = _dishes.toList(), pinned = _pinned.sorted()),
        )
        // Written to a sibling and moved into place, so an interrupted write cannot truncate the file.
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(payload)
        temporary.renameTo(file)
    }

    companion object {
        fun dishKey(id: String): String = "dish:${id.lowercase()}"
    }
}

/** A recent food: the same ingredient cooked and weighed the same way. Null for a quick-add item. */
fun foodKey(item: MealItem): String? {
    val ingredientId = item.ingredientId ?: return null
    if (item.isQuickAdd) return null
    return "food:$ingredientId|${item.cookingMethod}|${item.measuredState}"
}

/** A food eaten before, as the empty meal offers it: the last way it was logged. */
data class RecentFood(val key: String, val item: MealItem)

/**
 * Past foods ranked for [slot] (most eaten in that slot, then overall, then most recent), each with the
 * quantity and cooking of its last use. Computed from the journal, never stored.
 */
fun recentFoods(
    meals: List<SavedMeal>,
    slot: MealSlot,
    limit: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): List<RecentFood> {
    val uses = mutableListOf<LoggedUse>()
    val latest = mutableMapOf<String, Pair<Long, MealItem>>()
    for (meal in meals) {
        val mealSlot = meal.effectiveSlot(zone)
        for (item in meal.items) {
            // An item the server could not compute is not offered again as a recent food.
            if (!item.available) continue
            val key = foodKey(item) ?: continue
            uses += LoggedUse(key, mealSlot, meal.savedAtEpochMs.toDouble())
            val previous = latest[key]
            if (previous == null || meal.savedAtEpochMs > previous.first) latest[key] = meal.savedAtEpochMs to item
        }
    }
    return rankRecents(uses, slot, limit).mapNotNull { key -> latest[key]?.let { RecentFood(key, it.second) } }
}

/** The name a new dish is offered: its first three ingredient labels. */
fun suggestedDishName(items: List<MealItem>): String =
    items.filterNot { it.isQuickAdd }.take(3).joinToString(", ") { it.label }
