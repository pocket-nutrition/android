// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.defaultTimeFor
import org.pocketnutrition.core.mealSlotAt
import org.pocketnutrition.recognition.Entry

// The journal shows saved meals and the captures (dictations, meal photos) that are not meals yet, side
// by side in the day they belong to. A capture is removed once its meal is saved, so every stored entry
// is a meal in progress. Pure and generic over the meal type so the ordering is unit-tested.

sealed interface TimelineItem<out M> {
    data class Meal<M>(val meal: M) : TimelineItem<M>
    data class Capture(val entry: Entry) : TimelineItem<Nothing>
}

data class TimelineDay<M>(val day: LocalDate, val items: List<TimelineItem<M>>)

/**
 * Days newest first; within a day, meals and captures by time, newest first. A capture is dated when it
 * was recorded or photographed, which is also when its meal is dated once saved.
 */
fun <M> journalTimeline(
    meals: List<M>,
    timeOf: (M) -> Long,
    captures: List<Entry>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineDay<M>> {
    val dated: List<Pair<Long, TimelineItem<M>>> =
        meals.map { timeOf(it) to TimelineItem.Meal(it) } + captures.map { it.createdAtEpochMs to TimelineItem.Capture(it) }
    return dated
        .groupBy { (epochMs, _) -> Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate() }
        .map { (day, items) -> TimelineDay(day, items.sortedByDescending { it.first }.map { it.second }) }
        .sortedByDescending { it.day }
}

/** One part of a day in the journal: its slot and what was eaten then, oldest first. */
data class SlotGroup<M>(val slot: MealSlot, val items: List<TimelineItem<M>>)

/**
 * A day's entries grouped by slot, in day order (breakfast, lunch, snack, dinner), each oldest first.
 * A capture's slot is the one of the moment it was taken. With [includeEmptyMainSlots] (today), a main
 * meal with nothing logged yet still gets its group, empty, so the journal can offer to fill it; snacks
 * never do, since a day without one is not missing anything.
 */
fun <M> groupBySlot(
    day: TimelineDay<M>,
    slotOf: (M) -> MealSlot,
    timeOf: (M) -> Long,
    includeEmptyMainSlots: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): List<SlotGroup<M>> {
    fun slot(item: TimelineItem<M>): MealSlot = when (item) {
        is TimelineItem.Meal -> slotOf(item.meal)
        is TimelineItem.Capture -> mealSlotAt(item.entry.createdAtEpochMs, zone)
    }
    fun time(item: TimelineItem<M>): Long = when (item) {
        is TimelineItem.Meal -> timeOf(item.meal)
        is TimelineItem.Capture -> item.entry.createdAtEpochMs
    }
    val bySlot = day.items.groupBy(::slot)
    return MealSlot.inDayOrder.mapNotNull { candidate ->
        val items = bySlot[candidate].orEmpty().sortedBy(::time)
        if (items.isEmpty() && !(includeEmptyMainSlots && candidate.isMain)) null else SlotGroup(candidate, items)
    }
}

/** The timeline with today always present, empty if nothing is logged yet, so its meals can be offered. */
fun <M> withToday(days: List<TimelineDay<M>>, today: LocalDate): List<TimelineDay<M>> =
    if (days.firstOrNull()?.day == today) days else listOf(TimelineDay<M>(today, emptyList())) + days

/** The latest meal eaten in [slot] on the day before [today], if any. */
fun yesterdaysMeal(
    meals: List<SavedMeal>,
    slot: MealSlot,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): SavedMeal? {
    val yesterday = today.minusDays(1)
    return meals
        .filter { Instant.ofEpochMilli(it.savedAtEpochMs).atZone(zone).toLocalDate() == yesterday && it.effectiveSlot(zone) == slot }
        .maxByOrNull { it.savedAtEpochMs }
}

/** When a meal logged again in [slot] is dated: now if this is the current part of the day, else the slot's usual time today. */
fun repeatTime(slot: MealSlot, nowEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    if (mealSlotAt(nowEpochMs, zone) == slot) nowEpochMs else defaultTimeFor(slot, nowEpochMs, zone)

/**
 * Logs [meal] again in [slot] (see [repeatTime]): same items and quantities, so the same totals and
 * nutrients, with fresh item ids.
 */
fun repeatMeal(store: JournalStore, meal: SavedMeal, slot: MealSlot, nowEpochMs: Long = System.currentTimeMillis()) {
    store.save(
        items = meal.items.map { it.copy(id = UUID.randomUUID().toString()) },
        totals = meal.totals,
        atEpochMs = repeatTime(slot, nowEpochMs),
        slot = slot,
        nutrients = meal.nutrients,
    )
}
