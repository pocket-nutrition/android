// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.pocketnutrition.api.FoodItemRequest
import org.pocketnutrition.api.NutritionalProfile

/**
 * One food inside a meal, as the user is composing it or as it was saved.
 *
 * `quantityG` is grams and only grams for now. The API's `unit` accepts more and
 * `IngredientSuggestion` describes the alternatives, but rendering a unit picker needs
 * `shortcut-profile` and `step-label`. Both are now ported and vector-verified, so this is no longer
 * blocked — it is simply not built yet, and the portion-scale UI is where it lands.
 */
@Serializable
data class MealItem(
    val id: String = UUID.randomUUID().toString(),
    val ingredientId: String?,
    val label: String,
    val quantityG: Double,
    val cookingMethod: String,
    val measuredState: String,
) {
    fun asRequest() = FoodItemRequest(
        name = label,
        quantity = quantityG,
        unit = "g",
        cookingMethod = cookingMethod,
        measuredState = measuredState,
        ingredientId = ingredientId,
    )
}

/**
 * The four macros the journal shows. Deliberately not the full thirty-nutrient response: a saved meal
 * is a summary, and storing everything would grow the file without the UI ever reading it.
 */
@Serializable
data class Macros(
    val energyKcal: Double,
    val proteinG: Double,
    val carbohydratesG: Double,
    val fatG: Double,
) {
    operator fun plus(other: Macros) = Macros(
        energyKcal + other.energyKcal,
        proteinG + other.proteinG,
        carbohydratesG + other.carbohydratesG,
        fatG + other.fatG,
    )

    companion object {
        val Zero = Macros(0.0, 0.0, 0.0, 0.0)

        fun of(profile: NutritionalProfile) = Macros(
            profile.energyKcal,
            profile.proteinG,
            profile.carbohydratesG,
            profile.fatG,
        )
    }
}

/**
 * A meal the user chose to keep.
 *
 * `savedAtEpochMs` rather than a formatted string: the display format is a presentation concern and a
 * stored string would freeze today's locale into the file.
 */
@Serializable
data class SavedMeal(
    val id: String = UUID.randomUUID().toString(),
    val savedAtEpochMs: Long,
    val items: List<MealItem>,
    val totals: Macros,
)

/**
 * The on-disk shape, versioned from the first write.
 *
 * `version` exists so the next schema change can migrate rather than discard. Adding it later would
 * leave the first generation of files with nothing to branch on, which is the situation that forces
 * "delete the user's data on upgrade".
 */
@Serializable
private data class JournalFile(val version: Int, val meals: List<SavedMeal>)

/**
 * The local diary. This is the app's one piece of user data, and it never leaves the device.
 *
 * WHY A JSON FILE RATHER THAN ROOM
 * The whole store is a list appended to and occasionally deleted from: no relationships, no queries,
 * no paging. A file read at launch and rewritten on change is the entire requirement, and it keeps
 * the app free of a persistence library, its compiler plugin and its migration story. Revisit when
 * the journal needs querying rather than listing.
 *
 * Backed by a `mutableStateListOf` so Compose recomposes on change without an observable wrapper.
 */
class JournalStore(private val file: File) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val _meals = mutableStateListOf<SavedMeal>()

    /** Newest first, the order the journal lists them in. */
    val meals: List<SavedMeal> get() = _meals.sortedByDescending { it.savedAtEpochMs }

    init {
        load()
    }

    fun save(items: List<MealItem>, totals: Macros, atEpochMs: Long = System.currentTimeMillis()) {
        _meals.add(SavedMeal(savedAtEpochMs = atEpochMs, items = items, totals = totals))
        persist()
    }

    fun delete(meal: SavedMeal) {
        _meals.removeAll { it.id == meal.id }
        persist()
    }

    /** Replaces an existing entry's items and totals in place, keeping its original `savedAtEpochMs`. */
    fun update(id: String, items: List<MealItem>, totals: Macros) {
        val index = _meals.indexOfFirst { it.id == id }
        if (index < 0) return
        _meals[index] = _meals[index].copy(items = items, totals = totals)
        persist()
    }

    private fun load() {
        if (!file.exists()) return
        val decoded = runCatching { json.decodeFromString<JournalFile>(file.readText()) }.getOrNull()
        if (decoded == null) {
            // A file that will not decode is set aside, not overwritten: it is the user's only copy of
            // their diary, and an undecodable file is more often a schema change than real damage.
            // Renaming leaves it recoverable rather than silently destroyed by the next save.
            val salvage = File(file.parentFile, "journal.unreadable.json")
            salvage.delete()
            file.renameTo(salvage)
            return
        }
        _meals.addAll(decoded.meals)
    }

    private fun persist() {
        val payload = json.encodeToString(JournalFile(version = 1, meals = _meals.toList()))
        // Written to a sibling and moved into place, so an interrupted write cannot truncate the diary.
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(payload)
        temporary.renameTo(file)
    }
}
