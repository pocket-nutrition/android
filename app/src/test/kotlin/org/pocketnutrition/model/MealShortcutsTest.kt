// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.MealSlot
import org.pocketnutrition.core.NUTRIENT_KEYS
import org.pocketnutrition.core.sumNutrients
import org.pocketnutrition.ui.mergeQuickAdd

class NutrientTotalsTest {

    @Test
    fun `unknown stays unknown when summing, known values add up`() {
        val total = sumNutrients(mapOf("energyKcal" to 100.0, "ironMg" to 1.5), mapOf("energyKcal" to 50.0, "vitaminCMg" to 10.0))
        assertEquals(mapOf("energyKcal" to 150.0, "ironMg" to 1.5, "vitaminCMg" to 10.0), total)
        assertNull(total["zincMg"])
        assertEquals(24, NUTRIENT_KEYS.size)
    }

    @Test
    fun `an API profile keeps every nutrient it has and drops the null ones`() {
        val json = Json { ignoreUnknownKeys = true }
        val profile = json.decodeFromString<NutritionalProfile>(
            """{"name":"riz","quantity":150,"unit":"g","cookingMethod":"boiled","measuredState":"cooked",
               "energyKcal":195,"proteinG":4,"fatG":0.5,"carbohydratesG":42,"ironMg":null,"fiberG":0.6,
               "vitaminB12Ug":0.1,"confidence":0.9,"source":"ciqual","available":true}""",
        )
        assertEquals(
            mapOf("energyKcal" to 195.0, "proteinG" to 4.0, "fatG" to 0.5, "carbohydratesG" to 42.0, "fiberG" to 0.6, "vitaminB12Ug" to 0.1),
            profile.nutrients(),
        )
    }

    @Test
    fun `a quick-add profile has energy only`() {
        assertEquals(mapOf("energyKcal" to 450.0), NutritionalProfile.quickAdd("450 kcal", 450.0).nutrients())
    }
}

class QuickAddTest {

    private fun profile(name: String) = NutritionalProfile(name, 100.0, "g", "raw", "raw", 50.0, 1.0, 1.0, 1.0)

    @Test
    fun `quick-add rows are kept in place and the server answers fill the rest in order`() {
        val rows = listOf(
            MealItem(ingredientId = "a", label = "a", quantityG = 100.0, cookingMethod = "raw", measuredState = "raw").asMealIngredient(),
            quickAddRow(450.0),
            MealItem(ingredientId = "b", label = "b", quantityG = 100.0, cookingMethod = "raw", measuredState = "raw").asMealIngredient(),
        )
        val merged = mergeQuickAdd(rows, listOf(profile("a"), profile("b")))
        assertEquals(listOf("a", "450 kcal", "b"), merged.map { it.name })
        assertTrue(merged[1].isQuickAdd)
    }

    @Test
    fun `a quick-add row is saved as an item with no ingredient and its energy, and comes back`() {
        val item = quickAddRow(320.0).asMealItem()
        assertNull(item.ingredientId)
        assertEquals(320.0, item.energyKcal)
        assertTrue(item.isQuickAdd)
        assertTrue(item.asMealIngredient().isQuickAddRow)
        assertNull(foodKey(item))
    }
}

class JournalCompatibilityTest {

    @Test
    fun `a journal written before slots still loads, and its slot follows the time`() {
        val dir = Files.createTempDirectory("journal").toFile()
        val file = File(dir, "journal.json")
        // 2026-11-02 12:30 UTC, no slot and no nutrients.
        file.writeText(
            """{"version":1,"meals":[{"id":"m","savedAtEpochMs":1793622600000,"items":[{"id":"i",
               "ingredientId":"rice","label":"Rice","quantityG":150,"cookingMethod":"boiled","measuredState":"cooked"}],
               "totals":{"energyKcal":195,"proteinG":4,"carbohydratesG":42,"fatG":0.5}}]}""",
        )
        val meal = JournalStore(file).meals.single()
        assertNull(meal.slot)
        assertNull(meal.nutrients)
        assertEquals(MealSlot.LUNCH, meal.effectiveSlot(ZoneOffset.UTC))
        dir.deleteRecursively()
    }

    @Test
    fun `logging a meal again keeps items, totals and nutrients and dates it in the slot`() {
        val dir = Files.createTempDirectory("journal").toFile()
        val store = JournalStore(File(dir, "journal.json"))
        val item = MealItem(ingredientId = "rice", label = "Rice", quantityG = 150.0, cookingMethod = "boiled", measuredState = "cooked")
        store.save(listOf(item), Macros(195.0, 4.0, 42.0, 0.5), atEpochMs = 1_793_622_600_000L, nutrients = mapOf("energyKcal" to 195.0))
        val original = store.meals.single()
        repeatMeal(store, original, MealSlot.DINNER)
        val copy = store.meals.first { it.id != original.id }
        assertEquals(MealSlot.DINNER, copy.slot)
        assertEquals(original.totals, copy.totals)
        assertEquals(original.nutrients, copy.nutrients)
        assertEquals(listOf("Rice"), copy.items.map { it.label })
        assertTrue(copy.items.single().id != item.id)
        // Reloaded from disk, the new fields survive.
        assertEquals(MealSlot.DINNER, JournalStore(File(dir, "journal.json")).meals.first { it.id == copy.id }.slot)
        dir.deleteRecursively()
    }

    @Test
    fun `the meal to repeat is yesterday's latest in that slot`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 3)
        fun meal(id: String, hourOfNov2: Int, slot: MealSlot?) = SavedMeal(
            id = id,
            savedAtEpochMs = 1_793_577_600_000L + hourOfNov2 * 3_600_000L,
            items = emptyList(),
            totals = Macros.Zero,
            slot = slot,
        )
        val meals = listOf(meal("early", 12, null), meal("late", 13, MealSlot.LUNCH), meal("dinner", 20, null))
        assertEquals("late", yesterdaysMeal(meals, MealSlot.LUNCH, today, zone)?.id)
        assertNull(yesterdaysMeal(meals, MealSlot.BREAKFAST, today, zone))
    }
}

class RecentsAndDishesTest {

    private fun item(id: String, grams: Double, method: String = "raw") =
        MealItem(ingredientId = id, label = id, quantityG = grams, cookingMethod = method, measuredState = "raw")

    @Test
    fun `recent foods rank by slot and carry their last quantity`() {
        val zone = ZoneOffset.UTC
        val nov2 = 1_793_577_600_000L
        val meals = listOf(
            SavedMeal(savedAtEpochMs = nov2 + 8 * 3_600_000L, items = listOf(item("coffee", 200.0)), totals = Macros.Zero),
            SavedMeal(savedAtEpochMs = nov2 + 9 * 3_600_000L, items = listOf(item("coffee", 250.0)), totals = Macros.Zero),
            SavedMeal(savedAtEpochMs = nov2 + 12 * 3_600_000L, items = listOf(item("rice", 150.0, "boiled")), totals = Macros.Zero),
        )
        val recents = recentFoods(meals, MealSlot.BREAKFAST, limit = 5, zone = zone)
        assertEquals(listOf("food:coffee|raw|raw", "food:rice|boiled|raw"), recents.map { it.key })
        assertEquals(250.0, recents.first().item.quantityG, 0.0)
    }

    @Test
    fun `an item the server could not compute is never offered as a recent food`() {
        val zone = ZoneOffset.UTC
        val nov2 = 1_793_577_600_000L
        val meals = listOf(
            SavedMeal(
                savedAtEpochMs = nov2 + 8 * 3_600_000L,
                items = listOf(item("coffee", 200.0), item("zzqx", 100.0).copy(available = false)),
                totals = Macros.Zero,
            ),
        )
        val recents = recentFoods(meals, MealSlot.BREAKFAST, limit = 5, zone = zone)
        assertEquals(listOf("food:coffee|raw|raw"), recents.map { it.key })
    }

    @Test
    fun `a dish applies one portion, rescales by parts and writes back the full batch`() {
        val dish = Dish(name = "Gratin", servings = 4, items = listOf(item("potato", 800.0), item("cream", 200.0)), createdAtEpochMs = 0, updatedAtEpochMs = 0)
        val (group, rows) = requireNotNull(applyDish(dish))
        assertEquals(listOf(200.0, 50.0), rows.map { it.quantity })
        val half = rescaleGroup(rows, group, 0.5)
        assertEquals(listOf(100.0, 25.0), half.map { it.quantity })
        val halfGroup = group.copy(parts = 0.5)
        assertNull(updatableGroup(half, listOf(halfGroup), listOf(dish)))
        val edited = half.mapIndexed { i, row -> if (i == 0) row.copy(quantity = 120.0) else row }
        assertEquals(halfGroup, updatableGroup(edited, listOf(halfGroup), listOf(dish)))
        assertEquals(listOf(960.0, 200.0), batchItems(edited, halfGroup).map { it.quantityG })
    }

    @Test
    fun `a folded dish shows its head only, an unfolded one its rows too`() {
        val dish = Dish(name = "Salad", servings = 1, items = listOf(item("a", 10.0), item("b", 20.0)), createdAtEpochMs = 0, updatedAtEpochMs = 0)
        val (group, rows) = requireNotNull(applyDish(dish))
        val loose = item("c", 5.0).asMealIngredient()
        val all = rows + loose
        assertEquals(2, builderEntries(all, listOf(group), emptySet()).size)
        assertEquals(4, builderEntries(all, listOf(group), setOf(group.id)).size)
        assertTrue(withoutRow(withoutRow(listOf(group), rows[0].id), rows[1].id).isEmpty())
    }

    @Test
    fun `dishes and pins persist, pinned dishes first, and the name is suggested from three labels`() {
        val dir = Files.createTempDirectory("shortcuts").toFile()
        val file = File(dir, "meal_shortcuts.json")
        val store = MealShortcutsStore(file)
        val first = store.saveDish(" Soup ", 2, listOf(item("leek", 300.0), MealItem(ingredientId = null, label = "450 kcal", quantityG = 0.0, cookingMethod = "", measuredState = "", energyKcal = 450.0)))
        val second = store.saveDish("Pasta", 1, listOf(item("pasta", 100.0)))
        store.togglePin(MealShortcutsStore.dishKey(first.id))
        val reloaded = MealShortcutsStore(file)
        assertEquals(listOf("Soup", "Pasta"), reloaded.dishesByRelevance.map { it.name })
        assertEquals(1, reloaded.dishes.first { it.id == first.id }.items.size)
        assertTrue(reloaded.isPinned(MealShortcutsStore.dishKey(first.id)))
        reloaded.delete(first.id)
        assertEquals(listOf(second.id), MealShortcutsStore(file).dishes.map { it.id })
        assertEquals("a, b, c", suggestedDishName(listOf(item("a", 1.0), item("b", 1.0), item("c", 1.0), item("d", 1.0))))
        dir.deleteRecursively()
    }
}
