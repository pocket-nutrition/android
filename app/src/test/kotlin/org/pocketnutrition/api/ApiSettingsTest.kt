// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [isValidServerUrl] only — the pure half of [ApiSettings]. The persistence half needs a
 * [android.content.SharedPreferences] instance, which this module's plain-JUnit test setup (no
 * Robolectric) cannot provide; splitting the validation out is what keeps it testable without one.
 */
class ApiSettingsTest {

    @Test
    fun `accepts https and http URLs with a host`() {
        assertTrue(isValidServerUrl("https://api.pocketnutrition.org"))
        assertTrue(isValidServerUrl("http://192.168.1.5:8080"))
        assertTrue(isValidServerUrl("https://my-server.example/api"))
    }

    @Test
    fun `rejects a blank string`() {
        assertFalse(isValidServerUrl(""))
        assertFalse(isValidServerUrl("   "))
    }

    @Test
    fun `rejects a bare host with no scheme`() {
        // Without this check, URI parses this as a relative path with no host at all — silently
        // resolving to nothing rather than to the intended server.
        assertFalse(isValidServerUrl("192.168.1.5"))
    }

    @Test
    fun `rejects an unsupported scheme`() {
        assertFalse(isValidServerUrl("ftp://example.org"))
    }

    @Test
    fun `rejects a scheme with no host`() {
        assertFalse(isValidServerUrl("https://"))
    }
}
