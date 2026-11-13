// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {

    @Test
    fun `schedule doubles from 3 seconds and caps at 30`() {
        val schedule = (0..6).map { backoffDelayMs(it) }
        assertEquals(listOf(3_000L, 6_000L, 12_000L, 24_000L, 30_000L, 30_000L, 30_000L), schedule)
    }

    @Test
    fun `huge or negative attempts never overflow`() {
        assertEquals(30_000L, backoffDelayMs(10_000))
        assertEquals(3_000L, backoffDelayMs(-5))
    }
}
