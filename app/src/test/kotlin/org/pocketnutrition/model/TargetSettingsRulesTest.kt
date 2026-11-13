// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pocketnutrition.core.CalculationBasis
import org.pocketnutrition.core.NUTRIENT_KEYS
import org.pocketnutrition.core.OverrideVersion
import org.pocketnutrition.core.ProfileVersion
import org.pocketnutrition.core.Situation
import org.pocketnutrition.core.TargetOverride
import org.pocketnutrition.core.WatchList
import org.pocketnutrition.core.WatchListPreset
import org.pocketnutrition.core.WeightEntry

/** The local-model rules of spec 05 §2: versions dated today replace, older ones are never edited. */
class TargetSettingsRulesTest {

    private fun profile(from: String, pal: Double) =
        ProfileVersion(from, 1990, CalculationBasis.MALE, Situation.NONE, pal = pal, weightKg = 75.0)

    @Test
    fun `saving the questionnaire appends a version, or replaces today's`() {
        val first = TargetSettingsRules.saveProfile(emptyList(), profile("2026-11-05", 1.4))
        val second = TargetSettingsRules.saveProfile(first, profile("2026-11-06", 1.6))
        assertEquals(listOf("2026-11-05", "2026-11-06"), second.map { it.effectiveFrom })
        val replaced = TargetSettingsRules.saveProfile(second, profile("2026-11-06", 1.8))
        assertEquals(listOf(1.4, 1.8), replaced.map { it.pal })
    }

    @Test
    fun `an override change appends a full snapshot, or replaces today's`() {
        val yesterday = listOf(OverrideVersion("2026-11-05", mapOf("energyKcal" to TargetOverride(2200.0, 2200.0))))
        val set = TargetSettingsRules.setOverride(yesterday, "2026-11-06", "sugarsG", TargetOverride(null, 50.0))
        assertEquals(2, set.size)
        assertEquals(setOf("energyKcal", "sugarsG"), set.last().values.keys)
        assertEquals(yesterday.first(), set.first())

        val reset = TargetSettingsRules.setOverride(set, "2026-11-06", "energyKcal", null)
        assertEquals(2, reset.size)
        assertEquals(setOf("sugarsG"), reset.last().values.keys)

        val all = TargetSettingsRules.resetAll(reset, "2026-11-07")
        assertEquals(3, all.size)
        assertEquals(emptyMap<String, TargetOverride>(), all.last().values)
    }

    @Test
    fun `a weight entry replaces the one of the same date and can be deleted`() {
        val log = TargetSettingsRules.logWeight(listOf(WeightEntry("2026-11-05", 70.0)), WeightEntry("2026-11-06", 71.0))
        val again = TargetSettingsRules.logWeight(log, WeightEntry("2026-11-06", 70.4))
        assertEquals(listOf(WeightEntry("2026-11-05", 70.0), WeightEntry("2026-11-06", 70.4)), again)
        assertEquals(listOf(WeightEntry("2026-11-06", 70.4)), TargetSettingsRules.deleteWeight(again, "2026-11-05"))
    }

    @Test
    fun `presets list keys only, everything is every nutrient in order`() {
        assertEquals(WatchListPreset.STANDARD, WatchList.DEFAULT.preset)
        assertEquals(listOf("energyKcal", "proteinG", "carbohydratesG", "fatG", "fiberG", "sugarsG"), WatchList.DEFAULT.keys)
        assertEquals(listOf("energyKcal", "proteinG", "potassiumMg", "phosphorusMg", "sodiumMg"), WatchListPreset.KIDNEY.keys)
        assertEquals(NUTRIENT_KEYS, WatchListPreset.EVERYTHING.keys)
        assertEquals(24, WatchListPreset.EVERYTHING.keys.size)
    }

    @Test
    fun `choosing a preset replaces the keys, editing them makes the list custom`() {
        val sport = WatchList.of(WatchListPreset.SPORT)
        assertEquals(WatchListPreset.SPORT.keys, sport.keys)
        val edited = sport.withKeys(sport.keys - "waterG" + "ironMg")
        assertEquals(WatchListPreset.CUSTOM, edited.preset)
        assertEquals("custom", edited.preset.id)
        assertEquals(listOf("energyKcal", "proteinG", "carbohydratesG", "fatG", "fiberG", "ironMg"), edited.keys)
    }
}
