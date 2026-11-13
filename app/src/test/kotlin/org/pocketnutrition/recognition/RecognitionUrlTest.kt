// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionUrlTest {

    @Test
    fun `https with a host is accepted`() {
        assertTrue(isValidRecognitionUrl("https://recognition.pocketnutrition.org"))
        assertTrue(isValidRecognitionUrl("https://192.168.1.5:8443/base"))
        assertTrue(isValidRecognitionUrl("HTTPS://Example.org"))
    }

    @Test
    fun `http is rejected, cleartext is not relaxed`() {
        assertFalse(isValidRecognitionUrl("http://recognition.pocketnutrition.org"))
    }

    @Test
    fun `missing scheme, missing host, other schemes and junk are rejected`() {
        listOf(
            "", "   ", "recognition.pocketnutrition.org", "192.168.1.5", "https://", "https:///path",
            "ftp://example.org", "https://example.org?token=1", "https://example.org/a#frag", "https://example.org/?", "file:///etc/passwd", "https://exa mple.org", "https://user:pw@example.org",
        ).forEach { assertFalse("'$it' must be rejected", isValidRecognitionUrl(it)) }
    }

    @Test
    fun `normalising trims whitespace and trailing slashes`() {
        assertEquals("https://example.org", normalizeRecognitionUrl("  https://example.org/  "))
        assertEquals("https://example.org/base", normalizeRecognitionUrl("https://example.org/base//"))
    }

    @Test
    fun `config is present only when both url and key are usable`() {
        assertEquals(
            RecognitionConfig("https://example.org", "k"),
            recognitionConfigOrNull("https://example.org/", " k "),
        )
        assertNull(recognitionConfigOrNull(null, "k"))
        assertNull(recognitionConfigOrNull("https://example.org", null))
        assertNull(recognitionConfigOrNull("https://example.org", "   "))
        assertNull(recognitionConfigOrNull("http://example.org", "k"))
    }

    @Test
    fun `api keys must be visible ascii`() {
        assertTrue(isValidApiKey("sk_live-AbC123.~"))
        assertTrue(isValidApiKey("  padded  "))
        listOf("", "  ", "has space", "new\nline", "ta\tb", "caf\u00e9", "\u0000", "a".repeat(513)).forEach {
            assertFalse("'$it'", isValidApiKey(it))
        }
        assertNull(recognitionConfigOrNull("https://example.org", "bad\nkey"))
    }

    @Test
    fun `origin compares scheme host and port case-insensitively`() {
        assertEquals(originOf("https://Example.org"), originOf("https://example.org:443/other/path"))
        assertTrue(originOf("https://example.org") != originOf("https://evil.example.org"))
        assertTrue(originOf("https://example.org") != originOf("https://example.org:8443"))
        assertEquals("example.org", hostOf("https://example.org/x"))
    }
}
