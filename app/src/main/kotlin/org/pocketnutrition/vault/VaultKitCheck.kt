// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.vault

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/**
 * The gate in front of "finish" on an emergency kit: the kit must have left the screen (copied or shared)
 * AND the person must type part of the passphrase back. A generated passphrase asks for one randomly
 * chosen word; a passphrase the person chose asks for the whole of it, as on iOS. The maintainer lost a
 * vault because the kit was shown once and never saved; this makes saving it the only way forward.
 */
object VaultKitCheck {

    /** The words of a generated passphrase (hyphen-joined, see [VaultPassphraseSuggestion]). */
    fun words(passphrase: String): List<String> = passphrase.split('-').filter(String::isNotEmpty)

    /** A 0-based word index to ask for, or null when the passphrase has fewer than two words to pick from. */
    fun pickWordIndex(passphrase: String, random: Random = Random.Default): Int? {
        val count = words(passphrase).size
        return if (count < 2) null else random.nextInt(count)
    }

    /**
     * Whether [typed] passes the retype step: word [wordIndex] (case and surrounding spaces ignored), or,
     * when [wordIndex] is null, the whole passphrase exactly after the NFKC normalisation the KDF applies.
     */
    fun isRetypeCorrect(typed: String, passphrase: String, wordIndex: Int?): Boolean {
        if (wordIndex == null) {
            return typed.isNotEmpty() && Normalizer.normalize(typed, Normalizer.Form.NFKC) == Normalizer.normalize(passphrase, Normalizer.Form.NFKC)
        }
        val expected = words(passphrase).getOrNull(wordIndex) ?: return false
        return normalizeWord(typed) == normalizeWord(expected)
    }

    /** Whether "finish" may be enabled. */
    fun canFinish(saved: Boolean, typed: String, passphrase: String, wordIndex: Int?): Boolean =
        saved && isRetypeCorrect(typed, passphrase, wordIndex)

    private fun normalizeWord(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
