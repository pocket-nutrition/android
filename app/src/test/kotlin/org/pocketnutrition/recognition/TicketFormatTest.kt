// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketFormatTest {
    @Test
    fun `elapsed timer renders m ss`() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:09", formatElapsed(9_999))
        assertEquals("1:00", formatElapsed(60_000))
        assertEquals("0:00", formatElapsed(-5))
    }

    @Test
    fun `compact durations pick the coarsest sensible unit`() {
        assertEquals("<1 min", compactDuration(59_999))
        assertEquals("5 min", compactDuration(5 * 60_000L))
        assertEquals("3 h", compactDuration(3 * 3_600_000L + 1))
        assertEquals("2 d", compactDuration(49 * 3_600_000L))
        assertEquals("<1 min", compactDuration(-1))
    }

    @Test
    fun `clips under one second are not usable`() {
        org.junit.Assert.assertFalse(isUsableRecording(999))
        org.junit.Assert.assertTrue(isUsableRecording(1_000))
    }
}
