// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.pocketnutrition.api.ReferenceIntakesCache
import org.pocketnutrition.core.NutrientTarget
import org.pocketnutrition.core.ReferenceRow
import org.pocketnutrition.core.TargetsResult
import org.pocketnutrition.core.dailyTargets
import org.pocketnutrition.core.profileForDay

/**
 * Everything the daily-targets screens read, in one place: the stores, the cached reference table and the
 * journal. Reads Compose state only, so a screen that calls it recomposes when any input changes.
 */
class DailyTargetsController(
    private val journal: JournalStore,
    val store: TargetsStore,
    val reference: ReferenceIntakesCache,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun today(): LocalDate = LocalDate.now(zone())

    val table: List<ReferenceRow>? get() = reference.current?.response?.values

    fun inputs(): TargetInputs = TargetInputs(table, store.profiles, store.weights, store.overrides)

    /**
     * Memoised per day: the journal draws a header for every visible day on every recomposition, and each
     * computation scans the whole EFSA table. The memo is dropped whenever any input changes (the table is
     * compared by identity, the rest by value).
     */
    fun stateFor(day: LocalDate): DayTargetsState {
        val inputs = inputs()
        val last = memoInputs
        if (last == null || last.table !== inputs.table || last.copy(table = inputs.table) != inputs) {
            memoInputs = inputs
            memo.clear()
        }
        return memo.getOrPut(day.toString()) { targetsForDay(inputs, day.toString()) }
    }

    private var memoInputs: TargetInputs? = null
    private val memo = HashMap<String, DayTargetsState>()

    /** The EFSA targets of [day] before any override, for "Reset to EFSA" and the explanation screen. */
    fun computedFor(day: LocalDate): List<NutrientTarget> {
        val table = table ?: return emptyList()
        val profile = profileForDay(store.profiles, store.weights, day.toString()) ?: return emptyList()
        return (dailyTargets(table, profile) as? TargetsResult.Ok)?.targets.orEmpty()
    }

    fun dayOf(meal: SavedMeal): LocalDate = Instant.ofEpochMilli(meal.savedAtEpochMs).atZone(zone()).toLocalDate()

    fun mealsOn(day: LocalDate): List<SavedMeal> = journal.meals.filter { dayOf(it) == day }.sortedBy { it.savedAtEpochMs }

    /** Meals grouped by ISO day, for self-comparison and trends. */
    fun mealsByDay(): Map<String, List<SavedMeal>> = journal.meals.groupBy { dayOf(it).toString() }

    /** Whether today shows the "Set my targets" card: nothing set up at all yet. */
    fun needsSetup(): Boolean = store.profiles.isEmpty() && store.overrides.all { it.values.isEmpty() }
}
