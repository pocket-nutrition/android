// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.recognition.Entry
import org.pocketnutrition.recognition.EntryState

class JournalTimelineTest {

    private data class Meal(val name: String, val atMs: Long)

    /** 2026-11-02 at the given UTC hour. */
    private fun nov2(hour: Int): Long = 1_793_577_600_000L + hour * 3_600_000L

    private fun capture(id: String, atMs: Long) =
        Entry(localId = id, createdAtEpochMs = atMs, audioFile = "$id.jpg", state = EntryState.PROCESSING)

    private fun labels(day: TimelineDay<Meal>) = day.items.map {
        when (it) {
            is TimelineItem.Meal -> it.meal.name
            is TimelineItem.Capture -> "capture:" + it.entry.localId
        }
    }

    @Test
    fun `captures sit in their day between meals, newest first`() {
        val days = journalTimeline(
            meals = listOf(Meal("lunch", nov2(12)), Meal("dinner", nov2(20))),
            timeOf = Meal::atMs,
            captures = listOf(capture("p", nov2(16))),
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 11, 2), days[0].day)
        assertEquals(listOf("dinner", "capture:p", "lunch"), labels(days[0]))
    }

    @Test
    fun `a day with only a capture gets its own section, days newest first`() {
        val days = journalTimeline(
            meals = listOf(Meal("yesterday", nov2(-12))),
            timeOf = Meal::atMs,
            captures = listOf(capture("v", nov2(9))),
            zone = ZoneOffset.UTC,
        )
        assertEquals(listOf(listOf("capture:v"), listOf("yesterday")), days.map(::labels))
    }

    @Test
    fun `nothing in, nothing out`() {
        assertTrue(journalTimeline(emptyList<Meal>(), Meal::atMs, emptyList(), ZoneOffset.UTC).isEmpty())
    }
}

/** Mirrors the iOS `journal slot groups` suite. */
class JournalSlotGroupTest {

    private data class Meal(val name: String, val atMs: Long, val slot: MealSlot)

    /** 2026-11-02 at the given UTC hour. */
    private fun nov2(hour: Double): Long = 1_793_577_600_000L + (hour * 3_600_000L).toLong()

    private fun names(groups: List<SlotGroup<Meal>>) = groups.map { group ->
        group.slot.id + ":" + group.items.joinToString(",") {
            when (it) {
                is TimelineItem.Meal -> it.meal.name
                is TimelineItem.Capture -> "capture-" + it.entry.localId
            }
        }
    }

    @Test
    fun `groups in day order, oldest first inside a slot, captures take their moment's slot`() {
        val meals = listOf(
            Meal("dinner", nov2(20.0), MealSlot.DINNER),
            Meal("coffee", nov2(8.0), MealSlot.BREAKFAST),
            Meal("toast", nov2(7.5), MealSlot.BREAKFAST),
        )
        val photo = Entry(localId = "p", createdAtEpochMs = nov2(12.5), audioFile = "p.jpg", state = EntryState.PROCESSING)
        val day = journalTimeline(meals, Meal::atMs, listOf(photo), ZoneOffset.UTC)[0]
        val groups = groupBySlot(day, Meal::slot, Meal::atMs, includeEmptyMainSlots = false, zone = ZoneOffset.UTC)
        assertEquals(listOf("breakfast:toast,coffee", "lunch:capture-p", "dinner:dinner"), names(groups))
    }

    @Test
    fun `today shows empty main meals but never an empty snack`() {
        val day = TimelineDay(LocalDate.of(2026, 11, 2), listOf(TimelineItem.Meal(Meal("apple", nov2(16.0), MealSlot.SNACK))))
        val today = groupBySlot(day, Meal::slot, Meal::atMs, includeEmptyMainSlots = true, zone = ZoneOffset.UTC)
        assertEquals(listOf("breakfast:", "lunch:", "snack:apple", "dinner:"), names(today))
        val past = groupBySlot(day, Meal::slot, Meal::atMs, includeEmptyMainSlots = false, zone = ZoneOffset.UTC)
        assertEquals(listOf("snack:apple"), names(past))
    }

    @Test
    fun `an explicit slot wins over the meal's time`() {
        val day = TimelineDay(LocalDate.of(2026, 11, 2), listOf(TimelineItem.Meal(Meal("late lunch", nov2(15.5), MealSlot.LUNCH))))
        val groups = groupBySlot(day, Meal::slot, Meal::atMs, includeEmptyMainSlots = false, zone = ZoneOffset.UTC)
        assertEquals(listOf("lunch:late lunch"), names(groups))
    }

    @Test
    fun `today is always present, and only once`() {
        val today = LocalDate.of(2026, 11, 3)
        val older = listOf(TimelineDay<Meal>(LocalDate.of(2026, 11, 2), emptyList()))
        assertEquals(listOf(today, LocalDate.of(2026, 11, 2)), withToday(older, today).map { it.day })
        val withTodayAlready = listOf(TimelineDay<Meal>(today, emptyList()))
        assertEquals(1, withToday(withTodayAlready, today).size)
    }
}
