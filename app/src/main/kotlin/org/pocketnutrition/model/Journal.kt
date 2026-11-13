// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.pocketnutrition.api.FoodItemRequest
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.mealSlotAt

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
    /** Set only on a quick-add item: an energy figure the person typed, with no ingredient behind it. */
    val energyKcal: Double? = null,
    /**
     * False when the server could not compute this item at analysis time: it is shown as not recognised
     * and was left out of the meal's totals. Absent (true) in meals saved before the flag existed.
     */
    val available: Boolean = true,
) {
    val isQuickAdd: Boolean get() = ingredientId == null && energyKcal != null

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
 * The four macros the journal shows. The full nutrient set is kept beside them, in
 * [SavedMeal.nutrients], so a later screen can read what the engine computed.
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

        /** A meal's total. An item the server could not compute is unknown, so it adds nothing. */
        fun sumOf(profiles: List<NutritionalProfile>): Macros =
            profiles.filter { it.available }.map(::of).fold(Zero, Macros::plus)
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
    /**
     * The part of the day. Absent in meals saved before slots existed: [effectiveSlot] derives it from
     * the time instead, without rewriting the meal.
     */
    val slot: MealSlot? = null,
    /** Every nutrient the engine returned, summed over the items (unknown = absent). Absent in older meals. */
    val nutrients: Map<String, Double>? = null,
    /**
     * Last edit, for the vault's last-writer-wins. Absent in meals saved before sync existed: their save
     * time stands in (see [effectiveUpdatedAtEpochMs]).
     */
    val updatedAtEpochMs: Long? = null,
) {
    fun effectiveSlot(zone: ZoneId = ZoneId.systemDefault()): MealSlot = slot ?: mealSlotAt(savedAtEpochMs, zone)
}

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
 * The local diary. It stays on this device unless the person links a vault account, in which case an
 * end-to-end-encrypted copy of each meal is synced there (see `JournalSyncCoordinator`).
 *
 * WHY A JSON FILE RATHER THAN ROOM
 * The whole store is a list appended to and occasionally deleted from: no relationships, no queries,
 * no paging. A file read at launch and rewritten on change is the entire requirement, and it keeps
 * the app free of a persistence library, its compiler plugin and its migration story. Revisit when
 * the journal needs querying rather than listing.
 *
 * Backed by a `mutableStateListOf` so Compose recomposes on change without an observable wrapper.
 */
/** A record the person changed (a meal, or a daily-targets record), for the vault sync. Never raised for what the sync itself writes. */
sealed interface RecordChange {
    data class Saved(val id: String) : RecordChange
    data class Deleted(val id: String) : RecordChange
}

class JournalStore(private val file: File, private val now: () -> Long = System::currentTimeMillis) {

    /** Told about every change the person makes, never about what [apply] writes. */
    var onChange: ((RecordChange) -> Unit)? = null

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val _meals = mutableStateListOf<SavedMeal>()

    /** Newest first, the order the journal lists them in. */
    val meals: List<SavedMeal> get() = _meals.sortedByDescending { it.savedAtEpochMs }

    init {
        load()
    }

    fun save(
        items: List<MealItem>,
        totals: Macros,
        atEpochMs: Long = System.currentTimeMillis(),
        slot: MealSlot? = null,
        nutrients: Map<String, Double>? = null,
    ) {
        val meal = SavedMeal(
            savedAtEpochMs = atEpochMs,
            items = items,
            totals = totals,
            slot = slot ?: mealSlotAt(atEpochMs),
            nutrients = nutrients,
            updatedAtEpochMs = now(),
        )
        _meals.add(meal)
        persist()
        onChange?.invoke(RecordChange.Saved(meal.id))
    }

    fun delete(meal: SavedMeal) {
        _meals.removeAll { it.id == meal.id }
        persist()
        onChange?.invoke(RecordChange.Deleted(meal.id))
    }

    /** Writes what the vault sync brought in: one write, and no [onChange]. */
    fun apply(upserts: List<SavedMeal>, deletes: Set<String>) {
        if (upserts.isEmpty() && deletes.isEmpty()) return
        val incoming = upserts.associateBy { it.id }
        _meals.removeAll { it.id in deletes || it.id in incoming }
        _meals.addAll(incoming.values)
        persist()
    }

    /**
     * Replaces an existing entry's items and totals in place. Its time, slot and nutrients change only
     * when given; otherwise the original `savedAtEpochMs` is kept.
     */
    fun update(
        id: String,
        items: List<MealItem>,
        totals: Macros,
        savedAtEpochMs: Long? = null,
        slot: MealSlot? = null,
        nutrients: Map<String, Double>? = null,
    ) {
        val index = _meals.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = _meals[index]
        _meals[index] = current.copy(
            items = items,
            totals = totals,
            savedAtEpochMs = savedAtEpochMs ?: current.savedAtEpochMs,
            slot = slot ?: current.slot,
            nutrients = nutrients ?: current.nutrients,
            updatedAtEpochMs = now(),
        )
        persist()
        onChange?.invoke(RecordChange.Saved(id))
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
