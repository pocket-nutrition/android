// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Not corpus-driven: this table is Android-only, there is no web/iOS twin to stay pinned against. */
class CameraAccessTest {

    @Test
    fun `granted always wins, whatever the other two inputs say`() {
        assertEquals(CameraAccess.GRANTED, decideCameraAccess(granted = true, hasRequestedBefore = false, shouldShowRationale = false))
        assertEquals(CameraAccess.GRANTED, decideCameraAccess(granted = true, hasRequestedBefore = true, shouldShowRationale = true))
    }

    @Test
    fun `a fresh install's first tap is not determined`() {
        assertEquals(
            CameraAccess.NOT_DETERMINED,
            decideCameraAccess(granted = false, hasRequestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `denied once is retriable, since Android still offers the rationale`() {
        assertEquals(
            CameraAccess.DENIED_RETRIABLE,
            decideCameraAccess(granted = false, hasRequestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `denied twice, or don't-ask-again, is permanent`() {
        assertEquals(
            CameraAccess.PERMANENTLY_DENIED,
            decideCameraAccess(granted = false, hasRequestedBefore = true, shouldShowRationale = false),
        )
    }
}
