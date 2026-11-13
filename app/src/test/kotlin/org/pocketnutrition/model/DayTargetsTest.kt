// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.DayTarget
import org.pocketnutrition.core.NUTRIENT_KEYS
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.ReferenceRow
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.VectorCorpus
import org.pocketnutrition.core.WatchList
import org.pocketnutrition.core.WatchListPreset
import org.pocketnutrition.core.WeightEntry

/**
 * The definition-of-done behaviours of spec 05 §8 that do not need a screen. The reference table is the
 * `adult-male-reference` case of the vendored corpus — test data only; the app never bundles one.
 */
class DayTargetsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val table: List<ReferenceRow> = VectorCorpus.load("daily-targets").cases
        .first { it.id == "adult-male-reference" }
        .input.getValue("table").jsonArray
        .map { json.decodeFromJsonElement(ReferenceRow.serializer(), it) }

    private val profile = ProfileVersion("2026-11-01", 1996, CalculationBasis.MALE, Situation.NONE, pal = 1.6, weightKg = 75.0)

    private fun inputs(weights: List<WeightEntry> = emptyList(), overrides: List<org.pocketnutrition.core.OverrideVersion> = emptyList()) =
        TargetInputs(table, listOf(profile), weights, overrides)

    private fun target(state: DayTargetsState, key: String): DayTarget =
        state.targets.first { it.nutrientKey == key }

    private fun meal(nutrients: Map<String, Double>?) = SavedMeal(
        savedAtEpochMs = 0, items = emptyList(),
        totals = Macros(nutrients?.get("energyKcal") ?: 500.0, 20.0, 60.0, 10.0), nutrients = nutrients,
    )

    @Test
    fun `logging a weight changes today's protein target and not yesterday's`() {
        val before = inputs()
        val after = inputs(weights = listOf(WeightEntry("2026-11-06", 90.0)))
        assertEquals(target(targetsForDay(before, "2026-11-05"), "proteinG"), target(targetsForDay(after, "2026-11-05"), "proteinG"))
        assertNotEquals(target(targetsForDay(before, "2026-11-06"), "proteinG").min, target(targetsForDay(after, "2026-11-06"), "proteinG").min)
    }

    @Test
    fun `an energy override is flagged as overridden and keeps the other targets computed`() {
        val overrides = org.pocketnutrition.model.TargetSettingsRules.setOverride(emptyList(), "2026-11-06", "energyKcal", TargetOverride(2200.0, 2200.0))
        val state = targetsForDay(inputs(overrides = overrides), "2026-11-06")
        val energy = target(state, "energyKcal")
        assertTrue(energy.overridden)
        assertEquals(2200.0, energy.min)
        assertTrue(!target(state, "proteinG").overridden)
        // The day before the override keeps the computed value.
        assertTrue(!target(targetsForDay(inputs(overrides = overrides), "2026-11-05"), "energyKcal").overridden)
    }

    @Test
    fun `a meal without iron shows iron coverage 1 of 2`() {
        val state = targetsForDay(inputs(), "2026-11-06")
        val meals = listOf(meal(mapOf("energyKcal" to 600.0, "ironMg" to 4.0)), meal(mapOf("energyKcal" to 700.0)))
        val iron = progressForDay(state, meals, listOf("ironMg")).single()
        assertEquals(1, iron.knownMeals)
        assertEquals(2, iron.totalMeals)
        assertEquals(4.0, iron.total)
    }

    @Test
    fun `days before the first profile have no target, and no table means a connection is needed`() {
        assertEquals(DayTargetsState.None, targetsForDay(inputs(), "2026-10-31"))
        assertEquals(DayTargetsState.NeedsConnection, targetsForDay(inputs().copy(table = null), "2026-11-06"))
    }

    @Test
    fun `with no profile, overrides alone are the targets`() {
        val overrides = TargetSettingsRules.setOverride(emptyList(), "2026-11-06", "sugarsG", TargetOverride(null, 50.0))
        val state = targetsForDay(TargetInputs(null, emptyList(), emptyList(), overrides), "2026-11-06")
        assertEquals(listOf("sugarsG"), state.targets.map { it.nutrientKey })
    }

    @Test
    fun `an old meal without nutrients knows only its four macros, a quick add only its energy`() {
        val old = meal(null)
        assertEquals(setOf("energyKcal", "proteinG", "carbohydratesG", "fatG"), mealNutrients(old).keys)
        val quick = SavedMeal(
            savedAtEpochMs = 0,
            items = listOf(MealItem(ingredientId = null, label = "snack", quantityG = 0.0, cookingMethod = "", measuredState = "", energyKcal = 150.0)),
            totals = Macros(150.0, 0.0, 0.0, 0.0),
        )
        assertEquals(mapOf("energyKcal" to 150.0), mealNutrients(quick))
    }

    @Test
    fun `day-detail groups cover every nutrient exactly once`() {
        assertEquals(NUTRIENT_KEYS.sorted(), NutrientGroup.entries.flatMap { it.keys }.sorted())
    }

    @Test
    fun `salt is shown from sodium`() {
        assertEquals(6.0, saltGramsFromSodiumMg(2400.0), 1e-9)
    }

    @Test
    fun `week averages skip unlogged days`() {
        val state = targetsForDay(inputs(), "2026-11-06")
        val days = listOf(state to listOf(meal(mapOf("proteinG" to 31.0))), state to emptyList())
        val week = weekAverages(days, listOf("proteinG")).single()
        assertEquals(1, week.daysWithData)
        assertEquals(31.0, week.averageTotal)
        assertEquals(50, week.averagePercent)
    }

    @Test
    fun `a logged day that does not know a nutrient stays out of its week percent`() {
        val state = targetsForDay(inputs(), "2026-11-06")
        val days = listOf(state to listOf(meal(mapOf("proteinG" to 31.0))), state to listOf(meal(mapOf("energyKcal" to 500.0))))
        val week = weekAverages(days, listOf("proteinG")).single()
        assertEquals(1, week.daysWithData)
        assertEquals(50, week.averagePercent)
    }

    @Test
    fun `the store persists every change and reads it back`() {
        val file = Files.createTempDirectory("targets").toFile().resolve("daily_targets.json")
        var now = 100L
        val store = TargetsStore(file) { now }
        store.saveProfile(profile, heightCm = 180.0)
        store.setOverride("2026-11-06", "energyKcal", TargetOverride(2200.0, 2200.0))
        store.setWatchList(WatchList.of(WatchListPreset.KIDNEY))
        now = 200L
        store.logWeight("2026-11-06", 70.5)
        store.logWeight("2026-11-06", 70.0)

        val reread = TargetsStore(file) { now }
        assertEquals(listOf(profile), reread.profiles)
        assertEquals(180.0, reread.heightCm)
        assertEquals(TargetOverride(2200.0, 2200.0), reread.overrides.single().values["energyKcal"])
        assertEquals(WatchListPreset.KIDNEY, reread.watchList.preset)
        assertEquals(listOf(WeightEntry("2026-11-06", 70.0)), reread.weights)
        assertEquals(100L, reread.profileUpdatedAtEpochSeconds)
        assertEquals(200L, reread.weightRecords.single().updatedAt)
        // What the store holds is exactly a record payload: it decodes as one.
        val record = TargetRecords.encode(requireNotNull(reread.targetsRecord))
        assertTrue(TargetRecords.decodeTargets(record) is RecordRead.Ok)
    }
}
