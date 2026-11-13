// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps one secret encrypted with an AES-256-GCM key that lives in the Android Keystore; only the IV
 * and ciphertext (see [SecretBlob]) are written to [prefs]. Plain platform APIs, no dependency —
 * `EncryptedSharedPreferences` would be the library route and is deliberately not used.
 *
 * A definitive failure to decrypt (key missing or invalidated, bad GCM tag, unparseable blob) means "no
 * key is set": the stale entry is dropped and [read] returns `null`, so the user is asked to enter the
 * key again. Any other failure also reads as unset but leaves the entry alone, since it may be transient.
 */
class KeystoreSecretStore(
    private val prefs: SharedPreferences,
    // Defaults are the recognition key's: changing them would orphan an existing install's stored key.
    private val keyAlias: String = RECOGNITION_KEY_ALIAS,
    private val blobKey: String = RECOGNITION_BLOB_KEY,
) : SecretStore {

    // Decrypted once and kept in memory, so composition never does Keystore work on the main thread
    // after the first read. Invalidated by write() and clear().
    private val lock = Any()
    private var cached: String? = null
    private var cacheLoaded = false
    private var unavailableUntilMs = 0L

    override fun read(): String? = synchronized(lock) {
        if (cacheLoaded) return cached
        // A transient failure is remembered briefly so composition does not re-hit the Keystore.
        if (System.currentTimeMillis() < unavailableUntilMs) return null
        val value = decryptStored()
        // A transient failure is not cached, so the next read tries again.
        if (value.settled) {
            cached = value.secret
            cacheLoaded = true
        } else {
            unavailableUntilMs = System.currentTimeMillis() + TRANSIENT_BACKOFF_MS
        }
        value.secret
    }

    private class Decrypted(val secret: String?, val settled: Boolean)

    private fun decryptStored(): Decrypted {
        val packed = prefs.getString(blobKey, null) ?: return Decrypted(null, true)
        return try {
            val parts = SecretBlob.unpack(packed) ?: throw UnusableSecretException("unreadable blob")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(createIfMissing = false), GCMParameterSpec(TAG_BITS, parts.iv))
            Decrypted(String(cipher.doFinal(parts.ciphertext), Charsets.UTF_8), true)
        } catch (e: Exception) {
            // Only a definitive failure drops the stored entry; anything else may pass.
            if (shouldClearSecretAfter(e)) {
                prefs.edit { remove(blobKey) }
                Decrypted(null, true)
            } else {
                Decrypted(null, false)
            }
        }
    }

    override fun write(value: String): Boolean = synchronized(lock) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(createIfMissing = true))
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit { putString(blobKey, SecretBlob.pack(cipher.iv, ciphertext)) }
            cached = value
            cacheLoaded = true
            unavailableUntilMs = 0L
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun clear() {
        synchronized(lock) {
            prefs.edit { remove(blobKey) }
            cached = null
            cacheLoaded = true
        }
    }

    private fun key(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        // Decrypting with a freshly generated key could only ever fail; a missing key means the blob is dead.
        if (!createIfMissing) throw UnusableSecretException("keystore key missing")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val RECOGNITION_KEY_ALIAS = "pocket_nutrition_recognition_key"
        const val RECOGNITION_BLOB_KEY = "api_key_blob"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val TRANSIENT_BACKOFF_MS = 30_000L
    }
}
