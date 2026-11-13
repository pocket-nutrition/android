// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Meal logging helpers: which part of the day a meal belongs to, how a saved dish scales to the portion
// eaten, how a bare calorie figure typed in the search field is read, and which past foods to suggest
// first. Ported from the sandbox reference (`mealLogging.ts`), mirrored by iOS `MealLogging.swift`; the
// `meal-slot`, `meal-slot-default-time`, `scale-dish-quantity`, `parse-quick-add-kcal` and
// `rank-recents` vectors are the contract.

@Serializable
enum class MealSlot(val id: String) {
    @SerialName("breakfast") BREAKFAST("breakfast"),
    @SerialName("lunch") LUNCH("lunch"),
    @SerialName("dinner") DINNER("dinner"),
    @SerialName("snack") SNACK("snack"),
    ;

    /** Display order within a day: breakfast, lunch, snack, dinner. */
    val order: Int
        get() = when (this) {
            BREAKFAST -> 0
            LUNCH -> 1
            SNACK -> 2
            DINNER -> 3
        }

    /** A main meal is one a day is missing when nothing is logged; a snack never is. */
    val isMain: Boolean get() = this != SNACK

    companion object {
        fun fromId(id: String): MealSlot? = entries.firstOrNull { it.id == id }

        /** The four slots in display order. */
        val inDayOrder: List<MealSlot> get() = entries.sortedBy { it.order }
    }
}

private const val MINUTES_PER_DAY = 1440

/**
 * The slot a meal falls in, from its local time in minutes since midnight. Half-open bands:
 * breakfast [04:00, 10:30), lunch [10:30, 15:00), dinner [17:30, 22:30), snack otherwise.
 */
fun mealSlotForMinutes(minutes: Double): MealSlot {
    val m = ((floor(minutes).toInt() % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return when {
        m in 240 until 630 -> MealSlot.BREAKFAST
        m in 630 until 900 -> MealSlot.LUNCH
        m in 1050 until 1350 -> MealSlot.DINNER
        else -> MealSlot.SNACK
    }
}

/** The slot of a moment, in the given time zone. */
fun mealSlotAt(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): MealSlot {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    return mealSlotForMinutes((time.hour * 60 + time.minute).toDouble())
}

/** The time (minutes since midnight) given to a meal filed under [slot] when it is not logged now. */
fun defaultMinutes(slot: MealSlot): Int = when (slot) {
    MealSlot.BREAKFAST -> 480
    MealSlot.LUNCH -> 750
    MealSlot.DINNER -> 1170
    MealSlot.SNACK -> 960
}

/** That default time on the day of [onEpochMs], in epoch milliseconds. */
fun defaultTimeFor(slot: MealSlot, onEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val day: LocalDate = Instant.ofEpochMilli(onEpochMs).atZone(zone).toLocalDate()
    return day.atStartOfDay(zone).plusMinutes(defaultMinutes(slot).toLong()).toInstant().toEpochMilli()
}

/**
 * Grams of one ingredient of a dish saved for [servings] portions when [parts] portions are eaten,
 * rounded half-up to one decimal. `servings <= 0` counts as one portion.
 */
fun scaleDishQuantity(quantityG: Double, servings: Double, parts: Double): Double {
    val portions = if (servings > 0) servings else 1.0
    return roundHalfUp(quantityG * parts / portions * 10) / 10.0
}

private const val QUICK_ADD_MAX_KCAL = 5000
private const val QUICK_ADD_MAX_DIGITS = 4

/**
 * A calorie figure typed in the search field for quick add: a whole number 1-5000, optionally followed
 * by "kcal" (any case, optional space). Anything else returns null.
 */
fun parseQuickAddKcal(query: String): Int? {
    var digits = query.trim()
    if (digits.lowercase().endsWith("kcal")) digits = digits.dropLast(4).trim()
    if (digits.length !in 1..QUICK_ADD_MAX_DIGITS || !digits.all { it in '0'..'9' }) return null
    val kcal = digits.toIntOrNull() ?: return null
    return kcal.takeIf { it in 1..QUICK_ADD_MAX_KCAL }
}

/** One past use of a food, for ranking suggestions. [at] is any monotonic time (epoch seconds or ms). */
data class LoggedUse(val key: String, val slot: MealSlot, val at: Double)

/**
 * Distinct keys to suggest, best first, at most [limit]: most uses in [slot], then most uses overall,
 * then most recent use, then key ascending.
 */
fun rankRecents(uses: List<LoggedUse>, slot: MealSlot, limit: Int): List<String> {
    data class Stats(val inSlot: Int, val total: Int, val last: Double)

    val stats = uses.groupBy { it.key }.mapValues { (_, keyed) ->
        Stats(
            inSlot = keyed.count { it.slot == slot },
            total = keyed.size,
            last = keyed.maxOf { it.at }.coerceAtLeast(0.0),
        )
    }
    return stats.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Stats>> { it.value.inSlot }
                .thenByDescending { it.value.total }
                .thenByDescending { it.value.last }
                .thenBy { it.key },
        )
        .take(limit.coerceAtLeast(0))
        .map { it.key }
}
