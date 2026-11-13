// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pocketnutrition.core.NutrientTarget
import org.pocketnutrition.core.TargetUnit
import org.pocketnutrition.core.dayProgress

/** Spec 05 §6: every kind fills the same way, a range is full between its bounds, past 100 % is a second lap. */
class RingPercentTest {

    private fun fill(min: Double?, max: Double?, eaten: Double): Int {
        val target = NutrientTarget("carbohydratesG", TargetUnit.G, min, max, null, emptyList())
        val progress = dayProgress(listOf(target), listOf(mapOf("carbohydratesG" to eaten)), listOf("carbohydratesG")).single()
        return ringPercent(progress, target)
    }

    @Test
    fun `a goal fills toward 100 and keeps going on a second lap`() {
        assertEquals(50, fill(200.0, null, 100.0))
        assertEquals(150, fill(200.0, null, 300.0))
    }

    @Test
    fun `a range is full anywhere between its bounds and laps only past the upper bound`() {
        assertEquals(50, fill(200.0, 300.0, 100.0))
        assertEquals(100, fill(200.0, 300.0, 280.0))
        assertEquals(155, fill(200.0, 300.0, 310.0))
    }

    @Test
    fun `a limit fills against its maximum and the second lap is capped at two laps`() {
        assertEquals(50, fill(null, 50.0, 25.0))
        assertEquals(200, fill(null, 50.0, 500.0))
    }
}
