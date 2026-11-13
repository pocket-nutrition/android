// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.model

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pocketnutrition.api.NutritionalProfile
import org.pocketnutrition.core.sumNutrients

class UnavailableItemTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(extra: String) = json.decodeFromString<NutritionalProfile>(
        """{"name":"zorglub","quantity":100,"unit":"g","cookingMethod":"raw","measuredState":"raw",
           "energyKcal":0,"proteinG":0,"fatG":0,"carbohydratesG":0,"fiberG":null$extra}""",
    )

    private fun rice(available: Boolean = true) = NutritionalProfile(
        "riz", 150.0, "g", "boiled", "cooked", 195.0, 4.0, 0.5, 42.0, fiberG = 0.6, available = available,
    )

    @Test
    fun `available decodes when the server sends it`() {
        assertFalse(decode(""","source":"unavailable","available":false""").available)
        assertTrue(decode(""","source":"ml","available":true""").available)
    }

    @Test
    fun `a profile without the available field is available`() {
        assertTrue(decode("").available)
    }

    @Test
    fun `an unavailable profile has no known nutrients`() {
        assertEquals(emptyMap<String, Double>(), decode(""","available":false""").nutrients())
    }

    @Test
    fun `meal totals and nutrients ignore an unavailable item`() {
        val unknown = rice(available = false)
        val profiles = listOf(rice(), unknown)
        assertEquals(Macros.of(rice()), Macros.sumOf(profiles))
        assertEquals(rice().nutrients(), profiles.map { it.nutrients() }.fold(emptyMap(), ::sumNutrients))
    }

    @Test
    fun `analysis marks the matching saved items as unavailable`() {
        val items = listOf("riz", "zorglub").map {
            MealItem(ingredientId = it, label = it, quantityG = 100.0, cookingMethod = "raw", measuredState = "raw")
        }
        val marked = withAvailability(items, listOf(rice(), rice(available = false)))
        assertEquals(listOf(true, false), marked.map { it.available })
    }

    @Test
    fun `the flag survives a journal round trip`() {
        val dir = Files.createTempDirectory("journal").toFile()
        val file = File(dir, "journal.json")
        val known = MealItem(ingredientId = "rice", label = "Rice", quantityG = 150.0, cookingMethod = "boiled", measuredState = "cooked")
        val unknown = known.copy(id = "u", ingredientId = null, label = "Zorglub", available = false)
        JournalStore(file).save(listOf(known, unknown), Macros(195.0, 4.0, 42.0, 0.5), atEpochMs = 1_793_622_600_000L)

        val reloaded = JournalStore(file).meals.single()
        assertEquals(listOf(true, false), reloaded.items.map { it.available })
        dir.deleteRecursively()
    }

    @Test
    fun `a journal item written without the flag loads as available`() {
        val dir = Files.createTempDirectory("journal").toFile()
        val file = File(dir, "journal.json")
        file.writeText(
            """{"version":1,"meals":[{"id":"m","savedAtEpochMs":1793622600000,"items":[{"id":"i",
               "ingredientId":"rice","label":"Rice","quantityG":150,"cookingMethod":"boiled","measuredState":"cooked"}],
               "totals":{"energyKcal":195,"proteinG":4,"carbohydratesG":42,"fatG":0.5}}]}""",
        )
        assertTrue(JournalStore(file).meals.single().items.single().available)
        dir.deleteRecursively()
    }
}
