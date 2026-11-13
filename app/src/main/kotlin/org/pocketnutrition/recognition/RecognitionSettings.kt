// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.recognition

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Where the voice-recognition server lives. Separate from [org.pocketnutrition.api.ApiSettings], which
 * points at the nutrition API and is untouched.
 *
 * The URL and the encrypted key blob share one prefs file, `recognition_settings`, which is excluded
 * from backup (see res/xml/backup_rules.xml and data_extraction_rules.xml): a restored blob could not
 * be decrypted on another device anyway, because the Keystore key never leaves the original one.
 *
 * [init] must run once from MainActivity.onCreate, the same explicit pattern as `ApiSettings`.
 */
object RecognitionSettings {

    const val PREFS_NAME = "recognition_settings"
    private const val KEY_URL = "server_url"

    private var prefs: SharedPreferences? = null
    private var secrets: SecretStore? = null

    enum class SaveResult { SAVED, URL_INVALID, KEY_INVALID, KEY_REQUIRED_FOR_NEW_HOST, STORAGE_FAILED }

    fun init(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        val store = KeystoreSecretStore(sharedPrefs)
        secrets = store
        // Warm the in-memory key cache off the main thread.
        Thread { store.read() }.apply { isDaemon = true }.start()
    }

    /** The URL as saved, for prefilling the field. */
    val rawUrl: String? get() = prefs?.getString(KEY_URL, null)

    /** Whether a key is currently stored and decryptable. */
    val hasApiKey: Boolean get() = secrets?.read() != null

    /** `null` until both a valid https URL and a key are set. */
    val config: RecognitionConfig? get() = recognitionConfigOrNull(rawUrl, secrets?.read())

    /**
     * Saves [url] (must be https) and, when [apiKey] is non-blank, the key. A blank key leaves any
     * stored key untouched, so the URL can be edited without retyping a secret the screen never shows —
     * EXCEPT when the origin changes: a stored token is never replayed to a different host, so a new
     * origin requires the key to be entered again.
     */
    fun save(url: String, apiKey: String): SaveResult {
        if (!isValidRecognitionUrl(url)) return SaveResult.URL_INVALID
        val key = apiKey.trim()
        if (key.isNotEmpty() && !isValidApiKey(key)) return SaveResult.KEY_INVALID
        val previous = rawUrl
        if (key.isEmpty() && previous != null && originOf(previous) != originOf(url)) {
            return SaveResult.KEY_REQUIRED_FOR_NEW_HOST
        }
        if (key.isNotEmpty() && secrets?.write(key) != true) return SaveResult.STORAGE_FAILED
        prefs?.edit { putString(KEY_URL, normalizeRecognitionUrl(url)) }
        return SaveResult.SAVED
    }

    fun clear() {
        prefs?.edit { remove(KEY_URL) }
        secrets?.clear()
    }
}
