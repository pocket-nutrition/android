// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.AEADBadTagException

/** Where the recognition API key is kept. An interface so tests can fake it. */
interface SecretStore {
    /** The stored secret, or `null` when none is stored or it can no longer be decrypted. */
    fun read(): String?

    /** `false` when the secret could not be stored; nothing is stored in that case. */
    fun write(value: String): Boolean

    fun clear()
}

/**
 * The text form of an AES-GCM result as kept in SharedPreferences: `base64(iv):base64(ciphertext)`.
 * [unpack] returns `null` for anything that is not exactly that, rather than throwing, so a corrupt
 * entry reads as "no key".
 */
object SecretBlob {
    class Parts(val iv: ByteArray, val ciphertext: ByteArray)

    fun pack(iv: ByteArray, ciphertext: ByteArray): String {
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(iv) + ":" + encoder.encodeToString(ciphertext)
    }

    fun unpack(packed: String): Parts? {
        val pieces = packed.split(":")
        if (pieces.size != 2) return null
        return try {
            val decoder = Base64.getDecoder()
            val iv = decoder.decode(pieces[0])
            val ciphertext = decoder.decode(pieces[1])
            if (iv.isEmpty() || ciphertext.isEmpty()) null else Parts(iv, ciphertext)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** The stored blob (or the Keystore key it needs) is definitively unusable, as opposed to a transient failure. */
class UnusableSecretException(message: String) : Exception(message)

/**
 * Whether a failed decryption means the stored secret can never be read again (so it is dropped and the
 * user re-enters it) or may be a transient fault (so it is left alone and reads as "unset" this time).
 * Android's `KeyPermanentlyInvalidatedException` is matched by name to keep this unit-testable.
 */
fun shouldClearSecretAfter(e: Throwable): Boolean =
    e is AEADBadTagException ||
        e is UnrecoverableKeyException ||
        e is UnusableSecretException ||
        e.javaClass.name == "android.security.keystore.KeyPermanentlyInvalidatedException"
