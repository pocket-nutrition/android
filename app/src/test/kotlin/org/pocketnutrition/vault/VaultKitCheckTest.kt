// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultKitCheckTest {

    private val passphrase = "acorn-banjo-cider-dwell-ember"

    @Test
    fun `the asked word is one of the passphrase words`() {
        val seen = (0 until 200).mapNotNull { VaultKitCheck.pickWordIndex(passphrase, Random(it)) }.toSet()
        assertEquals((0 until 5).toSet(), seen)
        assertNull(VaultKitCheck.pickWordIndex("onlyoneword"))
    }

    @Test
    fun `the typed word must match, ignoring case and spaces`() {
        assertTrue(VaultKitCheck.isRetypeCorrect("cider", passphrase, 2))
        assertTrue(VaultKitCheck.isRetypeCorrect("  Cider ", passphrase, 2))
        assertFalse(VaultKitCheck.isRetypeCorrect("banjo", passphrase, 2))
        assertFalse(VaultKitCheck.isRetypeCorrect("", passphrase, 2))
        assertFalse(VaultKitCheck.isRetypeCorrect("cider", passphrase, 9))
    }

    @Test
    fun `a chosen passphrase must be typed whole and exactly`() {
        val chosen = "Mon Café préféré 42"
        assertTrue(VaultKitCheck.isRetypeCorrect(chosen, chosen, null))
        // Same text with a decomposed accent: the KDF sees the same bytes, so it passes.
        assertTrue(VaultKitCheck.isRetypeCorrect("Mon Cafe\u0301 préféré 42", chosen, null))
        assertFalse(VaultKitCheck.isRetypeCorrect("mon café préféré 42", chosen, null))
        assertFalse(VaultKitCheck.isRetypeCorrect("$chosen ", chosen, null))
        assertFalse(VaultKitCheck.isRetypeCorrect("", "", null))
    }

    @Test
    fun `finishing needs the kit saved and the right answer`() {
        assertFalse(VaultKitCheck.canFinish(saved = false, "acorn", passphrase, 0))
        assertFalse(VaultKitCheck.canFinish(saved = true, "banjo", passphrase, 0))
        assertTrue(VaultKitCheck.canFinish(saved = true, "acorn", passphrase, 0))
        assertFalse(VaultKitCheck.canFinish(saved = false, "chosen phrase", "chosen phrase", null))
        assertFalse(VaultKitCheck.canFinish(saved = true, "chosen", "chosen phrase", null))
        assertTrue(VaultKitCheck.canFinish(saved = true, "chosen phrase", "chosen phrase", null))
    }
}
