// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.MealSlot

/** The `meal` record payload, against the JSON iOS `MealRecordPayload` (v2) writes and reads. */
class MealRecordTest {

    private val id = "11111111-1111-4111-8111-111111111111"
    private val itemId = "0b5f2b7e-1f3a-4f7b-9c1e-3a2b1c0d9e8f"

    private val meal = SavedMeal(
        id = id,
        savedAtEpochMs = 1_762_430_000_123,
        updatedAtEpochMs = 1_762_430_500_456,
        items = listOf(
            MealItem(itemId, "rice", "Rice", 150.0, "boiled", "cooked"),
            MealItem("22222222-2222-4222-8222-222222222222", null, "Snack", 0.0, "raw", "raw", energyKcal = 120.0),
            MealItem("33333333-3333-4333-8333-333333333333", "x", "Mystery", 50.0, "raw", "raw", available = false),
        ),
        totals = Macros(520.0, 10.0, 80.0, 2.5),
        slot = MealSlot.LUNCH,
        nutrients = mapOf("energyKcal" to 520.0, "ironMg" to 1.2),
    )

    @Test
    fun `a meal encodes as the shared v2 JSON`() {
        val text = MealRecords.encode(meal.toRecord())
        assertTrue(text.startsWith("{\"v\":2,\"id\":\"$id\",\"saved_at\":1762430000.123,\"updated_at\":1762430500.456,"))
        assertTrue(text.contains("\"quantity_g\":150.0,\"cooking_method\":\"boiled\",\"measured_state\":\"cooked\""))
        // Optional fields are omitted when absent, and `available` is only ever written as false.
        assertFalse(text.contains("null"))
        assertEquals(1, Regex("\"available\"").findAll(text).count())
        assertTrue(text.contains("\"available\":false"))
        assertTrue(text.contains("\"slot\":\"lunch\""))
        assertTrue(text.contains("\"nutrients\":{\"energyKcal\":520.0,\"ironMg\":1.2}"))
    }

    @Test
    fun `a meal round-trips through its record`() {
        val back = (MealRecords.decode(MealRecords.encode(meal.toRecord()), id) as RecordRead.Ok).payload.toSavedMeal()
        assertEquals(meal, back)
    }

    @Test
    fun `an iOS v1 record still reads, with whole-number timestamps and unknown fields`() {
        val v1 = """{"v":1,"id":"$id","saved_at":1762430000,"updated_at":1762430000,"later":true,
            "items":[{"id":"$itemId","ingredient_id":"rice","label":"Riz","quantity_g":150,"cooking_method":"boiled","measured_state":"cooked"}],
            "totals":{"energy_kcal":200,"protein_g":4,"carbohydrates_g":44,"fat_g":0.4}}"""
        val read = MealRecords.decode(v1, id) as RecordRead.Ok
        val saved = checkNotNull(read.payload.toSavedMeal())
        assertEquals(1_762_430_000_000, saved.savedAtEpochMs)
        assertNull(saved.slot)
        assertTrue(saved.items.single().available)
    }

    @Test
    fun `a newer version is unsupported, a swapped id or garbage is malformed`() {
        val text = MealRecords.encode(meal.toRecord())
        assertEquals(RecordRead.UnsupportedVersion<MealRecordPayload>(3), MealRecords.decode(text.replace("\"v\":2", "\"v\":3"), id))
        assertEquals(RecordRead.Malformed, MealRecords.decode(text, "22222222-2222-4222-8222-222222222222"))
        assertEquals(RecordRead.Malformed, MealRecords.decode("{", id))
        assertEquals(RecordRead.Malformed, MealRecords.decode(text.replace("\"v\":2", "\"v\":0"), id))
    }

    @Test
    fun `a record whose ids are not UUIDs is not shown`() {
        assertNull(meal.toRecord().copy(id = "meal-1").toSavedMeal())
    }

    @Test
    fun `a meal saved before sync existed takes its save time as last edit`() {
        val old = meal.copy(updatedAtEpochMs = null)
        assertEquals(old.savedAtEpochMs / 1000.0, old.toRecord().updatedAt, 0.0)
    }
}
