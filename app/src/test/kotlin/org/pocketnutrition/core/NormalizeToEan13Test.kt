// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Not corpus-driven: this function has no web/iOS twin to stay pinned against, it is Android-only. */
class NormalizeToEan13Test {

    @Test
    fun `a 13-digit code passes through unchanged`() {
        assertEquals("3017620422003", normalizeToEan13("3017620422003"))
    }

    @Test
    fun `a 12-digit UPC-A is zero-padded to 13 digits`() {
        assertEquals("0049000028911", normalizeToEan13("049000028911"))
    }

    @Test
    fun `an 8-digit EAN-8 is not expanded`() {
        assertNull(normalizeToEan13("12345678"))
    }

    @Test
    fun `non-digit garbage is rejected`() {
        assertNull(normalizeToEan13("not-a-barcode"))
    }

    @Test
    fun `an empty payload is rejected`() {
        assertNull(normalizeToEan13(""))
    }

    @Test
    fun `non-digit separators inside an otherwise 13-digit payload are stripped first`() {
        assertEquals("3017620422003", normalizeToEan13("301-762-042-2003"))
    }
}
